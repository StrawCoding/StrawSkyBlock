package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

/**
 * 偵測水 / 岩漿流動產生鵝卵石，記錄為刷石機鵝卵石。
 */
public class BlockFromToListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public BlockFromToListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!plugin.getConfigManager().isGeneratorEnabled()) {
            return;
        }
        Block from = event.getBlock();
        if (!from.getWorld().getName().equals(plugin.getConfigManager().getIslandWorld())) {
            return;
        }
        Block to = event.getToBlock();

        // 下一刻才會變成鵝卵石；延遲一 tick 檢查目標方塊。
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (to.getType() == Material.COBBLESTONE
                    && plugin.getIslandService().getByLocation(to.getLocation()) != null) {
                plugin.getCobbleGeneratorService().markGeneratedCobblestone(to.getLocation());
            }
        });
    }
}
