package com.yuan.yuancode.storage;

import java.util.Map;
import java.util.UUID;

/** Cached aggregate used by PlaceholderAPI and the GUI. */
public record PlayerUsage(UUID uuid, Map<String, Integer> claims, Map<String, Long> lastRedeemedAt) {

    public PlayerUsage {
        claims = claims == null ? Map.of() : Map.copyOf(claims);
        lastRedeemedAt = lastRedeemedAt == null ? Map.of() : Map.copyOf(lastRedeemedAt);
    }

    public int totalClaims() {
        int total = 0;
        for (int value : claims.values()) {
            total += value;
        }
        return total;
    }
}
