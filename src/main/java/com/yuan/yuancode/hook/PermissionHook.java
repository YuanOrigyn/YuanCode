package com.yuan.yuancode.hook;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.code.PermissionReward;
import com.yuan.yuancode.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Permission rewards: by default LuckPerms is detected and driven through its
 * console commands. Without a permissions plugin a session-only Bukkit
 * attachment is used as a best-effort fallback.
 */
public final class PermissionHook {

    private final YuanCodePlugin plugin;
    private boolean luckPerms;
    private boolean warnedAttachment;

    public PermissionHook(YuanCodePlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        luckPerms = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
        if (luckPerms) {
            plugin.getLogger().info("LuckPerms detected: permission rewards are persistent.");
        } else {
            plugin.getLogger().warning("LuckPerms not found: permission rewards will be session-only attachments. "
                    + "Install LuckPerms or configure hooks.permission.provider=command.");
        }
    }

    public boolean apply(Player player, PermissionReward reward) {
        String provider = plugin.getConfigManager().get().hooks().permission().provider();
        boolean useCommand = provider.equals("command") || (provider.equals("auto") && luckPerms);
        if (useCommand) {
            return applyCommand(player, reward);
        }
        return applyAttachment(player, reward);
    }

    private boolean applyCommand(Player player, PermissionReward reward) {
        var permissionConfig = plugin.getConfigManager().get().hooks().permission();
        String template = reward.permanent()
                ? permissionConfig.permanentCommand()
                : permissionConfig.temporaryCommand();
        if (template == null || template.isBlank()) {
            return applyAttachment(player, reward);
        }
        String command = template
                .replace("%player%", player.getName())
                .replace("%permission%", reward.node())
                .replace("%duration%", reward.permanent() ? "permanent" : TimeUtil.lpDuration(reward.duration()));
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Permission command failed: " + ex.getMessage());
            return false;
        }
    }

    private boolean applyAttachment(Player player, PermissionReward reward) {
        if (!warnedAttachment) {
            warnedAttachment = true;
            plugin.getLogger().warning("Using session-only permission attachment for " + reward.node()
                    + ". It disappears when the player disconnects.");
        }
        int ticks = reward.permanent() ? 0 : (int) Math.min(Integer.MAX_VALUE, reward.duration().toMillis() / 50L);
        player.addAttachment(plugin, reward.node(), true, Math.max(0, ticks));
        return true;
    }
}
