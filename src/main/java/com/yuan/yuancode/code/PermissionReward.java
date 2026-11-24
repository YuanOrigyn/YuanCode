package com.yuan.yuancode.code;

import java.time.Duration;
import java.util.List;

/**
 * Grants a permission node. A null duration means permanent. Persistence is
 * delegated to the configured provider (LuckPerms commands or a session
 * attachment fallback).
 */
public record PermissionReward(
        String id,
        String node,
        Duration duration,
        Duration delay,
        List<String> conditions
) implements RewardDefinition {

    public PermissionReward {
        id = id == null || id.isBlank() ? "permission" : id;
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("Permission node must not be blank");
        }
        if (duration != null && (duration.isNegative() || duration.isZero())) {
            duration = null;
        }
        delay = delay == null ? Duration.ZERO : delay;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public boolean permanent() {
        return duration == null;
    }

    @Override
    public RewardType type() {
        return RewardType.PERMISSION;
    }
}
