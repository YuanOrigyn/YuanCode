package com.yuan.yuancode.service;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.code.CommandReward;
import com.yuan.yuancode.code.ItemReward;
import com.yuan.yuancode.code.MoneyReward;
import com.yuan.yuancode.code.PermissionReward;
import com.yuan.yuancode.code.RewardDefinition;
import com.yuan.yuancode.config.ConfigManager;
import com.yuan.yuancode.hook.PermissionHook;
import com.yuan.yuancode.hook.PlaceholderHook;
import com.yuan.yuancode.hook.VaultHook;
import com.yuan.yuancode.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Executes a single reward on the server thread. */
public final class RewardService {

    public enum DeliveryResult {
        SUCCESS,
        PLAYER_OFFLINE,
        FAILED
    }

    private final YuanCodePlugin plugin;
    private final ConfigManager config;
    private final VaultHook vault;
    private final PermissionHook permissions;
    private final PlaceholderHook placeholders;

    public RewardService(YuanCodePlugin plugin, ConfigManager config, VaultHook vault,
                         PermissionHook permissions, PlaceholderHook placeholders) {
        this.plugin = plugin;
        this.config = config;
        this.vault = vault;
        this.permissions = permissions;
        this.placeholders = placeholders;
    }

    public DeliveryResult apply(Player player, String code, RewardDefinition reward) {
        if (player == null || !player.isOnline()) {
            return DeliveryResult.PLAYER_OFFLINE;
        }
        try {
            return switch (reward) {
                case ItemReward item -> giveItem(player, item);
                case MoneyReward money -> giveMoney(player, money);
                case PermissionReward permission -> permissions.apply(player, permission)
                        ? DeliveryResult.SUCCESS : DeliveryResult.FAILED;
                case CommandReward command -> runCommand(player, code, command);
            };
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Reward " + reward.id() + " (" + reward.type() + ") failed for "
                    + player.getName() + ": " + ex.getMessage());
            return DeliveryResult.FAILED;
        }
    }

    private DeliveryResult giveItem(Player player, ItemReward reward) {
        Material material = reward.material();
        int remaining = reward.amount();
        int maxStack = Math.max(1, new ItemStack(material).getMaxStackSize());
        while (remaining > 0) {
            int amount = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(material, amount);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (reward.displayName() != null) {
                    meta.displayName(Text.mm(reward.displayName()));
                }
                if (!reward.lore().isEmpty()) {
                    meta.lore(Text.mmList(reward.lore()));
                }
                stack.setItemMeta(meta);
            }
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            if (!leftovers.isEmpty()) {
                Location dropAt = player.getLocation().add(0, 1, 0);
                for (ItemStack leftover : leftovers.values()) {
                    if (config.get().rewards().dropWhenFull()) {
                        player.getWorld().dropItemNaturally(dropAt, leftover);
                    } else {
                        plugin.getLogger().warning("Inventory full for " + player.getName()
                                + "; dropping reward item " + material + " x" + leftover.getAmount());
                        player.getWorld().dropItemNaturally(dropAt, leftover);
                    }
                }
            }
            remaining -= amount;
        }
        return DeliveryResult.SUCCESS;
    }

    private DeliveryResult giveMoney(Player player, MoneyReward reward) {
        if (!vault.available()) {
            plugin.getLogger().warning("Money reward skipped for " + player.getName()
                    + ": Vault economy is unavailable.");
            return DeliveryResult.FAILED;
        }
        return vault.deposit(player, reward.amount()) ? DeliveryResult.SUCCESS : DeliveryResult.FAILED;
    }

    private DeliveryResult runCommand(Player player, String code, CommandReward reward) {
        String command = reward.command();
        Map<String, String> values = new HashMap<>();
        values.put("%player%", player.getName());
        values.put("%uuid%", player.getUniqueId().toString());
        values.put("%code%", code == null ? "" : code);
        values.put("%world%", player.getWorld().getName());
        values.put("%x%", Integer.toString(player.getLocation().getBlockX()));
        values.put("%y%", Integer.toString(player.getLocation().getBlockY()));
        values.put("%z%", Integer.toString(player.getLocation().getBlockZ()));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            command = command.replace(entry.getKey(), entry.getValue());
        }
        command = placeholders.apply(player, command);
        if (config.get().rewards().stripLeadingSlash() && command.startsWith("/")) {
            command = command.substring(1);
        }
        command = command.replace('\n', ' ').replace('\r', ' ').trim();
        if (command.isBlank()) {
            return DeliveryResult.FAILED;
        }
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                    ? DeliveryResult.SUCCESS : DeliveryResult.FAILED;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Console reward command failed: " + ex.getMessage());
            return DeliveryResult.FAILED;
        }
    }

    /** Used by /yc reward add previews and tests. */
    public static List<String> supportedPlaceholders() {
        return List.of("%player%", "%uuid%", "%code%", "%world%", "%x%", "%y%", "%z%");
    }
}
