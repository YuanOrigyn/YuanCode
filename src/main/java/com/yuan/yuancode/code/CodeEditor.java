package com.yuan.yuancode.code;

import com.yuan.yuancode.config.ConfigManager;
import com.yuan.yuancode.config.PluginConfig;
import com.yuan.yuancode.util.TimeUtil;
import org.bukkit.Material;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared mutation logic for commands and the GUI. */
public final class CodeEditor {

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "display-name", "name", "type", "global-limit", "per-player-limit", "cooldown",
            "period", "start", "end", "permission", "uuid-whitelist", "uuid-white",
            "uuid-blacklist", "uuid-black", "ip", "enabled"
    );

    private final CodeManager codes;
    private final ConfigManager config;

    public CodeEditor(CodeManager codes, ConfigManager config) {
        this.codes = codes;
        this.config = config;
    }

    public EditOutcome create(String rawId, String rawType) {
        String id;
        try {
            id = CodeParser.normalizeId(rawId);
        } catch (RuntimeException ex) {
            return EditOutcome.error("edit.invalid-code", Map.of("code", String.valueOf(rawId)));
        }
        if (codes.find(id).isPresent()) {
            return EditOutcome.error("edit.code-exists", Map.of("code", id));
        }
        CodeType type;
        try {
            type = CodeType.parse(rawType);
        } catch (RuntimeException ex) {
            return EditOutcome.error("edit.invalid-type", Map.of("type", String.valueOf(rawType)));
        }
        CodeDefinition code = defaults(id, type);
        if (!codes.create(code)) {
            return EditOutcome.error("edit.code-exists", Map.of("code", id));
        }
        return EditOutcome.ok("edit.created", Map.of("code", id, "type", type.name()));
    }

    private CodeDefinition defaults(String id, CodeType type) {
        int global = switch (type) {
            case ONE_TIME -> 1;
            case GLOBAL_LIMIT -> 100;
            case PLAYER_LIMIT -> 0;
            default -> 0;
        };
        int perPlayer = type == CodeType.ONE_TIME ? 1 : 1;
        Duration period = type == CodeType.PERIODIC ? Duration.ofDays(1) : Duration.ZERO;
        Instant end = type == CodeType.EXPIRING ? Instant.now().plus(Duration.ofDays(30)) : null;
        return new CodeDefinition(id, false, type, null, null, end, global, perPlayer,
                Duration.ZERO, period, Set.of(), Set.of(), CodeDefinition.IpLimit.disabled(), null,
                List.of(), List.of());
    }

    public EditOutcome toggle(String rawId) {
        CodeDefinition code = codes.find(rawId).orElse(null);
        if (code == null) {
            return missing(rawId);
        }
        CodeDefinition updated = codes.update(code.id(), current -> current.withEnabled(!current.enabled())).orElse(code);
        return EditOutcome.ok(updated.enabled() ? "edit.enabled" : "edit.disabled",
                Map.of("code", updated.id()));
    }

    public EditOutcome delete(String rawId) {
        CodeDefinition code = codes.find(rawId).orElse(null);
        if (code == null) {
            return missing(rawId);
        }
        codes.delete(code.id());
        return EditOutcome.ok("edit.deleted", Map.of("code", code.id()));
    }

    public EditOutcome cloneCode(String rawId) {
        CodeDefinition code = codes.find(rawId).orElse(null);
        if (code == null) {
            return missing(rawId);
        }
        String base = code.id() + "-COPY";
        String newId = base;
        int suffix = 2;
        while (codes.find(newId).isPresent()) {
            newId = base + suffix++;
            if (newId.length() > 64) {
                newId = code.id().substring(0, Math.min(code.id().length(), 50)) + "-C" + suffix++;
            }
        }
        CodeDefinition clone = code.withId(newId).withEnabled(false);
        codes.create(clone);
        return EditOutcome.ok("edit.cloned", Map.of("code", code.id(), "new", newId));
    }

    public EditOutcome setField(String rawId, String field, String rawValue) {
        CodeDefinition code = codes.find(rawId).orElse(null);
        if (code == null) {
            return missing(rawId);
        }
        String name = field == null ? "" : field.toLowerCase(Locale.ROOT);
        String value = rawValue == null ? "" : rawValue.trim();
        try {
            if (!KNOWN_FIELDS.contains(name)) {
                return EditOutcome.error("edit.unknown-field", Map.of("field", name));
            }
            if ((name.equals("global-limit") || name.equals("per-player-limit"))
                    && code.type() == CodeType.ONE_TIME) {
                return EditOutcome.error("edit.one-time-locked", Map.of("code", code.id()));
            }
            if (name.equals("global-limit") && code.type() == CodeType.PLAYER_LIMIT) {
                return EditOutcome.error("edit.player-limit-global", Map.of("code", code.id()));
            }
            if (name.equals("period") && code.type() == CodeType.PERIODIC
                    && TimeUtil.parsePeriod(value).isZero()) {
                return EditOutcome.error("edit.period-required", Map.of("code", code.id()));
            }
            if (name.equals("end") && code.type() == CodeType.EXPIRING
                    && (value.equals("-") || value.isBlank())) {
                return EditOutcome.error("edit.end-required", Map.of("code", code.id()));
            }
            Instant startValue = code.startAt();
            Instant endValue = code.endAt();
            if (name.equals("start")) {
                startValue = parseInstant(value);
            }
            if (name.equals("end")) {
                endValue = parseInstant(value);
            }
            if ((name.equals("start") || name.equals("end"))
                    && startValue != null && endValue != null && !startValue.isBefore(endValue)) {
                return EditOutcome.error("edit.invalid-window", Map.of("code", code.id()));
            }
            CodeDefinition updated = switch (name) {
                case "display-name", "name" -> code.withDisplayName(
                        value.equals("-") || value.isBlank() ? null : value);
                case "type" -> {
                    CodeType type = CodeType.parse(value);
                    CodeDefinition candidate = code.withType(type);
                    if (type == CodeType.EXPIRING && candidate.endAt() == null) {
                        candidate = candidate.withEndAt(Instant.now().plus(Duration.ofDays(30)));
                    }
                    yield candidate;
                }
                case "global-limit" -> {
                    int limit = Integer.parseInt(value);
                    if (limit < 0 || limit > 1_000_000) {
                        throw new NumberFormatException("out of range");
                    }
                    if (code.type() == CodeType.GLOBAL_LIMIT && limit <= 0) {
                        throw new IllegalArgumentException("GLOBAL_LIMIT requires a positive global-limit");
                    }
                    yield code.withGlobalLimit(limit);
                }
                case "per-player-limit" -> {
                    int limit = Integer.parseInt(value);
                    if (limit < 0 || limit > 1_000_000) {
                        throw new NumberFormatException("out of range");
                    }
                    if (code.type() == CodeType.PLAYER_LIMIT && limit <= 0) {
                        throw new IllegalArgumentException("PLAYER_LIMIT requires per-player-limit > 0");
                    }
                    yield code.withPerPlayerLimit(limit);
                }
                case "cooldown" -> code.withCooldown(TimeUtil.parseDuration(value, Duration.ZERO));
                case "period" -> code.withPeriod(TimeUtil.parsePeriod(value));
                case "start" -> code.withStartAt(startValue);
                case "end" -> code.withEndAt(endValue);
                case "permission" -> code.withPermission(value.equals("-") || value.isBlank() ? null : value);
                case "uuid-whitelist", "uuid-white" -> code.withUuidWhitelist(parseUuids(value));
                case "uuid-blacklist", "uuid-black" -> code.withUuidBlacklist(parseUuids(value));
                case "ip" -> code.withIpLimit(parseIp(value));
                case "enabled" -> code.withEnabled(parseBoolean(value));
                default -> throw new IllegalStateException("Unhandled field: " + name);
            };
            codes.update(code.id(), ignored -> updated);
            return EditOutcome.ok("edit.field-set", Map.of("code", code.id(), "field", name, "value", value));
        } catch (NumberFormatException ex) {
            return EditOutcome.error("edit.invalid-number", Map.of("field", name, "value", value));
        } catch (IllegalArgumentException ex) {
            return EditOutcome.error("edit.invalid-value",
                    Map.of("field", name, "value", value, "reason", ex.getMessage() == null ? "" : ex.getMessage()));
        }
    }

    public EditOutcome addReward(String rawId, String spec) {
        CodeDefinition code = codes.find(rawId).orElse(null);
        if (code == null) {
            return missing(rawId);
        }
        try {
            RewardDefinition reward = parseReward(spec);
            List<RewardDefinition> updated = new ArrayList<>(code.rewards());
            updated.add(reward);
            codes.update(code.id(), current -> current.withRewards(updated));
            return EditOutcome.ok("edit.reward-added",
                    Map.of("code", code.id(), "type", reward.type().name(), "index", Integer.toString(updated.size())));
        } catch (IllegalArgumentException ex) {
            return EditOutcome.error("edit.invalid-reward",
                    Map.of("spec", spec == null ? "" : spec, "reason", ex.getMessage() == null ? "" : ex.getMessage()));
        }
    }

    public EditOutcome removeReward(String rawId, int oneBasedIndex) {
        CodeDefinition code = codes.find(rawId).orElse(null);
        if (code == null) {
            return missing(rawId);
        }
        if (oneBasedIndex < 1 || oneBasedIndex > code.rewards().size()) {
            return EditOutcome.error("edit.reward-index", Map.of("index", Integer.toString(oneBasedIndex)));
        }
        List<RewardDefinition> updated = new ArrayList<>(code.rewards());
        RewardDefinition removed = updated.remove(oneBasedIndex - 1);
        codes.update(code.id(), current -> current.withRewards(updated));
        return EditOutcome.ok("edit.reward-removed",
                Map.of("code", code.id(), "index", Integer.toString(oneBasedIndex), "type", removed.type().name()));
    }

    public EditOutcome clearRewards(String rawId) {
        CodeDefinition code = codes.find(rawId).orElse(null);
        if (code == null) {
            return missing(rawId);
        }
        int count = code.rewards().size();
        codes.update(code.id(), current -> current.withRewards(List.of()));
        return EditOutcome.ok("edit.rewards-cleared", Map.of("code", code.id(), "count", Integer.toString(count)));
    }

    private RewardDefinition parseReward(String rawSpec) {
        if (rawSpec == null || rawSpec.isBlank()) {
            throw new IllegalArgumentException("empty reward specification");
        }
        List<String> tokens = new ArrayList<>(Arrays.asList(rawSpec.trim().split("\\s+")));
        Duration delay = Duration.ZERO;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if (token.startsWith("--delay=")) {
                delay = TimeUtil.parseDuration(token.substring("--delay=".length()), Duration.ZERO);
                tokens.remove(i);
            }
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("missing reward type");
        }
        RewardType type = RewardType.parse(tokens.get(0));
        String id = "r" + System.nanoTime();
        return switch (type) {
            case MONEY -> {
                if (tokens.size() < 2) {
                    throw new IllegalArgumentException("usage: money <amount>");
                }
                yield new MoneyReward(id, Double.parseDouble(tokens.get(1)), delay, List.of());
            }
            case ITEM -> {
                if (tokens.size() < 2) {
                    throw new IllegalArgumentException("usage: item <material> [amount] [name...]");
                }
                Material material = Material.matchMaterial(tokens.get(1));
                if (material == null || material == Material.AIR) {
                    throw new IllegalArgumentException("invalid material: " + tokens.get(1));
                }
                int amount = 1;
                int nameStart = 2;
                if (tokens.size() >= 3) {
                    try {
                        amount = Integer.parseInt(tokens.get(2));
                        nameStart = 3;
                    } catch (NumberFormatException ignored) {
                        nameStart = 2;
                    }
                }
                String name = tokens.size() > nameStart
                        ? String.join(" ", tokens.subList(nameStart, tokens.size()))
                        : null;
                yield new ItemReward(id, material, amount, name, List.of(), delay, List.of());
            }
            case PERMISSION -> {
                if (tokens.size() < 2) {
                    throw new IllegalArgumentException("usage: permission <node> [duration|permanent]");
                }
                Duration duration = null;
                if (tokens.size() >= 3 && !tokens.get(2).equalsIgnoreCase("permanent")) {
                    duration = TimeUtil.parseDuration(tokens.get(2), null);
                }
                yield new PermissionReward(id, tokens.get(1), duration, delay, List.of());
            }
            case COMMAND -> {
                if (tokens.size() < 2) {
                    throw new IllegalArgumentException("usage: command <console command...>");
                }
                yield new CommandReward(id, String.join(" ", tokens.subList(1, tokens.size())), delay, List.of());
            }
        };
    }

    private Set<UUID> parseUuids(String value) {
        if (value == null || value.isBlank() || value.equals("-")) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (String part : value.split("[,\\s]+")) {
            if (!part.isBlank()) {
                result.add(UUID.fromString(part.trim()));
            }
        }
        return result;
    }

    private CodeDefinition.IpLimit parseIp(String value) {
        if (value == null || value.isBlank() || value.equals("-")) {
            return CodeDefinition.IpLimit.disabled();
        }
        String[] parts = value.split("[,\\s]+");
        if (parts.length < 3) {
            throw new IllegalArgumentException("usage: <max-claims> <max-accounts> <window>");
        }
        int maxClaims = Integer.parseInt(parts[0]);
        int maxAccounts = Integer.parseInt(parts[1]);
        Duration window = TimeUtil.parseDuration(parts[2], Duration.ofDays(7));
        if (maxClaims < 0 || maxAccounts < 0) {
            throw new IllegalArgumentException("limits must be >= 0");
        }
        if (window.isZero()) {
            window = Duration.ofDays(7);
        }
        return new CodeDefinition.IpLimit(maxClaims, maxAccounts, window);
    }

    private Instant parseInstant(String value) {
        PluginConfig snapshot = config.get();
        return TimeUtil.parseInstant(value, snapshot.zone(), snapshot.settings().dateFormat());
    }

    private static boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equals("1")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("expected true/false");
    }

    private static EditOutcome missing(String rawId) {
        return EditOutcome.error("edit.code-missing", Map.of("code", rawId == null ? "" : rawId));
    }
}
