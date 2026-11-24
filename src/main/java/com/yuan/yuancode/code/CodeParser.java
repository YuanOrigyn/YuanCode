package com.yuan.yuancode.code;

import com.yuan.yuancode.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Parses the {@code codes:} section into immutable definitions. */
public final class CodeParser {

    public static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final ZoneId zone;
    private final String datePattern;
    private final Consumer<String> warning;

    public CodeParser(ZoneId zone, String datePattern, Consumer<String> warning) {
        this.zone = zone;
        this.datePattern = datePattern;
        this.warning = warning == null ? ignored -> { } : warning;
    }

    public Map<String, CodeDefinition> parse(ConfigurationSection codesSection) {
        Map<String, CodeDefinition> result = new LinkedHashMap<>();
        if (codesSection == null) {
            return result;
        }
        for (String rawId : codesSection.getKeys(false)) {
            ConfigurationSection section = codesSection.getConfigurationSection(rawId);
            if (section == null) {
                warning.accept("Code '" + rawId + "' is not a YAML section; skipped.");
                continue;
            }
            try {
                CodeDefinition definition = parseCode(rawId, section);
                if (result.putIfAbsent(definition.id(), definition) != null) {
                    warning.accept("Duplicate code id after normalisation: " + definition.id());
                }
            } catch (CodeParseException ex) {
                warning.accept("Invalid code '" + rawId + "': " + ex.getMessage());
            }
        }
        return result;
    }

