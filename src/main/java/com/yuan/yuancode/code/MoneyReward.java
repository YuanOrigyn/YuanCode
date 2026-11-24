package com.yuan.yuancode.code;

import java.time.Duration;
import java.util.List;

public record MoneyReward(
        String id,
        double amount,
        Duration delay,
        List<String> conditions
) implements RewardDefinition {

    public MoneyReward {
        id = id == null || id.isBlank() ? "money" : id;
        if (amount <= 0 || !Double.isFinite(amount)) {
            throw new IllegalArgumentException("Money reward amount must be positive");
        }
        delay = delay == null ? Duration.ZERO : delay;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    @Override
    public RewardType type() {
        return RewardType.MONEY;
    }
}
