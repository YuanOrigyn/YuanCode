package com.yuan.yuancode.service;

import com.yuan.yuancode.code.CodeDefinition;
import com.yuan.yuancode.storage.PlayerUsage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory read cache for GUI and PlaceholderAPI. The database remains the
 * source of truth; values are updated after each successful claim and loaded
 * on join/startup.
 */
public final class UsageCache {

    private final ConcurrentHashMap<String, Integer> globalUsed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerUsage> players = new ConcurrentHashMap<>();

    public void replaceGlobal(Map<String, Integer> values) {
        globalUsed.clear();
        if (values != null) {
            globalUsed.putAll(values);
        }
    }

    public void setGlobal(String code, int used) {
        globalUsed.put(code, Math.max(0, used));
    }

    public int globalUsed(String code) {
        return globalUsed.getOrDefault(code, 0);
    }

    public void setPlayer(PlayerUsage usage) {
        if (usage != null) {
            players.put(usage.uuid(), usage);
        }
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public PlayerUsage player(UUID uuid) {
        return players.get(uuid);
    }

    public int playerClaims(UUID uuid, String code) {
        PlayerUsage usage = players.get(uuid);
        return usage == null ? 0 : usage.claims().getOrDefault(code, 0);
    }

    public long lastRedeemed(UUID uuid, String code) {
        PlayerUsage usage = players.get(uuid);
        return usage == null ? 0L : usage.lastRedeemedAt().getOrDefault(code, 0L);
    }

    public int totalClaims(UUID uuid) {
        PlayerUsage usage = players.get(uuid);
        return usage == null ? 0 : usage.totalClaims();
    }

    public void updateAfterClaim(UUID uuid, String code, int newGlobalUsed, long now) {
        if (code != null) {
            globalUsed.put(code, Math.max(0, newGlobalUsed));
        }
        players.compute(uuid, (key, current) -> {
            int claims = 1;
            long last = now;
            if (current != null) {
                claims = current.claims().getOrDefault(code, 0) + 1;
                last = Math.max(now, current.lastRedeemedAt().getOrDefault(code, 0L));
            }
            java.util.Map<String, Integer> claimMap = new java.util.HashMap<>();
            java.util.Map<String, Long> lastMap = new java.util.HashMap<>();
            if (current != null) {
                claimMap.putAll(current.claims());
                lastMap.putAll(current.lastRedeemedAt());
            }
            claimMap.put(code, claims);
            lastMap.put(code, last);
            return new PlayerUsage(uuid, claimMap, lastMap);
        });
    }

    public int remaining(CodeDefinition code) {
        if (code.globalLimit() <= 0) {
            return -1;
        }
        return Math.max(0, code.globalLimit() - globalUsed(code.id()));
    }
}
