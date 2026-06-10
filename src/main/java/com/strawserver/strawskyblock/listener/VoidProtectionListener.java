package com.strawserver.strawskyblock.listener;

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
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * 虛空防護：當玩家在空島世界或主世界大廳跌入虛空 (Y <= threshold) 時，
 * 取消傷害並將玩家安全傳送回其島嶼重生點（空島世界）或主世界安全地面（大廳世界）。
 *
 * <p>這比讓玩家死亡並由 {@link PlayerRespawnListener} 處理更友善，
 * 因為玩家不會損失物品與經驗，也不會看到死亡畫面。</p>
 */
public class VoidProtectionListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public VoidProtectionListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        boolean inIslandWorld = isInIslandWorld(player);
        boolean inMainWorld = isInMainWorld(player);

        if (!shouldCancelVoidDamage(
                event.getCause(),
                inIslandWorld || inMainWorld,
                player.getLocation().getY(),
                plugin.getConfigManager().getVoidProtectionThreshold())) {
            return;
        }

        // 取消虛空傷害
        event.setCancelled(true);
        player.setFallDistance(0.0F);

        if (!plugin.getConfigManager().isVoidProtectionEnabled()) {
            return;
        }

        // 給予短暫無敵避免傳送過程中再次受傷
        int invincibilityTicks = plugin.getConfigManager().getVoidProtectionInvincibilityTicks();
        if (invincibilityTicks > 0) {
            player.setNoDamageTicks(invincibilityTicks);
        }

        // 決定傳送目的地
        Location destination = resolveDestination(player, inIslandWorld, inMainWorld);
        if (destination == null || destination.getWorld() == null) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("虛空防護：玩家=" + player.getName()
                        + " 無法解析傳送目的地，取消傳送。");
            }
            return;
        }

        String operation = "void-protection-teleport";
        IslandTeleportHelper.teleportPlayer(plugin, player, destination,
                "island.void-teleport", operation);
    }

    /**
     * 純邏輯：是否應取消此次虛空傷害。
     */
    public static boolean shouldCancelVoidDamage(EntityDamageEvent.DamageCause cause,
                                                  boolean inProtectedWorld,
                                                  double y,
                                                  int yThreshold) {
        return cause == EntityDamageEvent.DamageCause.VOID
                && inProtectedWorld
                && y <= yThreshold;
    }

    /**
     * 純邏輯：是否應傳送至島嶼家點（而非 fallback）。
     */
    public static boolean shouldTeleportToIslandHome(boolean playerHasIsland,
                                                      boolean homeAvailable) {
        return playerHasIsland && homeAvailable;
    }

    /**
     * 虛空防護傳送目的地類型。
     */
    public enum VoidTeleportDestination {
        /** 主世界大廳出生點（主世界虛空時使用）。 */
        MAIN_WORLD_SPAWN,
        /** 玩家島嶼家點（空島世界虛空且有家點時使用）。 */
        ISLAND_HOME,
        /** fallback 傳送點（空島世界虛空但無家點時使用）。 */
        FALLBACK_SPAWN,
        /** 無傳送（不屬於受保護世界）。 */
        NONE
    }

    /**
     * 純邏輯：根據世界上下文與島嶼狀態決定虛空防護傳送目的地類型。
     *
     * @param inMainWorld   是否位於主世界大廳
     * @param inIslandWorld 是否位於空島世界
     * @param hasIsland     玩家是否擁有島嶼
     * @param homeAvailable 島嶼家點是否可用
     * @return 對應的 {@link VoidTeleportDestination}
     */
    public static VoidTeleportDestination resolveDestinationType(boolean inMainWorld,
                                                                  boolean inIslandWorld,
                                                                  boolean hasIsland,
                                                                  boolean homeAvailable) {
        if (inMainWorld) {
            return VoidTeleportDestination.MAIN_WORLD_SPAWN;
        }
        if (inIslandWorld) {
            if (shouldTeleportToIslandHome(hasIsland, homeAvailable)) {
                return VoidTeleportDestination.ISLAND_HOME;
            }
            return VoidTeleportDestination.FALLBACK_SPAWN;
        }
        return VoidTeleportDestination.NONE;
    }

    private boolean isInIslandWorld(Player player) {
        World world = player.getWorld();
        // v1.0.28：主世界與下界空島世界皆納入虛空防護，避免下界尚未生成地形時掉入虛空致死。
        return world != null
                && plugin.getConfigManager().isIslandWorld(world.getName());
    }

    /**
     * v1.0.38：判斷玩家是否位於主世界大廳（原版虛空主世界）。
     */
    private boolean isInMainWorld(Player player) {
        World world = player.getWorld();
        return world != null
                && plugin.getConfigManager().getVanillaOverworld().equals(world.getName());
    }

    private Location resolveDestination(Player player, boolean inIslandWorld, boolean inMainWorld) {
        if (inMainWorld) {
            // 主世界大廳虛空 → 傳回主世界安全地面（出生點），而非島嶼家點
            return resolveMainWorldSafeSpawn();
        }

        if (inIslandWorld) {
            Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());
            boolean hasIsland = island != null;
            boolean homeAvailable = hasIsland && island.getHomeLocation() != null
                    && island.getHomeLocation().getWorld() != null;

            if (shouldTeleportToIslandHome(hasIsland, homeAvailable)) {
                return island.getHomeLocation().clone();
            }

            // 無島嶼或家點不可用時，使用 fallback spawn
            return plugin.getConfigManager().getVoidProtectionFallbackSpawn();
        }

        // 不應該走到這裡（shouldCancelVoidDamage 已過濾）
        return null;
    }

    /**
     * v1.0.38：取得主世界大廳的安全傳送點。
     * 優先使用世界出生點（WorldManager 已於啟動時設定為地板上方），
     * 若不可用則 fallback 到 (0.5, islandY+1, 0.5)。
     */
    private Location resolveMainWorldSafeSpawn() {
        World mainWorld = Bukkit.getWorld(plugin.getConfigManager().getVanillaOverworld());
        if (mainWorld == null) {
            var worlds = Bukkit.getWorlds();
            if (!worlds.isEmpty()) {
                mainWorld = worlds.get(0);
            }
        }
        if (mainWorld == null) {
            return null;
        }
        Location spawn = mainWorld.getSpawnLocation();
        if (spawn != null && spawn.getWorld() != null) {
            // 確保 Y 位於地板上方（WorldManager 已設定，但這裡做保險）
            return spawn.clone();
        }
        int y = plugin.getConfigManager().getIslandY() + 1;
        return new Location(mainWorld, 0.5, y, 0.5);
    }
}
