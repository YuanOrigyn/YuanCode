package com.yuan.yuancode.gui;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.config.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Chat-based input for GUI fields. */
public final class ChatInputManager {

    private final YuanCodePlugin plugin;
    private final Lang lang;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChatInputManager(YuanCodePlugin plugin, Lang lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    public void request(Player player, String prompt, Consumer<String> consumer) {
        sessions.put(player.getUniqueId(), new Session(consumer, System.currentTimeMillis() + 60_000L));
        lang.send(player, "gui.input.prompt", Map.of("prompt", prompt));
        lang.send(player, "gui.input.cancel");
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public boolean handle(Player player, String message) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (System.currentTimeMillis() > session.expiresAt) {
            lang.send(player, "gui.input.timeout");
            return true;
        }
        if (message == null || message.trim().equalsIgnoreCase("cancel")) {
            lang.send(player, "gui.input.cancelled");
            return true;
        }
        try {
            session.consumer.accept(message.trim());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("GUI input handler failed: " + ex.getMessage());
            lang.send(player, "gui.input.error");
        }
        return true;
    }

    public void clear(UUID uuid) {
        sessions.remove(uuid);
    }

    public void clearAll() {
        sessions.clear();
    }

    public void expireOld() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt < now) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    lang.send(player, "gui.input.timeout");
                }
                return true;
            }
            return false;
        });
    }

    private record Session(Consumer<String> consumer, long expiresAt) {
    }
}
