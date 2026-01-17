package com.avilixradiomod.client.audio;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.blockentity.SpeakerBlockEntity;
import com.avilixradiomod.config.ModConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side audio controller.
 *
 * Key rule to prevent "double sound":
 *  - For each distinct URL, we decode/play the stream ONLY ONCE.
 *  - If multiple blocks (radio/speakers) use the same URL nearby, we choose a single "best" emitter
 *    (radio has priority over speaker) and compute volume from that emitter.
 */
public final class RadioAudioController {
    private RadioAudioController() {}

    private static final class Candidate {
        final BlockPos pos;
        final int priority; // 2 = radio, 1 = speaker
        final float targetVolume; // 0..100

        Candidate(BlockPos pos, int priority, float targetVolume) {
            this.pos = pos;
            this.priority = priority;
            this.targetVolume = targetVolume;
        }
    }

    private static final class StreamInstance {
        final String url;
        Mp3StreamPlayer player;
        float smoothVolume = 0f; // 0..100
        float targetVolume = 0f; // 0..100
        BlockPos currentEmitter = null;
        long cooldownUntilMs = 0L;

        StreamInstance(String url) {
            this.url = url;
        }

        void stop() {
            if (player != null) {
                try { player.stop(); } catch (Throwable ignored) {}
            }
            player = null;
            smoothVolume = 0f;
            targetVolume = 0f;
            currentEmitter = null;
        }
    }

    private static final Map<String, StreamInstance> INSTANCES = new HashMap<>();
    private static int tickCounter = 0;

