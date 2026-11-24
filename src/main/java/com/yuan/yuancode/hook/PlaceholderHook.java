package com.yuan.yuancode.hook;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.config.ConfigManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Soft PlaceholderAPI bridge. */
public final class PlaceholderHook {

    private final YuanCodePlugin plugin;
    private final ConfigManager config;
    private YuanCodeExpansion expansion;
    private boolean available;

    public PlaceholderHook(YuanCodePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setup() {
        available = false;
        expansion = null;
        if (!config.get().hooks().placeholderApi()) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        expansion = new YuanCodeExpansion(plugin);
        if (expansion.register()) {
            available = true;
            plugin.getLogger().info("Registered PlaceholderAPI expansion: %yuancode_...%");
        } else {
            plugin.getLogger().warning("Could not register the PlaceholderAPI expansion.");
        }
    }

    public boolean available() {
        return available;
    }

    public void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        available = false;
    }

    public String apply(Player player, String text) {
        if (!available || text == null || text.isEmpty()) {
            return text;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (RuntimeException ex) {
            return text;
        }
    }

    public String apply(OfflinePlayer player, String text) {
        if (!available || text == null || text.isEmpty()) {
            return text;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (RuntimeException ex) {
            return text;
        }
    }
}
