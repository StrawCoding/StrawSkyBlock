package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * 維護空島佔用狀態（「離開空島時馬上暫停空島」）。v1.0.39
 *
 * <p>於玩家加入 / 離線 / 傳送 / 換世界 / 跨越方塊（島嶼邊界）時即時更新其所屬島，
 * 使最後一人離開時島嶼立即暫停、任一人進入時立即恢復。所有事件僅更新在場狀態，不影響傳送本身。</p>
 */
public class IslandOccupancyListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public IslandOccupancyListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getIslandOccupancyService().updatePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // 離線涵蓋被踢出（PlayerQuitEvent 於 kick 後亦會觸發）。
        plugin.getIslandOccupancyService().removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        plugin.getIslandOccupancyService().updatePlayerLocation(event.getPlayer().getUniqueId(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.getIslandOccupancyService().updatePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // 島嶼範圍以 x/z 方塊座標判定；僅在跨越方塊時才重新計算，避免每次微小移動都運算。
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        plugin.getIslandOccupancyService().updatePlayerLocation(event.getPlayer().getUniqueId(), to);
    }
}
