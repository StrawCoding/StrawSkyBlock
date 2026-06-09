package com.strawserver.strawskyblock.robot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RobotPurchaseTest {

    @Test
    void okWhenNoRobotUnderLimitAndCanPay() {
        assertEquals(RobotPurchase.Result.OK,
                RobotPurchase.evaluate(false, 0, 1, true, true));
    }

    @Test
    void okWhenFreeRegardlessOfFunds() {
        assertEquals(RobotPurchase.Result.OK,
                RobotPurchase.evaluate(false, 0, 1, false, false));
    }

    @Test
    void alreadyExistsTakesPriority() {
        assertEquals(RobotPurchase.Result.ALREADY_EXISTS,
                RobotPurchase.evaluate(true, 0, 1, true, true));
    }

    @Test
    void limitReachedWhenOwnedAtMax() {
        assertEquals(RobotPurchase.Result.LIMIT_REACHED,
                RobotPurchase.evaluate(false, 1, 1, true, true));
    }

    @Test
    void unlimitedWhenMaxNonPositive() {
        assertEquals(RobotPurchase.Result.OK,
                RobotPurchase.evaluate(false, 99, 0, true, true));
    }

    @Test
    void notEnoughMoneyWhenChargeRequiredAndNoFunds() {
        assertEquals(RobotPurchase.Result.NOT_ENOUGH_MONEY,
                RobotPurchase.evaluate(false, 0, 1, true, false));
    }
}
