package com.yuan.yuancode.service;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.code.CodeDefinition;
import com.yuan.yuancode.code.RewardBranch;
import com.yuan.yuancode.code.RewardDefinition;
import com.yuan.yuancode.config.ConfigManager;
import com.yuan.yuancode.config.PluginConfig;
import com.yuan.yuancode.storage.CodeStorage;
import com.yuan.yuancode.storage.ReserveRequest;
import com.yuan.yuancode.storage.ReserveResult;
import com.yuan.yuancode.util.HashUtil;
import com.yuan.yuancode.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The core redemption pipeline:
 *
 * <ol>
 *   <li>server-thread checks (time, permission, UUID, conditions, branch);</li>
 *   <li>per-player non-blocking lock + sliding-window abuse limiter;</li>
 *   <li>one async SQL transaction (row lock, counters, cooldown, IP checks, claim insert);</li>
 *   <li>main-thread reward dispatch, with delayed/offline rewards persisted first.</li>
 * </ol>
 */
public final class RedemptionService {

    private final YuanCodePlugin plugin;
    private final ConfigManager config;
    private final CodeManagerView codes;
    private final CodeStorage storage;
    private final UsageCache cache;
    private final ConditionEvaluator conditions;
    private final ClaimLock claimLock;
    private final AttemptLimiter attempts;
    private final PendingRewardService pending;
    private final RewardService rewards;
    private final CodeCleanupService cleanup;

    /** Narrow view to avoid exposing the whole manager to this service. */
    public interface CodeManagerView {
        Optional<CodeDefinition> find(String id);
    }

    public RedemptionService(YuanCodePlugin plugin, ConfigManager config, CodeManagerView codes,
                             CodeStorage storage, UsageCache cache, ConditionEvaluator conditions,
                             ClaimLock claimLock, AttemptLimiter attempts,
                             PendingRewardService pending, RewardService rewards,
                             CodeCleanupService cleanup) {
        this.plugin = plugin;
        this.config = config;
        this.codes = codes;
        this.storage = storage;
        this.cache = cache;
        this.conditions = conditions;
        this.claimLock = claimLock;
        this.attempts = attempts;
        this.pending = pending;
        this.rewards = rewards;
        this.cleanup = cleanup;
    }

