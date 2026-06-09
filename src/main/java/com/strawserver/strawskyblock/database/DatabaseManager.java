package com.strawserver.strawskyblock.database;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 管理資料庫連線與資料表初始化。
 * 注意：所有查詢都應於非同步執行緒呼叫 {@link #getConnection()}。
 */
public class DatabaseManager {

    private final StrawSkyBlockPlugin plugin;
    private final MySQLProvider provider = new MySQLProvider();
    private DataSource dataSource;

    public DatabaseManager(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        this.dataSource = provider.init(plugin.getConfigManager());
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) {
                throw new SQLException("資料庫連線驗證失敗。");
            }
        }
        initSchema();
        plugin.getLogger().info("MySQL 連線成功，資料表已就緒。");
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("資料庫尚未初始化。");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return dataSource != null;
    }

    public void close() {
        provider.close();
        dataSource = null;
    }

    private void initSchema() throws SQLException {
        String[] statements = new String[]{
                """
                CREATE TABLE IF NOT EXISTS straw_skyblock_islands (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    island_uuid CHAR(36) NOT NULL UNIQUE,
                    owner_uuid CHAR(36) NOT NULL,
                    owner_name VARCHAR(16) NOT NULL,
                    world_name VARCHAR(64) NOT NULL,
                    island_index INT NOT NULL,
                    center_x INT NOT NULL,
                    center_y INT NOT NULL,
                    center_z INT NOT NULL,
                    home_x DOUBLE NOT NULL,
                    home_y DOUBLE NOT NULL,
                    home_z DOUBLE NOT NULL,
                    home_yaw FLOAT NOT NULL,
                    home_pitch FLOAT NOT NULL,
                    size INT NOT NULL DEFAULT 200,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE,
                    INDEX idx_owner_uuid (owner_uuid),
                    INDEX idx_world_location (world_name, center_x, center_z)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS straw_skyblock_members (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    island_uuid CHAR(36) NOT NULL,
                    player_uuid CHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    role VARCHAR(16) NOT NULL,
                    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_member (island_uuid, player_uuid),
                    INDEX idx_player_uuid (player_uuid)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS straw_skyblock_flags (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    island_uuid CHAR(36) NOT NULL,
                    flag_key VARCHAR(64) NOT NULL,
                    flag_value BOOLEAN NOT NULL,
                    UNIQUE KEY unique_flag (island_uuid, flag_key)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS straw_skyblock_invites (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    island_uuid CHAR(36) NOT NULL,
                    inviter_uuid CHAR(36) NOT NULL,
                    target_uuid CHAR(36) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP NOT NULL,
                    accepted BOOLEAN NOT NULL DEFAULT FALSE,
                    INDEX idx_target_uuid (target_uuid)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS straw_skyblock_stats (
                    island_uuid CHAR(36) PRIMARY KEY,
                    blocks_broken BIGINT NOT NULL DEFAULT 0,
                    generator_blocks_broken BIGINT NOT NULL DEFAULT 0,
                    ores_generated BIGINT NOT NULL DEFAULT 0,
                    animals_spawned BIGINT NOT NULL DEFAULT 0,
                    last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS straw_skyblock_robots (
                    world_name VARCHAR(64) NOT NULL,
                    origin_x INT NOT NULL,
                    origin_y INT NOT NULL,
                    origin_z INT NOT NULL,
                    island_uuid CHAR(36) NOT NULL,
                    owner_uuid CHAR(36) NOT NULL,
                    yaw FLOAT NOT NULL DEFAULT 0,
                    chest_x INT NULL,
                    chest_y INT NULL,
                    chest_z INT NULL,
                    level INT NOT NULL DEFAULT 1,
                    active BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (world_name, origin_x, origin_y, origin_z),
                    INDEX idx_robot_owner (owner_uuid),
                    INDEX idx_robot_island (island_uuid)
                )
                """
        };

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
        runMigrations();
    }

    /**
     * 對既有資料表執行相容性遷移。所有步驟皆為冪等，重複執行安全。
     */
    private void runMigrations() throws SQLException {
        // v1.0.24：朝向欄位。
        addColumnIfMissing("straw_skyblock_robots", "yaw", "FLOAT NOT NULL DEFAULT 0");
        // v1.0.25：統一等級制 + 多台機器人（以座標為主鍵）。
        migrateRobotUnifiedLevel();
    }

    /**
     * 將舊版「每島一台、island_uuid 主鍵、speed_level/length_level」的機器人表，
     * 遷移為「以座標為主鍵、單一 level、支援多台」。
     */
    private void migrateRobotUnifiedLevel() throws SQLException {
        String table = "straw_skyblock_robots";
        // 1) 新增統一 level 欄位。
        addColumnIfMissing(table, "level", "INT NOT NULL DEFAULT 1");
        // 2) 由舊的 speed_level / length_level 推導 level（取兩者較大值），僅當兩欄皆存在時執行。
        if (columnExists(table, "speed_level") && columnExists(table, "length_level")) {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("UPDATE " + table
                        + " SET level = GREATEST(speed_level, length_level) WHERE level <= 1");
            }
        }
        // 3) island_uuid 由可空改為非空（舊版本即為 NOT NULL，這裡確保一致）。
        // 4) 將主鍵由 island_uuid 改為 (world_name, origin_x, origin_y, origin_z)。
        List<String> pk = primaryKeyColumns(table);
        if (pk.size() == 1 && pk.get(0).equalsIgnoreCase("island_uuid")) {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table
                        + " DROP PRIMARY KEY,"
                        + " ADD PRIMARY KEY (world_name, origin_x, origin_y, origin_z)");
                plugin.getLogger().info("資料表 " + table + " 主鍵已改為放置座標（支援多台機器人）。");
            }
            addIndexIfMissing(table, "idx_robot_island", "island_uuid");
        }
        // 5) 移除已不再使用的舊等級欄位。
        dropColumnIfExists(table, "speed_level");
        dropColumnIfExists(table, "length_level");
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            plugin.getLogger().info("資料表 " + table + " 已新增欄位 " + column + "。");
        } catch (SQLException e) {
            // 1060 = duplicate column：欄位已存在，視為已完成遷移。
            if (e.getErrorCode() != 1060) {
                throw e;
            }
        }
    }

    private void dropColumnIfExists(String table, String column) throws SQLException {
        if (!columnExists(table, column)) {
            return;
        }
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
            plugin.getLogger().info("資料表 " + table + " 已移除舊欄位 " + column + "。");
        } catch (SQLException e) {
            // 1091 = can't drop；欄位已不存在，忽略。
            if (e.getErrorCode() != 1091) {
                throw e;
            }
        }
    }

    private void addIndexIfMissing(String table, String indexName, String column) throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD INDEX " + indexName + " (" + column + ")");
        } catch (SQLException e) {
            // 1061 = duplicate key name：索引已存在，忽略。
            if (e.getErrorCode() != 1061) {
                throw e;
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (Connection connection = getConnection();
             java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> primaryKeyColumns(String table) throws SQLException {
        List<String> columns = new java.util.ArrayList<>();
        String sql = "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = 'PRIMARY' "
                + "ORDER BY SEQ_IN_INDEX";
        try (Connection connection = getConnection();
             java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }
}
