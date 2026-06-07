package com.strawserver.strawskyblock.database;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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
                """
        };

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }
}
