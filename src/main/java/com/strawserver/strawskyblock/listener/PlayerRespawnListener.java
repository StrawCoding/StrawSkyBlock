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

import java.util.Set;
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
 * 位於空島世界、並且不是玩家自行設定的床／重生錨時，將重生落點改為玩家自己的島嶼家點。
 * 主世界（或其他世界）的死亡重生完全不受影響，玩家自設的床／錨重生點也予以尊重。</p>
 *
 * <p>僅改變重生座標不足以讓客戶端正確載入虛空空島世界中的遠端家點區塊；
 * 必須與 {@link IslandTeleportHelper} 的顯式傳送流程相同，於重生前預載目標區塊，
 * 並在 Paper 完成重生後於下一 tick 再次確保區塊已載入。</p>
 */
public class PlayerRespawnListener implements Listener {

    private final StrawSkyBlockPlugin plugin;
    private final Set<UUID> pendingIslandHomeChunkEnsure = ConcurrentHashMap.newKeySet();

    public PlayerRespawnListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());
        if (island == null) {
            // 沒有島：維持原版／預設行為。
            return;
        }

        Location home = island.getHomeLocation();
        boolean explicitSpawnPoint = event.isBedSpawn() || event.isAnchorSpawn();
        boolean respawnInIslandWorld = isInIslandWorld(event.getRespawnLocation());

        if (!shouldRedirectToIslandHome(true, home != null, respawnInIslandWorld, explicitSpawnPoint)) {
            // 家點不可用（世界未載入等）、落點不在空島世界、或玩家有床／錨：維持原版行為，不丟例外。
            return;
        }

        IslandTeleportHelper.prepareForTeleport(player, home, IslandTeleportHelper.DEFAULT_CHUNK_RADIUS);
        event.setRespawnLocation(home);
        pendingIslandHomeChunkEnsure.add(player.getUniqueId());
    }

    /**
     * Paper 在 {@link PlayerRespawnEvent} 之後才將玩家放到最終落點；於重生完成後再確保一次區塊，
     * 避免客戶端卡在「載入地形」。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        if (!pendingIslandHomeChunkEnsure.remove(player.getUniqueId())) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            IslandTeleportHelper.ensureChunksLoaded(
                    player.getWorld(),
                    player.getLocation(),
                    IslandTeleportHelper.DEFAULT_CHUNK_RADIUS);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingIslandHomeChunkEnsure.remove(event.getPlayer().getUniqueId());
    }

    private boolean isInIslandWorld(Location location) {
        if (location == null) {
            return false;
        }
        World world = location.getWorld();
        return world != null && world.getName().equals(plugin.getConfigManager().getIslandWorld());
    }

    /**
     * 純粹的決策邏輯，方便在不依賴 Bukkit 執行環境下進行單元測試。
     *
     * @param playerHasIsland     玩家是否屬於某座空島
     * @param islandHomeAvailable 該島的家點是否可用（世界已載入且不為 null）
     * @param respawnInIslandWorld 此次預設重生落點是否位於空島世界
     * @param explicitSpawnPoint  是否為玩家自設的床／重生錨重生點
     * @return 是否應將重生落點改為該島家點
     */
    public static boolean shouldRedirectToIslandHome(boolean playerHasIsland,
                                                     boolean islandHomeAvailable,
                                                     boolean respawnInIslandWorld,
                                                     boolean explicitSpawnPoint) {
        return playerHasIsland && islandHomeAvailable && respawnInIslandWorld && !explicitSpawnPoint;
    }

    /**
     * 是否應在重生流程中預載家點區塊；與 {@link #shouldRedirectToIslandHome} 條件一致。
     */
    public static boolean shouldPrepareChunksForIslandHomeRespawn(boolean playerHasIsland,
                                                                  boolean islandHomeAvailable,
                                                                  boolean respawnInIslandWorld,
                                                                  boolean explicitSpawnPoint) {
        return shouldRedirectToIslandHome(
                playerHasIsland, islandHomeAvailable, respawnInIslandWorld, explicitSpawnPoint);
    }
}
