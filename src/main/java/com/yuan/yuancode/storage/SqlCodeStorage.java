package com.yuan.yuancode.storage;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.config.DatabaseType;
import com.yuan.yuancode.config.PluginConfig;
import com.yuan.yuancode.util.Text;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * JDBC storage using SQLite (default) or MySQL. Every public operation is
 * executed on a dedicated pool and returns a CompletableFuture, so no database
 * call can block the server thread.
 */
public final class SqlCodeStorage implements CodeStorage {

    private final java.util.logging.Logger logger;
    private final PluginConfig config;
    private final PluginConfig.Database database;
    private final Path dataFolder;
    private final ExecutorService executor;
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Dialect dialect;
    private HikariDataSource dataSource;

    private enum Dialect {
        SQLITE,
        MYSQL
    }

    public SqlCodeStorage(YuanCodePlugin plugin, PluginConfig config) {
        this(plugin.getLogger(), plugin.getDataFolder().toPath(), config);
    }

    /** Package-visible/testable constructor that does not depend on a live server. */
    public SqlCodeStorage(java.util.logging.Logger logger, Path dataFolder, PluginConfig config) {
        this.logger = logger;
        this.config = config;
        this.database = config.database();
        this.dataFolder = dataFolder;
        this.dialect = database.type() == DatabaseType.MYSQL ? Dialect.MYSQL : Dialect.SQLITE;
        int threads = dialect == Dialect.MYSQL ? Math.max(2, database.mysql().poolSize()) : 2;
        this.executor = Executors.newFixedThreadPool(threads, threadFactory());
    }

