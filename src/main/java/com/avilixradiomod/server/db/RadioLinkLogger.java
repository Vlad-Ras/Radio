package com.avilixradiomod.server.db;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.config.ModConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class RadioLinkLogger {
    private RadioLinkLogger() {}

    private static final String INSERT_SQL =
            "INSERT INTO avilix_radio_links (player_uuid, player_name, url, dimension, x, y, z) VALUES (?,?,?,?,?,?,?)";

    public static void logPastedLink(ServerPlayer player, BlockPos radioPos, String url) {
        if (!RadioDatabase.isEnabled()) return;
        if (url == null || url.isBlank()) return;

        // Clamp to config max length (server-side safety)
        final int maxLen = ModConfigs.COMMON.maxUrlLength.get();
        final String finalUrl = (url.length() > maxLen) ? url.substring(0, maxLen) : url;

        final String uuid = player.getUUID().toString();
        final String name = player.getGameProfile().getName();
        final ResourceLocation dim = player.serverLevel().dimension().location();
        final String dimension = dim.toString();

        RadioDatabase.submit(() -> {
            try (Connection c = RadioDatabase.getConnection()) {
                if (c == null) return;
                try (PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
                    ps.setString(1, uuid);
                    ps.setString(2, name);
                    ps.setString(3, finalUrl);
                    ps.setString(4, dimension);
                    ps.setInt(5, radioPos.getX());
                    ps.setInt(6, radioPos.getY());
                    ps.setInt(7, radioPos.getZ());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                AvilixRadioMod.LOGGER.error("Failed to log radio link to MySQL.", e);
            }
        });
    }
}
