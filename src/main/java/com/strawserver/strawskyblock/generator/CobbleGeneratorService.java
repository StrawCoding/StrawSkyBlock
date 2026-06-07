package com.strawserver.strawskyblock.generator;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.util.BlockPosition;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 紀錄由水 / 岩漿生成的鵝卵石位置，並提供掉落抽取。
 * 暫存資料伺服器重啟後不需保存；定期清理避免無限成長。
 */
public class CobbleGeneratorService {

    private static final long EXPIRE_MILLIS = 10 * 60 * 1000L; // 10 分鐘
    private static final long CLEAN_INTERVAL_TICKS = 20L * 60 * 5; // 5 分鐘

    private final StrawSkyBlockPlugin plugin;
    private final OreDropTable dropTable;
    private final ConcurrentHashMap<BlockPosition, Long> generated = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public CobbleGeneratorService(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
        this.dropTable = new OreDropTable(plugin);
    }

    public OreDropTable getDropTable() {
        return dropTable;
    }

    public void reload() {
        dropTable.reload();
    }

    public void start() {
        stop();
        cleanupTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            generated.entrySet().removeIf(e -> now - e.getValue() > EXPIRE_MILLIS);
        }, CLEAN_INTERVAL_TICKS, CLEAN_INTERVAL_TICKS);
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        generated.clear();
    }

    public void markGeneratedCobblestone(Location location) {
        generated.put(BlockPosition.of(location), System.currentTimeMillis());
    }

    public boolean isGeneratedCobblestone(Location location) {
        return generated.containsKey(BlockPosition.of(location));
    }

    public void removeGeneratedCobblestone(Location location) {
        generated.remove(BlockPosition.of(location));
    }

    public int trackedCount() {
        return generated.size();
    }

    public ItemStack rollDrop() {
        return dropTable.roll();
    }
}