    public void redeem(Player player, String rawCode, Consumer<RedemptionResult> callback) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("RedemptionService.redeem must be called from the server thread");
        }
        if (player == null || !player.isOnline()) {
            callback.accept(new RedemptionResult.Failure(RedemptionFailure.PLAYER_ONLY, 0L));
            return;
        }
        String ip = playerIp(player);
        AttemptLimiter.Decision decision = attempts.check(player.getUniqueId(), ip);
        if (!decision.allowed()) {
            callback.accept(new RedemptionResult.Failure(RedemptionFailure.RATE_LIMITED, decision.retryAfterSeconds()));
            return;
        }
        if (!storage.isReady()) {
            callback.accept(new RedemptionResult.Failure(RedemptionFailure.DATABASE_ERROR, 0L));
            return;
        }

        Optional<CodeDefinition> optional = codes.find(rawCode);
        if (optional.isEmpty()) {
            fail(player, ip, RedemptionFailure.UNKNOWN_CODE, 0L, callback);
            return;
        }
        CodeDefinition code = optional.get();
        Instant now = Instant.now();
        if (!code.enabled()) {
            fail(player, ip, RedemptionFailure.DISABLED, 0L, callback);
            return;
        }
        if (!code.isStarted(now)) {
            fail(player, ip, RedemptionFailure.NOT_STARTED, 0L, callback);
            return;
        }
        if (code.isExpired(now)) {
            fail(player, ip, RedemptionFailure.EXPIRED, 0L, callback);
            return;
        }
        if (code.permission() != null && !player.hasPermission(code.permission())) {
            fail(player, ip, RedemptionFailure.NO_PERMISSION, 0L, callback);
            return;
        }
        if (code.uuidBlacklist().contains(player.getUniqueId())) {
            fail(player, ip, RedemptionFailure.UUID_BLOCKED, 0L, callback);
            return;
        }
        if (!code.uuidAllowed(player.getUniqueId())) {
            fail(player, ip, RedemptionFailure.UUID_NOT_ALLOWED, 0L, callback);
            return;
        }

        List<RewardDefinition> selected = selectRewards(player, code);
        if (selected.isEmpty()) {
            callback.accept(new RedemptionResult.Failure(RedemptionFailure.NO_REWARDS, 0L));
            return;
        }
        Duration longest = longestDelay(selected);
        UUID uuid = player.getUniqueId();
        if (!claimLock.tryAcquire(uuid)) {
            callback.accept(new RedemptionResult.Failure(RedemptionFailure.BUSY, 0L));
            return;
        }

        PluginConfig snapshot = config.get();
        String periodKey = code.type().periodic()
                ? TimeUtil.periodKey(code.period(), now, snapshot.zone())
                : "GLOBAL";
        ReserveRequest request = new ReserveRequest(
                code.id(),
                uuid,
                player.getName(),
                ip,
                now.toEpochMilli(),
                periodKey,
                code.globalLimit(),
                code.perPlayerLimit(),
                code.cooldown().toMillis(),
                code.ipLimit().maxClaims(),
                code.ipLimit().maxAccounts(),
                code.ipLimit().window().toMillis(),
                snapshot.settings().serverId()
        );

        storage.reserve(request).whenComplete((reserveResult, error) -> {
            claimLock.release(uuid);
            if (error != null) {
                plugin.getLogger().warning("Reservation failed unexpectedly for " + code.id() + ": " + error.getMessage());
                runMain(() -> {
                    attempts.recordFailure(uuid, ip);
                    callback.accept(new RedemptionResult.Failure(RedemptionFailure.DATABASE_ERROR, 0L));
                });
                return;
            }
            if (reserveResult instanceof ReserveResult.Failure failure) {
                runMain(() -> {
                    attempts.recordFailure(uuid, ip);
                    callback.accept(new RedemptionResult.Failure(mapFailure(failure.reason()), failure.retryAfterSeconds()));
                });
                return;
            }
            ReserveResult.Success success = (ReserveResult.Success) reserveResult;
            cache.updateAfterClaim(uuid, code.id(), success.globalUsed(), now.toEpochMilli());
            runMain(() -> {
                attempts.recordSuccess(uuid);
                Player online = Bukkit.getPlayer(uuid);
                DeliverySummary summary = deliver(online, uuid, code, selected, now, longest);
                cleanup.onClaimed(code, success.globalUsed());
                callback.accept(new RedemptionResult.Success(code.id(), success.claimId(),
                        success.globalUsed(), summary.pending, summary.longest, summary.failed));
            });
        });
    }

    private List<RewardDefinition> selectRewards(Player player, CodeDefinition code) {
        List<RewardDefinition> candidates = code.rewards();
        if (code.hasBranches()) {
            Optional<RewardBranch> branch = code.branches().stream()
                    .filter(candidate -> conditions.test(player, candidate.conditions()))
                    .findFirst();
            if (branch.isPresent()) {
                candidates = branch.get().rewards();
            }
        }
        List<RewardDefinition> result = new ArrayList<>();
        for (RewardDefinition reward : candidates) {
            if (conditions.test(player, reward.conditions())) {
                result.add(reward);
            }
        }
        return List.copyOf(result);
    }

    private DeliverySummary deliver(Player online, UUID uuid, CodeDefinition code,
                                    List<RewardDefinition> selected, Instant now, Duration longest) {
        int pendingCount = 0;
        int failed = 0;
        for (RewardDefinition reward : selected) {
            if (reward.delayed()) {
                pending.schedule(uuid, code.id(), reward, now.plus(reward.delay()));
                pendingCount++;
                continue;
            }
            if (online == null || !online.isOnline()) {
                pending.schedule(uuid, code.id(), reward, now);
                pendingCount++;
                continue;
            }
            RewardService.DeliveryResult result = rewards.apply(online, code.id(), reward);
            if (result == RewardService.DeliveryResult.PLAYER_OFFLINE) {
                pending.schedule(uuid, code.id(), reward, now);
                pendingCount++;
            } else if (result == RewardService.DeliveryResult.FAILED) {
                failed++;
            }
        }
        return new DeliverySummary(pendingCount, longest, failed);
    }

    private void fail(Player player, String ip, RedemptionFailure reason, long retry,
                      Consumer<RedemptionResult> callback) {
        attempts.recordFailure(player.getUniqueId(), ip);
        callback.accept(new RedemptionResult.Failure(reason, retry));
    }

    private void runMain(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (IllegalStateException ex) {
            plugin.getLogger().fine("Ignoring redemption callback during shutdown.");
        }
    }

    private String playerIp(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return "";
        }
        String raw = address.getAddress().getHostAddress();
        PluginConfig.Security security = config.get().settings().security();
        if (security.hashIp()) {
            return HashUtil.sha256(raw + "|" + security.ipSalt());
        }
        return raw;
    }

    private static Duration longestDelay(List<RewardDefinition> rewards) {
        Duration longest = Duration.ZERO;
        for (RewardDefinition reward : rewards) {
            if (reward.delay().compareTo(longest) > 0) {
                longest = reward.delay();
            }
        }
        return longest;
    }

    private static RedemptionFailure mapFailure(ReserveResult.Reason reason) {
        return switch (reason) {
            case GLOBAL_LIMIT -> RedemptionFailure.GLOBAL_LIMIT;
            case PLAYER_LIMIT -> RedemptionFailure.PLAYER_LIMIT;
            case COOLDOWN -> RedemptionFailure.COOLDOWN;
            case IP_LIMIT -> RedemptionFailure.IP_LIMIT;
            case BUSY -> RedemptionFailure.BUSY;
            case DATABASE_ERROR -> RedemptionFailure.DATABASE_ERROR;
        };
    }

    private record DeliverySummary(int pending, Duration longest, int failed) {
    }
}
