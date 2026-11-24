package com.yuan.yuancode;

import com.yuan.yuancode.code.CodeEditor;
import com.yuan.yuancode.code.CodeManager;
import com.yuan.yuancode.command.YuanCodeCommand;
import com.yuan.yuancode.config.ConfigManager;
import com.yuan.yuancode.config.Lang;
import com.yuan.yuancode.config.PluginConfig;
import com.yuan.yuancode.gui.ChatInputListener;
import com.yuan.yuancode.gui.ChatInputManager;
import com.yuan.yuancode.gui.GuiListener;
import com.yuan.yuancode.gui.GuiManager;
import com.yuan.yuancode.hook.PermissionHook;
import com.yuan.yuancode.hook.PlaceholderHook;
import com.yuan.yuancode.hook.VaultHook;
import com.yuan.yuancode.listener.PlayerListener;
import com.yuan.yuancode.service.AttemptLimiter;
import com.yuan.yuancode.service.ClaimLock;
import com.yuan.yuancode.service.CodeCleanupService;
import com.yuan.yuancode.service.ConditionEvaluator;
import com.yuan.yuancode.service.PendingRewardService;
import com.yuan.yuancode.service.RedemptionService;
import com.yuan.yuancode.service.RewardService;
import com.yuan.yuancode.service.UsageCache;
import com.yuan.yuancode.storage.CodeStorage;
import com.yuan.yuancode.storage.SqlCodeStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * YuanCode bootstrap. All database work is asynchronous; the main thread only
 * performs Bukkit API work and cheap in-memory checks.
 */
