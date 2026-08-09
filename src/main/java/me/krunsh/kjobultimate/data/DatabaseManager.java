package me.krunsh.kjobultimate.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Couche storage V1.
 *
 * SQLite garde une connexion unique protegee par verrou. MySQL utilise HikariCP:
 * chaque operation emprunte une connexion au pool puis la rend immediatement.
 */
public final class DatabaseManager {

    private enum StorageType {
        SQLITE,
        MYSQL
    }

    private final KjobUltimate plugin;
    private final Object sqliteLock = new Object();

    private Connection sqliteConnection;
    private HikariDataSource mysqlDataSource;
    private StorageType storageType;
    private String storageDescription;

    public DatabaseManager(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws Exception {
        String typeName = plugin.getConfigManager().getMainConfig().getString("storage.type", "SQLITE");
        storageType = "MYSQL".equalsIgnoreCase(typeName) ? StorageType.MYSQL : StorageType.SQLITE;

        if (storageType == StorageType.MYSQL) {
            initializeMySqlPool();
        } else {
            initializeSqlite();
        }

        int unresolvedQuestRewards;
        Connection conn = borrowConnection();
        try {
            conn.setAutoCommit(true);
            createTables(conn);
            unresolvedQuestRewards = QuestRewardClaimStore.countUnresolved(conn);
        } finally {
            releaseConnection(conn);
        }
        if (unresolvedQuestRewards > 0) {
            KjobLogger.warn("[Quests] " + unresolvedQuestRewards
                + " distribution(s) de recompense necessitent une verification manuelle "
                + "dans quest_reward_claims. Aucun rejeu automatique n'est effectue.");
        }
        KjobLogger.success("Storage " + storageType + " initialise : " + storageDescription);
    }

    private void initializeSqlite() throws Exception {
        String relPath = plugin.getConfigManager().getSqliteFile();
        File dbFile = new File(plugin.getDataFolder(), relPath);
        dbFile.getParentFile().mkdirs();
        storageDescription = dbFile.getAbsolutePath();

        Class.forName("me.krunsh.kjobultimate.libs.sqlite.JDBC");
        sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + storageDescription);
        sqliteConnection.setAutoCommit(true);

        int busyTimeout = plugin.getConfigManager().getMainConfig().getInt(
            "storage.sqlite.busy_timeout_ms", 5000);
        int checkpointPages = plugin.getConfigManager().getMainConfig().getInt(
            "storage.sqlite.wal_autocheckpoint_pages", 1000);
        int cacheSizePages = plugin.getConfigManager().getMainConfig().getInt(
            "storage.sqlite.cache_size_kib", 8000);
        long journalSizeLimit = plugin.getConfigManager().getMainConfig().getLong(
            "storage.sqlite.journal_size_limit_bytes", 67108864L);
        try (Statement stmt = sqliteConnection.createStatement()) {
            for (String pragma : SqliteTuning.pragmas(
                    busyTimeout, checkpointPages, cacheSizePages,
                    journalSizeLimit)) {
                stmt.execute(pragma);
            }
        }
    }

    private void initializeMySqlPool() throws Exception {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException modernMissing) {
            Class.forName("com.mysql.jdbc.Driver");
        }

        String host = plugin.getConfigManager().getMainConfig().getString("storage.mysql.host", "127.0.0.1");
        int port = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.port", 3306);
        String database = plugin.getConfigManager().getMainConfig().getString("storage.mysql.database", "kjobsultimate");
        String username = plugin.getConfigManager().getMainConfig().getString("storage.mysql.username", "root");
        String password = plugin.getConfigManager().getMainConfig().getString("storage.mysql.password", "");
        boolean ssl = plugin.getConfigManager().getMainConfig().getBoolean("storage.mysql.use_ssl", false);
        int connectTimeout = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.connection_timeout_ms", 10000);
        int socketTimeout = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.socket_timeout_ms", 30000);
        String extra = plugin.getConfigManager().getMainConfig().getString("storage.mysql.extra_params", "");

