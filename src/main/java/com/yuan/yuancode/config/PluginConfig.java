package com.yuan.yuancode.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

/** Immutable configuration snapshot. Reload replaces the whole object atomically. */
public record PluginConfig(
        int configVersion,
        Settings settings,
        Database database,
        Hooks hooks,
        RewardSettings rewards
) {

    public record Settings(
            String timeZone,
            ZoneId zone,
            String dateFormat,
            boolean debug,
            String serverId,
            Security security,
            Pending pending,
            Generator generator,
            Gui gui,
            Export export,
            AutoDeleteCodes autoDeleteCodes
    ) {
    }

    public record Security(
            int maxAttempts,
            Duration attemptWindow,
            Duration blockDuration,
            boolean hashIp,
            String ipSalt
    ) {
    }

    public record Pending(
            Duration checkInterval,
            Duration expireAfter,
            int maxAttempts
    ) {
    }

    public record Generator(
            int length,
            String charset,
            int maxBatch
    ) {
    }

    public record Gui(int pageSize) {
    }

    public record Export(boolean includeIp) {
    }

    public record AutoDeleteCodes(
            boolean enabled,
            boolean deleteExhausted,
            boolean deleteExpired,
            Duration checkInterval
    ) {
    }

    public record Database(DatabaseType type, Sqlite sqlite, Mysql mysql) {
    }

    public record Sqlite(String file) {
    }

    public record Mysql(
            String host,
            int port,
            String database,
            String username,
            String password,
            boolean useSsl,
            int poolSize,
            long connectionTimeoutMs,
            Map<String, String> properties
    ) {
    }

    public record Hooks(boolean vault, boolean placeholderApi, Permission permission) {
    }

    public record Permission(
            String provider,
            String temporaryCommand,
            String permanentCommand
    ) {
    }

    public record RewardSettings(boolean dropWhenFull, boolean stripLeadingSlash) {
    }

    public ZoneId zone() {
        return settings.zone();
    }
}
