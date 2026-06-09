package com.strawserver.strawskyblock.robot;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 小機器人資料的 JDBC 存取層。所有方法皆為同步阻塞，必須於非同步執行緒呼叫。
 */
public class RobotRepository {

    private final StrawSkyBlockPlugin plugin;

    public RobotRepository(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    private DatabaseManager db() {
        return plugin.getDatabaseManager();
    }

    public List<Robot> loadAll() throws SQLException {
        List<Robot> result = new ArrayList<>();
        String sql = "SELECT * FROM straw_skyblock_robots";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Integer chestX = (Integer) rs.getObject("chest_x");
                Integer chestY = (Integer) rs.getObject("chest_y");
                Integer chestZ = (Integer) rs.getObject("chest_z");
                Robot robot = new Robot(
                        UUID.fromString(rs.getString("island_uuid")),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("world_name"),
                        rs.getInt("origin_x"),
                        rs.getInt("origin_y"),
                        rs.getInt("origin_z"),
                        chestX, chestY, chestZ,
                        rs.getInt("level"),
                        rs.getBoolean("active"),
                        rs.getFloat("yaw"));
                result.add(robot);
            }
        }
        return result;
    }

    /**
     * 以 UPSERT 寫入完整機器人狀態（建立或更新皆可，主鍵為放置座標）。
     */
    public void save(Robot robot) throws SQLException {
        String sql = "INSERT INTO straw_skyblock_robots " +
                "(world_name, origin_x, origin_y, origin_z, island_uuid, owner_uuid, yaw, " +
                "chest_x, chest_y, chest_z, level, active) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE " +
                "island_uuid=VALUES(island_uuid), owner_uuid=VALUES(owner_uuid), yaw=VALUES(yaw), " +
                "chest_x=VALUES(chest_x), chest_y=VALUES(chest_y), chest_z=VALUES(chest_z), " +
                "level=VALUES(level), active=VALUES(active)";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, robot.getWorldName());
            ps.setInt(2, robot.getOriginX());
            ps.setInt(3, robot.getOriginY());
            ps.setInt(4, robot.getOriginZ());
            ps.setString(5, robot.getIslandUuid().toString());
            ps.setString(6, robot.getOwnerUuid().toString());
            ps.setFloat(7, robot.getYaw());
            setNullableInt(ps, 8, robot.getChestX());
            setNullableInt(ps, 9, robot.getChestY());
            setNullableInt(ps, 10, robot.getChestZ());
            ps.setInt(11, robot.getLevel());
            ps.setBoolean(12, robot.isActive());
            ps.executeUpdate();
        }
    }

    /**
     * 依放置座標刪除單一機器人。
     */
    public void deleteByLocation(String worldName, int x, int y, int z) throws SQLException {
        String sql = "DELETE FROM straw_skyblock_robots "
                + "WHERE world_name=? AND origin_x=? AND origin_y=? AND origin_z=?";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, worldName);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        }
    }

    /**
     * 刪除某座島嶼的所有機器人（島嶼被刪除時呼叫）。
     */
    public void deleteByIsland(UUID islandUuid) throws SQLException {
        String sql = "DELETE FROM straw_skyblock_robots WHERE island_uuid=?";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, islandUuid.toString());
            ps.executeUpdate();
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}
