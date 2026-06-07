package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.island.IslandFlag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

/**
 * 世界層級保護：爆炸、火焰蔓延、生物破壞、訪客撿物。
 */
public class WorldProtectionListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public WorldProtectionListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inIslandWorld(Block block) {
        return block.getWorld().getName().equals(plugin.getConfigManager().getIslandWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::explosionBlocked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::explosionBlocked);
    }

    private boolean explosionBlocked(Block block) {
        if (!inIslandWorld(block)) {
            return false;
        }
        Island island = plugin.getIslandService().getByLocation(block.getLocation());
        if (island == null) {
            return true;
        }
        return !island.getFlag(IslandFlag.EXPLOSION);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (fireBlocked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType().name().contains("FIRE") && fireBlocked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean fireBlocked(Block block) {
        if (!inIslandWorld(block)) {
            return false;
        }
        Island island = plugin.getIslandService().getByLocation(block.getLocation());
        if (island == null) {
            return true;
        }
        return !island.getFlag(IslandFlag.FIRE_SPREAD);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        Block block = event.getBlock();
        if (!inIslandWorld(block)) {
            return;
        }
        Island island = plugin.getIslandService().getByLocation(block.getLocation());
        if (island == null || !island.getFlag(IslandFlag.MOB_GRIEFING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getProtectionService().canPickup(player, event.getItem().getLocation())) {
            event.setCancelled(true);
        }
    }
}
