package com.yuan.yuancode.gui;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.code.CodeDefinition;
import com.yuan.yuancode.code.CodeEditor;
import com.yuan.yuancode.code.CodeType;
import com.yuan.yuancode.code.EditOutcome;
import com.yuan.yuancode.code.RewardBranch;
import com.yuan.yuancode.code.RewardDefinition;
import com.yuan.yuancode.code.ItemReward;
import com.yuan.yuancode.code.MoneyReward;
import com.yuan.yuancode.code.PermissionReward;
import com.yuan.yuancode.config.ConfigManager;
import com.yuan.yuancode.config.Lang;
import com.yuan.yuancode.service.UsageCache;
import com.yuan.yuancode.util.Text;
import com.yuan.yuancode.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds all YuanCode inventories. All methods must be called on the server thread. */
public final class GuiManager {

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final YuanCodePlugin plugin;
    private final Lang lang;
    private final ConfigManager config;
    private final com.yuan.yuancode.code.CodeManager codes;
    private final CodeEditor editor;
    private final UsageCache cache;
    private final ChatInputManager chat;

    public GuiManager(YuanCodePlugin plugin, Lang lang, ConfigManager config,
                      com.yuan.yuancode.code.CodeManager codes, CodeEditor editor,
                      UsageCache cache, ChatInputManager chat) {
        this.plugin = plugin;
        this.lang = lang;
        this.config = config;
        this.codes = codes;
        this.editor = editor;
        this.cache = cache;
        this.chat = chat;
    }

    public void openBrowser(Player player, int requestedPage) {
        List<CodeDefinition> all = new ArrayList<>(codes.all());
        int pageSize = Math.max(1, Math.min(CONTENT_SLOTS.length, config.get().settings().gui().pageSize()));
        int maxPage = all.isEmpty() ? 0 : (all.size() - 1) / pageSize;
        int page = Math.max(0, Math.min(maxPage, requestedPage));
        CodeBrowserHolder holder = new CodeBrowserHolder(page, maxPage);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                lang.componentNoPrefix("gui.browser.title",
                        Map.of("page", Integer.toString(page + 1), "pages", Integer.toString(maxPage + 1))));
        holder.inventory(inventory);
        fill(inventory);

        int start = page * pageSize;
        for (int i = 0; i < pageSize && start + i < all.size(); i++) {
            CodeDefinition code = all.get(start + i);
            int slot = CONTENT_SLOTS[i];
            inventory.setItem(slot, codeItem(code));
            holder.codeAt(slot, code.id());
        }
        if (all.isEmpty()) {
            inventory.setItem(22, GuiItems.make(Material.BARRIER,
                    lang.componentNoPrefix("gui.browser.empty-name", Map.of()), List.of()));
        }

