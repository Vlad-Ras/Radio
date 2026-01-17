package com.avilixradiomod.server.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-dimension persistent store of radio state.
 *
 * Purpose:
 *  - Speakers must keep working even when the linked radio chunk is NOT loaded.
 *  - We store the last known state for each Radio block position.
 */
public final class RadioWorldState extends SavedData {

    private static final String DATA_NAME = "avilixradiomod_radio_state";
    private static final String TAG_ENTRIES = "Entries";

    private final Map<Long, Entry> byPos = new HashMap<>();

    public record Entry(String url, boolean playing, int volume) {}

    /**
     * Minecraft/NeoForge changed {@link net.minecraft.world.level.saveddata.SavedData.Factory}
     * constructor signatures multiple times (2 args vs 3 args with a DataFixTypes parameter).
     *
     * To keep this mod compiling across mappings/patch versions, we instantiate the factory
     * reflectively and pass a best-effort DataFixTypes when required.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final SavedData.Factory<RadioWorldState> FACTORY = (SavedData.Factory) createFactoryCompat();

    private static Object createFactoryCompat() {
        try {
            // The real runtime order is NOT stable across mappings/patches.
            // We detect parameter types and place arguments accordingly.
            var loadFn = (java.util.function.BiFunction<CompoundTag, HolderLookup.Provider, RadioWorldState>) RadioWorldState::load;
            var supplier = (java.util.function.Supplier<RadioWorldState>) RadioWorldState::new;

            for (var ctor : SavedData.Factory.class.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length != 2 && p.length != 3) continue;

                Object[] args = new Object[p.length];
                boolean ok = true;
                for (int i = 0; i < p.length; i++) {
                    Class<?> pi = p[i];
                    if (pi.isAssignableFrom(java.util.function.Supplier.class)) {
                        args[i] = supplier;
                    } else if (pi.isAssignableFrom(java.util.function.BiFunction.class)) {
                        args[i] = loadFn;
                    } else {
                        // Usually DataFixTypes (enum) in newer versions.
                        args[i] = resolveDataFixTypesConstant(pi);
                    }

                    // If we couldn't match a required functional interface, skip this ctor.
                    if (args[i] == null && (pi.isAssignableFrom(java.util.function.Supplier.class)
                            || pi.isAssignableFrom(java.util.function.BiFunction.class))) {
                        ok = false;
                        break;
                    }
                }

                if (!ok) continue;
                return ctor.newInstance(args);
            }
            throw new IllegalStateException("No compatible SavedData.Factory constructor found");
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create SavedData.Factory for RadioWorldState", t);
        }
    }

    private static Object resolveDataFixTypesConstant(Class<?> paramType) {
        try {
            // Most common type: net.minecraft.util.datafix.DataFixTypes (an enum)
            if (paramType.isEnum()) {
                for (String name : new String[]{"SAVED_DATA", "LEVEL", "CHUNK", "PLAYER"}) {
                    try {
                        return java.lang.Enum.valueOf((Class) paramType, name);
                    } catch (IllegalArgumentException ignored) {
                        // keep trying
                    }
                }
                // Fallback: first enum constant
                Object[] constants = paramType.getEnumConstants();
                if (constants != null && constants.length > 0) return constants[0];
            }
        } catch (Throwable ignored) {
        }
        return null; // best-effort
    }

    public static RadioWorldState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public RadioWorldState() {}

    public static RadioWorldState load(CompoundTag tag, HolderLookup.Provider registries) {
        RadioWorldState st = new RadioWorldState();
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            long pos = e.getLong("Pos");
            String url = e.getString("Url");
            boolean playing = e.getBoolean("Playing");
            int volume = e.contains("Volume") ? e.getInt("Volume") : 100;
            st.byPos.put(pos, new Entry(url, playing, volume));
        }
        return st;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var e : byPos.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey());
            Entry v = e.getValue();
            t.putString("Url", v.url() == null ? "" : v.url());
            t.putBoolean("Playing", v.playing());
            t.putInt("Volume", v.volume());
            list.add(t);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    public void update(BlockPos pos, String url, boolean playing, int volume) {
        long key = pos.asLong();
        byPos.put(key, new Entry(url == null ? "" : url, playing, Math.max(0, Math.min(100, volume))));
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (byPos.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public Entry get(BlockPos pos) {
        return byPos.get(pos.asLong());
    }
}
