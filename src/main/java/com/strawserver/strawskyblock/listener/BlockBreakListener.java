package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.island.Island;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 處理破壞保護、刷石機礦物掉落與挖石生成動物。
 */
public class BlockBreakListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public BlockBreakListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        // 1. 保護判定
        if (!plugin.getProtectionService().canBreak(player, loc)) {
            event.setCancelled(true);
            plugin.getMessageManager().send(player, "protection.cannot-break");
            return;
        }

        // 2. 僅在空島世界處理刷石機邏輯
        if (!loc.getWorld().getName().equals(plugin.getConfigManager().getIslandWorld())) {
            return;
        }
        if (block.getType() != Material.COBBLESTONE) {
            return;
        }
        if (!plugin.getConfigManager().isGeneratorEnabled()) {
            return;
        }

        // 3. 僅處理刷石機生成的鵝卵石
        if (plugin.getConfigManager().isOnlyGeneratedCobblestone()
                && !plugin.getCobbleGeneratorService().isGeneratedCobblestone(loc)) {
            return;
        }

        Island island = plugin.getIslandService().getByLocation(loc);
        if (island == null) {
            return;
        }

        // 4. 取消原始掉落，依機率抽取
        event.setDropItems(false);
        ItemStack drop = plugin.getCobbleGeneratorService().rollDrop();
        if (drop != null && drop.getType() != Material.AIR) {
            loc.getWorld().dropItemNaturally(loc.toCenterLocation(), drop);
            if (drop.getType() != Material.COBBLESTONE) {
                plugin.getMessageManager().send(player, "generator.ore-drop",
                        MessageManager.placeholders("item", drop.getType().name()));
            }
        }
        plugin.getCobbleGeneratorService().removeGeneratedCobblestone(loc);

        boolean ore = drop != null && drop.getType() != Material.COBBLESTONE;

        // 5. 嘗試生成動物（主執行緒）
        boolean spawned = plugin.getAnimalSpawnService().trySpawnAnimal(player, island, loc);

        // 6. 更新統計（非阻塞）
        plugin.getIslandService().addStats(island, 1, 1, ore ? 1 : 0, spawned ? 1 : 0);
    }
}
