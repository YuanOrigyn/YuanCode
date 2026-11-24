package com.yuan.yuancode.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Per-player non-blocking claim lock. A semaphore is used instead of a
 * ReentrantLock because acquisition happens on the server thread while release
 * happens on a database worker. It stops the same player from firing several
 * asynchronous claims at once; the database transaction is the cross-server
 * guard.
 */
public final class ClaimLock {

    private final ConcurrentHashMap<UUID, Semaphore> locks = new ConcurrentHashMap<>();

    public boolean tryAcquire(UUID uuid) {
        Semaphore lock = locks.computeIfAbsent(uuid, ignored -> new Semaphore(1));
        return lock.tryAcquire();
    }

    public void release(UUID uuid) {
        Semaphore lock = locks.get(uuid);
        if (lock != null) {
            lock.release();
        }
    }
}
