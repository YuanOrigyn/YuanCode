package com.yuan.yuancode.code;

import java.util.Locale;

/** The six supported redemption-code behaviours. */
public enum CodeType {
    /** One claim globally. */
    ONE_TIME,
    /** A configurable total number of claims. */
    GLOBAL_LIMIT,
    /** Unlimited global claims; each player is limited by per-player-limit. */
    PLAYER_LIMIT,
    /** Only UUIDs on the whitelist may claim. */
    UUID_BOUND,
    /** Each player may claim once per rolling period (day/week/month/...). */
    PERIODIC,
    /** Valid only inside the configured start/end window. */
    EXPIRING;

    public static CodeType parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Missing code type");
        }
        return switch (input.trim().toUpperCase(Locale.ROOT)) {
            case "ONE_TIME", "ONCE", "SINGLE" -> ONE_TIME;
            case "GLOBAL_LIMIT", "GLOBAL", "LIMITED" -> GLOBAL_LIMIT;
            case "PLAYER_LIMIT", "PER_PLAYER", "PER_PLAYER_LIMIT", "PERSONAL" -> PLAYER_LIMIT;
            case "UUID_BOUND", "UUID", "BOUND" -> UUID_BOUND;
            case "PERIODIC", "PERIOD", "REPEAT" -> PERIODIC;
            case "EXPIRING", "TIME_LIMITED", "TIMED", "EXPIRY" -> EXPIRING;
            default -> throw new IllegalArgumentException("Unknown code type: " + input);
        };
    }

    public boolean periodic() {
        return this == PERIODIC;
    }
}
