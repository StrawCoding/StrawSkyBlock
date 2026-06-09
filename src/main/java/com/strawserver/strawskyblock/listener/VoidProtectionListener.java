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
 * 虛空防護：當玩家在空島世界跌入虛空 (Y <= threshold) 時，
 * 取消傷害並將玩家安全傳送回其島嶼重生點。
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

        if (!shouldCancelVoidDamage(
                event.getCause(),
                isInIslandWorld(player),
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

        // 決定傳送目的地：有家則回家，無家則 fallback spawn
        Location destination = resolveDestination(player);
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
                                                  boolean inIslandWorld,
                                                  double y,
                                                  int yThreshold) {
        return cause == EntityDamageEvent.DamageCause.VOID
                && inIslandWorld
                && y <= yThreshold;
    }

    /**
     * 純邏輯：是否應傳送至島嶼家點（而非 fallback）。
     */
    public static boolean shouldTeleportToIslandHome(boolean playerHasIsland,
                                                      boolean homeAvailable) {
        return playerHasIsland && homeAvailable;
    }

    private boolean isInIslandWorld(Player player) {
        World world = player.getWorld();
        // v1.0.28：主世界與下界空島世界皆納入虛空防護，避免下界尚未生成地形時掉入虛空致死。
        return world != null
                && plugin.getConfigManager().isIslandWorld(world.getName());
    }

    private Location resolveDestination(Player player) {
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
}
