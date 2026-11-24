package com.yuan.yuancode.code;

import java.time.Duration;
import java.util.List;

public record CommandReward(
        String id,
        String command,
        Duration delay,
        List<String> conditions
) implements RewardDefinition {

    public CommandReward {
        id = id == null || id.isBlank() ? "command" : id;
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Command reward must not be blank");
        }
        command = command.trim();
        delay = delay == null ? Duration.ZERO : delay;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    @Override
    public RewardType type() {
        return RewardType.COMMAND;
    }
}
