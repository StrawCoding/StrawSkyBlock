package com.strawserver.strawskyblock.island;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 島嶼資料的 JDBC 存取層。所有方法皆為同步阻塞，必須於非同步執行緒呼叫。
 */
public class IslandRepository {

    private final StrawSkyBlockPlugin plugin;

    public IslandRepository(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    private DatabaseManager db() {
        return plugin.getDatabaseManager();
    }

    /**
     * 取得目前最大島嶼 index（含已刪除），用於分配不重複的新座標。
     */
    public int findMaxIndex() throws SQLException {
        String sql = "SELECT MAX(island_index) AS max_idx FROM straw_skyblock_islands";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int max = rs.getInt("max_idx");
                return rs.wasNull() ? -1 : max;
            }
        }
        return -1;
    }

    public boolean ownerHasIsland(UUID ownerUuid) throws SQLException {
        String sql = "SELECT 1 FROM straw_skyblock_islands WHERE owner_uuid = ? AND deleted = FALSE LIMIT 1";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 完整建立島嶼：寫入 islands、owner member、預設 flags、stats。
     */
    public void insertIsland(Island island, Map<IslandFlag, Boolean> defaultFlags) throws SQLException {
        String islandSql = "INSERT INTO straw_skyblock_islands " +
                "(island_uuid, owner_uuid, owner_name, world_name, island_index, center_x, center_y, center_z, " +
                "home_x, home_y, home_z, home_yaw, home_pitch, size) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = db().getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(islandSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, island.getIslandUuid().toString());
                    ps.setString(2, island.getOwnerUuid().toString());
                    ps.setString(3, island.getOwnerName());
                    ps.setString(4, island.getWorldName());
                    ps.setInt(5, island.getIndex());
                    ps.setInt(6, island.getCenterX());
                    ps.setInt(7, island.getCenterY());
                    ps.setInt(8, island.getCenterZ());
                    ps.setDouble(9, island.getHomeX());
                    ps.setDouble(10, island.getHomeY());
                    ps.setDouble(11, island.getHomeZ());
                    ps.setFloat(12, island.getHomeYaw());
                    ps.setFloat(13, island.getHomePitch());
                    ps.setInt(14, island.getSize());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            island.setId(keys.getLong(1));
                        }
                    }
                }

                String memberSql = "INSERT INTO straw_skyblock_members " +
                        "(island_uuid, player_uuid, player_name, role) VALUES (?,?,?,?)";
                try (PreparedStatement ps = c.prepareStatement(memberSql)) {
                    ps.setString(1, island.getIslandUuid().toString());
                    ps.setString(2, island.getOwnerUuid().toString());
                    ps.setString(3, island.getOwnerName());
                    ps.setString(4, IslandRole.OWNER.name());
                    ps.executeUpdate();
                }

                String flagSql = "INSERT INTO straw_skyblock_flags (island_uuid, flag_key, flag_value) VALUES (?,?,?)";
                try (PreparedStatement ps = c.prepareStatement(flagSql)) {
                    for (Map.Entry<IslandFlag, Boolean> entry : defaultFlags.entrySet()) {
                        ps.setString(1, island.getIslandUuid().toString());
                        ps.setString(2, entry.getKey().getKey());
                        ps.setBoolean(3, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                String statsSql = "INSERT INTO straw_skyblock_stats (island_uuid) VALUES (?)";
                try (PreparedStatement ps = c.prepareStatement(statsSql)) {
                    ps.setString(1, island.getIslandUuid().toString());
                    ps.executeUpdate();
                }

                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public List<Island> loadAllActiveIslands() throws SQLException {
        Map<String, Island> byUuid = new LinkedHashMap<>();
        String islandSql = "SELECT * FROM straw_skyblock_islands WHERE deleted = FALSE";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(islandSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Island island = new Island(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("island_uuid")),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("owner_name"),
                        rs.getString("world_name"),
                        rs.getInt("island_index"),
                        rs.getInt("center_x"),
                        rs.getInt("center_y"),
                        rs.getInt("center_z"),
                        rs.getDouble("home_x"),
                        rs.getDouble("home_y"),
                        rs.getDouble("home_z"),
                        rs.getFloat("home_yaw"),
                        rs.getFloat("home_pitch"),
                        rs.getInt("size"));
                byUuid.put(island.getIslandUuid().toString(), island);
            }
        }

        if (byUuid.isEmpty()) {
            return new ArrayList<>();
        }

        // members
        String memberSql = "SELECT * FROM straw_skyblock_members";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(memberSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Island island = byUuid.get(rs.getString("island_uuid"));
                if (island == null) {
                    continue;
                }
                island.addMember(new IslandMember(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        IslandRole.fromString(rs.getString("role"))));
            }
        }

        // flags
        String flagSql = "SELECT * FROM straw_skyblock_flags";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(flagSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Island island = byUuid.get(rs.getString("island_uuid"));
                if (island == null) {
                    continue;
                }
                IslandFlag flag = IslandFlag.fromKey(rs.getString("flag_key"));
                if (flag != null) {
                    island.setFlag(flag, rs.getBoolean("flag_value"));
                }
            }
        }

        return new ArrayList<>(byUuid.values());
    }

    public void updateHome(Island island) throws SQLException {
        String sql = "UPDATE straw_skyblock_islands SET home_x=?, home_y=?, home_z=?, home_yaw=?, home_pitch=? " +
                "WHERE island_uuid=?";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, island.getHomeX());
            ps.setDouble(2, island.getHomeY());
            ps.setDouble(3, island.getHomeZ());
            ps.setFloat(4, island.getHomeYaw());
            ps.setFloat(5, island.getHomePitch());
            ps.setString(6, island.getIslandUuid().toString());
            ps.executeUpdate();
        }
    }

    public void updateFlag(UUID islandUuid, IslandFlag flag, boolean value) throws SQLException {
        String sql = "INSERT INTO straw_skyblock_flags (island_uuid, flag_key, flag_value) VALUES (?,?,?) " +
                "ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value)";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, islandUuid.toString());
            ps.setString(2, flag.getKey());
            ps.setBoolean(3, value);
            ps.executeUpdate();
        }
    }

    public void softDeleteIsland(UUID islandUuid) throws SQLException {
        try (Connection c = db().getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE straw_skyblock_islands SET deleted = TRUE WHERE island_uuid = ?")) {
                    ps.setString(1, islandUuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM straw_skyblock_members WHERE island_uuid = ?")) {
                    ps.setString(1, islandUuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM straw_skyblock_flags WHERE island_uuid = ?")) {
                    ps.setString(1, islandUuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM straw_skyblock_invites WHERE island_uuid = ?")) {
                    ps.setString(1, islandUuid.toString());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void addOrUpdateMember(UUID islandUuid, IslandMember member) throws SQLException {
        String sql = "INSERT INTO straw_skyblock_members (island_uuid, player_uuid, player_name, role) " +
                "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), role=VALUES(role)";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, islandUuid.toString());
            ps.setString(2, member.getPlayerUuid().toString());
            ps.setString(3, member.getPlayerName());
            ps.setString(4, member.getRole().name());
            ps.executeUpdate();
        }
    }

    public void removeMember(UUID islandUuid, UUID playerUuid) throws SQLException {
        String sql = "DELETE FROM straw_skyblock_members WHERE island_uuid=? AND player_uuid=?";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, islandUuid.toString());
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
        }
    }

    public void updateOwner(UUID islandUuid, UUID newOwner, String newOwnerName) throws SQLException {
        String sql = "UPDATE straw_skyblock_islands SET owner_uuid=?, owner_name=? WHERE island_uuid=?";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newOwner.toString());
            ps.setString(2, newOwnerName);
            ps.setString(3, islandUuid.toString());
            ps.executeUpdate();
        }
    }

    // ---- invites ----
    public void insertInvite(UUID islandUuid, UUID inviter, UUID target, long expiresAtMillis) throws SQLException {
        String sql = "INSERT INTO straw_skyblock_invites (island_uuid, inviter_uuid, target_uuid, expires_at) " +
                "VALUES (?,?,?,?)";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, islandUuid.toString());
            ps.setString(2, inviter.toString());
            ps.setString(3, target.toString());
            ps.setTimestamp(4, new Timestamp(expiresAtMillis));
            ps.executeUpdate();
        }
    }

    public String findActiveInviteIsland(UUID target) throws SQLException {
        String sql = "SELECT island_uuid FROM straw_skyblock_invites " +
                "WHERE target_uuid=? AND accepted=FALSE AND expires_at > NOW() ORDER BY created_at DESC LIMIT 1";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("island_uuid");
                }
            }
        }
        return null;
    }

    public void deleteInvites(UUID target) throws SQLException {
        String sql = "DELETE FROM straw_skyblock_invites WHERE target_uuid=?";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            ps.executeUpdate();
        }
    }

    // ---- stats ----
    public void incrementStats(UUID islandUuid, long blocksBroken, long generatorBroken,
                               long ores, long animals) throws SQLException {
        String sql = "INSERT INTO straw_skyblock_stats " +
                "(island_uuid, blocks_broken, generator_blocks_broken, ores_generated, animals_spawned, last_active_at) " +
                "VALUES (?,?,?,?,?,NOW()) " +
                "ON DUPLICATE KEY UPDATE " +
                "blocks_broken = blocks_broken + VALUES(blocks_broken), " +
                "generator_blocks_broken = generator_blocks_broken + VALUES(generator_blocks_broken), " +
                "ores_generated = ores_generated + VALUES(ores_generated), " +
                "animals_spawned = animals_spawned + VALUES(animals_spawned), " +
                "last_active_at = NOW()";
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, islandUuid.toString());
            ps.setLong(2, blocksBroken);
            ps.setLong(3, generatorBroken);
            ps.setLong(4, ores);
            ps.setLong(5, animals);
            ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> topBy(String column, int limit) throws SQLException {
        String sql = "SELECT s.island_uuid, i.owner_name, s." + column + " AS value " +
                "FROM straw_skyblock_stats s JOIN straw_skyblock_islands i ON s.island_uuid = i.island_uuid " +
                "WHERE i.deleted = FALSE ORDER BY s." + column + " DESC LIMIT ?";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = db().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("owner_name", rs.getString("owner_name"));
                    row.put("value", rs.getLong("value"));
                    result.add(row);
                }
            }
        }
        return result;
    }
}
