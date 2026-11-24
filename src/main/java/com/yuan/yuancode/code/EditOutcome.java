package com.yuan.yuancode.code;

import java.util.Map;

/** Result of an in-game edit, expressed as a lang.yml key. */
public record EditOutcome(boolean success, String messageKey, Map<String, String> placeholders) {

    public static EditOutcome ok(String key, Map<String, String> placeholders) {
        return new EditOutcome(true, key, placeholders == null ? Map.of() : Map.copyOf(placeholders));
    }

    public static EditOutcome error(String key, Map<String, String> placeholders) {
        return new EditOutcome(false, key, placeholders == null ? Map.of() : Map.copyOf(placeholders));
    }
}
