package com.strawserver.strawskyblock.robot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RobotPurchaseTest {

    @Test
    void okWhenUnderLimit() {
        assertEquals(RobotPurchase.Result.OK,
                RobotPurchase.evaluate(0, 5, true, true));
    }

    @Test
    void okWhenFreeAndNoEconomy() {
        assertEquals(RobotPurchase.Result.OK,
                RobotPurchase.evaluate(0, 5, false, false));
    }

    @Test
    void limitReached() {
        assertEquals(RobotPurchase.Result.LIMIT_REACHED,
                RobotPurchase.evaluate(5, 5, true, true));
    }

    @Test
    void unlimitedWhenLimitZero() {
        assertEquals(RobotPurchase.Result.OK,
                RobotPurchase.evaluate(99, 0, true, true));
    }

    @Test
    void notEnoughMoney() {
        assertEquals(RobotPurchase.Result.NOT_ENOUGH_MONEY,
                RobotPurchase.evaluate(0, 5, true, false));
    }

    @Test
    void bypassIgnoresLimit() {
        // 管理員繞過：已達上限仍可購買。
        assertEquals(RobotPurchase.Result.OK,
                RobotPurchase.evaluate(99, 5, true, true, true));
    }

    @Test
    void bypassIgnoresMoney() {
        // 管理員繞過：金錢不足仍可購買（無視費用）。
        assertEquals(RobotPurchase.Result.OK,
                RobotPurchase.evaluate(0, 5, true, false, true));
    }

    @Test
    void bypassIgnoresLimitAndMoneyTogether() {
        assertEquals(RobotPurchase.Result.OK,
                RobotPurchase.evaluate(99, 5, true, false, true));
    }

    @Test
    void withoutBypassStillEnforcesLimit() {
        assertEquals(RobotPurchase.Result.LIMIT_REACHED,
                RobotPurchase.evaluate(5, 5, true, true, false));
    }
}
