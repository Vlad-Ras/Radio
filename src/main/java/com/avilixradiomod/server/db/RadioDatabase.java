package com.avilixradiomod.server.db;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.config.ModConfigs;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight MySQL connection pool + async executor.
 *
 * Goal: log player-pasted links without keeping chunks loaded and without ticking heavy logic.
 */
public final class RadioDatabase {
    private RadioDatabase() {}

    private static volatile @Nullable HikariDataSource dataSource;
    private static volatile @Nullable ExecutorService executor;
    private static volatile boolean initAttempted = false;

    public static boolean isEnabled() {
        return ModConfigs.COMMON.dbEnabled.get();
    }

    /**
     * Call on server starting.
     */
    public static synchronized void init() {
        if (FMLEnvironment.dist.isClient()) {
            // Dedicated server + integrated server both run server code; dist check just in case.
        }
        if (initAttempted) return;
        initAttempted = true;

        if (!isEnabled()) {
            AvilixRadioMod.LOGGER.info("MySQL logging disabled (avilixradiomod-common.toml -> database.enabled=false)");
            return;
        }

        try {
            final String host = Objects.requireNonNull(ModConfigs.COMMON.dbHost.get());
            final int port = ModConfigs.COMMON.dbPort.get();
            final String db = Objects.requireNonNull(ModConfigs.COMMON.dbName.get());
            final String user = Objects.requireNonNull(ModConfigs.COMMON.dbUser.get());
            final String pass = Objects.requireNonNull(ModConfigs.COMMON.dbPassword.get());
            final String params = Objects.requireNonNull(ModConfigs.COMMON.dbParams.get());
            final int poolSize = ModConfigs.COMMON.dbPoolSize.get();

            // Optionally create the database if it doesn't exist.
            // This is a one-time cheap operation at startup and avoids confusing "Unknown database" errors.
            if (ModConfigs.COMMON.dbCreateDatabase.get()) {
                createDatabaseIfMissing(host, port, db, user, pass, params);
            }

            final String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db + (params.isBlank() ? "" : ("?" + params));

            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
            cfg.setMaximumPoolSize(poolSize);
            cfg.setPoolName("AvilixRadioMod-MySQL");
            cfg.setAutoCommit(true);

            // Fail fast to surface misconfig, but not crash the server.
            cfg.setInitializationFailTimeout(5_000);
            cfg.setConnectionTimeout(5_000);
            cfg.setValidationTimeout(3_000);
            cfg.setLeakDetectionThreshold(0);

            dataSource = new HikariDataSource(cfg);

            executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                private final AtomicInteger idx = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "AvilixRadioMod-MySQL-" + idx.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

            ensureSchema();
            AvilixRadioMod.LOGGER.info("MySQL logging enabled: {}", jdbcUrl);
        } catch (Throwable t) {
            AvilixRadioMod.LOGGER.error("Failed to initialize MySQL logging (it will be disabled for this run).", t);
            shutdown();
        }
    }

    private static void createDatabaseIfMissing(String host, int port, String dbName, String user, String pass, String params) {
        // Connect without selecting a database.
        final String baseUrl = "jdbc:mysql://" + host + ":" + port + "/" + (params == null || params.isBlank() ? "" : ("?" + params));
        final String safeDbName = dbName == null ? "minecraft" : dbName;
        final String escaped = safeDbName.replace("`", "``");

        try {
            // Ensure the driver is loaded even in some exotic classloader setups.
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Throwable ignored) {}

        try (java.sql.Connection c = java.sql.DriverManager.getConnection(baseUrl, user, pass);
             java.sql.Statement st = c.createStatement()) {
            st.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS `" + escaped + "` " +
                    "DEFAULT CHARACTER SET utf8mb4 " +
                    "DEFAULT COLLATE utf8mb4_unicode_ci"
            );
        } catch (Throwable t) {
            AvilixRadioMod.LOGGER.warn("Failed to auto-create database '{}' (you may need to create it manually).", safeDbName, t);
        }
    }

    private static void ensureSchema() {
        final HikariDataSource ds = dataSource;
        if (ds == null) return;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS avilix_radio_links (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  player_uuid CHAR(36) NOT NULL," +
                    "  player_name VARCHAR(64) NOT NULL," +
                    "  url VARCHAR(8192) NOT NULL," +
                    "  dimension VARCHAR(128) NOT NULL," +
                    "  x INT NOT NULL," +
                    "  y INT NOT NULL," +
                    "  z INT NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  INDEX idx_created_at (created_at)," +
                    "  INDEX idx_player_uuid (player_uuid)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        } catch (SQLException e) {
            AvilixRadioMod.LOGGER.error("Failed to ensure MySQL schema for avilix_radio_links.", e);
        }
    }

    public static void submit(Runnable task) {
        final ExecutorService ex = executor;
        if (ex == null) return;
        ex.execute(task);
    }

    public static @Nullable Connection getConnection() throws SQLException {
        final HikariDataSource ds = dataSource;
        if (ds == null) return null;
        return ds.getConnection();
    }

    /**
     * Call on server stopping.
     */
    public static synchronized void shutdown() {
        ExecutorService ex = executor;
        executor = null;
        if (ex != null) {
            ex.shutdownNow();
        }

        HikariDataSource ds = dataSource;
        dataSource = null;
        if (ds != null) {
            try {
                ds.close();
            } catch (Throwable ignored) {}
        }
    }
}