        if (page > 0) {
            inventory.setItem(45, GuiItems.make(Material.ARROW,
                    lang.componentNoPrefix("gui.button.prev", Map.of()), List.of()));
        }
        inventory.setItem(48, GuiItems.make(Material.EMERALD,
                lang.componentNoPrefix("gui.button.create", Map.of()), List.of()));
        inventory.setItem(49, GuiItems.make(Material.BARRIER,
                lang.componentNoPrefix("gui.button.close", Map.of()), List.of()));
        if (page < maxPage) {
            inventory.setItem(53, GuiItems.make(Material.ARROW,
                    lang.componentNoPrefix("gui.button.next", Map.of()), List.of()));
        }
        player.openInventory(inventory);
    }

    public void openEditor(Player player, String codeId) {
        CodeDefinition code = codes.find(codeId).orElse(null);
        if (code == null) {
            lang.send(player, "gui.code-missing", Map.of("code", codeId == null ? "" : codeId));
            openBrowser(player, 0);
            return;
        }
        CodeEditorHolder holder = new CodeEditorHolder(code.id());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                lang.componentNoPrefix("gui.editor.title", Map.of("code", code.id())));
        holder.inventory(inventory);
        fill(inventory);

        Map<String, String> info = new HashMap<>();
        info.put("code", code.id());
        info.put("type", code.type().name());
        info.put("status", status(code));
        info.put("rewards", Integer.toString(code.rewards().size()));
        info.put("branches", Integer.toString(code.branches().size()));
        inventory.setItem(4, GuiItems.make(Material.PAPER,
                lang.componentNoPrefix("gui.editor.info-name", info),
                Text.mmList(lang.rawList("gui.editor.info-lore"), info)));

        inventory.setItem(10, field(Material.LIME_DYE, "toggle", status(code),
                "gui.editor.field.toggle", true));
        inventory.setItem(11, field(Material.COMPARATOR, "type", code.type().name(),
                "gui.editor.field.type", true));
        inventory.setItem(12, field(Material.HOPPER, "global-limit", limit(code.globalLimit()),
                "gui.editor.field.global-limit", true));
        inventory.setItem(13, field(Material.PLAYER_HEAD, "per-player-limit", limit(code.perPlayerLimit()),
                "gui.editor.field.per-player-limit", true));
        inventory.setItem(14, field(Material.CLOCK, "cooldown", TimeUtil.compact(code.cooldown()),
                "gui.editor.field.cooldown", true));
        inventory.setItem(15, field(Material.REPEATER, "period",
                code.period().isZero() ? "-" : TimeUtil.compact(code.period()),
                "gui.editor.field.period", true));
        inventory.setItem(16, field(Material.DAYLIGHT_DETECTOR, "start", date(code.startAt()),
                "gui.editor.field.start", true));
        inventory.setItem(17, field(Material.REDSTONE_LAMP, "end", date(code.endAt()),
                "gui.editor.field.end", true));
        inventory.setItem(19, field(Material.NAME_TAG, "permission",
                code.permission() == null ? "-" : code.permission(),
                "gui.editor.field.permission", true));
        inventory.setItem(20, field(Material.WRITABLE_BOOK, "uuid-whitelist",
                Integer.toString(code.uuidWhitelist().size()),
                "gui.editor.field.uuid-whitelist", true));
        inventory.setItem(21, field(Material.BOOK, "uuid-blacklist",
                Integer.toString(code.uuidBlacklist().size()),
                "gui.editor.field.uuid-blacklist", true));
        inventory.setItem(22, field(Material.SHIELD, "ip", ip(code),
                "gui.editor.field.ip", true));
        inventory.setItem(23, field(Material.CHEST, "rewards",
                Integer.toString(code.rewards().size()),
                "gui.editor.field.rewards", true));
        inventory.setItem(24, field(Material.KNOWLEDGE_BOOK, "branches",
                Integer.toString(code.branches().size()),
                "gui.editor.field.branches", false));

        inventory.setItem(30, GuiItems.make(Material.PAPER,
                lang.componentNoPrefix("gui.editor.button.clone", Map.of()), List.of()));
        inventory.setItem(31, GuiItems.make(Material.LAVA_BUCKET,
                lang.componentNoPrefix("gui.editor.button.delete", Map.of()), List.of()));
        inventory.setItem(40, GuiItems.make(Material.ARROW,
                lang.componentNoPrefix("gui.editor.button.back", Map.of()), List.of()));
        player.openInventory(inventory);
    }

    public void openRewards(Player player, String codeId) {
        CodeDefinition code = codes.find(codeId).orElse(null);
        if (code == null) {
            lang.send(player, "gui.code-missing", Map.of("code", codeId == null ? "" : codeId));
            openBrowser(player, 0);
            return;
        }
        RewardListHolder holder = new RewardListHolder(code.id());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                lang.componentNoPrefix("gui.rewards.title", Map.of("code", code.id())));
        holder.inventory(inventory);
        fill(inventory);

        List<RewardDefinition> rewards = code.rewards();
        for (int i = 0; i < rewards.size() && i < CONTENT_SLOTS.length; i++) {
            RewardDefinition reward = rewards.get(i);
            int slot = CONTENT_SLOTS[i];
            inventory.setItem(slot, rewardItem(reward, i + 1));
            holder.rewardAt(slot, i + 1);
        }
        if (rewards.isEmpty()) {
            inventory.setItem(22, GuiItems.make(Material.CHEST,
                    lang.componentNoPrefix("gui.rewards.empty-name", Map.of()),
                    Text.mmList(lang.rawList("gui.rewards.empty-lore"), Map.of())));
        }
        inventory.setItem(45, GuiItems.make(Material.ARROW,
                lang.componentNoPrefix("gui.editor.button.back", Map.of()), List.of()));
        inventory.setItem(48, GuiItems.make(Material.EMERALD,
                lang.componentNoPrefix("gui.rewards.add", Map.of()), List.of()));
        inventory.setItem(50, GuiItems.make(Material.LAVA_BUCKET,
                lang.componentNoPrefix("gui.rewards.clear", Map.of()), List.of()));
        player.openInventory(inventory);
    }

    public void promptField(Player player, String codeId, String field, String promptKey, Runnable reopen) {
        player.closeInventory();
        chat.request(player, lang.raw(promptKey), value -> {
            EditOutcome outcome = editor.setField(codeId, field, value);
            send(player, outcome);
            if (player.isOnline()) {
                reopen.run();
            }
        });
    }

    public void promptCreate(Player player) {
        player.closeInventory();
        chat.request(player, lang.raw("gui.prompt.create"), value -> {
            String[] parts = value.trim().split("\\s+");
            if (parts.length < 2) {
                lang.send(player, "gui.prompt.create-usage");
                openBrowser(player, 0);
                return;
            }
            EditOutcome outcome = editor.create(parts[0], parts[1]);
            send(player, outcome);
            openBrowser(player, 0);
        });
    }

    public void promptAddReward(Player player, String codeId) {
        player.closeInventory();
        chat.request(player, lang.raw("gui.prompt.add-reward"), value -> {
            EditOutcome outcome = editor.addReward(codeId, value);
            send(player, outcome);
            openRewards(player, codeId);
        });
    }

    public void cycleType(Player player, String codeId) {
        CodeDefinition code = codes.find(codeId).orElse(null);
        if (code == null) {
            return;
        }
        CodeType[] values = CodeType.values();
        CodeType next = values[(code.type().ordinal() + 1) % values.length];
        send(player, editor.setField(code.id(), "type", next.name()));
        openEditor(player, code.id());
    }

    public void toggle(Player player, String codeId) {
        send(player, editor.toggle(codeId));
        openEditor(player, codeId);
    }

    public void delete(Player player, String codeId) {
        send(player, editor.delete(codeId));
        openBrowser(player, 0);
    }

    public void cloneCode(Player player, String codeId) {
        send(player, editor.cloneCode(codeId));
        openBrowser(player, 0);
    }

    public void removeReward(Player player, String codeId, int index) {
        send(player, editor.removeReward(codeId, index));
        openRewards(player, codeId);
    }

    public void clearRewards(Player player, String codeId) {
        send(player, editor.clearRewards(codeId));
        openRewards(player, codeId);
    }

    public void send(Player player, EditOutcome outcome) {
        lang.send(player, outcome.messageKey(), outcome.placeholders());
    }

    private ItemStack codeItem(CodeDefinition code) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("code", code.id());
        placeholders.put("type", code.type().name());
        placeholders.put("status", status(code));
        placeholders.put("used", Integer.toString(cache.globalUsed(code.id())));
        placeholders.put("limit", limit(code.globalLimit()));
        placeholders.put("per_player", limit(code.perPlayerLimit()));
        placeholders.put("cooldown", TimeUtil.compact(code.cooldown()));
        placeholders.put("start", date(code.startAt()));
        placeholders.put("end", date(code.endAt()));
        placeholders.put("rewards", Integer.toString(code.rewards().size()));
        placeholders.put("branches", Integer.toString(code.branches().size()));
        placeholders.put("whitelist", Integer.toString(code.uuidWhitelist().size()));
        placeholders.put("ip", ip(code));
        placeholders.put("display", code.displayName() == null ? "" : code.displayName());

        List<Component> lore = new ArrayList<>();
        if (code.displayName() != null) {
            lore.add(Text.mm(code.displayName()));
        }
        lore.addAll(Text.mmList(lang.rawList("gui.browser.code-lore"), placeholders));
        return GuiItems.make(code.enabled() ? Material.ENCHANTED_BOOK : Material.BOOK,
                lang.componentNoPrefix("gui.browser.code-name", placeholders), lore);
    }

    private ItemStack field(Material material, String field, String value, String langKey, boolean editable) {
        Map<String, String> placeholders = Map.of("field", field, "value", value);
        List<Component> lore = new ArrayList<>();
        lore.addAll(Text.mmList(lang.rawList(editable ? "gui.editor.lore-edit" : "gui.editor.lore-read"), placeholders));
        return GuiItems.make(material, lang.componentNoPrefix(langKey, placeholders), lore);
    }

    private ItemStack rewardItem(RewardDefinition reward, int index) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("index", Integer.toString(index));
        placeholders.put("type", reward.type().name());
        placeholders.put("id", reward.id());
        placeholders.put("delay", TimeUtil.compact(reward.delay()));
        placeholders.put("conditions", Integer.toString(reward.conditions().size()));
        String detail = switch (reward) {
            case ItemReward item -> item.material().name() + " x" + item.amount();
            case MoneyReward money -> Double.toString(money.amount());
            case PermissionReward permission -> permission.node()
                    + (permission.permanent() ? "" : " (" + TimeUtil.compact(permission.duration()) + ")");
            case com.yuan.yuancode.code.CommandReward command -> command.command();
        };
        placeholders.put("detail", detail);
        Material material = switch (reward) {
            case ItemReward item -> item.material();
            case MoneyReward ignored -> Material.GOLD_INGOT;
            case PermissionReward ignored -> Material.NAME_TAG;
            case com.yuan.yuancode.code.CommandReward ignored -> Material.COMMAND_BLOCK;
        };
        return GuiItems.make(material,
                lang.componentNoPrefix("gui.rewards.entry", placeholders),
                Text.mmList(lang.rawList("gui.rewards.entry-lore"), placeholders));
    }

    private String status(CodeDefinition code) {
        Instant now = Instant.now();
        if (!code.enabled()) {
            return lang.raw("gui.status.disabled", "DISABLED");
        }
        if (!code.isStarted(now)) {
            return lang.raw("gui.status.not-started", "NOT_STARTED");
        }
        if (code.isExpired(now)) {
            return lang.raw("gui.status.expired", "EXPIRED");
        }
        return lang.raw("gui.status.active", "ACTIVE");
    }

    private String limit(int value) {
        return value <= 0 ? "∞" : Integer.toString(value);
    }

    private String date(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return TimeUtil.formatInstant(instant, config.get().zone(), config.get().settings().dateFormat());
    }

    private String ip(CodeDefinition code) {
        CodeDefinition.IpLimit limit = code.ipLimit();
        if (!limit.enabled()) {
            return lang.raw("gui.value.off", "off");
        }
        return limit.maxClaims() + "/" + limit.maxAccounts() + " " + TimeUtil.compact(limit.window());
    }

    private static void fill(Inventory inventory) {
        ItemStack filler = GuiItems.filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    /** Used by the listener to show branch-only rewards. */
    public List<RewardBranch> branches(String codeId) {
        return codes.find(codeId).map(CodeDefinition::branches).orElse(List.of());
    }
}
