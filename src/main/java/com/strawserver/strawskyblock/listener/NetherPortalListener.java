package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.ConfigManager;
import com.strawserver.strawskyblock.util.IslandTeleportHelper;
import com.strawserver.strawskyblock.world.NetherPortalRouter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * 下界獨立空島模式（v1.0.28）：攔截下界傳送門事件，將空島主世界 ↔ 專屬下界世界
 * 以「相同座標」1:1 對應導向（不套用原版 8 倍縮放），讓每座島擁有隔離的下界區域。
 *
 * <p>導向沿用 {@link IslandTeleportHelper} 的穩健跨世界傳送（區塊票證／預載／同步傳送），
 * 以避免客戶端卡在「載入地形」。導向下界時若落點下方為虛空，會視設定鋪設一小塊基岩安全平台。</p>
 *
 * <p>注意：依需求，下界目前<b>不生成地形與初始模板</b>，僅建立世界與傳送門導向。</p>
 */
public class NetherPortalListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public NetherPortalListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isNetherEnabled()) {
            return;
        }
        Location from = event.getFrom();
        if (from == null || from.getWorld() == null) {
            return;
        }
        boolean netherCause = event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL;
        NetherPortalRouter.Direction direction = NetherPortalRouter.resolve(
                true, netherCause, from.getWorld().getName(),
                cfg.getIslandWorld(), cfg.getNetherWorld());
        if (direction == NetherPortalRouter.Direction.NONE) {
            return;
        }

        World destWorld = direction == NetherPortalRouter.Direction.TO_NETHER
                ? plugin.getWorldManager().getNetherWorld()
                : plugin.getWorldManager().getIslandWorld();
        if (destWorld == null) {
            plugin.getLogger().warning("[Nether] 下界傳送門導向失敗：目的地世界尚未載入（"
                    + direction + "）。");
            return;
        }

        int x = from.getBlockX();
        int z = from.getBlockZ();
        int y = NetherPortalRouter.clampY(from.getBlockY(), destWorld.getMinHeight(), destWorld.getMaxHeight());
        Location destination = new Location(destWorld, x + 0.5, y, z + 0.5, from.getYaw(), from.getPitch());

        // 進入下界且落點為虛空時，鋪設一小塊基岩安全平台（地形尚未生成階段的防墜底線）。
        if (direction == NetherPortalRouter.Direction.TO_NETHER && cfg.isNetherSafePlatform()) {
            ensureSafePlatform(destWorld, x, y, z);
        }

        // 取消原版傳送門導向（避免 8 倍縮放／搜尋既有傳送門），改走插件穩健跨世界傳送。
        event.setCancelled(true);
        Player player = event.getPlayer();
        String operation = direction == NetherPortalRouter.Direction.TO_NETHER
                ? "nether-portal-in" : "nether-portal-out";
        IslandTeleportHelper.teleportPlayer(plugin, player, destination, null, operation);
    }

    /**
     * 若落點下方無可站立方塊，於落點下方鋪設 3x3 基岩，並清出上方 2 格空間，避免玩家掉入虛空。
     */
    private void ensureSafePlatform(World world, int x, int y, int z) {
        boolean hasGround = false;
        for (int dx = -1; dx <= 1 && !hasGround; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (world.getBlockAt(x + dx, y - 1, z + dz).getType().isSolid()) {
                    hasGround = true;
                    break;
                }
            }
        }
        if (hasGround) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.BEDROCK, false);
                for (int dy = 0; dy <= 1; dy++) {
                    Block above = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (above.getType() != Material.AIR) {
                        above.setType(Material.AIR, false);
                    }
                }
            }
        }
    }
}
