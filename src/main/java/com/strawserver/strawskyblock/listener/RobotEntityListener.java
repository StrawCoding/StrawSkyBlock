package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * 小機器人盔甲架小人的維護與保護：
 * <ul>
 *   <li>區塊載入時補上盔甲架並清除孤兒。</li>
 *   <li>禁止玩家操作（取放裝備）或破壞機器人盔甲架。</li>
 * </ul>
 */
public class RobotEntityListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public RobotEntityListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getRobotService().handleChunkLoad(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (plugin.getRobotService().isRobotStand(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.getRobotService().isRobotStand(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
