package com.yuan.yuancode.storage;

import java.util.UUID;

/** Everything the storage layer needs to atomically validate and reserve a claim. */
public record ReserveRequest(
        String code,
        UUID playerUuid,
        String playerName,
        String playerIp,
        long now,
        String periodKey,
        int globalLimit,
        int perPlayerLimit,
        long cooldownMillis,
        int ipMaxClaims,
        int ipMaxAccounts,
        long ipWindowMillis,
        String serverId
) {
}
