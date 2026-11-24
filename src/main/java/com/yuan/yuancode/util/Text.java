package com.yuan.yuancode.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small MiniMessage facade. All user supplied values are escaped before parsing. */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    /** Parses trusted text (lang.yml / codes.yml, written by administrators). */
    public static Component mm(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(input);
    }

    /**
     * Parses a message and substitutes {@code {key}} placeholders. Values are
     * escaped, so a player name such as {@code <red>x} can never inject tags.
     */
    public static Component mm(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String result = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String safe = MINI.escapeTags(entry.getValue() == null ? "" : entry.getValue());
                result = result.replace("{" + entry.getKey() + "}", safe);
            }
        }
        return MINI.deserialize(result);
    }

    public static List<Component> mmList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<Component> out = new ArrayList<>(input.size());
        for (String line : input) {
            out.add(mm(line));
        }
        return List.copyOf(out);
    }

    public static List<Component> mmList(List<String> input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<Component> out = new ArrayList<>(input.size());
        for (String line : input) {
            out.add(mm(line, placeholders));
        }
        return List.copyOf(out);
    }

    public static List<Component> lore(String... lines) {
        if (lines == null || lines.length == 0) {
            return List.of();
        }
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(mm(line));
        }
        return List.copyOf(out);
    }

    public static String escape(String input) {
        return input == null ? "" : MINI.escapeTags(input);
    }
}