public final class YuanCodePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private Lang lang;
    private CodeManager codeManager;
    private CodeEditor codeEditor;
    private CodeStorage storage;
    private CodeCleanupService codeCleanupService;
    private UsageCache usageCache;
    private ClaimLock claimLock;
    private AttemptLimiter attemptLimiter;
    private ConditionEvaluator conditionEvaluator;
    private VaultHook vaultHook;
    private PermissionHook permissionHook;
    private PlaceholderHook placeholderHook;
    private RewardService rewardService;
    private PendingRewardService pendingRewardService;
    private RedemptionService redemptionService;
    private ChatInputManager chatInputManager;
    private GuiManager guiManager;
    private GuiListener guiListener;

    private ExecutorService ioExecutor;
    private ExecutorService saveExecutor;
    private BukkitTask pendingTask;
    private BukkitTask chatTask;
    private BukkitTask limiterTask;
    private BukkitTask cleanupTask;
    private volatile boolean storageReady;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ioExecutor = Executors.newFixedThreadPool(2, namedFactory("YuanCode-IO-"));
        saveExecutor = Executors.newSingleThreadExecutor(namedFactory("YuanCode-Save-"));

        configManager = new ConfigManager(this);
        configManager.loadSync();
        lang = new Lang(this);
        lang.loadSync();

        codeManager = new CodeManager(this, configManager, saveExecutor);
        codeManager.loadSync();
        codeEditor = new CodeEditor(codeManager, configManager);

        usageCache = new UsageCache();
        claimLock = new ClaimLock();
        attemptLimiter = new AttemptLimiter(configManager);

        vaultHook = new VaultHook(this, configManager);
        vaultHook.setup();
        permissionHook = new PermissionHook(this);
        permissionHook.setup();
        placeholderHook = new PlaceholderHook(this, configManager);
        placeholderHook.setup();
        conditionEvaluator = new ConditionEvaluator(this, placeholderHook);

        storage = new SqlCodeStorage(this, configManager.get());
        codeCleanupService = new CodeCleanupService(this, configManager, codeManager, usageCache, storage);
        storage.initialize()
                .thenCompose(ignored -> storage.loadGlobalUsage())
                .thenAccept(usageCache::replaceGlobal)
                .thenRun(codeCleanupService::tick)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        getLogger().severe("Storage initialisation failed: " + error.getMessage());
                        storageReady = false;
                    } else {
                        storageReady = true;
                        getLogger().info("Storage and usage cache are ready.");
                    }
                });

        rewardService = new RewardService(this, configManager, vaultHook, permissionHook, placeholderHook);
        pendingRewardService = new PendingRewardService(this, configManager, storage, rewardService);
        redemptionService = new RedemptionService(this, configManager, id -> codeManager.find(id),
                storage, usageCache, conditionEvaluator, claimLock, attemptLimiter,
                pendingRewardService, rewardService, codeCleanupService);

        chatInputManager = new ChatInputManager(this, lang);
        guiManager = new GuiManager(this, lang, configManager, codeManager, codeEditor, usageCache,
                chatInputManager);
        guiListener = new GuiListener(this, lang, guiManager, codeManager);

        YuanCodeCommand command = new YuanCodeCommand(this);
        if (getCommand("yuancode") != null) {
            getCommand("yuancode").setExecutor(command);
            getCommand("yuancode").setTabCompleter(command);
        } else {
            getLogger().severe("Command 'yuancode' is missing from plugin.yml.");
        }
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this, chatInputManager), this);

        startTasks();
        getLogger().info("YuanCode 1.0.0 by Yuan enabled for Paper 26.1+.");
    }

    private void startTasks() {
        long pendingTicks = Math.max(20L,
                configManager.get().settings().pending().checkInterval().toMillis() / 50L);
        pendingTask = getServer().getScheduler().runTaskTimerAsynchronously(this,
                pendingRewardService::tick, 100L, pendingTicks);
        chatTask = getServer().getScheduler().runTaskTimer(this,
                chatInputManager::expireOld, 100L, 20L);
        limiterTask = getServer().getScheduler().runTaskTimerAsynchronously(this,
                attemptLimiter::cleanup, 200L, 6000L);
        scheduleCleanupTask();
    }

    private void scheduleCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        long cleanupTicks = Math.max(200L,
                configManager.get().settings().autoDeleteCodes().checkInterval().toMillis() / 50L);
        cleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this,
                codeCleanupService::tick, 100L, cleanupTicks);
    }

    public CompletableFuture<Void> reloadAll() {
        PluginConfig before = configManager.get();
        return configManager.reloadAsync(ioExecutor)
                .thenCompose(loaded -> lang.reloadAsync(ioExecutor))
                .thenCompose(ignored -> codeManager.flush())
                .thenCompose(ignored -> codeManager.reloadAsync(configManager.get()))
                .thenCompose(ignored -> storage.loadGlobalUsage())
                .thenAccept(usageCache::replaceGlobal)
                .thenRun(() -> {
                    PluginConfig after = configManager.get();
                    if (!before.database().equals(after.database())) {
                        getLogger().warning("database.* changed. Restart the server to reconnect storage.");
                    }
                    scheduleCleanupOnMainThread();
                    getLogger().info("Reloaded config.yml, lang.yml and codes.yml.");
                });
    }

    private void scheduleCleanupOnMainThread() {
        try {
            Bukkit.getScheduler().runTask(this, this::scheduleCleanupTask);
        } catch (IllegalStateException ignored) {
            // Plugin is shutting down.
        }
    }

    public void onJoin(Player player) {
        if (!storage.isReady()) {
            return;
        }
        storage.loadPlayerUsage(player.getUniqueId())
                .thenAccept(usageCache::setPlayer)
                .exceptionally(error -> {
                    getLogger().warning("Could not load usage for " + player.getName() + ": " + error.getMessage());
                    return null;
                });
        pendingRewardService.deliverFor(player);
    }

    public void onQuit(Player player) {
        chatInputManager.clear(player.getUniqueId());
        guiListener.clear(player.getUniqueId());
        usageCache.removePlayer(player.getUniqueId());
    }

    @Override
    public void onDisable() {
        if (pendingTask != null) {
            pendingTask.cancel();
        }
        if (chatTask != null) {
            chatTask.cancel();
        }
        if (limiterTask != null) {
            limiterTask.cancel();
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        getServer().getScheduler().cancelTasks(this);
        if (placeholderHook != null) {
            placeholderHook.unregister();
        }
        if (codeManager != null) {
            try {
                codeManager.flush().orTimeout(5, TimeUnit.SECONDS).join();
            } catch (Exception ex) {
                getLogger().warning("Could not flush codes.yml: " + ex.getMessage());
            }
        }
        if (storage != null) {
            try {
                storage.close().orTimeout(5, TimeUnit.SECONDS).join();
            } catch (Exception ex) {
                getLogger().warning("Could not close storage cleanly: " + ex.getMessage());
            }
        }
        if (saveExecutor != null) {
            saveExecutor.shutdown();
        }
        if (ioExecutor != null) {
            ioExecutor.shutdown();
        }
        getLogger().info("YuanCode disabled.");
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Lang getLang() {
        return lang;
    }

    public CodeManager getCodeManager() {
        return codeManager;
    }

    public CodeEditor getCodeEditor() {
        return codeEditor;
    }

    public CodeStorage getStorage() {
        return storage;
    }

    public CodeCleanupService getCodeCleanupService() {
        return codeCleanupService;
    }

    public UsageCache getUsageCache() {
        return usageCache;
    }

    public ClaimLock getClaimLock() {
        return claimLock;
    }

    public AttemptLimiter getAttemptLimiter() {
        return attemptLimiter;
    }

    public ConditionEvaluator getConditionEvaluator() {
        return conditionEvaluator;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public PermissionHook getPermissionHook() {
        return permissionHook;
    }

    public PlaceholderHook getPlaceholderHook() {
        return placeholderHook;
    }

    public RewardService getRewardService() {
        return rewardService;
    }

    public PendingRewardService getPendingRewardService() {
        return pendingRewardService;
    }

    public RedemptionService getRedemptionService() {
        return redemptionService;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    public boolean isStorageReady() {
        return storageReady && storage != null && storage.isReady();
    }

    public boolean isDebug() {
        return configManager.get().settings().debug();
    }

    /** Convenience for console messages during startup. */
    public void logInfo(String message) {
        getLogger().info(message);
    }
}
