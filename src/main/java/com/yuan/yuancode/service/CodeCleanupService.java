package com.yuan.yuancode.service;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.code.CodeDefinition;
import com.yuan.yuancode.code.CodeManager;
import com.yuan.yuancode.config.ConfigManager;
import com.yuan.yuancode.config.PluginConfig;
import com.yuan.yuancode.storage.CodeStorage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Removes finished definitions from codes.yml so the file does not grow
 * forever. A code is considered finished when its global usage reaches the
 * configured limit or when its end time has passed. Redemption history and the
 * usage row are intentionally kept; reuse the same id only after accepting
 * that old per-player history still applies.
 */
public final class CodeCleanupService {

    private final YuanCodePlugin plugin;
    private final ConfigManager config;
    private final CodeManager codes;
    private final UsageCache usageCache;
    private final CodeStorage storage;
    private final AtomicBoolean running = new AtomicBoolean();

    public CodeCleanupService(YuanCodePlugin plugin, ConfigManager config, CodeManager codes,
                              UsageCache usageCache, CodeStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.codes = codes;
        this.usageCache = usageCache;
        this.storage = storage;
    }

    /** Called by the repeating async task. */
    public void tick() {
        PluginConfig.AutoDeleteCodes settings = config.get().settings().autoDeleteCodes();
        if (!settings.enabled() || !storage.isReady() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanupNow();
        } finally {
            running.set(false);
        }
    }

    /** Called immediately after a successful claim. Must run on the server thread. */
    public void onClaimed(CodeDefinition code, int globalUsed) {
        PluginConfig.AutoDeleteCodes settings = config.get().settings().autoDeleteCodes();
        if (!settings.enabled() || !settings.deleteExhausted() || !storage.isReady()) {
            return;
        }
        if (code.globalLimit() <= 0 || globalUsed < code.globalLimit()) {
            return;
        }
        if (codes.delete(code.id())) {
            plugin.getLogger().info("已自动删除兑换完的兑换码: " + code.id());
        }
    }

    /** Scans all definitions; safe to call from an async thread. */
    public int cleanupNow() {
        PluginConfig.AutoDeleteCodes settings = config.get().settings().autoDeleteCodes();
        if (!settings.enabled() || !storage.isReady()) {
            return 0;
        }
        Instant now = Instant.now();
        List<String> candidates = new ArrayList<>();
        for (CodeDefinition code : codes.all()) {
            boolean expired = settings.deleteExpired()
                    && code.endAt() != null
                    && !now.isBefore(code.endAt());
            boolean exhausted = settings.deleteExhausted()
                    && code.globalLimit() > 0
                    && usageCache.globalUsed(code.id()) >= code.globalLimit();
            if (expired || exhausted) {
                candidates.add(code.id());
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }
        List<String> removed = codes.deleteMany(candidates);
        if (!removed.isEmpty()) {
            plugin.getLogger().info("已自动删除 " + removed.size() + " 个已兑换完/已过期的兑换码: "
                    + String.join(", ", removed));
        }
        return removed.size();
    }
}
