package com.strawserver.strawskyblock.protection;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.island.IslandFlag;
import com.strawserver.strawskyblock.util.AdminBypass;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * 判定玩家是否可在某位置 / 對某實體執行行為。
 * 全程使用 IslandCache，不直接查資料庫，以維持高頻互動效能。
 */
public class ProtectionService {

    private final StrawSkyBlockPlugin plugin;

    public ProtectionService(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inIslandWorld(Location location) {
        return location != null && location.getWorld() != null
                && location.getWorld().getName().equals(plugin.getConfigManager().getIslandWorld());
    }

    /**
     * 世界保護（破壞／放置／互動／傷害）的繞過判定。
     *
     * <p>全域管理員繞過：只要持有 {@link AdminBypass#PERMISSION} 權限即放行所有
     * 世界保護限制（破壞／放置／互動／傷害），不再額外要求 {@code /is admin bypass}
     * 切換開關，與其他「限制類」繞過一致（見 {@link AdminBypass}）。</p>
     *
     * <p>{@code /is admin bypass} 切換開關仍保留作為手動繞過途徑：未持有權限但被
     * 暫時授權切換的情境仍可生效。</p>
     */
    public boolean hasBypass(Player player) {
        return AdminBypass.hasBypass(player)
                || plugin.isBypassing(player.getUniqueId());
    }

    /**
     * 共用判定：trusted 成員放行；訪客依 flag 決定。
     */
    private boolean checkVisitorFlag(Player player, Location location, IslandFlag flag) {
        if (!inIslandWorld(location)) {
            return true;
        }
        if (hasBypass(player)) {
            return true;
        }
        Island island = plugin.getIslandService().getByLocation(location);
        if (island == null) {
            return false;
        }
        if (island.getRole(player.getUniqueId()).isTrusted()) {
            return true;
        }
        return island.getFlag(flag);
    }

    public boolean canBreak(Player player, Location location) {
        return checkVisitorFlag(player, location, IslandFlag.VISITOR_BREAK);
    }

    public boolean canPlace(Player player, Location location) {
        return checkVisitorFlag(player, location, IslandFlag.VISITOR_PLACE);
    }

    public boolean canInteract(Player player, Location location, InteractionType type) {
        return checkVisitorFlag(player, location, type.getFlag());
    }

    public boolean canPickup(Player player, Location location) {
        return checkVisitorFlag(player, location, IslandFlag.VISITOR_PICKUP);
    }

    public boolean canDamageEntity(Player player, Entity entity) {
        Location location = entity.getLocation();
        if (!inIslandWorld(location)) {
            return true;
        }
        if (hasBypass(player)) {
            return true;
        }
        Island island = plugin.getIslandService().getByLocation(location);
        if (island == null) {
            return false;
        }
        if (entity instanceof Player) {
            return island.getFlag(IslandFlag.PVP);
        }
        if (island.getRole(player.getUniqueId()).isTrusted()) {
            return true;
        }
        return island.getFlag(IslandFlag.VISITOR_DAMAGE_ANIMALS);
    }

    public Island islandAt(Location location) {
        return plugin.getIslandService().getByLocation(location);
    }
}
