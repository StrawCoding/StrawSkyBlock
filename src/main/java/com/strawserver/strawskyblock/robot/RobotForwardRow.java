package com.strawserver.strawskyblock.robot;

import java.util.ArrayList;
import java.util.List;

/**
 * 小機器人「前方整列」挖掘範圍（純邏輯，不依賴 Bukkit）。v1.0.33
 *
 * <p>機器人不再以原點為中心掃描方形範圍，而是依其面向（yaw）取得前方的四方位之一，
 * 自原點往前延伸 {@code length} 格，形成一整列方塊；{@code length}（前方格數）即由等級決定，
 * 升級會加長這一列。每次挖掘會一次處理整列（由呼叫端逐格挖取）。</p>
 *
 * <p>水平方向裁切到島嶼方形範圍內；垂直方向以 {@code verticalRange} 作為容差（每格前方欄位
 * 額外涵蓋上下各 verticalRange 格），預設可為 0 形成單純水平列。</p>
 */
public final class RobotForwardRow {

    /** 單一目標方塊座標。 */
    public record Cell(int x, int y, int z) {
    }

    private RobotForwardRow() {
    }

    /**
     * 由 yaw 取得前方水平單位步進，裁切到東/西/南/北其一。回傳 {@code {dx, dz}}。
     *
     * <p>Minecraft 朝向：yaw 0 面向 +Z（南）、90 面向 -X（西）、180 面向 -Z（北）、270 面向 +X（東）。
     * 朝向向量水平分量為 {@code x = -sin(yaw), z = cos(yaw)}，取絕對值較大者作為主軸並取其正負號。</p>
     */
    public static int[] forwardStep(float yaw) {
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[]{dx >= 0 ? 1 : -1, 0};
        }
        return new int[]{0, dz >= 0 ? 1 : -1};
    }

    /**
     * 產生前方整列的方塊座標清單（已裁切到島嶼水平範圍），由近至遠排序。
     *
     * @param originX       機器人原點 X（盔甲架所在）
     * @param originY       機器人原點 Y
     * @param originZ       機器人原點 Z
     * @param yaw           機器人面向
     * @param length        前方格數（= 等級對應的 range）
     * @param verticalRange 垂直容差（每格前方欄位額外涵蓋上下各幾格，&ge; 0）
     * @param islandCenterX 島嶼中心 X
     * @param islandCenterZ 島嶼中心 Z
     * @param islandHalf    島嶼半邊長（= size / 2）
     */
    public static List<Cell> cells(int originX, int originY, int originZ, float yaw,
                                   int length, int verticalRange,
                                   int islandCenterX, int islandCenterZ, int islandHalf) {
        List<Cell> out = new ArrayList<>();
        int len = Math.max(0, length);
        int vr = Math.max(0, verticalRange);
        int[] step = forwardStep(yaw);
        int sx = step[0];
        int sz = step[1];

        int minX = islandCenterX - islandHalf;
        int maxX = islandCenterX + islandHalf;
        int minZ = islandCenterZ - islandHalf;
        int maxZ = islandCenterZ + islandHalf;

        for (int i = 1; i <= len; i++) {
            int x = originX + sx * i;
            int z = originZ + sz * i;
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                break; // 超出島嶼邊界，更遠的格子也會超出，停止延伸。
            }
            for (int dy = -vr; dy <= vr; dy++) {
                out.add(new Cell(x, originY + dy, z));
            }
        }
        return out;
    }
}
