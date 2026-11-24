package com.yuan.yuancode;

import com.yuan.yuancode.code.CommandReward;
import com.yuan.yuancode.code.ItemReward;
import com.yuan.yuancode.code.MoneyReward;
import com.yuan.yuancode.code.PermissionReward;
import com.yuan.yuancode.code.RewardDefinition;
import com.yuan.yuancode.util.RewardCodec;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RewardCodecTest {

    @Test
    void roundTripsAllRewardTypes() {
        List<RewardDefinition> rewards = List.of(
                new ItemReward("r1", Material.DIAMOND, 3, "<aqua>Test</aqua>",
                        List.of("<gray>Lore</gray>"), Duration.ofSeconds(5), List.of("permission:test")),
                new MoneyReward("r2", 12.5, Duration.ZERO, List.of()),
                new PermissionReward("r3", "test.node", Duration.ofDays(30), Duration.ZERO, List.of()),
                new CommandReward("r4", "give %player% diamond 1", Duration.ofMinutes(1), List.of("world:world"))
        );
        for (RewardDefinition reward : rewards) {
            RewardDefinition decoded = RewardCodec.decode(RewardCodec.encode(reward));
            assertEquals(reward.id(), decoded.id());
            assertEquals(reward.type(), decoded.type());
            assertEquals(reward.delay(), decoded.delay());
            assertEquals(reward.conditions(), decoded.conditions());
        }
        assertInstanceOf(ItemReward.class, RewardCodec.decode(RewardCodec.encode(rewards.getFirst())));
    }
}
