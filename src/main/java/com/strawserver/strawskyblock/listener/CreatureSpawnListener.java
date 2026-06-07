package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * 限制空島世界的自然生物生成，僅允許插件 / 玩家主動行為。
 */
public class CreatureSpawnListener implements Listener {

    private static final Set<CreatureSpawnEvent.SpawnReason> ALLOWED = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,
            CreatureSpawnEvent.SpawnReason.BREEDING,
            CreatureSpawnEvent.SpawnReason.EGG,
            CreatureSpawnEvent.SpawnReason.DISPENSE_EGG,
            CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN,
            CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM,
            CreatureSpawnEvent.SpawnReason.BUILD_WITHER
    );

    private final StrawSkyBlockPlugin plugin;

    public CreatureSpawnListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!event.getLocation().getWorld().getName().equals(plugin.getConfigManager().getIslandWorld())) {
            return;
        }
        if (!ALLOWED.contains(event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }
}
