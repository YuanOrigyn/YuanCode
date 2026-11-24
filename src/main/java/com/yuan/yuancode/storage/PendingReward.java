package com.yuan.yuancode.storage;

import java.util.UUID;

/** A persisted reward that is waiting for its due time or for its player to join. */
public record PendingReward(
        long id,
        UUID playerUuid,
        String code,
        String rewardJson,
        long executeAt,
        int attempts
) {
}
