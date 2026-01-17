package com.avilixradiomod.blockentity;

import com.avilixradiomod.block.SpeakerBlock;
import com.avilixradiomod.registry.ModBlockEntities;
import com.avilixradiomod.server.data.RadioWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

public class SpeakerBlockEntity extends BlockEntity {

    private static final String TAG_LINKED = "Linked";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_DIM = "Dim";

    // Cached radio state (so the speaker keeps working even if the radio chunk is unloaded)
    private static final String TAG_CACHED_URL = "CachedUrl";
    private static final String TAG_CACHED_PLAYING = "CachedPlaying";
    private static final String TAG_CACHED_VOLUME = "CachedVolume";

    @Nullable
    private BlockPos radioPos;
    @Nullable
    private ResourceLocation radioDim;

    // Last known radio state (server-authoritative), synced to the client via BE update packet.
    private String cachedUrl = "";
    private boolean cachedPlaying = false;
    private int cachedVolume = 100;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    @Nullable
    public BlockPos getRadioPos() {
        return radioPos;
    }

    @Nullable
    public ResourceLocation getRadioDim() {
        return radioDim;
    }

    public void setRadioLink(@Nullable BlockPos pos, @Nullable ResourceLocation dim) {
        this.radioPos = pos;
        this.radioDim = dim;

        // Reset cached state when re-linking.
        this.cachedUrl = "";
        this.cachedPlaying = false;
        this.cachedVolume = 100;
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            // ^ вместо 3, чтобы точно ушло клиенту
        }
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (radioPos != null && radioDim != null) {
            CompoundTag linked = new CompoundTag();
            linked.putInt(TAG_X, radioPos.getX());
            linked.putInt(TAG_Y, radioPos.getY());
            linked.putInt(TAG_Z, radioPos.getZ());
            linked.putString(TAG_DIM, radioDim.toString());
            tag.put(TAG_LINKED, linked);
        }

        tag.putString(TAG_CACHED_URL, cachedUrl == null ? "" : cachedUrl);
        tag.putBoolean(TAG_CACHED_PLAYING, cachedPlaying);
        tag.putInt(TAG_CACHED_VOLUME, cachedVolume);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains(TAG_LINKED)) {
            CompoundTag linked = tag.getCompound(TAG_LINKED);
            radioPos = new BlockPos(
                    linked.getInt(TAG_X),
                    linked.getInt(TAG_Y),
                    linked.getInt(TAG_Z)
            );
            String dim = linked.getString(TAG_DIM);
            radioDim = dim.isEmpty() ? null : ResourceLocation.tryParse(dim);
        } else {
            radioPos = null;
            radioDim = null;
        }

        cachedUrl = tag.getString(TAG_CACHED_URL);
        cachedPlaying = tag.getBoolean(TAG_CACHED_PLAYING);
        cachedVolume = tag.contains(TAG_CACHED_VOLUME) ? tag.getInt(TAG_CACHED_VOLUME) : 100;
    }

    // ------------------------------------------------------------------
    // Cached getters (client uses these to actually play audio)
    // ------------------------------------------------------------------

    public String getCachedUrl() {
        return cachedUrl;
    }

    public boolean isCachedPlaying() {
        return cachedPlaying;
    }

    public int getCachedVolume() {
        return cachedVolume;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------
    // SERVER TICK — включает / выключает анимацию
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity speaker) {
        if ((level.getGameTime() % 10L) != 0L) return; // раз в 10 тиков

        boolean cachedChanged = false;

        // Default: keep whatever we already know.
        boolean shouldBePlaying = speaker.cachedPlaying && speaker.cachedUrl != null && !speaker.cachedUrl.isBlank();

        if (speaker.radioPos != null
                && speaker.radioDim != null
                && speaker.radioDim.equals(level.dimension().location())) {

            // Important:
            // - If the chunk is NOT loaded -> keep cached state (this is the whole point)
            // - If the chunk IS loaded but BE is missing / not a radio -> clear cache (radio was removed)
            boolean chunkLoaded = false;
            try {
                chunkLoaded = level.hasChunkAt(speaker.radioPos);
            } catch (Throwable t) {
                // Fallback for mappings/edge cases
                try {
                    chunkLoaded = level.isLoaded(speaker.radioPos);
                } catch (Throwable t2) {
                    chunkLoaded = false;
                }
            }

            if (chunkLoaded) {
                BlockEntity be = level.getBlockEntity(speaker.radioPos);
                if (be instanceof RadioBlockEntity radio) {
                    String url = radio.getUrl() == null ? "" : radio.getUrl().trim();
                    boolean playing = radio.isPlaying() && !url.isBlank();
                    int vol = Math.max(0, Math.min(100, radio.getVolume()));

                    if (!url.equals(speaker.cachedUrl)) {
                        speaker.cachedUrl = url;
                        cachedChanged = true;
                    }
                    if (speaker.cachedPlaying != playing) {
                        speaker.cachedPlaying = playing;
                        cachedChanged = true;
                    }
                    if (speaker.cachedVolume != vol) {
                        speaker.cachedVolume = vol;
                        cachedChanged = true;
                    }

                    shouldBePlaying = playing;
                } else {
                    // Chunk is loaded but the radio is gone/replaced.
                    if (!speaker.cachedUrl.isEmpty()) { speaker.cachedUrl = ""; cachedChanged = true; }
                    if (speaker.cachedPlaying) { speaker.cachedPlaying = false; cachedChanged = true; }
                    if (speaker.cachedVolume != 100) { speaker.cachedVolume = 100; cachedChanged = true; }
                    shouldBePlaying = false;
                }
            } else if (!level.isClientSide && level instanceof net.minecraft.server.level.ServerLevel sl) {
                // Chunk is NOT loaded: use world SavedData published by the radio itself.
                RadioWorldState.Entry e = RadioWorldState.get(sl).get(speaker.radioPos);
                if (e != null) {
                    String url = e.url() == null ? "" : e.url().trim();
                    boolean playing = e.playing() && !url.isBlank();
                    int vol = Math.max(0, Math.min(100, e.volume()));

                    if (!url.equals(speaker.cachedUrl)) { speaker.cachedUrl = url; cachedChanged = true; }
                    if (speaker.cachedPlaying != playing) { speaker.cachedPlaying = playing; cachedChanged = true; }
                    if (speaker.cachedVolume != vol) { speaker.cachedVolume = vol; cachedChanged = true; }

                    shouldBePlaying = playing;
                }
            }
        } else {
            // Not linked / different dimension.
            if (!speaker.cachedUrl.isEmpty()) { speaker.cachedUrl = ""; cachedChanged = true; }
            if (speaker.cachedPlaying) { speaker.cachedPlaying = false; cachedChanged = true; }
            if (speaker.cachedVolume != 100) { speaker.cachedVolume = 100; cachedChanged = true; }
            shouldBePlaying = false;
        }

        boolean isPlaying = state.getValue(SpeakerBlock.PLAYING);

        if (cachedChanged) {
            speaker.setChanged();
            level.sendBlockUpdated(speaker.worldPosition, state, state, Block.UPDATE_ALL);
        }

        if (isPlaying != shouldBePlaying) {
            level.setBlock(
                    pos,
                    state.setValue(SpeakerBlock.PLAYING, shouldBePlaying),
                    3
            );
        }
    }
}