    private static ThreadFactory threadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "YuanCode-DB-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                openPool();
                createSchema();
                ready.set(true);
                logger.info("Storage ready: " + (dialect == Dialect.MYSQL
                        ? "MySQL " + database.mysql().host() + ":" + database.mysql().port()
                        : "SQLite " + database.sqlite().file()));
            } catch (Exception ex) {
                ready.set(false);
                closePool();
                throw new CompletionException(new StorageException("Could not initialise storage", ex));
            }
        }, executor);
    }

    private void openPool() {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("YuanCode-" + dialect.name().toLowerCase(Locale.ROOT));
        hikari.setInitializationFailTimeout(10_000L);
        hikari.setAutoCommit(true);
        hikari.setLeakDetectionThreshold(0L);

        if (dialect == Dialect.SQLITE) {
            Path dbFile = dataFolder.resolve(database.sqlite().file()).normalize();
            try {
                Files.createDirectories(dbFile.toAbsolutePath().getParent());
            } catch (IOException ex) {
                throw new StorageException("Could not create SQLite directory", ex);
            }
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath()
                    + "?busy_timeout=10000&journal_mode=WAL&synchronous=NORMAL&foreign_keys=ON";
            hikari.setJdbcUrl(url);
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
            hikari.setMinimumIdle(1);
            hikari.setConnectionTimeout(10_000L);
            hikari.setConnectionInitSql("PRAGMA foreign_keys=ON");
        } else {
            PluginConfig.Mysql mysql = database.mysql();
            Map<String, String> properties = new LinkedHashMap<>();
            properties.put("useUnicode", "true");
            properties.put("characterEncoding", "UTF-8");
            properties.put("useSSL", Boolean.toString(mysql.useSsl()));
            properties.put("allowPublicKeyRetrieval", "true");
            properties.put("serverTimezone", "UTC");
            properties.put("rewriteBatchedStatements", "true");
            properties.putAll(mysql.properties());
            StringBuilder url = new StringBuilder("jdbc:mysql://")
                    .append(mysql.host()).append(':').append(mysql.port()).append('/')
                    .append(mysql.database()).append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!first) {
                    url.append('&');
                }
                first = false;
                url.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8).replace("+", "%20"));
            }
            hikari.setJdbcUrl(url.toString());
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setUsername(mysql.username());
            hikari.setPassword(mysql.password());
            hikari.setMaximumPoolSize(mysql.poolSize());
            hikari.setMinimumIdle(1);
            hikari.setConnectionTimeout(mysql.connectionTimeoutMs());
        }
        dataSource = new HikariDataSource(hikari);
    }

    private void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (dialect == Dialect.SQLITE) {
                statement.execute("PRAGMA journal_mode=WAL");
            }
            for (String ddl : ddl()) {
                statement.execute(ddl);
            }
            if (dialect == Dialect.SQLITE) {
                statement.execute("INSERT OR IGNORE INTO yuancode_meta(meta_key, meta_value) VALUES('schema_version','1')");
            } else {
                statement.execute("INSERT IGNORE INTO yuancode_meta(meta_key, meta_value) VALUES('schema_version','1')");
            }
        }
    }

    private List<String> ddl() {
        if (dialect == Dialect.SQLITE) {
            return List.of(
                    """
                    CREATE TABLE IF NOT EXISTS yuancode_meta (
                      meta_key TEXT PRIMARY KEY,
                      meta_value TEXT NOT NULL
                    )
                    """,
                    """
                    CREATE TABLE IF NOT EXISTS yuancode_usage (
                      code TEXT PRIMARY KEY,
                      global_used INTEGER NOT NULL DEFAULT 0,
                      updated_at INTEGER NOT NULL
                    )
                    """,
                    """
                    CREATE TABLE IF NOT EXISTS yuancode_redemptions (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      claim_key TEXT NOT NULL,
                      code TEXT NOT NULL,
                      player_uuid TEXT NOT NULL,
                      player_name TEXT NOT NULL,
                      player_ip TEXT,
                      period_key TEXT NOT NULL,
                      redeemed_at INTEGER NOT NULL,
                      server_id TEXT
                    )
                    """,
                    "CREATE UNIQUE INDEX IF NOT EXISTS ux_yc_claim ON yuancode_redemptions(claim_key)",
                    "CREATE INDEX IF NOT EXISTS ix_yc_code_player ON yuancode_redemptions(code, player_uuid, redeemed_at)",
                    "CREATE INDEX IF NOT EXISTS ix_yc_code_ip ON yuancode_redemptions(code, player_ip, redeemed_at)",
                    "CREATE INDEX IF NOT EXISTS ix_yc_time ON yuancode_redemptions(redeemed_at)",
                    """
                    CREATE TABLE IF NOT EXISTS yuancode_pending (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      player_uuid TEXT NOT NULL,
                      code TEXT NOT NULL,
                      reward_json TEXT NOT NULL,
                      execute_at INTEGER NOT NULL,
                      created_at INTEGER NOT NULL,
                      attempts INTEGER NOT NULL DEFAULT 0
                    )
                    """,
                    "CREATE INDEX IF NOT EXISTS ix_yc_pending_due ON yuancode_pending(execute_at)",
                    "CREATE INDEX IF NOT EXISTS ix_yc_pending_player ON yuancode_pending(player_uuid, execute_at)"
            );
        }
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS yuancode_meta (
                  meta_key VARCHAR(64) PRIMARY KEY,
                  meta_value VARCHAR(255) NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS yuancode_usage (
                  code VARCHAR(64) NOT NULL,
                  global_used INT NOT NULL DEFAULT 0,
                  updated_at BIGINT NOT NULL,
                  PRIMARY KEY (code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS yuancode_redemptions (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  claim_key VARCHAR(191) NOT NULL,
                  code VARCHAR(64) NOT NULL,
                  player_uuid CHAR(36) NOT NULL,
                  player_name VARCHAR(32) NOT NULL,
                  player_ip VARCHAR(64) NULL,
                  period_key VARCHAR(32) NOT NULL,
                  redeemed_at BIGINT NOT NULL,
                  server_id VARCHAR(64) NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY ux_yc_claim (claim_key),
                  KEY ix_yc_code_player (code, player_uuid, redeemed_at),
                  KEY ix_yc_code_ip (code, player_ip, redeemed_at),
                  KEY ix_yc_time (redeemed_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS yuancode_pending (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  player_uuid CHAR(36) NOT NULL,
                  code VARCHAR(64) NOT NULL,
                  reward_json LONGTEXT NOT NULL,
                  execute_at BIGINT NOT NULL,
                  created_at BIGINT NOT NULL,
                  attempts INT NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  KEY ix_yc_pending_due (execute_at),
                  KEY ix_yc_pending_player (player_uuid, execute_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """
        );
    }

    @Override
    public boolean isReady() {
        return ready.get() && !closed.get();
    }

    @Override
    public CompletableFuture<ReserveResult> reserve(ReserveRequest request) {
        if (!isReady()) {
            return CompletableFuture.completedFuture(ReserveResult.Failure.of(ReserveResult.Reason.DATABASE_ERROR));
        }
        return CompletableFuture.supplyAsync(() -> reserveInternal(request), executor);
    }

    private ReserveResult reserveInternal(ReserveRequest request) {
        if (!isReady()) {
            return ReserveResult.Failure.of(ReserveResult.Reason.DATABASE_ERROR);
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureUsageRow(connection, request.code(), request.now());
                int used = selectUsedForUpdate(connection, request.code());
                if (request.globalLimit() > 0 && used >= request.globalLimit()) {
                    connection.rollback();
                    return ReserveResult.Failure.of(ReserveResult.Reason.GLOBAL_LIMIT);
                }

                int playerClaims = 0;
                if (request.perPlayerLimit() > 0) {
                    playerClaims = countPlayerClaims(connection, request.code(), request.playerUuid(), request.periodKey());
                    if (playerClaims >= request.perPlayerLimit()) {
                        connection.rollback();
                        return ReserveResult.Failure.of(ReserveResult.Reason.PLAYER_LIMIT);
                    }
                }

                if (request.cooldownMillis() > 0) {
                    long last = lastRedeemedAt(connection, request.code(), request.playerUuid());
                    if (last > 0) {
                        long elapsed = request.now() - last;
                        if (elapsed < request.cooldownMillis()) {
                            long remaining = (request.cooldownMillis() - elapsed + 999L) / 1000L;
                            connection.rollback();
                            return new ReserveResult.Failure(ReserveResult.Reason.COOLDOWN, Math.max(1L, remaining));
                        }
                    }
                }

                String ip = request.playerIp();
                if (ip != null && !ip.isBlank()) {
                    long cutoff = request.now() - Math.max(0L, request.ipWindowMillis());
                    if (request.ipMaxClaims() > 0
                            && countIpClaims(connection, request.code(), ip, cutoff) >= request.ipMaxClaims()) {
                        connection.rollback();
                        return ReserveResult.Failure.of(ReserveResult.Reason.IP_LIMIT);
                    }
                    if (request.ipMaxAccounts() > 0
                            && countIpAccounts(connection, request.code(), ip, cutoff) >= request.ipMaxAccounts()) {
                        connection.rollback();
                        return ReserveResult.Failure.of(ReserveResult.Reason.IP_LIMIT);
                    }
                }

                String claimKey = claimKey(request, playerClaims + 1);
                long claimId = insertRedemption(connection, claimKey, request);
                updateUsage(connection, request.code(), request.now());
                connection.commit();
                return new ReserveResult.Success(claimId, used + 1, request.periodKey());
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                if (isBusy(ex)) {
                    logger.warning("Storage busy while claiming " + request.code() + ": " + ex.getMessage());
                    return new ReserveResult.Failure(ReserveResult.Reason.BUSY, 2L);
                }
                logger.warning("Storage error while claiming " + request.code() + ": " + ex.getMessage());
                return ReserveResult.Failure.of(ReserveResult.Reason.DATABASE_ERROR);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // Connection is returned to the pool and reset by Hikari.
                }
            }
        } catch (SQLException ex) {
            logger.warning("Could not obtain a database connection: " + ex.getMessage());
            return ReserveResult.Failure.of(ReserveResult.Reason.DATABASE_ERROR);
        }
    }

    private String claimKey(ReserveRequest request, int sequence) {
        return request.code() + "|" + request.playerUuid() + "|" + request.periodKey() + "|" + sequence;
    }

    private void ensureUsageRow(Connection connection, String code, long now) throws SQLException {
        String sql = dialect == Dialect.SQLITE
                ? "INSERT OR IGNORE INTO yuancode_usage(code, global_used, updated_at) VALUES(?,0,?)"
                : "INSERT IGNORE INTO yuancode_usage(code, global_used, updated_at) VALUES(?,0,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setLong(2, now);
            statement.executeUpdate();
        }
    }

    private int selectUsedForUpdate(Connection connection, String code) throws SQLException {
        String sql = "SELECT global_used FROM yuancode_usage WHERE code=?"
                + (dialect == Dialect.MYSQL ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private int countPlayerClaims(Connection connection, String code, UUID uuid, String periodKey) throws SQLException {
        String sql = "SELECT COUNT(*) FROM yuancode_redemptions WHERE code=? AND player_uuid=? AND period_key=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, uuid.toString());
            statement.setString(3, periodKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private long lastRedeemedAt(Connection connection, String code, UUID uuid) throws SQLException {
        String sql = "SELECT MAX(redeemed_at) FROM yuancode_redemptions WHERE code=? AND player_uuid=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private int countIpClaims(Connection connection, String code, String ip, long cutoff) throws SQLException {
        String sql = "SELECT COUNT(*) FROM yuancode_redemptions WHERE code=? AND player_ip=? AND redeemed_at>=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, ip);
            statement.setLong(3, cutoff);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private int countIpAccounts(Connection connection, String code, String ip, long cutoff) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT player_uuid) FROM yuancode_redemptions "
                + "WHERE code=? AND player_ip=? AND redeemed_at>=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, ip);
            statement.setLong(3, cutoff);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private long insertRedemption(Connection connection, String claimKey, ReserveRequest request) throws SQLException {
        String sql = """
                INSERT INTO yuancode_redemptions
                (claim_key, code, player_uuid, player_name, player_ip, period_key, redeemed_at, server_id)
                VALUES(?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, claimKey);
            statement.setString(2, request.code());
            statement.setString(3, request.playerUuid().toString());
            statement.setString(4, request.playerName());
            statement.setString(5, request.playerIp());
            statement.setString(6, request.periodKey());
            statement.setLong(7, request.now());
            statement.setString(8, request.serverId());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     dialect == Dialect.SQLITE ? "SELECT last_insert_rowid()" : "SELECT LAST_INSERT_ID()")) {
            return result.next() ? result.getLong(1) : -1L;
        }
    }

    private void updateUsage(Connection connection, String code, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE yuancode_usage SET global_used=global_used+1, updated_at=? WHERE code=?")) {
            statement.setLong(1, now);
            statement.setString(2, code);
            statement.executeUpdate();
        }
    }

    @Override
    public CompletableFuture<Map<String, Integer>> loadGlobalUsage() {
        return supply(connection -> {
            Map<String, Integer> result = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT code, global_used FROM yuancode_usage");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(rows.getString(1), rows.getInt(2));
                }
            }
            return Map.copyOf(result);
        });
    }

    @Override
    public CompletableFuture<PlayerUsage> loadPlayerUsage(UUID playerUuid) {
        return supply(connection -> {
            Map<String, Integer> claims = new LinkedHashMap<>();
            Map<String, Long> last = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT code, COUNT(*) AS total, MAX(redeemed_at) AS last_at "
                            + "FROM yuancode_redemptions WHERE player_uuid=? GROUP BY code")) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        claims.put(rows.getString(1), rows.getInt(2));
                        last.put(rows.getString(1), rows.getLong(3));
                    }
                }
            }
            return new PlayerUsage(playerUuid, claims, last);
        });
    }

    @Override
    public CompletableFuture<Long> insertPending(UUID playerUuid, String code, String rewardJson, long executeAt) {
        return supply(connection -> {
            String sql = """
                    INSERT INTO yuancode_pending(player_uuid, code, reward_json, execute_at, created_at, attempts)
                    VALUES(?,?,?,?,?,0)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, code);
                statement.setString(3, rewardJson);
                statement.setLong(4, executeAt);
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            return -1L;
        });
    }

    @Override
    public CompletableFuture<List<PendingReward>> findDuePending(long now, int limit) {
        return supply(connection -> {
            List<PendingReward> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, player_uuid, code, reward_json, execute_at, attempts "
                            + "FROM yuancode_pending WHERE execute_at<=? ORDER BY execute_at ASC LIMIT ?")) {
                statement.setLong(1, now);
                statement.setInt(2, Math.max(1, limit));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(mapPending(rows));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public CompletableFuture<List<PendingReward>> findPendingForPlayer(UUID playerUuid, long dueBefore, int limit) {
        return supply(connection -> {
            List<PendingReward> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, player_uuid, code, reward_json, execute_at, attempts "
                            + "FROM yuancode_pending WHERE player_uuid=? AND execute_at<=? "
                            + "ORDER BY execute_at ASC LIMIT ?")) {
                statement.setString(1, playerUuid.toString());
                statement.setLong(2, dueBefore);
                statement.setInt(3, Math.max(1, limit));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(mapPending(rows));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    private PendingReward mapPending(ResultSet rows) throws SQLException {
        return new PendingReward(
                rows.getLong("id"),
                UUID.fromString(rows.getString("player_uuid")),
                rows.getString("code"),
                rows.getString("reward_json"),
                rows.getLong("execute_at"),
                rows.getInt("attempts")
        );
    }

    @Override
    public CompletableFuture<Void> deletePending(long id) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM yuancode_pending WHERE id=?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Integer> incrementPendingAttempts(long id) {
        return supply(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE yuancode_pending SET attempts=attempts+1 WHERE id=?")) {
                update.setLong(1, id);
                update.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT attempts FROM yuancode_pending WHERE id=?")) {
                select.setLong(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public CompletableFuture<Integer> purgePending(long before) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM yuancode_pending WHERE execute_at<?")) {
                statement.setLong(1, before);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<ExportResult> exportCsv(Path target, boolean includeIp, ZoneId zone) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isReady()) {
                throw new StorageException("Storage is not ready");
            }
            try {
                Files.createDirectories(target.toAbsolutePath().getParent());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                        .withZone(zone);
                long rowsWritten = 0;
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "SELECT id, code, player_uuid, player_name, player_ip, period_key, "
                                     + "redeemed_at, server_id FROM yuancode_redemptions ORDER BY id ASC");
                     ResultSet rows = statement.executeQuery();
                     BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                    writer.write('\uFEFF');
                    writer.write("id,code,player_uuid,player_name,player_ip,period_key,redeemed_at,redeemed_at_epoch_ms,server_id\n");
                    while (rows.next()) {
                        long redeemedAt = rows.getLong("redeemed_at");
                        String ip = includeIp ? rows.getString("player_ip") : "";
                        if (ip == null) {
                            ip = "";
                        }
                        String line = rows.getLong("id") + ","
                                + csv(rows.getString("code")) + ","
                                + csv(rows.getString("player_uuid")) + ","
                                + csv(rows.getString("player_name")) + ","
                                + csv(ip) + ","
                                + csv(rows.getString("period_key")) + ","
                                + csv(formatter.format(Instant.ofEpochMilli(redeemedAt))) + ","
                                + redeemedAt + ","
                                + csv(rows.getString("server_id")) + "\n";
                        writer.write(line);
                        rowsWritten++;
                    }
                }
                return new ExportResult(target, rowsWritten);
            } catch (SQLException | IOException ex) {
                throw new CompletionException(new StorageException("CSV export failed", ex));
            }
        }, executor);
    }

    private static String csv(String value) {
        return com.yuan.yuancode.util.FileUtil.csv(value);
    }

    @Override
    public CompletableFuture<StorageStats> loadStats() {
        return supply(connection -> {
            long redemptions;
            long pending;
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM yuancode_redemptions")) {
                redemptions = rows.next() ? rows.getLong(1) : 0L;
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM yuancode_pending")) {
                pending = rows.next() ? rows.getLong(1) : 0L;
            }
            return new StorageStats(redemptions, pending);
        });
    }

    private <T> CompletableFuture<T> supply(SqlFunction<T> function) {
        if (!isReady()) {
            return CompletableFuture.failedFuture(new StorageException("Storage is not ready"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return function.apply(connection);
            } catch (SQLException ex) {
                throw new CompletionException(new StorageException("Database operation failed", ex));
            }
        }, executor);
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Nothing useful can be done here.
        }
    }

    private static boolean isBusy(SQLException ex) {
        String state = ex.getSQLState();
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        return "40001".equals(state) || "23000".equals(state)
                || message.contains("locked") || message.contains("busy")
                || message.contains("deadlock") || message.contains("duplicate");
    }

    @Override
    public CompletableFuture<Void> close() {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        ready.set(false);
        CompletableFuture<Void> future = CompletableFuture.runAsync(this::closePool, executor);
        future.whenComplete((ignored, error) -> executor.shutdown());
        return future;
    }

    private void closePool() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}
