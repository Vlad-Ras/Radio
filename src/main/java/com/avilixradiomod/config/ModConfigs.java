package com.avilixradiomod.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;

import java.util.List;

public final class ModConfigs {
    private ModConfigs() {}

    // ---------------- COMMON ----------------
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    // ---------------- CLIENT ----------------
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        var commonPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = commonPair.getRight();
        COMMON = commonPair.getLeft();

        var clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "avilixradiomod-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "avilixradiomod-client.toml");
    }

    // ======= COMMON SETTINGS =======
    public static final class Common {
        public final ModConfigSpec.IntValue scanEveryTicks;
        public final ModConfigSpec.IntValue maxHearDistance;
        public final ModConfigSpec.DoubleValue smoothing;
        public final ModConfigSpec.DoubleValue stopThreshold;
        public final ModConfigSpec.IntValue defaultVolume;

        public final ModConfigSpec.ConfigValue<String> defaultStreamUrl;

        public final ModConfigSpec.IntValue maxUrlLength;

        // --- Database (MySQL) ---
        public final ModConfigSpec.BooleanValue dbEnabled;
        public final ModConfigSpec.ConfigValue<String> dbHost;
        public final ModConfigSpec.IntValue dbPort;
        public final ModConfigSpec.ConfigValue<String> dbName;
        public final ModConfigSpec.ConfigValue<String> dbUser;
        public final ModConfigSpec.ConfigValue<String> dbPassword;
        public final ModConfigSpec.ConfigValue<String> dbParams;
        public final ModConfigSpec.IntValue dbPoolSize;
        public final ModConfigSpec.BooleanValue dbCreateDatabase;

        Common(ModConfigSpec.Builder b) {
            b.push("audio");
            scanEveryTicks = b.comment("How often (in ticks) to rescan nearby speakers/radios.")
                    .defineInRange("scanEveryTicks", 10, 1, 200);

            maxHearDistance = b.comment("Max distance in blocks where speakers are audible (also affects scan radius).")
                    .defineInRange("maxHearDistance", 30, 4, 128);

            smoothing = b.comment("Volume smoothing factor 0..1. Higher = faster response.")
                    .defineInRange("smoothing", 0.20, 0.01, 1.00);

            stopThreshold = b.comment("If target volume is 0 and smoothed volume drops below this -> stop decoding.")
                    .defineInRange("stopThreshold", 0.50, 0.0, 5.0);

            defaultVolume = b.comment("Default volume for new radios (0..100).")
                    .defineInRange("defaultVolume", 50, 0, 100);

            defaultStreamUrl = b.comment("Default stream URL for newly placed radios. Applied once on first load. Empty = none.")
                    .define("defaultStreamUrl", "");
            b.pop();

            b.push("validation");
            maxUrlLength = b.comment("Max URL length allowed in GUI/network.")
                    .defineInRange("maxUrlLength", 8192, 128, 16384);
            b.pop();

            b.push("database");
            dbEnabled = b.comment("Enable MySQL logging of pasted radio links (server-side).")
                    .define("enabled", false);

            dbHost = b.comment("MySQL host")
                    .define("host", "127.0.0.1");

            dbPort = b.comment("MySQL port")
                    .defineInRange("port", 3306, 1, 65535);

            dbName = b.comment("Database name")
                    .define("database", "minecraft");

            dbUser = b.comment("Database username")
                    .define("user", "root");

            dbPassword = b.comment("Database password")
                    .define("password", "");

            dbParams = b.comment("Extra JDBC params appended to URL (without leading '?').")
                    .define("params", "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");

            dbPoolSize = b.comment("HikariCP max pool size")
                    .defineInRange("poolSize", 4, 1, 32);

            dbCreateDatabase = b.comment("If true, the mod will attempt to CREATE DATABASE IF NOT EXISTS on startup.")
                    .define("createDatabase", true);
            b.pop();
        }
    }

    // ======= CLIENT SETTINGS =======
    public static final class Client {
        public final ModConfigSpec.DoubleValue globalVolume;
        public final ModConfigSpec.ConfigValue<String> defaultUrl;
        public final ModConfigSpec.IntValue historyLimit;
        public final ModConfigSpec.ConfigValue<List<? extends String>> urlHistory;


        Client(ModConfigSpec.Builder b) {
            b.push("audio");

            globalVolume = b.comment("Global (master) volume for ALL Avilix Radio blocks. 0..1")
                    .defineInRange("globalVolume", 1.0, 0.0, 1.0);

            b.pop();

            b.push("radio");

            defaultUrl = b.comment("Default URL shown in the GUI when empty.")
                    .define("defaultUrl", "https://radio.avilix.ru/listen/avilix/radio.mp3");

            historyLimit = b.comment("How many recent URLs to remember.")
                    .defineInRange("historyLimit", 12, 0, 64);

            urlHistory = b.comment("Recent URLs (client-side only).")
                    .defineListAllowEmpty("urlHistory", List.of(), o -> o instanceof String s && !s.isBlank());

            b.pop();
        }
    }
}
