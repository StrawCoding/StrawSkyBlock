package com.strawserver.strawskyblock.island;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 以網格方式分配島嶼座標。index 由資料庫最大值初始化，之後以原子遞增避免並發重複。
 */
public class IslandLocationAllocator {

    private static final int GRID_SIZE = 10_000;

    private final StrawSkyBlockPlugin plugin;
    private final AtomicInteger counter = new AtomicInteger(-1);

    public IslandLocationAllocator(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 以資料庫目前最大 index 初始化（-1 代表尚無島嶼）。
     */
    public void init(int maxIndex) {
        counter.set(maxIndex);
    }

    public int allocateIndex() {
        return counter.incrementAndGet();
    }

    /**
     * 依 index 計算島嶼中心座標 [centerX, centerZ]。
     */
    public int[] computeCenter(int index) {
        int spacing = plugin.getConfigManager().getIslandSpacing();
        int gridX = index % GRID_SIZE;
        int gridZ = index / GRID_SIZE;
        return new int[]{gridX * spacing, gridZ * spacing};
    }
}
