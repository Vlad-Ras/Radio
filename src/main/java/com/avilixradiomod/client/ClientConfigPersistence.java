package com.avilixradiomod.client;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.config.ModConfigs;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the mod master volume to the client TOML.
 *
 * NeoForge loads configs normally, but some runtime edits via a custom GUI slider
 * may not be written back to disk in all environments (especially when only the
 * integrated server is restarted). This helper performs an explicit write.
 */
public final class ClientConfigPersistence {
    private ClientConfigPersistence() {}

    private static final String CLIENT_TOML = "avilixradiomod-client.toml";
    private static final String KEY_GLOBAL_VOLUME = "audio.globalVolume";

    /**
     * Reads audio.globalVolume from avilixradiomod-client.toml (if it exists) and pushes
     * it into {@link ModConfigs#CLIENT} so the slider and audio controller start with
     * the persisted value.
     */
    public static void loadGlobalVolumeIntoConfig() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve(CLIENT_TOML);
            if (!Files.exists(path)) return;

            CommentedFileConfig cfg = CommentedFileConfig.builder(path)
                    .sync()
                    .preserveInsertionOrder()
                    .build();
            cfg.load();

            Object raw = cfg.get(KEY_GLOBAL_VOLUME);
            Double v = null;
            if (raw instanceof Number n) {
                v = n.doubleValue();
            } else if (raw instanceof String s) {
                try { v = Double.parseDouble(s); } catch (NumberFormatException ignored) {}
            }

            if (v != null) {
                ModConfigs.CLIENT.globalVolume.set(clamp01(v));
            }

            cfg.close();
        } catch (Throwable t) {
            AvilixRadioMod.LOGGER.warn("Failed to load {} from client config", KEY_GLOBAL_VOLUME, t);
        }
    }

    /**
     * Writes audio.globalVolume to avilixradiomod-client.toml.
     */
    public static void saveGlobalVolume(double value) {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve(CLIENT_TOML);

            CommentedFileConfig cfg = CommentedFileConfig.builder(path)
                    .sync()
                    .preserveInsertionOrder()
                    .build();

            cfg.load();
            cfg.set(KEY_GLOBAL_VOLUME, clamp01(value));
            cfg.save();
            cfg.close();
        } catch (Throwable t) {
            AvilixRadioMod.LOGGER.warn("Failed to save {} to client config", KEY_GLOBAL_VOLUME, t);
        }
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
