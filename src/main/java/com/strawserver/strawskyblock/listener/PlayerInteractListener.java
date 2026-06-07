package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.protection.InteractionType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Locale;

/**
 * 容器、按鈕、門、拉桿、工作台等互動保護，含倒水 / 倒岩漿。
 */
public class PlayerInteractListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public PlayerInteractListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        InteractionType type = classify(block);
        if (type == null) {
            return;
        }
        if (!plugin.getProtectionService().canInteract(event.getPlayer(), block.getLocation(), type)) {
            event.setCancelled(true);
            plugin.getMessageManager().send(event.getPlayer(), "protection.cannot-interact");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!plugin.getProtectionService().canPlace(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.getMessageManager().send(event.getPlayer(), "protection.cannot-place");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!plugin.getProtectionService().canBreak(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.getMessageManager().send(event.getPlayer(), "protection.cannot-break");
        }
    }

    private InteractionType classify(Block block) {
        Material material = block.getType();
        String name = material.name().toUpperCase(Locale.ROOT);

        BlockState state = block.getState();
        if (state instanceof InventoryHolder) {
            return InteractionType.CONTAINER;
        }
        if (name.endsWith("_BUTTON") || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")
                || name.endsWith("_FENCE_GATE") || name.equals("LEVER")
                || name.endsWith("_PRESSURE_PLATE")) {
            return InteractionType.BUTTON;
        }
        if (name.equals("REPEATER") || name.equals("COMPARATOR") || name.equals("DAYLIGHT_DETECTOR")
                || name.equals("NOTE_BLOCK")) {
            return InteractionType.REDSTONE;
        }
        if (name.equals("CRAFTING_TABLE") || name.equals("ANVIL") || name.equals("CHIPPED_ANVIL")
                || name.equals("DAMAGED_ANVIL") || name.equals("ENCHANTING_TABLE")
                || name.equals("GRINDSTONE") || name.equals("LOOM") || name.equals("CARTOGRAPHY_TABLE")
                || name.equals("SMITHING_TABLE") || name.equals("STONECUTTER")
                || name.equals("BEACON") || name.equals("BELL")) {
            return InteractionType.CONTAINER;
        }
        return null;
    }
}
