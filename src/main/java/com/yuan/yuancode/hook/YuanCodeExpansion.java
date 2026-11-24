package com.yuan.yuancode.hook;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.code.CodeDefinition;
import com.yuan.yuancode.storage.PlayerUsage;
import com.yuan.yuancode.util.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Locale;

/**
 * PlaceholderAPI bridge. All placeholders are served from the in-memory cache,
 * so a placeholder request never blocks the server thread on SQL.
 *
 * <pre>
 * %yuancode_total%
 * %yuancode_global_used_CODE%
 * %yuancode_remaining_CODE%       (-1 = unlimited)
 * %yuancode_player_claims_CODE%
 * %yuancode_cooldown_CODE%        (seconds)
 * %yuancode_last_redeem_CODE%
 * %yuancode_status_CODE%          (ACTIVE/DISABLED/EXPIRED/NOT_STARTED)
 * %yuancode_type_CODE%
 * %yuancode_enabled_CODE%
 * </pre>
 */
public final class YuanCodeExpansion extends PlaceholderExpansion {

    private final YuanCodePlugin plugin;

    public YuanCodeExpansion(YuanCodePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yuancode";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Yuan";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("total")) {
            return player == null ? "0" : String.valueOf(plugin.getUsageCache().totalClaims(player.getUniqueId()));
        }
        if (key.startsWith("global_used_")) {
            CodeDefinition code = find(key.substring("global_used_".length()));
            return code == null ? "" : String.valueOf(plugin.getUsageCache().globalUsed(code.id()));
        }
        if (key.startsWith("remaining_")) {
            CodeDefinition code = find(key.substring("remaining_".length()));
            return code == null ? "" : String.valueOf(plugin.getUsageCache().remaining(code));
        }
        if (key.startsWith("player_claims_")) {
            CodeDefinition code = find(key.substring("player_claims_".length()));
            if (code == null || player == null) {
                return "";
            }
            return String.valueOf(plugin.getUsageCache().playerClaims(player.getUniqueId(), code.id()));
        }
        if (key.startsWith("cooldown_")) {
            CodeDefinition code = find(key.substring("cooldown_".length()));
            if (code == null) {
                return "";
            }
            if (code.cooldown().isZero()) {
                return "0";
            }
            if (player == null) {
                return "0";
            }
            long last = plugin.getUsageCache().lastRedeemed(player.getUniqueId(), code.id());
            if (last <= 0) {
                return "0";
            }
            long readyAt = last + code.cooldown().toMillis();
            return String.valueOf(Math.max(0L, (readyAt - System.currentTimeMillis() + 999L) / 1000L));
        }
        if (key.startsWith("last_redeem_")) {
            CodeDefinition code = find(key.substring("last_redeem_".length()));
            if (code == null || player == null) {
                return "";
            }
            long last = plugin.getUsageCache().lastRedeemed(player.getUniqueId(), code.id());
            if (last <= 0) {
                return "";
            }
            var config = plugin.getConfigManager().get();
            return TimeUtil.formatInstant(Instant.ofEpochMilli(last), config.zone(), config.settings().dateFormat());
        }
        if (key.startsWith("status_")) {
            CodeDefinition code = find(key.substring("status_".length()));
            if (code == null) {
                return "";
            }
            Instant now = Instant.now();
            if (!code.enabled()) {
                return "DISABLED";
            }
            if (!code.isStarted(now)) {
                return "NOT_STARTED";
            }
            if (code.isExpired(now)) {
                return "EXPIRED";
            }
            return "ACTIVE";
        }
        if (key.startsWith("type_")) {
            CodeDefinition code = find(key.substring("type_".length()));
            return code == null ? "" : code.type().name();
        }
        if (key.startsWith("enabled_")) {
            CodeDefinition code = find(key.substring("enabled_".length()));
            return code == null ? "" : Boolean.toString(code.enabled());
        }
        return null;
    }

    private CodeDefinition find(String raw) {
        CodeDefinition direct = plugin.getCodeManager().find(raw).orElse(null);
        if (direct != null) {
            return direct;
        }
        // Placeholder parameters are case-insensitive; the code id itself may
        // contain '-' or '_', so fall back to a case-insensitive scan.
        return plugin.getCodeManager().all().stream()
                .filter(code -> code.id().equalsIgnoreCase(raw))
                .findFirst()
                .orElse(null);
    }
}
