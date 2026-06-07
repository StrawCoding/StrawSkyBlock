package com.strawserver.strawskyblock.robot;

/**
 * 小機器人挖掘掃描範圍（純邏輯，不依賴 Bukkit）。
 *
 * <p>以機器人原點為中心，水平半徑為 {@code range}、垂直半徑為 {@code verticalRange}，
 * 計算出一個方形掃描盒，並將水平邊界裁切到島嶼方形範圍內，確保機器人永遠不會掃描到
 * 島嶼邊界之外的方塊。垂直方向不做裁切，由呼叫端依世界高度限制處理。</p>
 *
 * <p>所有座標皆為方塊座標。</p>
 */
public record RobotScanArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /**
     * 計算裁切到島嶼範圍內的掃描盒。
     *
     * @param originX        機器人原點 X
     * @param originY        機器人原點 Y
     * @param originZ        機器人原點 Z
     * @param range          水平半徑（&ge; 0）
     * @param verticalRange  垂直半徑（&ge; 0）
     * @param islandCenterX  島嶼中心 X
     * @param islandCenterZ  島嶼中心 Z
     * @param islandHalf     島嶼半邊長（= size / 2）
     * @return 已裁切的掃描盒
     */
    public static RobotScanArea around(int originX, int originY, int originZ,
                                       int range, int verticalRange,
                                       int islandCenterX, int islandCenterZ, int islandHalf) {
        int r = Math.max(0, range);
        int vr = Math.max(0, verticalRange);

        int islandMinX = islandCenterX - islandHalf;
        int islandMaxX = islandCenterX + islandHalf;
        int islandMinZ = islandCenterZ - islandHalf;
        int islandMaxZ = islandCenterZ + islandHalf;

        int minX = Math.max(originX - r, islandMinX);
        int maxX = Math.min(originX + r, islandMaxX);
        int minZ = Math.max(originZ - r, islandMinZ);
        int maxZ = Math.min(originZ + r, islandMaxZ);

        int minY = originY - vr;
        int maxY = originY + vr;

        return new RobotScanArea(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * @return 掃描盒涵蓋的方塊數量（含邊界），若為空盒則為 0。
     */
    public long blockCount() {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return 0L;
        }
        long dx = (long) (maxX - minX) + 1;
        long dy = (long) (maxY - minY) + 1;
        long dz = (long) (maxZ - minZ) + 1;
        return dx * dy * dz;
    }

    /**
     * @return 掃描盒是否完全沒有可掃描的方塊（水平邊界被島嶼裁切到無交集時）。
     */
    public boolean isEmpty() {
        return blockCount() == 0L;
    }
}