    public CodeDefinition parseCode(String rawId, ConfigurationSection section) {
        String id = normalizeId(rawId);
        CodeType type = parseType(section.getString("type", "ONE_TIME"), id);
        boolean enabled = section.getBoolean("enabled", true);
        String displayName = blankToNull(section.getString("display-name"));

        Instant startAt = parseInstant(section.getString("start"), id, "start");
        Instant endAt = parseInstant(section.getString("end"), id, "end");
        if (startAt != null && endAt != null && !startAt.isBefore(endAt)) {
            throw new CodeParseException("start must be before end");
        }

        int globalLimit = Math.max(0, Math.min(1_000_000, section.getInt("global-limit", 0)));
        int perPlayerLimit = Math.max(0, Math.min(1_000_000, section.getInt("per-player-limit", 1)));
        Duration cooldown = parseDuration(section.getString("cooldown"), Duration.ZERO, id, "cooldown");
        String periodRaw = section.getString("period");
        Duration period = Duration.ZERO;
        if (periodRaw != null && !periodRaw.isBlank() && !periodRaw.trim().equals("0")) {
            try {
                period = TimeUtil.parsePeriod(periodRaw);
            } catch (IllegalArgumentException ex) {
                throw new CodeParseException("period: " + ex.getMessage());
            }
        }

        switch (type) {
            case ONE_TIME -> {
                globalLimit = 1;
                perPlayerLimit = 1;
                period = Duration.ZERO;
            }
            case GLOBAL_LIMIT -> {
                if (globalLimit <= 0) {
                    throw new CodeParseException("GLOBAL_LIMIT requires global-limit > 0");
                }
            }
            case PLAYER_LIMIT -> {
                if (perPlayerLimit <= 0) {
                    throw new CodeParseException("PLAYER_LIMIT requires per-player-limit > 0");
                }
                if (globalLimit > 0) {
                    warning.accept("Code '" + id + "' is PLAYER_LIMIT; global-limit is ignored and forced to unlimited.");
                }
                globalLimit = 0;
                period = Duration.ZERO;
            }
            case PERIODIC -> {
                if (period.isZero()) {
                    throw new CodeParseException("PERIODIC requires a positive period");
                }
            }
            case EXPIRING -> {
                if (endAt == null) {
                    throw new CodeParseException("EXPIRING requires an end time");
                }
            }
            case UUID_BOUND -> {
                if (perPlayerLimit <= 0) {
                    perPlayerLimit = 1;
                }
            }
        }

        ConfigurationSection uuidSection = section.getConfigurationSection("uuid");
        Set<UUID> whitelist = parseUuids(uuidSection, "whitelist", id);
        Set<UUID> blacklist = parseUuids(uuidSection, "blacklist", id);
        if (type == CodeType.UUID_BOUND && whitelist.isEmpty()) {
            warning.accept("Code '" + id + "' is UUID_BOUND but uuid.whitelist is empty; nobody can redeem it until configured.");
        }

        CodeDefinition.IpLimit ipLimit = parseIpLimit(section.getConfigurationSection("ip"));
        String permission = blankToNull(section.getString("permission"));

        List<RewardDefinition> rewards = parseRewards(section.getMapList("rewards"), "r");
        List<RewardBranch> branches = parseBranches(section.getMapList("branches"));
        if (rewards.isEmpty() && branches.isEmpty()) {
            warning.accept("Code '" + id + "' has no rewards; it will reject redemptions until configured.");
        }

        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit,
                perPlayerLimit, cooldown, period, whitelist, blacklist, ipLimit, permission, rewards, branches);
    }

    private CodeType parseType(String raw, String codeId) {
        try {
            return CodeType.parse(raw);
        } catch (IllegalArgumentException ex) {
            throw new CodeParseException("type: " + ex.getMessage());
        }
    }

    private Instant parseInstant(String raw, String codeId, String field) {
        try {
            return TimeUtil.parseInstant(raw, zone, datePattern);
        } catch (IllegalArgumentException ex) {
            throw new CodeParseException(field + ": " + ex.getMessage());
        }
    }

    private Duration parseDuration(String raw, Duration fallback, String codeId, String field) {
        try {
            return TimeUtil.parseDuration(raw, fallback);
        } catch (IllegalArgumentException ex) {
            throw new CodeParseException(field + ": " + ex.getMessage());
        }
    }

    private Set<UUID> parseUuids(ConfigurationSection section, String path, String codeId) {
        if (section == null) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (String raw : section.getStringList(path)) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                result.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ex) {
                warning.accept("Code '" + codeId + "' has an invalid UUID in uuid." + path + ": " + raw);
            }
        }
        return result;
    }

    private CodeDefinition.IpLimit parseIpLimit(ConfigurationSection section) {
        if (section == null) {
            return CodeDefinition.IpLimit.disabled();
        }
        int maxClaims = Math.max(0, section.getInt("max-claims", 0));
        int maxAccounts = Math.max(0, section.getInt("max-accounts", 0));
        if (maxClaims == 0 && maxAccounts == 0) {
            return CodeDefinition.IpLimit.disabled();
        }
        Duration window = parseDuration(section.getString("window"), Duration.ofDays(7), null, "ip.window");
        if (window.isZero()) {
            window = Duration.ofDays(7);
        }
        return new CodeDefinition.IpLimit(maxClaims, maxAccounts, window);
    }

    private List<RewardDefinition> parseRewards(List<Map<?, ?>> maps, String prefix) {
        if (maps == null || maps.isEmpty()) {
            return List.of();
        }
        List<RewardDefinition> result = new ArrayList<>(maps.size());
        int index = 1;
        for (Map<?, ?> map : maps) {
            String defaultId = prefix + index;
            result.add(parseReward(map, defaultId));
            index++;
        }
        return List.copyOf(result);
    }

    private List<RewardBranch> parseBranches(List<Map<?, ?>> maps) {
        if (maps == null || maps.isEmpty()) {
            return List.of();
        }
        List<RewardBranch> result = new ArrayList<>(maps.size());
        int index = 1;
        for (Map<?, ?> map : maps) {
            String id = blankToNull(asString(map.get("id")));
            if (id == null) {
                id = "branch-" + index;
            }
            List<String> conditions = asStringList(map.get("conditions"));
            List<Map<?, ?>> rewardMaps = asMapList(map.get("rewards"));
            List<RewardDefinition> rewards = parseRewards(rewardMaps, id + "-r");
            result.add(new RewardBranch(id, conditions, rewards));
            index++;
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private RewardDefinition parseReward(Map<?, ?> map, String defaultId) {
        if (map == null) {
            throw new CodeParseException("Empty reward entry");
        }
        RewardType type;
        try {
            type = RewardType.parse(asString(map.get("type")));
        } catch (IllegalArgumentException ex) {
            throw new CodeParseException("reward: " + ex.getMessage());
        }
        String id = blankToNull(asString(map.get("id")));
        if (id == null) {
            id = defaultId;
        }
        Duration delay = parseDuration(asString(map.get("delay")), Duration.ZERO, null, "reward.delay");
        List<String> conditions = asStringList(map.get("conditions"));

        return switch (type) {
            case ITEM -> {
                String materialName = asString(map.get("material"));
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material == null || material == Material.AIR) {
                    throw new CodeParseException("item reward has invalid material: " + materialName);
                }
                int amount = Math.max(1, Math.min(2304, asInt(map.get("amount"), 1)));
                String name = blankToNull(asString(map.get("name")));
                List<String> lore = asStringList(map.get("lore"));
                yield new ItemReward(id, material, amount, name, lore, delay, conditions);
            }
            case MONEY -> {
                double amount = asDouble(map.get("amount"), 0);
                if (amount <= 0) {
                    throw new CodeParseException("money reward requires amount > 0");
                }
                yield new MoneyReward(id, amount, delay, conditions);
            }
            case PERMISSION -> {
                String node = blankToNull(asString(map.get("node")));
                if (node == null) {
                    throw new CodeParseException("permission reward requires node");
                }
                String durationRaw = blankToNull(asString(map.get("duration")));
                Duration duration = null;
                if (durationRaw != null && !durationRaw.equalsIgnoreCase("permanent")) {
                    duration = parseDuration(durationRaw, null, null, "permission.duration");
                    if (duration.isZero()) {
                        duration = null;
                    }
                }
                yield new PermissionReward(id, node, duration, delay, conditions);
            }
            case COMMAND -> {
                String command = blankToNull(asString(map.get("command")));
                if (command == null) {
                    throw new CodeParseException("command reward requires command");
                }
                yield new CommandReward(id, command, delay, conditions);
            }
        };
    }

    public static String normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new CodeParseException("Code id is blank");
        }
        String id = rawId.trim().toUpperCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new CodeParseException("Code id must match [A-Za-z0-9_-]{1,64}: " + rawId);
        }
        return id;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
            return List.copyOf(result);
        }
        String single = String.valueOf(value);
        return single.isBlank() ? List.of() : List.of(single);
    }

    private static List<Map<?, ?>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<?, ?>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(map);
            }
        }
        return List.copyOf(result);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class CodeParseException extends RuntimeException {
        public CodeParseException(String message) {
            super(message);
        }
    }
}
