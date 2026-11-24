package com.yuan.yuancode.service;

/** Local or storage failure reasons, mapped to lang.yml keys by the command layer. */
public enum RedemptionFailure {
    UNKNOWN_CODE,
    DISABLED,
    NOT_STARTED,
    EXPIRED,
    NO_PERMISSION,
    UUID_NOT_ALLOWED,
    UUID_BLOCKED,
    NO_REWARDS,
    GLOBAL_LIMIT,
    PLAYER_LIMIT,
    COOLDOWN,
    IP_LIMIT,
    BUSY,
    RATE_LIMITED,
    DATABASE_ERROR,
    PLAYER_ONLY
}
