package com.yuan.yuancode.config;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Loads config.yml off the main thread and publishes immutable snapshots. */
public final class ConfigManager {

    private final YuanCodePlugin plugin;
    private final File file;
    private volatile PluginConfig current;

    public ConfigManager(YuanCodePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "config.yml");
    }

    public void loadSync() {
        try {
            if (!file.exists()) {
                plugin.saveDefaultConfig();
            }
            current = from(YamlConfiguration.loadConfiguration(file));
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load config.yml, using defaults: " + ex.getMessage());
            current = defaults();
        }
    }

    public CompletableFuture<PluginConfig> reloadAsync(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PluginConfig loaded = from(YamlConfiguration.loadConfiguration(file));
                current = loaded;
                return loaded;
            } catch (Exception ex) {
                throw new IllegalStateException("Could not reload config.yml: " + ex.getMessage(), ex);
            }
        }, executor);
    }

    public PluginConfig get() {
        PluginConfig snapshot = current;
        return snapshot == null ? defaults() : snapshot;
    }

    public File file() {
        return file;
    }

    public static PluginConfig from(FileConfiguration yaml) {
        ZoneId zone = TimeUtil.zoneOrServer(yaml.getString("settings.time-zone", "server"));
        String dateFormat = yaml.getString("settings.date-format", "yyyy-MM-dd HH:mm:ss");
        if (dateFormat == null || dateFormat.isBlank()) {
            dateFormat = "yyyy-MM-dd HH:mm:ss";
        }
        boolean debug = yaml.getBoolean("settings.debug", false);
        String serverId = yaml.getString("settings.server-id", "server-1");
        if (serverId == null || serverId.isBlank()) {
            serverId = "server-1";
        }

        PluginConfig.Security security = new PluginConfig.Security(
                clamp(yaml.getInt("settings.security.max-attempts", 8), 0, 1000),
                duration(yaml, "settings.security.attempt-window", Duration.ofSeconds(10)),
                duration(yaml, "settings.security.block-duration", Duration.ofSeconds(30)),
                yaml.getBoolean("settings.security.hash-ip", false),
                yaml.getString("settings.security.ip-salt", "")
        );

        PluginConfig.Pending pending = new PluginConfig.Pending(
                duration(yaml, "settings.pending.check-interval", Duration.ofSeconds(5)),
                duration(yaml, "settings.pending.expire-after", Duration.ofDays(30)),
                clamp(yaml.getInt("settings.pending.max-attempts", 8), 1, 100)
        );

        String charset = yaml.getString("settings.generator.charset", "ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
        if (charset == null || charset.length() < 8) {
            charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        }
        PluginConfig.Generator generator = new PluginConfig.Generator(
                clamp(yaml.getInt("settings.generator.length", 10), 4, 32),
                charset,
                clamp(yaml.getInt("settings.generator.max-batch", 5000), 1, 100_000)
        );

        PluginConfig.Gui gui = new PluginConfig.Gui(
                clamp(yaml.getInt("settings.gui.page-size", 28), 7, 45)
        );
        PluginConfig.Export export = new PluginConfig.Export(
                yaml.getBoolean("settings.export.include-ip", true)
        );
        PluginConfig.AutoDeleteCodes autoDeleteCodes = new PluginConfig.AutoDeleteCodes(
                yaml.getBoolean("settings.auto-delete-codes.enabled", false),
                yaml.getBoolean("settings.auto-delete-codes.delete-exhausted", true),
                yaml.getBoolean("settings.auto-delete-codes.delete-expired", true),
                duration(yaml, "settings.auto-delete-codes.check-interval", Duration.ofMinutes(5))
        );

        PluginConfig.Settings settings = new PluginConfig.Settings(
                yaml.getString("settings.time-zone", "server"), zone, dateFormat, debug, serverId,
                security, pending, generator, gui, export, autoDeleteCodes
        );

        DatabaseType type;
        try {
            type = DatabaseType.parse(yaml.getString("database.type", "SQLITE"));
        } catch (IllegalArgumentException ex) {
            type = DatabaseType.SQLITE;
        }

        String sqliteFile = yaml.getString("database.sqlite.file", "yuancode.db");
        if (sqliteFile == null || sqliteFile.isBlank()) {
            sqliteFile = "yuancode.db";
        }
        PluginConfig.Sqlite sqlite = new PluginConfig.Sqlite(sqliteFile);

        String dbName = yaml.getString("database.mysql.database", "yuancode");
        if (dbName == null || !dbName.matches("[A-Za-z0-9_]+")) {
            dbName = "yuancode";
        }
        Map<String, String> properties = new LinkedHashMap<>();
        ConfigurationSection propertySection = yaml.getConfigurationSection("database.mysql.properties");
        if (propertySection != null) {
            for (String key : propertySection.getKeys(false)) {
                properties.put(key, String.valueOf(propertySection.get(key)));
            }
        }
        PluginConfig.Mysql mysql = new PluginConfig.Mysql(
                yaml.getString("database.mysql.host", "127.0.0.1"),
                clamp(yaml.getInt("database.mysql.port", 3306), 1, 65535),
                dbName,
                yaml.getString("database.mysql.username", "root"),
                yaml.getString("database.mysql.password", ""),
                yaml.getBoolean("database.mysql.use-ssl", false),
                clamp(yaml.getInt("database.mysql.pool-size", 6), 1, 32),
                clamp(yaml.getLong("database.mysql.connection-timeout-ms", 10_000L), 1_000L, 60_000L),
                Map.copyOf(properties)
        );
        PluginConfig.Database database = new PluginConfig.Database(type, sqlite, mysql);

        String provider = yaml.getString("hooks.permission.provider", "auto");
        provider = provider == null ? "auto" : provider.toLowerCase(Locale.ROOT);
        if (!provider.equals("auto") && !provider.equals("command") && !provider.equals("attachment")) {
            provider = "auto";
        }
        PluginConfig.Permission permission = new PluginConfig.Permission(
                provider,
                yaml.getString("hooks.permission.temporary-command",
                        "lp user %player% permission settemp %permission% true %duration%"),
                yaml.getString("hooks.permission.permanent-command",
                        "lp user %player% permission set %permission% true")
        );

        PluginConfig.Hooks hooks = new PluginConfig.Hooks(
                yaml.getBoolean("hooks.vault.enabled", true),
                yaml.getBoolean("hooks.placeholderapi.enabled", true),
                permission
        );

        PluginConfig.RewardSettings rewards = new PluginConfig.RewardSettings(
                yaml.getBoolean("rewards.item.drop-when-full", true),
                yaml.getBoolean("rewards.command.strip-leading-slash", true)
        );

        return new PluginConfig(
                yaml.getInt("config-version", 1),
                settings,
                database,
                hooks,
                rewards
        );
    }

    public static PluginConfig defaults() {
        ZoneId zone = ZoneId.systemDefault();
        return new PluginConfig(
                1,
                new PluginConfig.Settings("server", zone, "yyyy-MM-dd HH:mm:ss", false, "server-1",
                        new PluginConfig.Security(8, Duration.ofSeconds(10), Duration.ofSeconds(30), false, ""),
                        new PluginConfig.Pending(Duration.ofSeconds(5), Duration.ofDays(30), 8),
                        new PluginConfig.Generator(10, "ABCDEFGHJKLMNPQRSTUVWXYZ23456789", 5000),
                        new PluginConfig.Gui(28),
                        new PluginConfig.Export(true),
                        new PluginConfig.AutoDeleteCodes(false, true, true, Duration.ofMinutes(5))),
                new PluginConfig.Database(DatabaseType.SQLITE, new PluginConfig.Sqlite("yuancode.db"),
                        new PluginConfig.Mysql("127.0.0.1", 3306, "yuancode", "root", "", false, 6, 10_000, Map.of())),
                new PluginConfig.Hooks(true, true,
                        new PluginConfig.Permission("auto",
                                "lp user %player% permission settemp %permission% true %duration%",
                                "lp user %player% permission set %permission% true")),
                new PluginConfig.RewardSettings(true, true)
        );
    }

    private static Duration duration(FileConfiguration yaml, String path, Duration fallback) {
        try {
            Duration parsed = TimeUtil.parseDuration(yaml.getString(path), fallback);
            return parsed.isNegative() ? fallback : parsed;
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
