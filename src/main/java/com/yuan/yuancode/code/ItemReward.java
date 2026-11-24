package com.yuan.yuancode.code;

import org.bukkit.Material;

import java.time.Duration;
import java.util.List;

public record ItemReward(
        String id,
        Material material,
        int amount,
        String displayName,
        List<String> lore,
        Duration delay,
        List<String> conditions
) implements RewardDefinition {

    public ItemReward {
        id = id == null || id.isBlank() ? "item" : id;
        if (material == null || material == Material.AIR) {
            throw new IllegalArgumentException("Item reward material must not be air");
        }
        amount = Math.max(1, amount);
        lore = lore == null ? List.of() : List.copyOf(lore);
        delay = delay == null ? Duration.ZERO : delay;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    @Override
    public RewardType type() {
        return RewardType.ITEM;
    }
}
