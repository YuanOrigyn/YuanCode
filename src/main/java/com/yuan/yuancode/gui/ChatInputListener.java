package com.yuan.yuancode.gui;

import com.yuan.yuancode.YuanCodePlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Captures the next chat message for a GUI input prompt. */
public final class ChatInputListener implements Listener {

    private final YuanCodePlugin plugin;
    private final ChatInputManager chat;

    public ChatInputListener(YuanCodePlugin plugin, ChatInputManager chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!chat.hasSession(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> chat.handle(player, message));
    }
}
