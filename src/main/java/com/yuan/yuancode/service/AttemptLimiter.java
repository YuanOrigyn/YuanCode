package com.yuan.yuancode.service;

import com.yuan.yuancode.config.ConfigManager;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window anti-brute-force limiter for UUID and IP. Purely in-memory by
 * design: it is a cheap first line of defence, while database limits protect
 * durable state.
 */
public final class AttemptLimiter {

    private final ConfigManager config;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AttemptLimiter(ConfigManager config) {
        this.config = config;
    }

    public Decision check(UUID uuid, String ip) {
        long now = System.currentTimeMillis();
        Decision uuidDecision = checkKey("u:" + uuid, now);
        if (!uuidDecision.allowed()) {
            return uuidDecision;
        }
        if (ip != null && !ip.isBlank()) {
            return checkKey("i:" + ip, now);
        }
        return Decision.ALLOWED;
    }

    public void recordFailure(UUID uuid, String ip) {
        long now = System.currentTimeMillis();
        addFailure("u:" + uuid, now);
        if (ip != null && !ip.isBlank()) {
            addFailure("i:" + ip, now);
        }
    }

    public void recordSuccess(UUID uuid) {
        windows.remove("u:" + uuid);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        long maxAge = Math.max(60_000L, config.get().settings().security().attemptWindow().toMillis() * 3);
        windows.entrySet().removeIf(entry -> entry.getValue().isStale(now, maxAge));
    }

    private Decision checkKey(String key, long now) {
        Window window = windows.get(key);
        if (window == null) {
            return Decision.ALLOWED;
        }
        return window.check(now, config.get().settings().security());
    }

    private void addFailure(String key, long now) {
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        window.add(now, config.get().settings().security());
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        public static final Decision ALLOWED = new Decision(true, 0L);
    }

    private static final class Window {
        private final Deque<Long> failures = new ArrayDeque<>();
        private long blockedUntil;

        synchronized Decision check(long now, com.yuan.yuancode.config.PluginConfig.Security security) {
            if (security.maxAttempts() <= 0) {
                return Decision.ALLOWED;
            }
            if (blockedUntil > now) {
                return new Decision(false, Math.max(1L, (blockedUntil - now + 999L) / 1000L));
            }
            long cutoff = now - security.attemptWindow().toMillis();
            prune(cutoff);
            if (failures.size() >= security.maxAttempts()) {
                blockedUntil = now + security.blockDuration().toMillis();
                return new Decision(false, Math.max(1L, security.blockDuration().toSeconds()));
            }
            return Decision.ALLOWED;
        }

        synchronized void add(long now, com.yuan.yuancode.config.PluginConfig.Security security) {
            if (security.maxAttempts() <= 0) {
                return;
            }
            failures.addLast(now);
            long cutoff = now - security.attemptWindow().toMillis();
            prune(cutoff);
            if (failures.size() >= security.maxAttempts()) {
                blockedUntil = Math.max(blockedUntil, now + security.blockDuration().toMillis());
            }
        }

        synchronized boolean isStale(long now, long maxAge) {
            if (blockedUntil > now) {
                return false;
            }
            Long last = failures.peekLast();
            return last == null || now - last > maxAge;
        }

        private void prune(long cutoff) {
            while (!failures.isEmpty() && failures.peekFirst() < cutoff) {
                failures.removeFirst();
            }
        }
    }
}
