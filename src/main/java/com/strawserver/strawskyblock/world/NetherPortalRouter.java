package com.strawserver.strawskyblock.world;

/**
 * 下界傳送門導向的純邏輯（v1.0.28）：判定一次傳送門事件應將玩家導向哪個世界，
 * 並提供 1:1 座標映射所需的 Y 夾擠（不依賴 Bukkit，方便單元測試）。
 *
 * <p>設計：空島主世界 ↔ 專屬下界世界採「相同座標」對應，不套用原版下界 8 倍縮放，
 * 因此每座島（以 island-spacing 分隔）在下界擁有彼此隔離的專屬區域。</p>
 */
public final class NetherPortalRouter {

    /** 導向方向。 */
    public enum Direction {
        /** 由主世界空島導向下界。 */
        TO_NETHER,
        /** 由下界導向主世界空島。 */
        TO_OVERWORLD,
        /** 不由本插件處理（沿用原版行為）。 */
        NONE
    }

    private NetherPortalRouter() {
    }

    /**
     * 判定此次傳送門事件的導向方向（純邏輯）。
     *
     * @param netherEnabled     是否啟用下界獨立空島模式
     * @param netherPortalCause 事件原因是否為下界傳送門（NETHER_PORTAL）
     * @param fromWorld         玩家當前所在世界名稱
     * @param overworldName     空島主世界名稱
     * @param netherName        下界空島世界名稱
     */
    public static Direction resolve(boolean netherEnabled,
                                    boolean netherPortalCause,
                                    String fromWorld,
                                    String overworldName,
                                    String netherName) {
        if (!netherEnabled || !netherPortalCause || fromWorld == null) {
            return Direction.NONE;
        }
        if (fromWorld.equals(overworldName)) {
            return Direction.TO_NETHER;
        }
        if (fromWorld.equals(netherName)) {
            return Direction.TO_OVERWORLD;
        }
        return Direction.NONE;
    }

    /**
     * 將目的地 Y 夾擠到世界可用高度的安全範圍內（保留上下緩衝，避免貼著界線）。
     */
    public static int clampY(int y, int minHeight, int maxHeight) {
        int lower = minHeight + 2;
        int upper = maxHeight - 2;
        if (upper < lower) {
            return lower;
        }
        return Math.max(lower, Math.min(y, upper));
    }
}