        int maxPoolSize = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.pool.maximum_pool_size", 10);
        int minIdle = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.pool.minimum_idle", 2);
        long idleTimeout = plugin.getConfigManager().getMainConfig().getLong("storage.mysql.pool.idle_timeout_ms", 600000L);
        long maxLifetime = plugin.getConfigManager().getMainConfig().getLong("storage.mysql.pool.max_lifetime_ms", 1800000L);
        long leakDetection = plugin.getConfigManager().getMainConfig().getLong("storage.mysql.pool.leak_detection_ms", 0L);
        String testQuery = plugin.getConfigManager().getMainConfig().getString("storage.mysql.pool.connection_test_query", "SELECT 1");

        StringBuilder url = new StringBuilder("jdbc:mysql://")
            .append(host).append(':').append(port).append('/').append(database)
            .append("?useSSL=").append(ssl)
            .append("&autoReconnect=true")
            .append("&useUnicode=true")
            .append("&characterEncoding=utf8")
            .append("&serverTimezone=UTC")
            .append("&cachePrepStmts=true")
            .append("&prepStmtCacheSize=250")
            .append("&prepStmtCacheSqlLimit=2048")
            .append("&useServerPrepStmts=true")
            .append("&rewriteBatchedStatements=true")
            .append("&connectTimeout=").append(connectTimeout)
            .append("&socketTimeout=").append(socketTimeout);
        if (extra != null && !extra.trim().isEmpty()) {
            String cleaned = extra.trim();
            if (cleaned.startsWith("?")) cleaned = cleaned.substring(1);
            if (cleaned.startsWith("&")) cleaned = cleaned.substring(1);
            if (!cleaned.isEmpty()) url.append('&').append(cleaned);
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("KjobsUltimate-MySQL");
        config.setJdbcUrl(url.toString());
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setAutoCommit(true);
        if (leakDetection > 0L) config.setLeakDetectionThreshold(leakDetection);
        if (testQuery != null && !testQuery.trim().isEmpty()) config.setConnectionTestQuery(testQuery.trim());

        mysqlDataSource = new HikariDataSource(config);
        storageDescription = host + ":" + port + "/" + database + " pool=" + maxPoolSize;
    }

    private Connection borrowConnection() throws SQLException {
        if (storageType == StorageType.MYSQL) return mysqlDataSource.getConnection();
        return sqliteConnection;
    }

