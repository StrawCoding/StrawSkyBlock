package com.strawserver.strawskyblock.robot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotScanAreaTest {

    @Test
    void boxAroundOriginWithinIsland() {
        // 島嶼中心 (0,0)，size 200 → half 100，原點在中心，範圍 3，垂直 1
        RobotScanArea area = RobotScanArea.around(0, 100, 0, 3, 1, 0, 0, 100);
        assertEquals(-3, area.minX());
        assertEquals(3, area.maxX());
        assertEquals(-3, area.minZ());
        assertEquals(3, area.maxZ());
        assertEquals(99, area.minY());
        assertEquals(101, area.maxY());
        // 7 x 7 x 3
        assertEquals(7L * 7L * 3L, area.blockCount());
        assertFalse(area.isEmpty());
    }

    @Test
    void clampsToIslandBoundary() {
        // 原點貼近東邊界 (x=99)，範圍 5 → maxX 應裁切到島嶼邊界 100，而非 104
        RobotScanArea area = RobotScanArea.around(99, 100, 0, 5, 0, 0, 0, 100);
        assertEquals(94, area.minX());
        assertEquals(100, area.maxX());
        assertEquals(-5, area.minZ());
        assertEquals(5, area.maxZ());
    }

    @Test
    void neverExceedsIslandOnBothSides() {
        // 小島：half 2，原點在中心，範圍很大 → 整個盒子被裁切到 [-2,2]
        RobotScanArea area = RobotScanArea.around(0, 64, 0, 100, 0, 0, 0, 2);
        assertEquals(-2, area.minX());
        assertEquals(2, area.maxX());
        assertEquals(-2, area.minZ());
        assertEquals(2, area.maxZ());
        assertEquals(5L * 5L * 1L, area.blockCount());
    }

    @Test
    void emptyWhenOriginOutsideIsland() {
        // 原點遠在島嶼東側之外，範圍小 → 水平邊界無交集
        RobotScanArea area = RobotScanArea.around(500, 64, 0, 2, 0, 0, 0, 100);
        assertTrue(area.isEmpty());
        assertEquals(0L, area.blockCount());
    }

    @Test
    void negativeRangeTreatedAsZero() {
        RobotScanArea area = RobotScanArea.around(10, 64, 10, -5, -3, 0, 0, 100);
        assertEquals(10, area.minX());
        assertEquals(10, area.maxX());
        assertEquals(10, area.minZ());
        assertEquals(10, area.maxZ());
        assertEquals(64, area.minY());
        assertEquals(64, area.maxY());
        assertEquals(1L, area.blockCount());
    }
}
