package com.yuan.yuancode.command;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.code.CodeDefinition;
import com.yuan.yuancode.code.CodeEditor;
import com.yuan.yuancode.code.CodeType;
import com.yuan.yuancode.code.EditOutcome;
import com.yuan.yuancode.config.Lang;
import com.yuan.yuancode.service.RedemptionFailure;
import com.yuan.yuancode.service.RedemptionResult;
import com.yuan.yuancode.storage.ExportResult;
import com.yuan.yuancode.storage.StorageStats;
import com.yuan.yuancode.util.FileUtil;
import com.yuan.yuancode.util.Text;
import com.yuan.yuancode.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * /yuancode (alias /yc). Legacy Bukkit command executor keeps setup simple and
 * works on Paper 26.1+, while all expensive work is delegated to async services.
 */
public final class YuanCodeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "redeem", "gui", "list", "info", "create", "delete", "enable", "disable",
            "set", "reward", "generate", "export", "reload", "stats", "help"
    );

    private final YuanCodePlugin plugin;

    public YuanCodeCommand(YuanCodePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Lang lang = plugin.getLang();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "redeem" -> redeem(sender, args);
                case "gui" -> gui(sender);
                case "list" -> list(sender, args);
                case "info" -> info(sender, args);
                case "create" -> create(sender, args);
                case "delete" -> delete(sender, args);
                case "enable" -> setEnabled(sender, args, true);
                case "disable" -> setEnabled(sender, args, false);
                case "set" -> set(sender, args);
                case "reward" -> reward(sender, args);
                case "generate" -> generate(sender, args);
                case "export" -> export(sender, args);
                case "reload" -> reload(sender);
                case "stats" -> stats(sender);
                default -> {
                    lang.send(sender, "command.unknown");
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Command /" + label + " " + String.join(" ", args) + " failed: " + ex.getMessage());
            lang.send(sender, "command.error");
        }
        return true;
    }

    private void redeem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLang().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission("yuancode.use")) {
            plugin.getLang().send(player, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getLang().send(player, "redeem.usage");
            return;
        }
        plugin.getRedemptionService().redeem(player, args[1], result -> handleRedemption(player, result));
    }

    private void handleRedemption(Player player, RedemptionResult result) {
        if (!player.isOnline()) {
            return;
        }
        Lang lang = plugin.getLang();
        switch (result) {
            case RedemptionResult.Failure failure -> {
                String key = "redeem." + failure.reason().name().toLowerCase(Locale.ROOT);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("retry", Long.toString(failure.retryAfterSeconds()));
                placeholders.put("seconds", Long.toString(failure.retryAfterSeconds()));
                lang.send(player, key, placeholders);
            }
            case RedemptionResult.Success success -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("code", success.code());
                placeholders.put("used", Integer.toString(success.globalUsed()));
                placeholders.put("pending", Integer.toString(success.pendingRewards()));
                placeholders.put("delay", success.longestDelay().isZero()
                        ? TimeUtil.compact(Duration.ZERO)
                        : TimeUtil.compact(success.longestDelay()));
                lang.send(player, "redeem.success", placeholders);
                if (success.pendingRewards() > 0) {
                    lang.send(player, "redeem.pending", placeholders);
                }
                if (success.failedRewards() > 0) {
                    lang.send(player, "redeem.partial-failure",
                            Map.of("failed", Integer.toString(success.failedRewards())));
                }
            }
        }
    }

    private void gui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLang().send(sender, "general.player-only");
            return;
        }
        if (!require(player, "gui")) {
            return;
        }
        plugin.getGuiManager().openBrowser(player, 0);
    }

    private void list(CommandSender sender, String[] args) {
        if (!require(sender, "list")) {
            return;
        }
        List<CodeDefinition> all = new ArrayList<>(plugin.getCodeManager().all());
        int pageSize = Math.max(5, Math.min(20, plugin.getConfigManager().get().settings().gui().pageSize()));
        int maxPage = all.isEmpty() ? 0 : (all.size() - 1) / pageSize;
        int page = 0;
        if (args.length >= 2) {
            try {
                page = Math.max(0, Math.min(maxPage, Integer.parseInt(args[1]) - 1));
            } catch (NumberFormatException ignored) {
                page = 0;
            }
        }
        Lang lang = plugin.getLang();
        lang.sendNoPrefix(sender, "command.list.header", Map.of(
                "page", Integer.toString(page + 1),
                "pages", Integer.toString(maxPage + 1),
                "total", Integer.toString(all.size())
        ));
        int start = page * pageSize;
        for (int i = start; i < Math.min(all.size(), start + pageSize); i++) {
            CodeDefinition code = all.get(i);
            lang.sendNoPrefix(sender, "command.list.entry", Map.of(
                    "code", code.id(),
                    "type", code.type().name(),
                    "status", code.enabled() ? lang.raw("gui.status.active", "ACTIVE")
                            : lang.raw("gui.status.disabled", "DISABLED"),
                    "used", Integer.toString(plugin.getUsageCache().globalUsed(code.id())),
                    "limit", code.globalLimit() <= 0 ? "∞" : Integer.toString(code.globalLimit())
            ));
        }
        if (all.isEmpty()) {
            lang.sendNoPrefix(sender, "command.list.empty");
        }
        lang.sendNoPrefix(sender, "command.list.footer", Map.of(
                "prev", Integer.toString(Math.max(1, page)),
                "next", Integer.toString(Math.min(maxPage + 1, page + 2))
        ));
    }

    private void info(CommandSender sender, String[] args) {
        if (!require(sender, "info")) {
            return;
        }
        if (args.length < 2) {
            plugin.getLang().send(sender, "command.info.usage");
            return;
        }
        Optional<CodeDefinition> optional = plugin.getCodeManager().find(args[1]);
        if (optional.isEmpty()) {
            plugin.getLang().send(sender, "redeem.unknown_code", Map.of("code", args[1]));
            return;
        }
        CodeDefinition code = optional.get();
        Lang lang = plugin.getLang();
        int used = plugin.getUsageCache().globalUsed(code.id());
        Map<String, String> p = new HashMap<>();
        p.put("code", code.id());
        p.put("type", code.type().name());
        p.put("status", code.enabled() ? "ENABLED" : "DISABLED");
        p.put("used", Integer.toString(used));
        p.put("limit", code.globalLimit() <= 0 ? "∞" : Integer.toString(code.globalLimit()));
        p.put("remaining", code.globalLimit() <= 0 ? "∞" : Integer.toString(Math.max(0, code.globalLimit() - used)));
        p.put("per_player", code.perPlayerLimit() <= 0 ? "∞" : Integer.toString(code.perPlayerLimit()));
        p.put("cooldown", TimeUtil.compact(code.cooldown()));
        p.put("period", code.period().isZero() ? "-" : TimeUtil.compact(code.period()));
        p.put("start", date(code.startAt()));
        p.put("end", date(code.endAt()));
        p.put("permission", code.permission() == null ? "-" : code.permission());
        p.put("rewards", Integer.toString(code.rewards().size()));
        p.put("branches", Integer.toString(code.branches().size()));
        p.put("whitelist", Integer.toString(code.uuidWhitelist().size()));
        p.put("blacklist", Integer.toString(code.uuidBlacklist().size()));
        p.put("ip", code.ipLimit().enabled()
                ? code.ipLimit().maxClaims() + "/" + code.ipLimit().maxAccounts() + " "
                + TimeUtil.compact(code.ipLimit().window())
                : "off");
        lang.sendNoPrefix(sender, "command.info.header", p);
        lang.sendNoPrefix(sender, "command.info.body", p);
        if (code.displayName() != null) {
            sender.sendMessage(Text.mm(lang.raw("command.info.display") + code.displayName()));
        }
    }

    private void create(CommandSender sender, String[] args) {
        if (!require(sender, "create")) {
            return;
        }
        if (args.length < 3) {
            plugin.getLang().send(sender, "command.create.usage");
            return;
        }
        EditOutcome outcome = plugin.getCodeEditor().create(args[1], args[2]);
        plugin.getLang().send(sender, outcome.messageKey(), outcome.placeholders());
    }

    private void delete(CommandSender sender, String[] args) {
        if (!require(sender, "delete")) {
            return;
        }
        if (args.length < 2) {
            plugin.getLang().send(sender, "command.delete.usage");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            plugin.getLang().send(sender, "command.delete.confirm", Map.of("code", args[1]));
            return;
        }
        EditOutcome outcome = plugin.getCodeEditor().delete(args[1]);
        plugin.getLang().send(sender, outcome.messageKey(), outcome.placeholders());
    }

    private void setEnabled(CommandSender sender, String[] args, boolean enabled) {
        if (!require(sender, "edit")) {
            return;
        }
        if (args.length < 2) {
            plugin.getLang().send(sender, "command.usage.code", Map.of("usage", "/yc " + args[0] + " <code>"));
            return;
        }
        EditOutcome outcome = plugin.getCodeEditor().setField(args[1], "enabled", Boolean.toString(enabled));
        plugin.getLang().send(sender, outcome.messageKey(), outcome.placeholders());
    }

    private void set(CommandSender sender, String[] args) {
        if (!require(sender, "edit")) {
            return;
        }
        if (args.length < 4) {
            plugin.getLang().send(sender, "command.set.usage");
            return;
        }
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        EditOutcome outcome = plugin.getCodeEditor().setField(args[1], args[2], value);
        plugin.getLang().send(sender, outcome.messageKey(), outcome.placeholders());
    }

    private void reward(CommandSender sender, String[] args) {
        if (!require(sender, "reward")) {
            return;
        }
        if (args.length < 3) {
            plugin.getLang().send(sender, "command.reward.usage");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String codeId = args[2];
        switch (action) {
            case "list" -> {
                CodeDefinition code = plugin.getCodeManager().find(codeId).orElse(null);
                if (code == null) {
                    plugin.getLang().send(sender, "edit.code-missing", Map.of("code", codeId));
                    return;
                }
                plugin.getLang().send(sender, "command.reward.list-header",
                        Map.of("code", code.id(), "count", Integer.toString(code.rewards().size())));
                for (int i = 0; i < code.rewards().size(); i++) {
                    plugin.getLang().sendNoPrefix(sender, "command.reward.list-entry",
                            Map.of("index", Integer.toString(i + 1),
                                    "type", code.rewards().get(i).type().name(),
                                    "id", code.rewards().get(i).id()));
                }
            }
            case "add" -> {
                if (args.length < 4) {
                    plugin.getLang().send(sender, "command.reward.add-usage");
                    return;
                }
                String spec = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                EditOutcome outcome = plugin.getCodeEditor().addReward(codeId, spec);
                plugin.getLang().send(sender, outcome.messageKey(), outcome.placeholders());
            }
            case "remove" -> {
                if (args.length < 4) {
                    plugin.getLang().send(sender, "command.reward.remove-usage");
                    return;
                }
                try {
                    EditOutcome outcome = plugin.getCodeEditor().removeReward(codeId, Integer.parseInt(args[3]));
                    plugin.getLang().send(sender, outcome.messageKey(), outcome.placeholders());
                } catch (NumberFormatException ex) {
                    plugin.getLang().send(sender, "edit.invalid-number",
                            Map.of("field", "index", "value", args[3]));
                }
            }
            case "clear" -> {
                EditOutcome outcome = plugin.getCodeEditor().clearRewards(codeId);
                plugin.getLang().send(sender, outcome.messageKey(), outcome.placeholders());
            }
            default -> plugin.getLang().send(sender, "command.reward.usage");
        }
    }

    private void generate(CommandSender sender, String[] args) {
        if (!require(sender, "generate")) {
            return;
        }
        if (args.length < 3) {
            plugin.getLang().send(sender, "command.generate.usage");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            plugin.getLang().send(sender, "edit.invalid-number", Map.of("field", "amount", "value", args[2]));
            return;
        }
        Integer length = null;
        if (args.length >= 4) {
            try {
                length = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                plugin.getLang().send(sender, "edit.invalid-number", Map.of("field", "length", "value", args[3]));
                return;
            }
        }
        String prefix = args.length >= 5 ? args[4] : null;
        List<String> generated;
        try {
            generated = plugin.getCodeManager().generate(args[1], amount, length, prefix,
                    plugin.getConfigManager().get());
        } catch (RuntimeException ex) {
            plugin.getLang().send(sender, "command.generate.failed", Map.of("reason", ex.getMessage()));
            return;
        }
        Path directory = plugin.getDataFolder().toPath().resolve("generated");
        Path file = directory.resolve(FileUtil.timestampFile("codes", "txt", plugin.getConfigManager().get().zone()));
        plugin.getIoExecutor().execute(() -> {
            try {
                Files.createDirectories(directory);
                FileUtil.writeLines(file, generated);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getLang().send(sender,
                        "command.generate.success", Map.of(
                                "count", Integer.toString(generated.size()),
                                "file", file.toAbsolutePath().toString())));
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not write generated code list: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getLang().send(sender,
                        "command.generate.failed", Map.of("reason", ex.getMessage())));
            }
        });
    }

    private void export(CommandSender sender, String[] args) {
        if (!require(sender, "export")) {
            return;
        }
        if (!plugin.getStorage().isReady()) {
            plugin.getLang().send(sender, "general.database-unavailable");
            return;
        }
        String prefix = args.length >= 2 ? FileUtil.safeFileName(args[1], "redemptions") : "redemptions";
        Path directory = plugin.getDataFolder().toPath().resolve("exports");
        Path file = directory.resolve(FileUtil.timestampFile(prefix, "csv",
                plugin.getConfigManager().get().zone()));
        plugin.getLang().send(sender, "command.export.started");
        plugin.getStorage().exportCsv(file, plugin.getConfigManager().get().settings().export().includeIp(),
                        plugin.getConfigManager().get().zone())
                .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () ->
                        sendExport(sender, result)))
                .exceptionally(error -> {
                    plugin.getLogger().warning("CSV export failed: " + error.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getLang().send(sender,
                            "command.export.failed", Map.of("reason", String.valueOf(error.getMessage()))));
                    return null;
                });
    }

    private void sendExport(CommandSender sender, ExportResult result) {
        plugin.getLang().send(sender, "command.export.success", Map.of(
                "rows", Long.toString(result.rows()),
                "file", result.file().toAbsolutePath().toString()));
    }

    private void reload(CommandSender sender) {
        if (!require(sender, "reload")) {
            return;
        }
        plugin.getLang().send(sender, "command.reload.started");
        plugin.reloadAll().thenAccept(ignored -> Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getLang().send(sender, "command.reload.success")))
                .exceptionally(error -> {
                    plugin.getLogger().severe("Reload failed: " + error.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getLang().send(sender,
                            "command.reload.failed", Map.of("reason", String.valueOf(error.getMessage()))));
                    return null;
                });
    }

    private void stats(CommandSender sender) {
        if (!require(sender, "stats")) {
            return;
        }
        plugin.getStorage().loadStats()
                .thenAccept(stats -> Bukkit.getScheduler().runTask(plugin, () -> sendStats(sender, stats)))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Stats query failed: " + error.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getLang().send(sender, "general.database-unavailable"));
                    return null;
                });
    }

    private void sendStats(CommandSender sender, StorageStats stats) {
        int enabled = (int) plugin.getCodeManager().all().stream().filter(CodeDefinition::enabled).count();
        plugin.getLang().send(sender, "command.stats", Map.of(
                "codes", Integer.toString(plugin.getCodeManager().size()),
                "enabled", Integer.toString(enabled),
                "redemptions", Long.toString(stats.redemptions()),
                "pending", Long.toString(stats.pending())
        ));
    }

    private void help(CommandSender sender) {
        plugin.getLang().sendList(sender, "help.lines", Map.of());
    }

    private boolean require(CommandSender sender, String node) {
        String permission = "yuancode.admin." + node;
        if (sender.hasPermission("yuancode.admin") || sender.hasPermission(permission)) {
            return true;
        }
        plugin.getLang().send(sender, "general.no-permission");
        return false;
    }

    private String date(java.time.Instant instant) {
        if (instant == null) {
            return "-";
        }
        return TimeUtil.formatInstant(instant, plugin.getConfigManager().get().zone(),
                plugin.getConfigManager().get().settings().dateFormat());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("create")) {
                return List.of();
            }
            if (sub.equals("reward") || sub.equals("set") || sub.equals("info") || sub.equals("delete")
                    || sub.equals("enable") || sub.equals("disable") || sub.equals("generate")) {
                return filter(plugin.getCodeManager().all().stream().map(CodeDefinition::id).toList(), args[1]);
            }
            if (sub.equals("export")) {
                return List.of();
            }
            if (sub.equals("list") || sub.equals("stats") || sub.equals("reload") || sub.equals("gui")
                    || sub.equals("redeem")) {
                return List.of();
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("create")) {
                return filter(Arrays.stream(CodeType.values()).map(Enum::name).toList(), args[2]);
            }
            if (sub.equals("reward")) {
                return filter(List.of("add", "list", "remove", "clear"), args[2]);
            }
            if (sub.equals("set")) {
                return filter(List.of("display-name", "type", "global-limit", "per-player-limit", "cooldown",
                        "period", "start", "end", "permission", "uuid-whitelist", "uuid-blacklist", "ip",
                        "enabled"), args[2]);
            }
            if (sub.equals("delete")) {
                return filter(List.of("confirm"), args[2]);
            }
            if (sub.equals("generate")) {
                return filter(List.of("10", "16", "32"), args[2]);
            }
        }
        if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("reward")) {
                return filter(List.of("item", "money", "permission", "command"), args[3]);
            }
            if (sub.equals("set") && args[2].equalsIgnoreCase("type")) {
                return filter(Arrays.stream(CodeType.values()).map(Enum::name).toList(), args[3]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .toList();
    }
}
