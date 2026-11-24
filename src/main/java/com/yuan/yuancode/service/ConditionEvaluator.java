package com.yuan.yuancode.service;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.hook.PlaceholderHook;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Condition DSL used by branches and by individual rewards. Conditions are
 * ANDed; each entry is one of:
 *
 * <pre>
 * permission:group.vip
 * !permission:group.banned
 * world:world_nether
 * gamemode:survival
 * papi:%player_level% >= 10
 * papi:%player_name% contains a
 * </pre>
 */
public final class ConditionEvaluator {

    private static final Pattern PAPI_PATTERN = Pattern.compile(
            "^papi:(.+?)\\s+(==|!=|>=|<=|>|<|contains|!contains|startsWith|endsWith|matches)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final YuanCodePlugin plugin;
    private final PlaceholderHook placeholders;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public ConditionEvaluator(YuanCodePlugin plugin, PlaceholderHook placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
    }

    public boolean test(Player player, List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (String condition : conditions) {
            if (condition == null || condition.isBlank()) {
                continue;
            }
            if (!testOne(player, condition.trim())) {
                return false;
            }
        }
        return true;
    }

    private boolean testOne(Player player, String condition) {
        boolean negated = false;
        String value = condition;
        if (value.startsWith("!")) {
            negated = true;
            value = value.substring(1).trim();
        }

        String lower = value.toLowerCase(Locale.ROOT);
        boolean result;
        if (lower.startsWith("permission:") || lower.startsWith("perm:")) {
            String node = value.substring(value.indexOf(':') + 1).trim();
            result = player.hasPermission(node);
        } else if (lower.startsWith("world:")) {
            result = player.getWorld().getName().equalsIgnoreCase(value.substring("world:".length()).trim());
        } else if (lower.startsWith("gamemode:")) {
            String mode = value.substring("gamemode:".length()).trim();
            try {
                result = player.getGameMode() == GameMode.valueOf(mode.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                result = false;
            }
        } else if (lower.startsWith("papi:")) {
            result = testPapi(player, value);
        } else {
            warnOnce(condition, "Unknown condition type: " + condition);
            result = false;
        }
        return negated != result;
    }

    private boolean testPapi(Player player, String value) {
        if (!placeholders.available()) {
            warnOnce(value, "Condition uses papi: but PlaceholderAPI is not available: " + value);
            return false;
        }
        Matcher matcher = PAPI_PATTERN.matcher(value);
        if (!matcher.matches()) {
            warnOnce(value, "Invalid papi: condition (expected 'papi:<placeholder> <operator> <value>'): " + value);
            return false;
        }
        String placeholder = matcher.group(1).trim();
        String operator = matcher.group(2).toLowerCase(Locale.ROOT);
        String expected = stripQuotes(matcher.group(3).trim());
        String actual = placeholders.apply(player, placeholder);
        if (actual == null) {
            actual = "";
        }

        Double actualNumber = tryDouble(actual);
        Double expectedNumber = tryDouble(expected);
        if (actualNumber != null && expectedNumber != null) {
            int comparison = Double.compare(actualNumber, expectedNumber);
            return switch (operator) {
                case "==" -> comparison == 0;
                case "!=" -> comparison != 0;
                case ">" -> comparison > 0;
                case ">=" -> comparison >= 0;
                case "<" -> comparison < 0;
                case "<=" -> comparison <= 0;
                default -> stringCompare(actual, expected, operator);
            };
        }
        return stringCompare(actual, expected, operator);
    }

    private boolean stringCompare(String actual, String expected, String operator) {
        String a = actual == null ? "" : actual;
        String e = expected == null ? "" : expected;
        return switch (operator) {
            case "==" -> a.equals(e);
            case "!=" -> !a.equals(e);
            case "contains" -> a.toLowerCase(Locale.ROOT).contains(e.toLowerCase(Locale.ROOT));
            case "!contains" -> !a.toLowerCase(Locale.ROOT).contains(e.toLowerCase(Locale.ROOT));
            case "startswith" -> a.toLowerCase(Locale.ROOT).startsWith(e.toLowerCase(Locale.ROOT));
            case "endswith" -> a.toLowerCase(Locale.ROOT).endsWith(e.toLowerCase(Locale.ROOT));
            case "matches" -> {
                try {
                    yield Pattern.compile(e).matcher(a).find();
                } catch (RuntimeException ex) {
                    yield false;
                }
            }
            default -> false;
        };
    }

    private static Double tryDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            plugin.getLogger().warning(message);
        }
    }
}
