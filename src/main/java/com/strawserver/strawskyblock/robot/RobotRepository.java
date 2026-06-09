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
                        rs.getInt("speed_level"),
                        rs.getInt("length_level"),
                        rs.getBoolean("active"),
                        rs.getFloat("yaw"));
                result.add(robot);
            }
        }
        return result;
    }

    /**
     * 以 UPSERT 寫入完整機器人狀態（建立或更新皆可）。
     */
    public void save(Robot robot) throws SQLException {
        String sql = "INSERT INTO straw_skyblock_robots " +
                "(island_uuid, owner_uuid, world_name, origin_x, origin_y, origin_z, yaw, " +
                "chest_x, chest_y, chest_z, speed_level, length_level, active) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE " +
                "owner_uuid=VALUES(owner_uuid), world_name=VALUES(world_name), " +
                "origin_x=VALUES(origin_x), origin_y=VALUES(origin_y), origin_z=VALUES(origin_z), " +
                "yaw=VALUES(yaw), " +
                "chest_x=VALUES(chest_x), chest_y=VALUES(chest_y), chest_z=VALUES(chest_z), " +
                "speed_level=VALUES(speed_level), length_level=VALUES(length_level), active=VALUES(active)";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, robot.getIslandUuid().toString());
            ps.setString(2, robot.getOwnerUuid().toString());
            ps.setString(3, robot.getWorldName());
            ps.setInt(4, robot.getOriginX());
            ps.setInt(5, robot.getOriginY());
            ps.setInt(6, robot.getOriginZ());
            ps.setFloat(7, robot.getYaw());
            setNullableInt(ps, 8, robot.getChestX());
            setNullableInt(ps, 9, robot.getChestY());
            setNullableInt(ps, 10, robot.getChestZ());
            ps.setInt(11, robot.getSpeedLevel());
            ps.setInt(12, robot.getLengthLevel());
            ps.setBoolean(13, robot.isActive());
            ps.executeUpdate();
        }
    }

    public void delete(UUID islandUuid) throws SQLException {
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
