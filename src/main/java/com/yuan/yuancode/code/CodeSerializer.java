package com.yuan.yuancode.code;

import com.yuan.yuancode.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes a clean, readable codes.yml. GUI edits rewrite comments by design. */
public final class CodeSerializer {

    private CodeSerializer() {
    }

    public static String serialize(Collection<CodeDefinition> definitions, ZoneId zone, String datePattern) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "YuanCode - codes.yml",
                "Documentation: docs/CODES.md",
                "GUI edits rewrite this file and may remove custom comments.",
                "Code ids are case-insensitive and support A-Z, 0-9, '_' and '-'."
        ));
        ConfigurationSection root = yaml.createSection("codes");
        List<CodeDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        for (CodeDefinition code : sorted) {
            writeCode(root.createSection(code.id()), code, zone, datePattern);
        }
        return yaml.saveToString();
    }

    private static void writeCode(ConfigurationSection section, CodeDefinition code, ZoneId zone, String datePattern) {
        section.set("enabled", code.enabled());
        section.set("type", code.type().name());
        if (code.displayName() != null) {
            section.set("display-name", code.displayName());
        }
        if (code.startAt() != null) {
            section.set("start", TimeUtil.formatInstant(code.startAt(), zone, datePattern));
        }
        if (code.endAt() != null) {
            section.set("end", TimeUtil.formatInstant(code.endAt(), zone, datePattern));
        }
        section.set("global-limit", code.globalLimit());
        section.set("per-player-limit", code.perPlayerLimit());
        if (!code.cooldown().isZero()) {
            section.set("cooldown", TimeUtil.compact(code.cooldown()).replace(" ", ""));
        }
        if (!code.period().isZero()) {
            section.set("period", TimeUtil.compact(code.period()).replace(" ", ""));
        }
        if (code.permission() != null) {
            section.set("permission", code.permission());
        }

        ConfigurationSection uuid = section.createSection("uuid");
        uuid.set("whitelist", code.uuidWhitelist().stream().map(java.util.UUID::toString).sorted().toList());
        uuid.set("blacklist", code.uuidBlacklist().stream().map(java.util.UUID::toString).sorted().toList());

        ConfigurationSection ip = section.createSection("ip");
        ip.set("max-claims", code.ipLimit().maxClaims());
        ip.set("max-accounts", code.ipLimit().maxAccounts());
        ip.set("window", TimeUtil.compact(code.ipLimit().window()).replace(" ", ""));

        section.set("rewards", writeRewards(code.rewards()));
        if (!code.branches().isEmpty()) {
            List<Map<String, Object>> branches = new ArrayList<>();
            for (RewardBranch branch : code.branches()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", branch.id());
                map.put("conditions", branch.conditions());
                map.put("rewards", writeRewards(branch.rewards()));
                branches.add(map);
            }
            section.set("branches", branches);
        }
    }

    private static List<Map<String, Object>> writeRewards(List<RewardDefinition> rewards) {
        List<Map<String, Object>> out = new ArrayList<>(rewards.size());
        for (RewardDefinition reward : rewards) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", reward.type().name().toLowerCase(java.util.Locale.ROOT));
            if (!reward.delay().isZero()) {
                map.put("delay", TimeUtil.compact(reward.delay()).replace(" ", ""));
            }
            if (!reward.conditions().isEmpty()) {
                map.put("conditions", reward.conditions());
            }
            switch (reward) {
                case ItemReward item -> {
                    map.put("material", item.material().name());
                    map.put("amount", item.amount());
                    if (item.displayName() != null) {
                        map.put("name", item.displayName());
                    }
                    if (!item.lore().isEmpty()) {
                        map.put("lore", item.lore());
                    }
                }
                case MoneyReward money -> map.put("amount", money.amount());
                case PermissionReward permission -> {
                    map.put("node", permission.node());
                    if (!permission.permanent()) {
                        map.put("duration", TimeUtil.compact(permission.duration()).replace(" ", ""));
                    }
                }
                case CommandReward command -> map.put("command", command.command());
            }
            out.add(map);
        }
        return out;
    }

    /** Exposed for tests; writes a single duration in the same format used by the file. */
    public static String duration(Duration duration) {
        return TimeUtil.compact(duration).replace(" ", "");
    }
}
