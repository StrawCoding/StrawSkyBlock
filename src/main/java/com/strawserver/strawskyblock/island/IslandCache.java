package com.strawserver.strawskyblock.island;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Location;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 島嶼記憶體快取。保護事件非常頻繁，必須以快取查詢而非每次查資料庫。
 * 透過網格座標換算可在 O(1) 內定位某座標屬於哪座島。
 */
public class IslandCache {

    private final StrawSkyBlockPlugin plugin;

    private final ConcurrentHashMap<UUID, Island> byUuid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Island> byOwner = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Island> byCenter = new ConcurrentHashMap<>();

    public IslandCache(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    private static long centerKey(int centerX, int centerZ) {
        return (((long) centerX) << 32) ^ (centerZ & 0xffffffffL);
    }

    public void clear() {
        byUuid.clear();
        byOwner.clear();
        byCenter.clear();
    }

    public void add(Island island) {
        byUuid.put(island.getIslandUuid(), island);
        byOwner.put(island.getOwnerUuid(), island);
        byCenter.put(centerKey(island.getCenterX(), island.getCenterZ()), island);
    }

    public void remove(Island island) {
        byUuid.remove(island.getIslandUuid());
        byOwner.remove(island.getOwnerUuid(), island);
        byCenter.remove(centerKey(island.getCenterX(), island.getCenterZ()), island);
    }

    /**
     * 轉移島主時更新 byOwner 索引。
     */
    public void reindexOwner(UUID oldOwner, Island island) {
        if (oldOwner != null) {
            byOwner.remove(oldOwner, island);
        }
        byOwner.put(island.getOwnerUuid(), island);
    }

    public Island getByUuid(UUID islandUuid) {
        return byUuid.get(islandUuid);
    }

    public Island getByOwner(UUID ownerUuid) {
        return byOwner.get(ownerUuid);
    }

    /**
     * 玩家所屬的島（島主或成員）。MVP 每人最多一座，故回傳第一個符合者。
     */
    public Island getByMember(UUID playerUuid) {
        Island owned = byOwner.get(playerUuid);
        if (owned != null) {
            return owned;
        }
        for (Island island : byUuid.values()) {
            if (island.getMembers().containsKey(playerUuid)) {
                return island;
            }
        }
        return null;
    }

    /**
     * 依座標定位島嶼，使用網格換算，僅在空島世界有效。
     */
    public Island getByLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        if (!location.getWorld().getName().equals(plugin.getConfigManager().getIslandWorld())) {
            return null;
        }
        int spacing = plugin.getConfigManager().getIslandSpacing();
        if (spacing <= 0) {
            return null;
        }
        int candidateX = Math.round((float) location.getBlockX() / spacing) * spacing;
        int candidateZ = Math.round((float) location.getBlockZ() / spacing) * spacing;
        Island candidate = byCenter.get(centerKey(candidateX, candidateZ));
        if (candidate != null && candidate.contains(location)) {
            return candidate;
        }
        return null;
    }

    public Collection<Island> all() {
        return byUuid.values();
    }

    public int size() {
        return byUuid.size();
    }
}