    public static void clientTick() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stopAll();
            return;
        }

        final int scanEveryTicks = ModConfigs.COMMON.scanEveryTicks.get();
        if (scanEveryTicks <= 0) return;

        final boolean doScan = (++tickCounter % scanEveryTicks) == 0;
        if (doScan) {
            rescanAndRetarget(mc);
        }
        updatePlayback(mc);
    }

    private static void rescanAndRetarget(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        final int maxDist = ModConfigs.COMMON.maxHearDistance.get();
        final Vec3 listener = mc.player.position();
        final AABB box = new AABB(listener.x - maxDist, listener.y - maxDist, listener.z - maxDist,
                listener.x + maxDist, listener.y + maxDist, listener.z + maxDist);

        // Gather best candidate per URL.
        final Map<String, Candidate> bestByUrl = new HashMap<>();

        scanLoadedAudioBlockEntities(mc, box, (pos, urlRaw, playingRaw, volumeRaw, priority) -> {
            final String url = safeUrl(urlRaw);
            if (url.isEmpty()) return;
            if (!playingRaw) return;

            final float target = computeTargetVolume(listener, pos, volumeRaw, maxDist);
            if (target <= 0.001f) return;

            final Candidate cand = new Candidate(pos.immutable(), priority, target);
            final Candidate prev = bestByUrl.get(url);
            if (prev == null || isBetter(cand, prev)) {
                bestByUrl.put(url, cand);
            }
        });

        // Apply targets to instances.
        // 1) Update or create instances for URLs we see.
        for (var e : bestByUrl.entrySet()) {
            final String url = e.getKey();
            final Candidate best = e.getValue();
            final StreamInstance inst = INSTANCES.computeIfAbsent(url, StreamInstance::new);
            inst.targetVolume = clampVol(best.targetVolume);
            inst.currentEmitter = best.pos;
        }

        // 2) URLs not seen -> target 0 (fade out and stop).
        for (var inst : new ArrayList<>(INSTANCES.values())) {
            if (!bestByUrl.containsKey(inst.url)) {
                inst.targetVolume = 0f;
                inst.currentEmitter = null;
            }
        }
    }

    private static boolean isBetter(Candidate a, Candidate b) {
        // We want to remove doubling but keep the point of speakers.
        // So we DON'T hard-force "radio always wins".
        // Instead, we give radio a small bonus so it wins when volumes are comparable.
        final float aScore = a.targetVolume + (a.priority == 2 ? 5.0f : 0.0f);
        final float bScore = b.targetVolume + (b.priority == 2 ? 5.0f : 0.0f);
        return aScore > bScore;
    }

    private interface AudioSourceConsumer {
        void accept(BlockPos pos, String url, boolean playing, int volume, int priority);
    }

    private static void scanLoadedAudioBlockEntities(Minecraft mc, AABB box, AudioSourceConsumer consumer) {
        if (mc.level == null) return;
        int minChunkX = Mth.floor(box.minX) >> 4;
        int maxChunkX = Mth.floor(box.maxX) >> 4;
        int minChunkZ = Mth.floor(box.minZ) >> 4;
        int maxChunkZ = Mth.floor(box.maxZ) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values()) {
                    if (be instanceof RadioBlockEntity radio) {
                        final BlockPos pos = radio.getBlockPos();
                        if (!box.contains(Vec3.atCenterOf(pos))) continue;
                        consumer.accept(pos, radio.getUrl(), radio.isPlaying(), radio.getVolume(), 2);
                    } else if (be instanceof SpeakerBlockEntity speaker) {
                        final BlockPos pos = speaker.getBlockPos();
                        if (!box.contains(Vec3.atCenterOf(pos))) continue;
                        consumer.accept(pos, speaker.getCachedUrl(), speaker.isCachedPlaying(), speaker.getCachedVolume(), 1);
                    }
                }
            }
        }
    }

    private static void updatePlayback(Minecraft mc) {
        final float smoothing = clamp01((float) ModConfigs.COMMON.smoothing.get().doubleValue());
        final float stopThreshold = (float) ModConfigs.COMMON.stopThreshold.get().doubleValue();
        final long now = System.currentTimeMillis();

        final List<String> toRemove = new ArrayList<>();

        for (StreamInstance inst : INSTANCES.values()) {
            // Smooth towards target.
            inst.smoothVolume = inst.smoothVolume + (inst.targetVolume - inst.smoothVolume) * smoothing;
            if (inst.smoothVolume < 0.001f) inst.smoothVolume = 0f;

            // Stop when inaudible.
            if (inst.targetVolume <= 0.001f && inst.smoothVolume <= stopThreshold) {
                inst.stop();
                toRemove.add(inst.url);
                continue;
            }

            // If target is very low -> fade without decoding.
            if (inst.targetVolume <= 0.001f) {
                if (inst.player != null) {
                    try {
                        inst.player.setVolume(Math.round(inst.smoothVolume));
                    } catch (Throwable t) {
                        inst.stop();
                        inst.cooldownUntilMs = now + 10_000L;
                    }
                }
                continue;
            }

            // Failure cooldown.
            if (inst.cooldownUntilMs > now && inst.player == null) {
                continue;
            }

            // Start or update.
            if (inst.player == null) {
                try {
                    inst.player = new Mp3StreamPlayer();
                    inst.player.play(inst.url, Math.round(inst.smoothVolume));
                } catch (Throwable t) {
                    inst.stop();
                    inst.cooldownUntilMs = now + 10_000L;
                    continue;
                }
            } else {
                // if stream failed internally, restart with cooldown
                if (inst.player.consumeFailed()) {
                    inst.stop();
                    inst.cooldownUntilMs = now + 5_000L;
                    continue;
                }
                try {
                    inst.player.setVolume(Math.round(inst.smoothVolume));
                } catch (Throwable t) {
                    inst.stop();
                    inst.cooldownUntilMs = now + 10_000L;
                }
            }
        }

        for (String url : toRemove) {
            INSTANCES.remove(url);
        }
    }

    private static float computeTargetVolume(Vec3 listener, BlockPos source, int sourceVolume, int maxDist) {
        if (sourceVolume <= 0) return 0f;
        final Vec3 p = Vec3.atCenterOf(source);
        final double dist = listener.distanceTo(p);
        if (dist >= maxDist) return 0f;

        final float atten = (float) (1.0 - (dist / (double) maxDist));

        // Global mod master volume (0..1) from the Minecraft sound settings slider.
        final float master = clamp01((float) ModConfigs.CLIENT.globalVolume.get().doubleValue());

        return clamp01(atten) * clamp01(sourceVolume / 100.0f) * master * 100.0f;
    }

    private static String safeUrl(String url) {
        if (url == null) return "";
        url = url.trim();
        if (url.isEmpty()) return "";
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return "";
        int max = ModConfigs.COMMON.maxUrlLength.get();
        if (max > 0 && url.length() > max) url = url.substring(0, max);
        return url;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float clampVol(float v) {
        return Math.max(0f, Math.min(100f, v));
    }

    public static void stopAll() {
        for (StreamInstance inst : INSTANCES.values()) {
            inst.stop();
        }
        INSTANCES.clear();
    }
}
