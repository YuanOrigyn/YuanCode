package com.yuan.yuancode.code;

import java.time.Duration;
import java.util.List;

/**
 * Immutable reward definition. Conditions are ANDed; an empty list always passes.
 * A non-zero delay is persisted in the database and delivered asynchronously.
 */
public sealed interface RewardDefinition
        permits ItemReward, MoneyReward, PermissionReward, CommandReward {

    String id();

    RewardType type();

    Duration delay();

    List<String> conditions();

    default boolean delayed() {
        return delay() != null && !delay().isZero() && !delay().isNegative();
    }
}
