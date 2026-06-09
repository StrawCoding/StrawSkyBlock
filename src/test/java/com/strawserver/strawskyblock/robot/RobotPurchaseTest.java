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
}