    private void releaseConnection(Connection conn) throws SQLException {
        if (storageType == StorageType.MYSQL && conn != null) conn.close();
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayersSql());
            stmt.execute(createJobDataSql());
            stmt.execute(createJobSlotsSql());
            stmt.execute(createQuestProgressSql());
            QuestRewardClaimStore.createTable(conn, stringType(128), numberType(),
                textType(), tableSuffix());
            stmt.execute(createBonusMultipliersSql());
        }

        ensureColumn(conn, "players", "last_job_change_at", numberType() + " NOT NULL DEFAULT 0");
        ensureColumn(conn, "players", "bossbar_enabled", intType() + " NOT NULL DEFAULT 1");
        ensureColumn(conn, "players", "actionbar_enabled", intType() + " NOT NULL DEFAULT 1");
        ensureColumn(conn, "job_slots", "slot_6", stringType(64));
        ensureIndex(conn, "job_data", "idx_job_data_rank",
            "job_id, level, xp");
        ensureIndex(conn, "quest_reward_claims", "idx_quest_rewards_status",
            "status, updated_at");
    }

    private String createPlayersSql() {
        return "CREATE TABLE IF NOT EXISTS players (" +
            "uuid " + stringType(32) + " NOT NULL PRIMARY KEY," +
            "last_seen " + numberType() + " NOT NULL," +
            "first_join " + numberType() + " NOT NULL," +
            "hud_enabled " + intType() + " NOT NULL DEFAULT 1," +
            "bossbar_enabled " + intType() + " NOT NULL DEFAULT 1," +
            "actionbar_enabled " + intType() + " NOT NULL DEFAULT 1," +
            "display_job " + stringType(64) + "," +
            "last_xp_timestamp " + numberType() + " NOT NULL DEFAULT 0," +
            "last_job_change_at " + numberType() + " NOT NULL DEFAULT 0" +
            ")" + tableSuffix();
    }

    private String createJobDataSql() {
        return "CREATE TABLE IF NOT EXISTS job_data (" +
            "uuid " + stringType(32) + " NOT NULL," +
            "job_id " + stringType(64) + " NOT NULL," +
            "level " + intType() + " NOT NULL DEFAULT 0," +
            "xp " + intType() + " NOT NULL DEFAULT 0," +
            "daily_xp " + intType() + " NOT NULL DEFAULT 0," +
            "join_timestamp " + numberType() + " NOT NULL DEFAULT 0," +
            "last_daily_reset " + numberType() + " NOT NULL DEFAULT 0," +
            "last_weekly_reset " + numberType() + " NOT NULL DEFAULT 0," +
            "assigned_daily_quests " + textType() + "," +
            "assigned_weekly_quests " + textType() + "," +
            "PRIMARY KEY (uuid, job_id)" +
            ")" + tableSuffix();
    }

    private String createJobSlotsSql() {
        return "CREATE TABLE IF NOT EXISTS job_slots (" +
            "uuid " + stringType(32) + " NOT NULL PRIMARY KEY," +
            "unlocked_slots " + intType() + " NOT NULL DEFAULT 1," +
            "slot_1 " + stringType(64) + "," +
            "slot_2 " + stringType(64) + "," +
            "slot_3 " + stringType(64) + "," +
            "slot_4 " + stringType(64) + "," +
            "slot_5 " + stringType(64) + "," +
            "slot_6 " + stringType(64) +
            ")" + tableSuffix();
    }

    private String createQuestProgressSql() {
        return "CREATE TABLE IF NOT EXISTS quest_progress (" +
            "uuid " + stringType(32) + " NOT NULL," +
            "quest_id " + stringType(128) + " NOT NULL," +
            "progress " + intType() + " NOT NULL DEFAULT 0," +
            "completed " + intType() + " NOT NULL DEFAULT 0," +
            "claimed " + intType() + " NOT NULL DEFAULT 0," +
            "completed_at " + numberType() + " NOT NULL DEFAULT 0," +
            "PRIMARY KEY (uuid, quest_id)" +
            ")" + tableSuffix();
    }

    private String createBonusMultipliersSql() {
        return "CREATE TABLE IF NOT EXISTS bonus_multipliers (" +
            "uuid " + stringType(32) + " NOT NULL," +
            "job_id " + stringType(64) + " NOT NULL," +
            "multiplier DOUBLE NOT NULL DEFAULT 1.0," +
            "set_by " + stringType(64) + "," +
            "set_at " + numberType() + " NOT NULL DEFAULT 0," +
            "PRIMARY KEY (uuid, job_id)" +
            ")" + tableSuffix();
    }

    private void ensureColumn(Connection conn, String table, String column, String type) throws SQLException {
        String catalog = storageType == StorageType.MYSQL ? conn.getCatalog() : null;
        try (ResultSet rs = conn.getMetaData().getColumns(catalog, null, table, column)) {
            if (rs.next()) return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    private void ensureIndex(Connection conn, String table, String index,
                             String columns) throws SQLException {
        String catalog = storageType == StorageType.MYSQL ? conn.getCatalog() : null;
        try (ResultSet result = conn.getMetaData().getIndexInfo(
                catalog, null, table, false, false)) {
            while (result.next()) {
                String existing = result.getString("INDEX_NAME");
                if (existing != null && existing.equalsIgnoreCase(index)) {
                    return;
                }
            }
        }
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE INDEX " + index + " ON "
                + table + " (" + columns + ")");
        }
    }

    public PlayerData loadPlayer(UUID uuid) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                return loadPlayer0(sqliteConnection, uuid);
            }
        }
        Connection conn = borrowConnection();
        try {
            return loadPlayer0(conn, uuid);
        } finally {
            releaseConnection(conn);
        }
    }

    private PlayerData loadPlayer0(Connection conn, UUID uuid) throws SQLException {
        String uuidStr = uuid.toString().replace("-", "");
        PlayerData data = new PlayerData(uuid);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT last_seen, first_join, hud_enabled, bossbar_enabled, actionbar_enabled, display_job, last_xp_timestamp, last_job_change_at FROM players WHERE uuid=?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                data.setLastSeen(rs.getLong("last_seen"));
                data.setFirstJoin(rs.getLong("first_join"));
                data.setHudEnabled(rs.getInt("hud_enabled") == 1);
                data.setBossBarHudEnabled(rs.getInt("bossbar_enabled") == 1);
                data.setActionBarHudEnabled(rs.getInt("actionbar_enabled") == 1);
                data.setLastJobChangeAt(rs.getLong("last_job_change_at"));
                String displayJob = rs.getString("display_job");
                if (displayJob != null) data.setDisplayJob(displayJob);
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT job_id, level, xp, daily_xp, last_daily_reset FROM job_data WHERE uuid=?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String jobId = rs.getString("job_id");
                    data.setLevel(jobId, rs.getInt("level"));
                    data.setXP(jobId, rs.getInt("xp"));
                    data.getDailyXPMap().put(jobId, rs.getInt("daily_xp"));
                    data.getDailyXpResetTimeMap().put(jobId, rs.getLong("last_daily_reset"));
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT unlocked_slots, slot_1, slot_2, slot_3, slot_4, slot_5, slot_6 FROM job_slots WHERE uuid=?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.setUnlockedSlots(rs.getInt("unlocked_slots"));
                    for (int i = 1; i <= 6; i++) {
                        String slotJob = rs.getString("slot_" + i);
                        if (slotJob != null) data.setJobInSlot(i, slotJob);
                    }
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT quest_id, progress, completed, claimed, completed_at FROM quest_progress WHERE uuid=?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    QuestData questData = new QuestData(
                        rs.getString("quest_id"),
                        rs.getInt("progress"),
                        rs.getInt("completed") == 1,
                        rs.getInt("claimed") == 1,
                        rs.getLong("completed_at"));
                    data.getQuestProgress().put(questData.getQuestId(), questData);
                }
            }
        }

        // Le registre de distribution est la source monotone pour empêcher
        // qu'une ancienne sauvegarde asynchrone claimed=0 ne rouvre un gain.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT quest_id FROM quest_reward_claims WHERE uuid=?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    QuestData questData = data.getQuestProgress().get(rs.getString("quest_id"));
                    if (questData != null) questData.setClaimed(true);
                }
            }
        }

        data.markClean();
        return data;
    }

    public void savePlayer(PlayerData data) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                savePlayer0(sqliteConnection, data);
            }
            return;
        }
        Connection conn = borrowConnection();
        try {
            savePlayer0(conn, data);
        } finally {
            releaseConnection(conn);
        }
    }

    private void savePlayer0(Connection conn, PlayerData data) throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            String uuidStr = data.getUuid().toString().replace("-", "");

            try (PreparedStatement ps = conn.prepareStatement(upsertPlayersSql())) {
                ps.setString(1, uuidStr);
                ps.setLong(2, data.getLastSeen());
                ps.setLong(3, data.getFirstJoin());
                ps.setInt(4, data.isHudEnabled() ? 1 : 0);
                ps.setInt(5, data.isBossBarHudEnabled() ? 1 : 0);
                ps.setInt(6, data.isActionBarHudEnabled() ? 1 : 0);
                ps.setString(7, data.getDisplayJob());
                ps.setLong(8, data.getLastXpTimestamp());
                ps.setLong(9, data.getLastJobChangeAt());
                ps.executeUpdate();
            }

            for (Map.Entry<String, Integer> entry : data.getJobLevels().entrySet()) {
                String jobId = entry.getKey();
                try (PreparedStatement ps = conn.prepareStatement(upsertJobDataSql())) {
                    ps.setString(1, uuidStr);
                    ps.setString(2, jobId);
                    ps.setInt(3, entry.getValue());
                    ps.setInt(4, data.getXP(jobId));
                    ps.setInt(5, data.getDailyXP(jobId));
                    ps.setLong(6, data.getDailyXpResetTimeMap().getOrDefault(jobId, 0L));
                    ps.executeUpdate();
                }
            }

            Map<Integer, String> slots = data.getSlotJobs();
            try (PreparedStatement ps = conn.prepareStatement(upsertJobSlotsSql())) {
                ps.setString(1, uuidStr);
                ps.setInt(2, data.getUnlockedSlots());
                for (int i = 1; i <= 6; i++) ps.setString(i + 2, slots.get(i));
                ps.executeUpdate();
            }

            for (QuestData questData : data.getQuestProgress().values()) {
                saveQuestProgress0(conn, data.getUuid(), questData.getQuestId(),
                    questData.getProgress(), questData.isCompleted(),
                    questData.isClaimed(), questData.getCompletedAt());
            }

            conn.commit();
            data.markClean();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                KjobLogger.error("Erreur rollback savePlayer", rollbackError);
            }
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    public void saveQuestProgress(UUID uuid, String questId, int progress,
                                  boolean completed, boolean claimed, long completedAt) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                saveQuestProgress0(sqliteConnection, uuid, questId, progress, completed, claimed, completedAt);
            }
            return;
        }
        Connection conn = borrowConnection();
        try {
            saveQuestProgress0(conn, uuid, questId, progress, completed, claimed, completedAt);
        } finally {
            releaseConnection(conn);
        }
    }

    private void saveQuestProgress0(Connection conn, UUID uuid, String questId, int progress,
                                    boolean completed, boolean claimed, long completedAt) throws SQLException {
        QuestProgressStore.saveMonotonic(conn,
                storageType == StorageType.MYSQL, uuidStorage(uuid), questId,
                progress, completed, claimed, completedAt);
    }

    /**
     * Réserve durablement une récompense avant toute mutation Bukkit.
     */
    public QuestRewardClaimStore.ReservationResult reserveQuestReward(
            UUID uuid, String questId, String playerName) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                return QuestRewardClaimStore.reserve(sqliteConnection, uuidStorage(uuid),
                    questId, playerName, System.currentTimeMillis());
            }
        }
        Connection conn = borrowConnection();
        try {
            return QuestRewardClaimStore.reserve(conn, uuidStorage(uuid),
                questId, playerName, System.currentTimeMillis());
        } finally {
            releaseConnection(conn);
        }
    }

    /**
     * Passe PREPARED vers DISTRIBUTING par compare-and-set.
     */
    public boolean beginQuestRewardDistribution(UUID uuid, String questId) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                return QuestRewardClaimStore.transition(sqliteConnection, uuidStorage(uuid),
                    questId, QuestRewardClaimStore.PREPARED,
                    QuestRewardClaimStore.DISTRIBUTING, System.currentTimeMillis(), null);
            }
        }
        Connection conn = borrowConnection();
        try {
            return QuestRewardClaimStore.transition(conn, uuidStorage(uuid),
                questId, QuestRewardClaimStore.PREPARED,
                QuestRewardClaimStore.DISTRIBUTING, System.currentTimeMillis(), null);
        } finally {
            releaseConnection(conn);
        }
    }

    /**
     * Annule une réservation qui n'a pas encore commencé à distribuer.
     */
    public boolean cancelPreparedQuestReward(UUID uuid, String questId) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                return QuestRewardClaimStore.cancelPrepared(sqliteConnection,
                    uuidStorage(uuid), questId);
            }
        }
        Connection conn = borrowConnection();
        try {
            return QuestRewardClaimStore.cancelPrepared(conn, uuidStorage(uuid), questId);
        } finally {
            releaseConnection(conn);
        }
    }

    /**
     * Finalise dans une même transaction le flag historique et le registre
     * monotone. FAILED reste volontairement non rejouable automatiquement :
     * une commande peut avoir eu un effet avant de lever une erreur.
     */
    public void finishQuestReward(UUID uuid, String questId, int progress,
                                  long completedAt, boolean success,
                                  String error) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                finishQuestReward0(sqliteConnection, uuid, questId, progress,
                    completedAt, success, error);
            }
            return;
        }
        Connection conn = borrowConnection();
        try {
            finishQuestReward0(conn, uuid, questId, progress,
                completedAt, success, error);
        } finally {
            releaseConnection(conn);
        }
    }

    private void finishQuestReward0(Connection conn, UUID uuid, String questId,
                                    int progress, long completedAt,
                                    boolean success, String error) throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            saveQuestProgress0(conn, uuid, questId, progress, true, true, completedAt);
            String target = success ? QuestRewardClaimStore.DISTRIBUTED : QuestRewardClaimStore.FAILED;
            boolean transitioned = QuestRewardClaimStore.transition(conn, uuidStorage(uuid),
                questId, QuestRewardClaimStore.DISTRIBUTING, target,
                System.currentTimeMillis(), error);
            if (!transitioned) {
                String current = QuestRewardClaimStore.findStatus(conn, uuidStorage(uuid), questId);
                if (!target.equals(current)) {
                    throw new SQLException("Transition de recompense invalide pour "
                        + uuid + "/" + questId + ": etat=" + current + ", cible=" + target);
                }
            }
            conn.commit();
        } catch (SQLException failure) {
            try {
                conn.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    /**
     * Reset administratif explicite : progression et verrou de distribution
     * sont réinitialisés ensemble. L'appelant doit d'abord vérifier qu'aucune
     * distribution n'est en cours dans ce processus.
     */
    public void resetQuestState(UUID uuid, String questId, int progress,
                                boolean completed, long completedAt) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                resetQuestState0(sqliteConnection, uuid, questId, progress,
                    completed, completedAt);
            }
            return;
        }
        Connection conn = borrowConnection();
        try {
            resetQuestState0(conn, uuid, questId, progress, completed, completedAt);
        } finally {
            releaseConnection(conn);
        }
    }

    private void resetQuestState0(Connection conn, UUID uuid, String questId,
                                  int progress, boolean completed,
                                  long completedAt) throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            QuestRewardClaimStore.clear(conn, uuidStorage(uuid), questId);
            QuestProgressStore.replace(conn, storageType == StorageType.MYSQL,
                uuidStorage(uuid), questId, progress, completed, false,
                completedAt);
            conn.commit();
        } catch (SQLException failure) {
            try {
                conn.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    public double getBonusMultiplier(UUID uuid, String jobId) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                return getBonusMultiplier0(sqliteConnection, uuid, jobId);
            }
        }
        Connection conn = borrowConnection();
        try {
            return getBonusMultiplier0(conn, uuid, jobId);
        } finally {
            releaseConnection(conn);
        }
    }

    private double getBonusMultiplier0(Connection conn, UUID uuid, String jobId) throws SQLException {
        String uuidStr = uuid.toString().replace("-", "");
        double max = 1.0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT multiplier FROM bonus_multipliers WHERE uuid=? AND (job_id=? OR job_id='all')")) {
            ps.setString(1, uuidStr);
            ps.setString(2, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double m = rs.getDouble("multiplier");
                    if (m > max) max = m;
                }
            }
        }
        return max;
    }

    public Map<String, Double> loadBonusMultipliers(UUID uuid) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                return loadBonusMultipliers0(sqliteConnection, uuid);
            }
        }
        Connection conn = borrowConnection();
        try {
            return loadBonusMultipliers0(conn, uuid);
        } finally {
            releaseConnection(conn);
        }
    }

    private Map<String, Double> loadBonusMultipliers0(Connection conn, UUID uuid) throws SQLException {
        String uuidStr = uuid.toString().replace("-", "");
        Map<String, Double> result = new HashMap<String, Double>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT job_id, multiplier FROM bonus_multipliers WHERE uuid=?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString("job_id"), rs.getDouble("multiplier"));
            }
        }
        return result;
    }

    public void saveBonusMultiplier(UUID uuid, String jobId, double multiplier, String setBy) throws SQLException {
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                saveBonusMultiplier0(sqliteConnection, uuid, jobId, multiplier, setBy);
            }
            return;
        }
        Connection conn = borrowConnection();
        try {
            saveBonusMultiplier0(conn, uuid, jobId, multiplier, setBy);
        } finally {
            releaseConnection(conn);
        }
    }

    private void saveBonusMultiplier0(Connection conn, UUID uuid, String jobId, double multiplier, String setBy) throws SQLException {
        String uuidStr = uuid.toString().replace("-", "");
        try (PreparedStatement ps = conn.prepareStatement(upsertBonusMultiplierSql())) {
            ps.setString(1, uuidStr);
            ps.setString(2, jobId);
            ps.setDouble(3, multiplier);
            ps.setString(4, setBy);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<RankingEntry> getTop(String jobId, int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(50, limit));
        String filter = normalizeRankingFilter(jobId);
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                return getTop0(sqliteConnection, filter, safeLimit);
            }
        }
        Connection conn = borrowConnection();
        try {
            return getTop0(conn, filter, safeLimit);
        } finally {
            releaseConnection(conn);
        }
    }

    private List<RankingEntry> getTop0(Connection conn, String jobId, int limit) throws SQLException {
        List<RankingEntry> result = new ArrayList<RankingEntry>();
        boolean global = jobId == null;
        String sql = global
            ? "SELECT uuid, SUM(level) AS total_level, SUM(xp) AS total_xp FROM job_data GROUP BY uuid ORDER BY total_level DESC, total_xp DESC LIMIT ?"
            : "SELECT uuid, level, xp FROM job_data WHERE job_id=? ORDER BY level DESC, xp DESC LIMIT ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (global) {
                ps.setInt(1, limit);
            } else {
                ps.setString(1, jobId);
                ps.setInt(2, limit);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = uuidFromStorage(rs.getString("uuid"));
                    if (uuid == null) continue;
                    int level = global ? rs.getInt("total_level") : rs.getInt("level");
                    int xp = global ? rs.getInt("total_xp") : rs.getInt("xp");
                    result.add(new RankingEntry(uuid, global ? "global" : jobId, level, xp));
                }
            }
        }
        return result;
    }

    public int getRank(UUID uuid, String jobId) throws SQLException {
        String filter = normalizeRankingFilter(jobId);
        if (storageType == StorageType.SQLITE) {
            synchronized (sqliteLock) {
                return getRank0(sqliteConnection, uuid, filter);
            }
        }
        Connection conn = borrowConnection();
        try {
            return getRank0(conn, uuid, filter);
        } finally {
            releaseConnection(conn);
        }
    }

    private int getRank0(Connection conn, UUID uuid, String jobId) throws SQLException {
        String uuidStr = uuidStorage(uuid);
        boolean global = jobId == null;
        int level;
        int xp;

        if (global) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT SUM(level) AS total_level, SUM(xp) AS total_xp FROM job_data WHERE uuid=?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return -1;
                    level = rs.getInt("total_level");
                    xp = rs.getInt("total_xp");
                    if (rs.wasNull()) return -1;
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS better FROM (" +
                        "SELECT uuid, SUM(level) AS total_level, SUM(xp) AS total_xp FROM job_data GROUP BY uuid" +
                    ") ranked WHERE ranked.total_level > ? OR (ranked.total_level = ? AND ranked.total_xp > ?)")) {
                ps.setInt(1, level);
                ps.setInt(2, level);
                ps.setInt(3, xp);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt("better") + 1 : -1;
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT level, xp FROM job_data WHERE uuid=? AND job_id=?")) {
            ps.setString(1, uuidStr);
            ps.setString(2, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return -1;
                level = rs.getInt("level");
                xp = rs.getInt("xp");
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) AS better FROM job_data WHERE job_id=? AND (level > ? OR (level = ? AND xp > ?))")) {
            ps.setString(1, jobId);
            ps.setInt(2, level);
            ps.setInt(3, level);
            ps.setInt(4, xp);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("better") + 1 : -1;
            }
        }
    }

    private String normalizeRankingFilter(String jobId) {
        if (jobId == null) return null;
        String lower = jobId.trim().toLowerCase();
        return lower.isEmpty() || "global".equals(lower) || "all".equals(lower) || "general".equals(lower) ? null : lower;
    }

    private String upsertPlayersSql() {
        if (storageType == StorageType.MYSQL) {
            return "INSERT INTO players (uuid, last_seen, first_join, hud_enabled, bossbar_enabled, actionbar_enabled, display_job, last_xp_timestamp, last_job_change_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "last_seen=VALUES(last_seen), first_join=VALUES(first_join), hud_enabled=VALUES(hud_enabled), " +
                "bossbar_enabled=VALUES(bossbar_enabled), actionbar_enabled=VALUES(actionbar_enabled), " +
                "display_job=VALUES(display_job), last_xp_timestamp=VALUES(last_xp_timestamp), " +
                "last_job_change_at=VALUES(last_job_change_at)";
        }
        return "INSERT OR REPLACE INTO players (uuid, last_seen, first_join, hud_enabled, bossbar_enabled, actionbar_enabled, display_job, last_xp_timestamp, last_job_change_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    private String upsertJobDataSql() {
        if (storageType == StorageType.MYSQL) {
            return "INSERT INTO job_data (uuid, job_id, level, xp, daily_xp, last_daily_reset) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "level=VALUES(level), xp=VALUES(xp), daily_xp=VALUES(daily_xp), last_daily_reset=VALUES(last_daily_reset)";
        }
        return "INSERT OR REPLACE INTO job_data (uuid, job_id, level, xp, daily_xp, last_daily_reset) VALUES (?, ?, ?, ?, ?, ?)";
    }

    private String upsertJobSlotsSql() {
        if (storageType == StorageType.MYSQL) {
            return "INSERT INTO job_slots (uuid, unlocked_slots, slot_1, slot_2, slot_3, slot_4, slot_5, slot_6) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "unlocked_slots=VALUES(unlocked_slots), slot_1=VALUES(slot_1), slot_2=VALUES(slot_2), " +
                "slot_3=VALUES(slot_3), slot_4=VALUES(slot_4), slot_5=VALUES(slot_5), slot_6=VALUES(slot_6)";
        }
        return "INSERT OR REPLACE INTO job_slots (uuid, unlocked_slots, slot_1, slot_2, slot_3, slot_4, slot_5, slot_6) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    private String upsertBonusMultiplierSql() {
        if (storageType == StorageType.MYSQL) {
            return "INSERT INTO bonus_multipliers (uuid, job_id, multiplier, set_by, set_at) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "multiplier=VALUES(multiplier), set_by=VALUES(set_by), set_at=VALUES(set_at)";
        }
        return "INSERT OR REPLACE INTO bonus_multipliers (uuid, job_id, multiplier, set_by, set_at) VALUES (?, ?, ?, ?, ?)";
    }

    private String stringType(int length) {
        return storageType == StorageType.MYSQL ? "VARCHAR(" + length + ")" : "TEXT";
    }

    private String textType() {
        return "TEXT";
    }

    private String intType() {
        return storageType == StorageType.MYSQL ? "INT" : "INTEGER";
    }

    private String numberType() {
        return storageType == StorageType.MYSQL ? "BIGINT" : "INTEGER";
    }

    private String tableSuffix() {
        return storageType == StorageType.MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : ";";
    }

    private String uuidStorage(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private UUID uuidFromStorage(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim();
        try {
            if (cleaned.length() == 32) {
                cleaned = cleaned.substring(0, 8) + "-" +
                    cleaned.substring(8, 12) + "-" +
                    cleaned.substring(12, 16) + "-" +
                    cleaned.substring(16, 20) + "-" +
                    cleaned.substring(20);
            }
            return UUID.fromString(cleaned);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void close() {
        if (mysqlDataSource != null) {
            mysqlDataSource.close();
            mysqlDataSource = null;
        }
        if (sqliteConnection != null) {
            try {
                sqliteConnection.close();
            } catch (SQLException e) {
                KjobLogger.error("Erreur lors de la fermeture du storage", e);
            } finally {
                sqliteConnection = null;
            }
        }
    }

    public String getDbPath() {
        return storageDescription;
    }

    public String getStorageTypeName() {
        return storageType == null ? "UNKNOWN" : storageType.name();
    }

    public boolean isOpen() {
        try {
            if (storageType == StorageType.MYSQL) return mysqlDataSource != null && !mysqlDataSource.isClosed();
            return sqliteConnection != null && !sqliteConnection.isClosed();
        } catch (SQLException ignored) {
            return false;
        }
    }

    public String getPoolStatus() {
        if (storageType != StorageType.MYSQL || mysqlDataSource == null) return "n/a";
        try {
            return "active=" + mysqlDataSource.getHikariPoolMXBean().getActiveConnections()
                + ", idle=" + mysqlDataSource.getHikariPoolMXBean().getIdleConnections()
                + ", total=" + mysqlDataSource.getHikariPoolMXBean().getTotalConnections()
                + ", waiting=" + mysqlDataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }
}
