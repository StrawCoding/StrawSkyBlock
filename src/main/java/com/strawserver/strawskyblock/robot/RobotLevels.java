package com.strawserver.strawskyblock.robot;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 小機器人等級定義表（純邏輯，不依賴 Bukkit）。
 *
 * <p>包含速度等級（對應挖掘間隔 tick 數與升級花費）與長度 / 範圍等級（對應水平掃描半徑與
 * 升級花費）。等級採 1 ~ maxLevel 的玩家升級進程；本類別負責等級裁切、查值與升級合法性檢查，
 * 方便獨立單元測試。</p>
 */
public final class RobotLevels {

    private final int maxLevel;
    private final Map<Integer, Long> speedIntervalTicks;
    private final Map<Integer, Double> speedCosts;
    private final Map<Integer, Integer> lengthRanges;
    private final Map<Integer, Double> lengthCosts;

    private final long fallbackInterval;
    private final int fallbackRange;

    public RobotLevels(Map<Integer, Long> speedIntervalTicks,
                       Map<Integer, Double> speedCosts,
                       Map<Integer, Integer> lengthRanges,
                       Map<Integer, Double> lengthCosts,
                       long fallbackInterval,
                       int fallbackRange) {
        this.speedIntervalTicks = new TreeMap<>(speedIntervalTicks);
        this.speedCosts = new TreeMap<>(speedCosts);
        this.lengthRanges = new TreeMap<>(lengthRanges);
        this.lengthCosts = new TreeMap<>(lengthCosts);
        this.fallbackInterval = Math.max(1L, fallbackInterval);
        this.fallbackRange = Math.max(0, fallbackRange);

        // 以速度與長度等級的最大鍵值取交集，確保兩種等級進程一致。
        int speedMax = this.speedIntervalTicks.isEmpty() ? 0
                : Collections.max(this.speedIntervalTicks.keySet());
        int lengthMax = this.lengthRanges.isEmpty() ? 0
                : Collections.max(this.lengthRanges.keySet());
        this.maxLevel = Math.max(1, Math.min(speedMax == 0 ? Integer.MAX_VALUE : speedMax,
                lengthMax == 0 ? Integer.MAX_VALUE : lengthMax));
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
     * 取得指定速度等級對應的挖掘間隔（tick 數），未定義則回傳後備值。
     */
    public long intervalTicks(int speedLevel) {
        Long value = speedIntervalTicks.get(clampLevel(speedLevel));
        if (value == null || value < 1L) {
            return fallbackInterval;
        }
        return value;
    }

    /**
     * 取得指定長度等級對應的水平掃描半徑，未定義則回傳後備值。
     */
    public int range(int lengthLevel) {
        Integer value = lengthRanges.get(clampLevel(lengthLevel));
        if (value == null || value < 0) {
            return fallbackRange;
        }
        return value;
    }

    /**
     * 取得升級到目標速度等級所需花費，未定義則為 0。
     */
    public double speedUpgradeCost(int targetLevel) {
        Double value = speedCosts.get(clampLevel(targetLevel));
        return value == null ? 0.0D : Math.max(0.0D, value);
    }

    /**
     * 取得升級到目標長度等級所需花費，未定義則為 0。
     */
    public double lengthUpgradeCost(int targetLevel) {
        Double value = lengthCosts.get(clampLevel(targetLevel));
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
