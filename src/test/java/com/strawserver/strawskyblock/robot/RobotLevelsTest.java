package com.strawserver.strawskyblock.robot;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotLevelsTest {

    private RobotLevels buildL1ToL6() {
        Map<Integer, Long> speed = new LinkedHashMap<>();
        Map<Integer, Double> speedCost = new LinkedHashMap<>();
        Map<Integer, Integer> range = new LinkedHashMap<>();
        Map<Integer, Double> rangeCost = new LinkedHashMap<>();
        long[] intervals = {200, 160, 120, 80, 40, 20};
        int[] ranges = {2, 3, 4, 5, 6, 7};
        double[] costs = {0, 1000, 2500, 5000, 10000, 20000};
        for (int level = 1; level <= 6; level++) {
            speed.put(level, intervals[level - 1]);
            speedCost.put(level, costs[level - 1]);
            range.put(level, ranges[level - 1]);
            rangeCost.put(level, costs[level - 1]);
        }
        return new RobotLevels(speed, speedCost, range, rangeCost, 200L, 2);
    }

    @Test
    void maxLevelIsSix() {
        assertEquals(6, buildL1ToL6().getMaxLevel());
    }

    @Test
    void intervalAndRangeLookup() {
        RobotLevels levels = buildL1ToL6();
        assertEquals(200L, levels.intervalTicks(1));
        assertEquals(20L, levels.intervalTicks(6));
        assertEquals(2, levels.range(1));
        assertEquals(7, levels.range(6));
    }

    @Test
    void clampOutOfRangeLevels() {
        RobotLevels levels = buildL1ToL6();
        assertEquals(1, levels.clampLevel(0));
        assertEquals(1, levels.clampLevel(-3));
        assertEquals(6, levels.clampLevel(99));
        // 超出範圍時 lookup 退回裁切後的等級值
        assertEquals(200L, levels.intervalTicks(0));
        assertEquals(20L, levels.intervalTicks(100));
    }

    @Test
    void upgradeValidation() {
        RobotLevels levels = buildL1ToL6();
        assertEquals(UpgradeResult.OK, levels.checkUpgrade(1, 2));
        assertEquals(UpgradeResult.OK, levels.checkUpgrade(3, 6));
        assertEquals(UpgradeResult.NOT_HIGHER, levels.checkUpgrade(3, 3));
        assertEquals(UpgradeResult.NOT_HIGHER, levels.checkUpgrade(4, 2));
        assertEquals(UpgradeResult.OUT_OF_RANGE, levels.checkUpgrade(1, 7));
        assertEquals(UpgradeResult.OUT_OF_RANGE, levels.checkUpgrade(1, 0));
        assertEquals(UpgradeResult.ALREADY_MAX, levels.checkUpgrade(6, 6));
    }

    @Test
    void costLookup() {
        RobotLevels levels = buildL1ToL6();
        assertEquals(0.0D, levels.speedUpgradeCost(1));
        assertEquals(20000.0D, levels.speedUpgradeCost(6));
        assertEquals(2500.0D, levels.lengthUpgradeCost(3));
    }

    @Test
    void validLevelChecks() {
        RobotLevels levels = buildL1ToL6();
        assertTrue(levels.isValidLevel(1));
        assertTrue(levels.isValidLevel(6));
        assertFalse(levels.isValidLevel(0));
        assertFalse(levels.isValidLevel(7));
    }

    @Test
    void fallbackUsedWhenLevelUndefined() {
        // 只定義 L1~L3，maxLevel 應為 3，超出者裁切後仍取得對應值
        Map<Integer, Long> speed = new LinkedHashMap<>();
        Map<Integer, Integer> range = new LinkedHashMap<>();
        speed.put(1, 100L);
        speed.put(2, 80L);
        speed.put(3, 60L);
        range.put(1, 2);
        range.put(2, 3);
        range.put(3, 4);
        RobotLevels levels = new RobotLevels(speed, Map.of(), range, Map.of(), 999L, 9);
        assertEquals(3, levels.getMaxLevel());
        assertEquals(60L, levels.intervalTicks(5)); // 裁切到 3
        assertEquals(4, levels.range(5));
    }
}
