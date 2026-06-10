package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

/**
 * 偵測水 / 岩漿流動產生鵝卵石，並依設定將其轉換為礦石方塊（v1.0.32）。
 *
 * <p>治本：鵝卵石由 {@link BlockFormEvent} 觸發（非 {@code BlockFromToEvent} 延遲檢查）。
 * 於形成當下取消原版結果並直接放置礦石方塊，避免延遲任務與流體互動的競態。</p>
 */
public class BlockFromToListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public BlockFromToListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (event.getNewState().getType() != Material.COBBLESTONE) {
            return;
        }
        Block block = event.getBlock();
        boolean dbg = plugin.getConfig().getBoolean("generator.debug-form", false);
        if (!block.getWorld().getName().equals(plugin.getConfigManager().getIslandWorld())) {
            if (dbg) plugin.getLogger().info("[OreGen] skip world=" + block.getWorld().getName());
            return;
        }
        if (!plugin.getConfigManager().isGeneratorEnabled()) {
            if (dbg) plugin.getLogger().info("[OreGen] skip generator disabled");
            return;
        }
        boolean onIsland = plugin.getIslandService().getByLocation(block.getLocation()) != null;
        if (!onIsland) {
            if (dbg) plugin.getLogger().info("[OreGen] skip not-on-island @"
                    + block.getX() + "," + block.getY() + "," + block.getZ());
            return;
        }

        Material finalType = Material.COBBLESTONE;
        if (plugin.getConfigManager().isGeneratorOreBlockMode()) {
            Material ore = plugin.getCobbleGeneratorService().rollOreBlock();
            if (ore != null && ore.isBlock()) {
                finalType = ore;
            }
            if (dbg) plugin.getLogger().info("[OreGen] form @"
                    + block.getX() + "," + block.getY() + "," + block.getZ()
                    + " rolled=" + ore + " -> " + finalType
                    + " empty=" + plugin.getCobbleGeneratorService().getOreBlockTable().isEmpty());
        }
        // 取消原版鵝卵石形成，改由插件直接放置最終方塊（避免 getNewState().setType 在部分 Paper 版本不生效）。
        event.setCancelled(true);
        block.setType(finalType, true);
        plugin.getCobbleGeneratorService().markGeneratedCobblestone(block.getLocation());
    }
}
