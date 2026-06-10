package com.strawserver.strawskyblock.robot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotForwardRowTest {

    @Test
    void forwardStepCardinalDirections() {
        // yaw 0 → 南 (+Z)
        assertArrayEquals(new int[]{0, 1}, RobotForwardRow.forwardStep(0f));
        // yaw 90 → 西 (-X)
        assertArrayEquals(new int[]{-1, 0}, RobotForwardRow.forwardStep(90f));
        // yaw 180 → 北 (-Z)
        assertArrayEquals(new int[]{0, -1}, RobotForwardRow.forwardStep(180f));
        // yaw 270 → 東 (+X)
        assertArrayEquals(new int[]{1, 0}, RobotForwardRow.forwardStep(270f));
    }

    @Test
    void forwardStepSnapsDiagonalsToNearestAxis() {
        // 接近南偏西，但 Z 分量較大 → 仍取南
        assertArrayEquals(new int[]{0, 1}, RobotForwardRow.forwardStep(30f));
        // 接近西偏南，X 分量較大 → 取西
        assertArrayEquals(new int[]{-1, 0}, RobotForwardRow.forwardStep(60f));
        // 負 yaw 正規化：-90 等同 270 → 東
        assertArrayEquals(new int[]{1, 0}, RobotForwardRow.forwardStep(-90f));
    }

    @Test
    void rowExtendsForwardByLengthAtOriginYWhenNoVertical() {
        // 面向東(+X)，長度 3，垂直 0 → (origin+1..+3, originY, originZ)
        List<RobotForwardRow.Cell> cells =
                RobotForwardRow.cells(0, 100, 0, 270f, 3, 0, 0, 0, 100);
        assertEquals(3, cells.size());
        assertEquals(new RobotForwardRow.Cell(1, 100, 0), cells.get(0));
        assertEquals(new RobotForwardRow.Cell(2, 100, 0), cells.get(1));
        assertEquals(new RobotForwardRow.Cell(3, 100, 0), cells.get(2));
    }

    @Test
    void rowAppliesVerticalRangePerColumn() {
        // 面向南(+Z)，長度 2，垂直 1 → 每格欄位涵蓋 y-1,y,y+1，共 2*3 = 6 格
        List<RobotForwardRow.Cell> cells =
                RobotForwardRow.cells(5, 64, 5, 0f, 2, 1, 0, 0, 100);
        assertEquals(6, cells.size());
        // 第一欄位 z=6
        assertTrue(cells.contains(new RobotForwardRow.Cell(5, 63, 6)));
        assertTrue(cells.contains(new RobotForwardRow.Cell(5, 64, 6)));
        assertTrue(cells.contains(new RobotForwardRow.Cell(5, 65, 6)));
        // 第二欄位 z=7
        assertTrue(cells.contains(new RobotForwardRow.Cell(5, 64, 7)));
    }

    @Test
    void rowStopsAtIslandBoundary() {
        // 島嶼中心 (0,0)，half 2 → 邊界 x∈[-2,2]；原點 x=1 面向東，長度 5
        // 只有 x=2 在範圍內，x=3 起超界即停止 → 僅 1 格
        List<RobotForwardRow.Cell> cells =
                RobotForwardRow.cells(1, 100, 0, 270f, 5, 0, 0, 0, 2);
        assertEquals(1, cells.size());
        assertEquals(new RobotForwardRow.Cell(2, 100, 0), cells.get(0));
    }

    @Test
    void zeroLengthYieldsEmpty() {
        List<RobotForwardRow.Cell> cells =
                RobotForwardRow.cells(0, 100, 0, 0f, 0, 0, 0, 0, 100);
        assertTrue(cells.isEmpty());
    }
}
