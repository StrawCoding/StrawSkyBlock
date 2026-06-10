package com.strawserver.strawskyblock.world;

import java.util.ArrayList;
import java.util.List;

/**
 * 主世界（大廳）地板範圍計算（純邏輯，不依賴 Bukkit）。v1.0.36
 *
 * <p>大廳地板以區塊 (0,0) 為中心，往外延伸 {@code chunkRadius} 個區塊形成正方形完整區塊鋪面。
 * 預設 {@code chunkRadius = 1} 即 3x3 = 9 個區塊；每個區塊涵蓋方塊座標
 * {@code chunk*16 .. chunk*16+15}，故水平範圍為 {@code -16 .. 31}（48x48 = 2304 格）。</p>
 *
 * <p>本類別只負責產生「要鋪設地板的 (x, z) 方塊座標」清單，方便進行純邏輯單元測試；
 * 實際的方塊放置與出生點設定由 {@link WorldManager} 在主執行緒處理。</p>
 */
public final class LobbyFloorArea {

    /** 一個水平地板格子座標。 */
    public record Cell(int x, int z) {
    }

    /** 單一區塊邊長（方塊數）。 */
    public static final int CHUNK_SIZE = 16;

    private LobbyFloorArea() {
    }

    /**
     * 以中心區塊 (0,0) 計算地板水平方塊座標清單。
     *
     * @param chunkRadius 由中心區塊往外延伸的區塊數（&ge; 0）。1 代表 3x3 = 9 個區塊。
     */
    public static List<Cell> cells(int chunkRadius) {
        return cells(0, 0, chunkRadius);
    }

    /**
     * 計算地板水平方塊座標清單。
     *
     * @param centerChunkX 中心區塊 X
     * @param centerChunkZ 中心區塊 Z
     * @param chunkRadius  由中心區塊往外延伸的區塊數（&ge; 0）
     */
    public static List<Cell> cells(int centerChunkX, int centerChunkZ, int chunkRadius) {
        int radius = Math.max(0, chunkRadius);
        int minX = minBlock(centerChunkX, radius);
        int maxX = maxBlock(centerChunkX, radius);
        int minZ = minBlock(centerChunkZ, radius);
        int maxZ = maxBlock(centerChunkZ, radius);

        List<Cell> out = new ArrayList<>(blockCount(radius));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                out.add(new Cell(x, z));
            }
        }
        return out;
    }

    /** 地板在某軸上的最小方塊座標（含）。 */
    public static int minBlock(int centerChunk, int chunkRadius) {
        int radius = Math.max(0, chunkRadius);
        return (centerChunk - radius) * CHUNK_SIZE;
    }

    /** 地板在某軸上的最大方塊座標（含）。 */
    public static int maxBlock(int centerChunk, int chunkRadius) {
        int radius = Math.max(0, chunkRadius);
        return (centerChunk + radius) * CHUNK_SIZE + (CHUNK_SIZE - 1);
    }

    /** 地板覆蓋的區塊總數（邊長 2*radius+1 的正方形）。 */
    public static int chunkCount(int chunkRadius) {
        int side = 2 * Math.max(0, chunkRadius) + 1;
        return side * side;
    }

    /** 地板覆蓋的方塊總數。 */
    public static int blockCount(int chunkRadius) {
        int side = (2 * Math.max(0, chunkRadius) + 1) * CHUNK_SIZE;
        return side * side;
    }
}
