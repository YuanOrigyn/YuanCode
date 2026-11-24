package com.yuan.yuancode.gui;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.code.CodeDefinition;
import com.yuan.yuancode.config.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Handles all clicks in YuanCode inventories. */
public final class GuiListener implements Listener {

    private final YuanCodePlugin plugin;
    private final Lang lang;
    private final GuiManager gui;
    private final com.yuan.yuancode.code.CodeManager codes;
    private final Map<UUID, Confirm> confirmations = new ConcurrentHashMap<>();

    public GuiListener(YuanCodePlugin plugin, Lang lang, GuiManager gui,
                       com.yuan.yuancode.code.CodeManager codes) {
        this.plugin = plugin;
        this.lang = lang;
        this.gui = gui;
        this.codes = codes;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof CodeBrowserHolder)
                && !(holder instanceof CodeEditorHolder)
                && !(holder instanceof RewardListHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder instanceof CodeBrowserHolder browser) {
            handleBrowser(player, browser, slot, event.isRightClick(), event.isShiftClick());
        } else if (holder instanceof CodeEditorHolder editor) {
            handleEditor(player, editor.codeId(), slot);
        } else if (holder instanceof RewardListHolder rewards) {
            handleRewards(player, rewards, slot, event.isShiftClick(), event.isLeftClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CodeBrowserHolder || holder instanceof CodeEditorHolder
                || holder instanceof RewardListHolder) {
            event.setCancelled(true);
        }
    }

    private void handleBrowser(Player player, CodeBrowserHolder holder, int slot,
                               boolean rightClick, boolean shiftClick) {
        if (slot == 45 && holder.page() > 0) {
            gui.openBrowser(player, holder.page() - 1);
            return;
        }
        if (slot == 53 && holder.page() < holder.maxPage()) {
            gui.openBrowser(player, holder.page() + 1);
            return;
        }
        if (slot == 48) {
            gui.promptCreate(player);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        String codeId = holder.codeAt(slot);
        if (codeId == null) {
            return;
        }
        if (shiftClick && rightClick) {
            confirm(player, ConfirmAction.DELETE_CODE, codeId);
            return;
        }
        if (rightClick) {
            gui.toggle(player, codeId);
        } else {
            gui.openEditor(player, codeId);
        }
    }

    private void handleEditor(Player player, String codeId, int slot) {
        switch (slot) {
            case 4 -> gui.promptField(player, codeId, "display-name", "gui.prompt.display-name",
                    () -> gui.openEditor(player, codeId));
            case 10 -> gui.toggle(player, codeId);
            case 11 -> gui.cycleType(player, codeId);
            case 12 -> gui.promptField(player, codeId, "global-limit", "gui.prompt.global-limit",
                    () -> gui.openEditor(player, codeId));
            case 13 -> gui.promptField(player, codeId, "per-player-limit", "gui.prompt.per-player-limit",
                    () -> gui.openEditor(player, codeId));
            case 14 -> gui.promptField(player, codeId, "cooldown", "gui.prompt.cooldown",
                    () -> gui.openEditor(player, codeId));
            case 15 -> gui.promptField(player, codeId, "period", "gui.prompt.period",
                    () -> gui.openEditor(player, codeId));
            case 16 -> gui.promptField(player, codeId, "start", "gui.prompt.start",
                    () -> gui.openEditor(player, codeId));
            case 17 -> gui.promptField(player, codeId, "end", "gui.prompt.end",
                    () -> gui.openEditor(player, codeId));
            case 19 -> gui.promptField(player, codeId, "permission", "gui.prompt.permission",
                    () -> gui.openEditor(player, codeId));
            case 20 -> gui.promptField(player, codeId, "uuid-whitelist", "gui.prompt.uuid-whitelist",
                    () -> gui.openEditor(player, codeId));
            case 21 -> gui.promptField(player, codeId, "uuid-blacklist", "gui.prompt.uuid-blacklist",
                    () -> gui.openEditor(player, codeId));
            case 22 -> gui.promptField(player, codeId, "ip", "gui.prompt.ip",
                    () -> gui.openEditor(player, codeId));
            case 23 -> gui.openRewards(player, codeId);
            case 24 -> {
                CodeDefinition code = codes.find(codeId).orElse(null);
                int count = code == null ? 0 : code.branches().size();
                lang.send(player, "gui.branches.info", Map.of("count", Integer.toString(count)));
            }
            case 30 -> gui.cloneCode(player, codeId);
            case 31 -> confirm(player, ConfirmAction.DELETE_CODE, codeId);
            case 40 -> gui.openBrowser(player, 0);
            default -> {
                // Decorative border.
            }
        }
    }

    private void handleRewards(Player player, RewardListHolder holder, int slot,
                               boolean shiftClick, boolean leftClick) {
        if (slot == 45) {
            gui.openEditor(player, holder.codeId());
            return;
        }
        if (slot == 48) {
            gui.promptAddReward(player, holder.codeId());
            return;
        }
        if (slot == 50) {
            if (shiftClick) {
                gui.clearRewards(player, holder.codeId());
            } else {
                confirm(player, ConfirmAction.CLEAR_REWARDS, holder.codeId());
            }
            return;
        }
        Integer index = holder.rewardAt(slot);
        if (index != null && shiftClick && leftClick) {
            gui.removeReward(player, holder.codeId(), index);
        }
    }

    private void confirm(Player player, ConfirmAction action, String codeId) {
        long now = System.currentTimeMillis();
        Confirm existing = confirmations.get(player.getUniqueId());
        if (existing != null && existing.action == action && existing.codeId.equals(codeId)
                && existing.expiresAt > now) {
            confirmations.remove(player.getUniqueId());
            if (action == ConfirmAction.DELETE_CODE) {
                gui.delete(player, codeId);
            } else {
                gui.clearRewards(player, codeId);
            }
            return;
        }
        confirmations.put(player.getUniqueId(), new Confirm(action, codeId, now + 10_000L));
        lang.send(player, action == ConfirmAction.DELETE_CODE ? "gui.confirm.delete" : "gui.confirm.clear",
                Map.of("code", codeId));
    }

    public void clear(UUID uuid) {
        confirmations.remove(uuid);
    }

    private enum ConfirmAction {
        DELETE_CODE,
        CLEAR_REWARDS
    }

    private record Confirm(ConfirmAction action, String codeId, long expiresAt) {
    }
}
