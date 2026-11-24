package com.yuan.yuancode.service;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.code.RewardDefinition;
import com.yuan.yuancode.config.ConfigManager;
import com.yuan.yuancode.storage.CodeStorage;
import com.yuan.yuancode.storage.PendingReward;
import com.yuan.yuancode.util.RewardCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Durable delayed-reward queue. Delayed rewards are written to SQL immediately;
 * a periodic worker and the player-join path deliver them on the main thread.
 */
public final class PendingRewardService {

    private final YuanCodePlugin plugin;
    private final ConfigManager config;
    private final CodeStorage storage;
    private final RewardService rewards;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean ticking = new AtomicBoolean();
    private final AtomicInteger ticks = new AtomicInteger();

    public PendingRewardService(YuanCodePlugin plugin, ConfigManager config, CodeStorage storage,
                                RewardService rewards) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.rewards = rewards;
    }

    public void schedule(UUID playerUuid, String code, RewardDefinition reward, Instant dueAt) {
        if (!storage.isReady()) {
            plugin.getLogger().warning("Cannot persist delayed reward for " + playerUuid + ": storage is not ready.");
            return;
        }
        String json = RewardCodec.encode(reward);
        storage.insertPending(playerUuid, code, json, dueAt.toEpochMilli())
                .whenComplete((id, error) -> {
                    if (error != null) {
                        plugin.getLogger().warning("Could not persist delayed reward: " + error.getMessage());
                    }
                });
    }

    public void deliverFor(Player player) {
        if (!storage.isReady() || player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        storage.findPendingForPlayer(player.getUniqueId(), now, 200)
                .thenAccept(rows -> rows.forEach(this::tryDeliver))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Could not load pending rewards: " + error.getMessage());
                    return null;
                });
    }

    /** Called by an async repeating task. */
    public void tick() {
        if (!storage.isReady() || !ticking.compareAndSet(false, true)) {
            return;
        }
        long now = System.currentTimeMillis();
        storage.findDuePending(now, 200)
                .thenAccept(rows -> rows.forEach(this::tryDeliver))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Could not poll pending rewards: " + error.getMessage());
                    return null;
                })
                .whenComplete((ignored, error) -> ticking.set(false));

        if (ticks.incrementAndGet() % 360 == 0) {
            long cutoff = now - config.get().settings().pending().expireAfter().toMillis();
            storage.purgePending(cutoff).exceptionally(error -> {
                plugin.getLogger().warning("Could not purge old pending rewards: " + error.getMessage());
                return 0;
            });
        }
    }

    private void tryDeliver(PendingReward row) {
        if (!inFlight.add(row.id())) {
            return;
        }
        Player player = Bukkit.getPlayer(row.playerUuid());
        if (player == null || row.executeAt() > System.currentTimeMillis()) {
            inFlight.remove(row.id());
            return;
        }
        RewardDefinition reward;
        try {
            reward = RewardCodec.decode(row.rewardJson());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Discarding corrupt pending reward #" + row.id() + ": " + ex.getMessage());
            inFlight.remove(row.id());
            storage.deletePending(row.id());
            return;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(row.playerUuid());
                if (online == null || !online.isOnline()) {
                    inFlight.remove(row.id());
                    return;
                }
                RewardService.DeliveryResult result = rewards.apply(online, row.code(), reward);
                switch (result) {
                    case SUCCESS -> storage.deletePending(row.id()).whenComplete((ignored, error) -> {
                        if (error != null) {
                            plugin.getLogger().warning("Could not delete delivered pending reward #"
                                    + row.id() + ": " + error.getMessage());
                        }
                        inFlight.remove(row.id());
                    });
                    case PLAYER_OFFLINE -> inFlight.remove(row.id());
                    case FAILED -> storage.incrementPendingAttempts(row.id()).whenComplete((attempts, error) -> {
                        inFlight.remove(row.id());
                        if (error != null) {
                            plugin.getLogger().warning("Could not update pending reward #" + row.id() + ": "
                                    + error.getMessage());
                            return;
                        }
                        if (attempts >= config.get().settings().pending().maxAttempts()) {
                            plugin.getLogger().warning("Dropping pending reward #" + row.id() + " for "
                                    + row.playerUuid() + " after " + attempts + " failed attempts.");
                            storage.deletePending(row.id());
                        }
                    });
                }
            });
        } catch (IllegalStateException ex) {
            inFlight.remove(row.id());
        }
    }
}
