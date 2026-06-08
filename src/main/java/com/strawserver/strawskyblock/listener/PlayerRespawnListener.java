package com.strawserver.strawskyblock.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.util.IslandTeleportHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 處理空島成員的重生位置。
 *
 * <p>問題根源：空島世界的全域出生點被固定在 (0.5, islandY, 0.5)，且僅鋪設了一小塊基岩平台。
 * 因此在空島世界內死亡且沒有床／重生錨的玩家，原版會將其送往該全域出生點，而非自己的島嶼家點，
 * 對 index 0 以外的島嶼尤其危險（落點遠離自己的島，甚至可能掉入虛空）。</p>
 *
 * <p>採用的規則（僅治本、不擴大影響範圍）：當玩家屬於某座空島、其家點可用、且此次「預設重生落點」
 * 位於空島世界、並且不是玩家自行設定的床／重生錨時，於重生完成後將玩家傳送至自己的島嶼家點。
 * 主世界（或其他世界）的死亡重生完全不受影響，玩家自設的床／錨重生點也予以尊重。</p>
 *
 * <p>v1.0.4 僅在 {@link PlayerRespawnEvent} 以 {@code setRespawnLocation(home)} 改變落點並
 * 於伺服器端 {@code getChunkAt()} 預載區塊，仍無法讓客戶端脫離「載入地形」：重生流程不會像
 * {@link IslandTeleportHelper#teleportPlayer} 的 {@code teleportAsync} 那樣重新同步區塊封包。
 * 因此不在重生事件中直接改落點，改為維持原版世界出生點（區塊已載入），並在
 * {@link PlayerPostRespawnEvent} 下一 tick 以與 /is home 相同的顯式傳送流程送玩家回家。</p>
 */
public class PlayerRespawnListener implements Listener {

    private final StrawSkyBlockPlugin plugin;
    private final Map<UUID, Location> pendingIslandHomeTeleport = new ConcurrentHashMap<>();

    public PlayerRespawnListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());
        if (island == null) {
            return;
        }

        Location home = island.getHomeLocation();
        boolean explicitSpawnPoint = event.isBedSpawn() || event.isAnchorSpawn();
        boolean respawnInIslandWorld = isInIslandWorld(event.getRespawnLocation());

        if (!shouldDeferIslandHomeTeleportAfterRespawn(true, home != null, respawnInIslandWorld, explicitSpawnPoint)) {
            return;
        }

        pendingIslandHomeTeleport.put(player.getUniqueId(), home.clone());
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("空島重生：玩家=" + player.getName()
                    + " 將於重生完成後傳送至家點 "
                    + formatLoc(home));
        }
    }

    /**
     * Paper 在 {@link PlayerRespawnEvent} 之後才將玩家放到最終落點；於重生完成後下一 tick
     * 以 {@link IslandTeleportHelper#teleportPlayer} 顯式傳送，確保客戶端收到區塊資料。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        Location home = pendingIslandHomeTeleport.remove(player.getUniqueId());
        if (home == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location destination = home.getWorld() != null ? home : resolveHome(player);
            if (destination == null || destination.getWorld() == null) {
                plugin.getLogger().warning("空島重生傳送失敗：家點不可用（玩家=" + player.getName() + "）");
                return;
            }
            IslandTeleportHelper.teleportPlayer(plugin, player, destination, null);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingIslandHomeTeleport.remove(event.getPlayer().getUniqueId());
    }

    private Location resolveHome(Player player) {
        Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());
        return island == null ? null : island.getHomeLocation();
    }

    private boolean isInIslandWorld(Location location) {
        if (location == null) {
            return false;
        }
        World world = location.getWorld();
        return world != null && world.getName().equals(plugin.getConfigManager().getIslandWorld());
    }

    private static String formatLoc(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }
        return location.getWorld().getName() + "@"
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    /**
     * 純粹的決策邏輯，方便在不依賴 Bukkit 執行環境下進行單元測試。
     *
     * @param playerHasIsland      玩家是否屬於某座空島
     * @param islandHomeAvailable  該島的家點是否可用（世界已載入且不為 null）
     * @param respawnInIslandWorld 此次預設重生落點是否位於空島世界
     * @param explicitSpawnPoint   是否為玩家自設的床／重生錨重生點
     * @return 是否應於重生完成後傳送至該島家點
     */
    public static boolean shouldDeferIslandHomeTeleportAfterRespawn(boolean playerHasIsland,
                                                                    boolean islandHomeAvailable,
                                                                    boolean respawnInIslandWorld,
                                                                    boolean explicitSpawnPoint) {
        return playerHasIsland && islandHomeAvailable && respawnInIslandWorld && !explicitSpawnPoint;
    }

    /**
     * @deprecated 使用 {@link #shouldDeferIslandHomeTeleportAfterRespawn}；保留以相容舊測試語意。
     */
    @Deprecated
    public static boolean shouldRedirectToIslandHome(boolean playerHasIsland,
                                                     boolean islandHomeAvailable,
                                                     boolean respawnInIslandWorld,
                                                     boolean explicitSpawnPoint) {
        return shouldDeferIslandHomeTeleportAfterRespawn(
                playerHasIsland, islandHomeAvailable, respawnInIslandWorld, explicitSpawnPoint);
    }
}
