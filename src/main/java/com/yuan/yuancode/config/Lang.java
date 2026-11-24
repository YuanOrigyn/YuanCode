package com.yuan.yuancode.config;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * All user-facing messages come from the standalone lang.yml. Reloading swaps
 * the snapshot; MiniMessage is the only markup syntax.
 */
public final class Lang {

    private final YuanCodePlugin plugin;
    private final File file;
    private volatile YamlConfiguration config = new YamlConfiguration();

    public Lang(YuanCodePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lang.yml");
    }

    public void loadSync() {
        try {
            if (!file.exists()) {
                plugin.saveResource("lang.yml", false);
            }
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load lang.yml: " + ex.getMessage());
            config = new YamlConfiguration();
        }
    }

    public CompletableFuture<Void> reloadAsync(Executor executor) {
        return CompletableFuture.runAsync(() -> {
            try {
                config = YamlConfiguration.loadConfiguration(file);
            } catch (Exception ex) {
                throw new IllegalStateException("Could not reload lang.yml: " + ex.getMessage(), ex);
            }
        }, executor);
    }

    public String raw(String key) {
        String value = config.getString(key);
        if (value == null) {
            plugin.getLogger().warning("Missing lang.yml key: " + key);
            return "<red>[missing lang key: " + key + "]</red>";
        }
        return value;
    }

    public String raw(String key, String fallback) {
        return config.getString(key, fallback);
    }

    public List<String> rawList(String key) {
        return config.getStringList(key);
    }

    public Component component(String key, Map<String, String> placeholders) {
        return Text.mm(raw(key), placeholders);
    }

    public Component componentNoPrefix(String key, Map<String, String> placeholders) {
        return Text.mm(raw(key), placeholders);
    }

    public Component prefixed(String key, Map<String, String> placeholders) {
        String prefix = config.getString("prefix", "");
        return Text.mm(prefix + raw(key), placeholders);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(prefixed(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void sendNoPrefix(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(componentNoPrefix(key, placeholders));
    }

    public void sendNoPrefix(CommandSender sender, String key) {
        sendNoPrefix(sender, key, Map.of());
    }

    public void sendList(CommandSender sender, String key, Map<String, String> placeholders) {
        for (String line : rawList(key)) {
            sender.sendMessage(Text.mm(line, placeholders));
        }
    }

    public boolean has(String key) {
        return config.contains(key);
    }

    public File file() {
        return file;
    }
}
