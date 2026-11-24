package com.yuan.yuancode.storage;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Async-only persistence contract. Implementations must never touch the main thread. */
public interface CodeStorage {

    CompletableFuture<Void> initialize();

    boolean isReady();

    CompletableFuture<ReserveResult> reserve(ReserveRequest request);

    CompletableFuture<Map<String, Integer>> loadGlobalUsage();

    CompletableFuture<PlayerUsage> loadPlayerUsage(UUID playerUuid);

    CompletableFuture<Long> insertPending(UUID playerUuid, String code, String rewardJson, long executeAt);

    CompletableFuture<List<PendingReward>> findDuePending(long now, int limit);

    CompletableFuture<List<PendingReward>> findPendingForPlayer(UUID playerUuid, long dueBefore, int limit);

    CompletableFuture<Void> deletePending(long id);

    CompletableFuture<Integer> incrementPendingAttempts(long id);

    CompletableFuture<Integer> purgePending(long before);

    CompletableFuture<ExportResult> exportCsv(Path target, boolean includeIp, ZoneId zone);

    CompletableFuture<StorageStats> loadStats();

    CompletableFuture<Void> close();
}
