package com.yuan.yuancode.code;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable code definition loaded from codes.yml. Runtime claim counters live
 * in the database, never in this object.
 */
public record CodeDefinition(
        String id,
        boolean enabled,
        CodeType type,
        String displayName,
        Instant startAt,
        Instant endAt,
        int globalLimit,
        int perPlayerLimit,
        Duration cooldown,
        Duration period,
        Set<UUID> uuidWhitelist,
        Set<UUID> uuidBlacklist,
        IpLimit ipLimit,
        String permission,
        List<RewardDefinition> rewards,
        List<RewardBranch> branches
) {

    public CodeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Code id must not be blank");
        }
        id = id.trim().toUpperCase(java.util.Locale.ROOT);
        if (type == null) {
            type = CodeType.ONE_TIME;
        }
        globalLimit = Math.max(0, globalLimit);
        perPlayerLimit = Math.max(0, perPlayerLimit);
        cooldown = cooldown == null ? Duration.ZERO : cooldown;
        period = period == null ? Duration.ZERO : period;
        if (type == CodeType.PLAYER_LIMIT) {
            globalLimit = 0;
            perPlayerLimit = Math.max(1, perPlayerLimit);
            period = Duration.ZERO;
        }
        uuidWhitelist = uuidWhitelist == null ? Set.of() : Set.copyOf(uuidWhitelist);
        uuidBlacklist = uuidBlacklist == null ? Set.of() : Set.copyOf(uuidBlacklist);
        ipLimit = ipLimit == null ? IpLimit.disabled() : ipLimit;
        permission = permission == null || permission.isBlank() ? null : permission.trim();
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        branches = branches == null ? List.of() : List.copyOf(branches);
    }

    /** Per-code IP claim guard. A zero value disables the corresponding guard. */
    public record IpLimit(int maxClaims, int maxAccounts, Duration window) {
        public IpLimit {
            maxClaims = Math.max(0, maxClaims);
            maxAccounts = Math.max(0, maxAccounts);
            window = window == null || window.isNegative() ? Duration.ZERO : window;
        }

        public static IpLimit disabled() {
            return new IpLimit(0, 0, Duration.ZERO);
        }

        public boolean enabled() {
            return maxClaims > 0 || maxAccounts > 0;
        }
    }

    public boolean isStarted(Instant now) {
        return startAt == null || !now.isBefore(startAt);
    }

    public boolean isExpired(Instant now) {
        return endAt != null && !now.isBefore(endAt);
    }

    public boolean isActive(Instant now) {
        return enabled && isStarted(now) && !isExpired(now);
    }

    public boolean uuidAllowed(UUID uuid) {
        if (uuidBlacklist.contains(uuid)) {
            return false;
        }
        return uuidWhitelist.isEmpty() || uuidWhitelist.contains(uuid);
    }

    public int remainingGlobal(int used) {
        if (globalLimit <= 0) {
            return -1;
        }
        return Math.max(0, globalLimit - used);
    }

    public boolean hasBranches() {
        return !branches.isEmpty();
    }

    public Duration longestDelay() {
        Duration max = Duration.ZERO;
        for (RewardDefinition reward : rewards) {
            if (reward.delay().compareTo(max) > 0) {
                max = reward.delay();
            }
        }
        for (RewardBranch branch : branches) {
            for (RewardDefinition reward : branch.rewards()) {
                if (reward.delay().compareTo(max) > 0) {
                    max = reward.delay();
                }
            }
        }
        return max;
    }

    public CodeDefinition withEnabled(boolean value) {
        return new CodeDefinition(id, value, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withId(String value) {
        return new CodeDefinition(value, enabled, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withType(CodeType value) {
        CodeType newType = value == null ? type : value;
        int newGlobal = globalLimit;
        int newPerPlayer = perPlayerLimit;
        Duration newPeriod = period;
        if (newType == CodeType.ONE_TIME) {
            newGlobal = 1;
            newPerPlayer = 1;
            newPeriod = Duration.ZERO;
        } else if (newType == CodeType.GLOBAL_LIMIT && newGlobal <= 0) {
            newGlobal = 100;
        } else if (newType == CodeType.PLAYER_LIMIT) {
            newGlobal = 0;
            newPerPlayer = Math.max(1, newPerPlayer);
            newPeriod = Duration.ZERO;
        } else if (newType == CodeType.PERIODIC && newPeriod.isZero()) {
            newPeriod = Duration.ofDays(1);
            if (newPerPlayer <= 0) {
                newPerPlayer = 1;
            }
        }
        return new CodeDefinition(id, enabled, newType, displayName, startAt, endAt, newGlobal, newPerPlayer,
                cooldown, newPeriod, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withDisplayName(String value) {
        return new CodeDefinition(id, enabled, type, value, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withStartAt(Instant value) {
        return new CodeDefinition(id, enabled, type, displayName, value, endAt, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withEndAt(Instant value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, value, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withGlobalLimit(int value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, value, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withPerPlayerLimit(int value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit, value,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withCooldown(Duration value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                value, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withPeriod(Duration value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, value, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withUuidWhitelist(Set<UUID> value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, period, value, uuidBlacklist, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withUuidBlacklist(Set<UUID> value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, value, ipLimit, permission, rewards, branches);
    }

    public CodeDefinition withIpLimit(IpLimit value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, value, permission, rewards, branches);
    }

    public CodeDefinition withPermission(String value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, value, rewards, branches);
    }

    public CodeDefinition withRewards(List<RewardDefinition> value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, value, branches);
    }

    public CodeDefinition withBranches(List<RewardBranch> value) {
        return new CodeDefinition(id, enabled, type, displayName, startAt, endAt, globalLimit, perPlayerLimit,
                cooldown, period, uuidWhitelist, uuidBlacklist, ipLimit, permission, rewards, value);
    }
}
