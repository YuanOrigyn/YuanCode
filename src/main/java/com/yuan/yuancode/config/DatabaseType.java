package com.yuan.yuancode.config;

import java.util.Locale;

public enum DatabaseType {
    SQLITE,
    MYSQL;

    public static DatabaseType parse(String input) {
        if (input == null || input.isBlank()) {
            return SQLITE;
        }
        return switch (input.trim().toUpperCase(Locale.ROOT)) {
            case "SQLITE", "SQLITE3" -> SQLITE;
            case "MYSQL", "MARIADB" -> MYSQL;
            default -> throw new IllegalArgumentException("Unknown database type: " + input);
        };
    }
}
