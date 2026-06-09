package com.strawserver.strawskyblock.robot;

/**
 * 機器人商城購買資格的純函式判定，與 Bukkit / 經濟 API 解耦以便單元測試。
 */
public final class RobotPurchase {

    private RobotPurchase() {
    }

    public enum Result {
        /** 可購買。 */
        OK,
        /** 已達擁有數量上限。 */
        LIMIT_REACHED,
        /** 金錢不足。 */
        NOT_ENOUGH_MONEY
    }

    /**
     * 判定玩家是否能購買機器人（購買後放入背包，部署時才計入上限）。
     *
     * @param ownedCount     玩家目前已部署的機器人數量
     * @param limit          玩家可部署上限（&le;0 表示不限）
     * @param chargeRequired 是否需要扣款（費用 &gt; 0 且經濟系統可用）
     * @param hasFunds       玩家是否有足夠金錢
     * @return 判定結果
     */
    public static Result evaluate(int ownedCount, int limit,
                                  boolean chargeRequired, boolean hasFunds) {
        if (limit > 0 && ownedCount >= limit) {
            return Result.LIMIT_REACHED;
        }
        if (chargeRequired && !hasFunds) {
            return Result.NOT_ENOUGH_MONEY;
        }
        return Result.OK;
    }
}
