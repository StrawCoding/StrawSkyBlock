package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * 放置方塊保護。
 */
public class BlockPlaceListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public BlockPlaceListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getProtectionService().canPlace(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.getMessageManager().send(event.getPlayer(), "protection.cannot-place");
        }
    }
}
