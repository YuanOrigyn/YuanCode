package com.yuan.yuancode.storage;

/** Outcome of the atomic reservation transaction. */
public sealed interface ReserveResult permits ReserveResult.Success, ReserveResult.Failure {

    record Success(long claimId, int globalUsed, String periodKey) implements ReserveResult {
    }

    record Failure(Reason reason, long retryAfterSeconds) implements ReserveResult {
        public static Failure of(Reason reason) {
            return new Failure(reason, 0L);
        }
    }

    enum Reason {
        GLOBAL_LIMIT,
        PLAYER_LIMIT,
        COOLDOWN,
        IP_LIMIT,
        BUSY,
        DATABASE_ERROR
    }
}
