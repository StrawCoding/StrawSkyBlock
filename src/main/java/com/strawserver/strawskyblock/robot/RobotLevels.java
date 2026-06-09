package com.strawserver.strawskyblock.robot;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 小機器人統一等級定義表（純邏輯，不依賴 Bukkit）。
 *
 * <p>每台機器人只有單一等級 L1 ~ maxLevel：等級越高，挖掘間隔越短（越快）且水平掃描半徑越大；
 * 每升一級需付出對應花費。本類別負責等級裁切、查值與升級合法性檢查，方便獨立單元測試。</p>
 */
public final class RobotLevels {

    private final int maxLevel;
    private final Map<Integer, Long> intervalTicks;
    private final Map<Integer, Integer> ranges;
    private final Map<Integer, Double> costs;

    private final long fallbackInterval;
    private final int fallbackRange;

    public RobotLevels(Map<Integer, Long> intervalTicks,
                       Map<Integer, Integer> ranges,
                       Map<Integer, Double> costs,
                       long fallbackInterval,
                       int fallbackRange) {
        this.intervalTicks = new TreeMap<>(intervalTicks);
        this.ranges = new TreeMap<>(ranges);
        this.costs = new TreeMap<>(costs);
        this.fallbackInterval = Math.max(1L, fallbackInterval);
        this.fallbackRange = Math.max(0, fallbackRange);

        int intervalMax = this.intervalTicks.isEmpty() ? 0
                : Collections.max(this.intervalTicks.keySet());
        int rangeMax = this.ranges.isEmpty() ? 0
                : Collections.max(this.ranges.keySet());
        this.maxLevel = Math.max(1, Math.max(intervalMax, rangeMax));
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * 將等級裁切到 [1, maxLevel] 範圍內。
     */
    public int clampLevel(int level) {
        if (level < 1) {
            return 1;
        }
        if (level > maxLevel) {
            return maxLevel;
        }
        return level;
    }

    public boolean isValidLevel(int level) {
        return level >= 1 && level <= maxLevel;
    }

    /**
     * 取得指定等級對應的挖掘間隔（tick 數），未定義則回傳後備值。
     */
    public long intervalTicks(int level) {
        Long value = intervalTicks.get(clampLevel(level));
        if (value == null || value < 1L) {
            return fallbackInterval;
        }
        return value;
    }

    /**
     * 取得指定等級對應的水平掃描半徑，未定義則回傳後備值。
     */
    public int range(int level) {
        Integer value = ranges.get(clampLevel(level));
        if (value == null || value < 0) {
            return fallbackRange;
        }
        return value;
    }

    /**
     * 取得升級到目標等級所需花費，未定義則為 0。
     */
    public double upgradeCost(int targetLevel) {
        Double value = costs.get(clampLevel(targetLevel));
        return value == null ? 0.0D : Math.max(0.0D, value);
    }

    /**
     * 檢查由 {@code current} 升級到 {@code target} 是否合法（僅允許往上升、不可超過上限）。
     */
    public UpgradeResult checkUpgrade(int current, int target) {
        if (target < 1 || target > maxLevel) {
            return UpgradeResult.OUT_OF_RANGE;
        }
        if (current >= maxLevel) {
            return UpgradeResult.ALREADY_MAX;
        }
        if (target <= current) {
            return UpgradeResult.NOT_HIGHER;
        }
        return UpgradeResult.OK;
    }
}
