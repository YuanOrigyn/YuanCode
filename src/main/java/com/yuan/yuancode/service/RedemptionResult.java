package com.yuan.yuancode.service;

import java.time.Duration;

public sealed interface RedemptionResult permits RedemptionResult.Success, RedemptionResult.Failure {

    record Success(
            String code,
            long claimId,
            int globalUsed,
            int pendingRewards,
            Duration longestDelay,
            int failedRewards
    ) implements RedemptionResult {
    }

    record Failure(RedemptionFailure reason, long retryAfterSeconds) implements RedemptionResult {
    }
}
