package com.yuan.yuancode.code;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.config.ConfigManager;
import com.yuan.yuancode.config.PluginConfig;
import com.yuan.yuancode.util.FileUtil;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/** Thread-safe in-memory registry backed by codes.yml. */
public final class CodeManager {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final YuanCodePlugin plugin;
    private final ConfigManager config;
    private final ExecutorService saveExecutor;
    private final File file;
    private final ConcurrentHashMap<String, CodeDefinition> codes = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean();
    private final AtomicBoolean saveDirty = new AtomicBoolean();

    public CodeManager(YuanCodePlugin plugin, ConfigManager config, ExecutorService saveExecutor) {
        this.plugin = plugin;
        this.config = config;
        this.saveExecutor = saveExecutor;
        this.file = new File(plugin.getDataFolder(), "codes.yml");
    }

    public void loadSync() {
        try {
            if (!file.exists()) {
                plugin.saveResource("codes.yml", false);
            }
            Map<String, CodeDefinition> loaded = loadFromDisk(config.get());
            codes.clear();
            codes.putAll(loaded);
            plugin.getLogger().info("Loaded " + codes.size() + " code definition(s) from codes.yml.");
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load codes.yml: " + ex.getMessage());
        }
    }

    public CompletableFuture<Void> reloadAsync(PluginConfig snapshot) {
        return CompletableFuture.runAsync(() -> {
            Map<String, CodeDefinition> loaded = loadFromDisk(snapshot);
            codes.clear();
            codes.putAll(loaded);
        }, saveExecutor);
    }

    private Map<String, CodeDefinition> loadFromDisk(PluginConfig snapshot) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        CodeParser parser = new CodeParser(snapshot.zone(), snapshot.settings().dateFormat(),
                message -> plugin.getLogger().warning(message));
        return parser.parse(yaml.getConfigurationSection("codes"));
    }

    public Optional<CodeDefinition> find(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        String id;
        try {
            id = CodeParser.normalizeId(rawId);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return Optional.ofNullable(codes.get(id));
    }

    public CodeDefinition require(String rawId) {
        return find(rawId).orElseThrow(() -> new IllegalArgumentException("Unknown code: " + rawId));
    }

    public Collection<CodeDefinition> all() {
        List<CodeDefinition> result = new ArrayList<>(codes.values());
        result.sort(Comparator.comparing(CodeDefinition::id, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public int size() {
        return codes.size();
    }

    public boolean create(CodeDefinition definition) {
        boolean added = codes.putIfAbsent(definition.id(), definition) == null;
        if (added) {
            saveAsync();
        }
        return added;
    }

    public boolean delete(String id) {
        CodeDefinition removed = codes.remove(id == null ? "" : id.trim().toUpperCase(Locale.ROOT));
        if (removed != null) {
            saveAsync();
            return true;
        }
        return false;
    }

    /** Removes several codes with a single asynchronous YAML save. */
    public List<String> deleteMany(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> removed = new ArrayList<>();
        for (String raw : ids) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String id;
            try {
                id = CodeParser.normalizeId(raw);
            } catch (RuntimeException ex) {
                continue;
            }
            if (codes.remove(id) != null) {
                removed.add(id);
            }
        }
        if (!removed.isEmpty()) {
            saveAsync();
        }
        return List.copyOf(removed);
    }

    public Optional<CodeDefinition> update(String rawId, UnaryOperator<CodeDefinition> updater) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        String id;
        try {
            id = CodeParser.normalizeId(rawId);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        CodeDefinition updated = codes.computeIfPresent(id, (key, current) -> updater.apply(current));
        if (updated != null) {
            saveAsync();
        }
        return Optional.ofNullable(updated);
    }

    public List<String> generate(String templateId, int amount, Integer requestedLength, String requestedPrefix,
                                 PluginConfig snapshot) {
        CodeDefinition template = require(templateId);
        int maxBatch = snapshot.settings().generator().maxBatch();
        if (amount < 1 || amount > maxBatch) {
            throw new IllegalArgumentException("Amount must be between 1 and " + maxBatch);
        }
        int length = requestedLength == null
                ? snapshot.settings().generator().length()
                : Math.max(4, Math.min(32, requestedLength));
        String charset = snapshot.settings().generator().charset();
        String prefix = requestedPrefix == null || requestedPrefix.isBlank()
                ? template.id() + "-"
                : requestedPrefix.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "");
        if (prefix.length() + length > 64) {
            prefix = prefix.substring(0, Math.max(0, 64 - length));
        }

        List<String> generated = new ArrayList<>(amount);
        int guard = 0;
        while (generated.size() < amount) {
            if (++guard > amount * 100 + 1000) {
                throw new IllegalStateException("Could not generate enough unique codes; increase length or charset.");
            }
            String random = randomString(charset, length);
            String id = prefix + random;
            if (id.isBlank() || id.length() > 64) {
                continue;
            }
            CodeDefinition clone = template.withId(id);
            if (codes.putIfAbsent(id, clone) == null) {
                generated.add(id);
            }
        }
        saveAsync();
        return generated;
    }

    private static String randomString(String charset, int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(charset.charAt(RANDOM.nextInt(charset.length())));
        }
        return out.toString();
    }

    /** Coalescing asynchronous save. Every edit marks the file dirty. */
    public void saveAsync() {
        saveDirty.set(true);
        if (!saveQueued.compareAndSet(false, true)) {
            return;
        }
        saveExecutor.execute(this::drainSaves);
    }

    private void drainSaves() {
        try {
            do {
                saveDirty.set(false);
                writeSnapshot();
            } while (saveDirty.get());
        } finally {
            saveQueued.set(false);
        }
        if (saveDirty.get()) {
            saveAsync();
        }
    }

    private void writeSnapshot() {
        try {
            Map<String, CodeDefinition> snapshot = new LinkedHashMap<>();
            codes.values().stream()
                    .sorted(Comparator.comparing(CodeDefinition::id, String.CASE_INSENSITIVE_ORDER))
                    .forEach(code -> snapshot.put(code.id(), code));
            PluginConfig current = config.get();
            String yaml = CodeSerializer.serialize(snapshot.values(), current.zone(), current.settings().dateFormat());
            FileUtil.writeAtomic(file.toPath(), yaml);
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().severe("Could not save codes.yml: " + ex.getMessage());
            saveDirty.set(true);
        }
    }

    /** Waits for queued saves (bounded by the caller). */
    public CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(() -> { }, saveExecutor)
                .thenRunAsync(() -> {
                    if (saveDirty.get() || saveQueued.get()) {
                        saveDirty.set(false);
                        writeSnapshot();
                    }
                }, saveExecutor);
    }

    public File file() {
        return file;
    }
}
