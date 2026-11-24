package com.yuan.yuancode.code;

/** Reward kinds supported by codes.yml and the in-game editor. */
public enum RewardType {
    ITEM,
    MONEY,
    PERMISSION,
    COMMAND;

    public static RewardType parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Missing reward type");
        }
        try {
            return valueOf(input.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown reward type: " + input);
        }
    }
}
