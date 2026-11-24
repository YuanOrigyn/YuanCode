package com.yuan.yuancode.code;

import java.util.List;

/**
 * Ordered conditional reward branch. The first branch whose conditions all
 * match wins. Top-level {@code rewards} acts as the fallback when no branch
 * matches (and is also used when a code has no branches at all).
 */
public record RewardBranch(
        String id,
        List<String> conditions,
        List<RewardDefinition> rewards
) {
    public RewardBranch {
        id = id == null || id.isBlank() ? "branch" : id;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
    }
}
