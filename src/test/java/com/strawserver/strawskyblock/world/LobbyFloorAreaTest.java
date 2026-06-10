package com.strawserver.strawskyblock.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyFloorAreaTest {

    @Test
    void radiusOneCoversNineChunks() {
        assertEquals(9, LobbyFloorArea.chunkCount(1));
    }

    @Test
    void radiusOneCovers48x48Blocks() {
        assertEquals(48 * 48, LobbyFloorArea.blockCount(1));
        List<LobbyFloorArea.Cell> cells = LobbyFloorArea.cells(1);
        assertEquals(48 * 48, cells.size());
    }

    @Test
    void radiusOneSpansBlockRangeMinus16To31() {
        // 區塊 -1..1 → 方塊 -16..31（含），即 chunk*16 .. chunk*16+15
        assertEquals(-16, LobbyFloorArea.minBlock(0, 1));
        assertEquals(31, LobbyFloorArea.maxBlock(0, 1));

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (LobbyFloorArea.Cell cell : LobbyFloorArea.cells(1)) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxZ = Math.max(maxZ, cell.z());
        }
        assertEquals(-16, minX);
        assertEquals(31, maxX);
        assertEquals(-16, minZ);
        assertEquals(31, maxZ);
    }

    @Test
    void floorIncludesOriginSoSpawnIsSupported() {
        List<LobbyFloorArea.Cell> cells = LobbyFloorArea.cells(1);
        assertTrue(cells.contains(new LobbyFloorArea.Cell(0, 0)),
                "出生點 (0,0) 必須位於地板之上");
    }

    @Test
    void cellsAreUniqueAndWithinComputedBounds() {
        List<LobbyFloorArea.Cell> cells = LobbyFloorArea.cells(1);
        assertEquals(cells.size(), cells.stream().distinct().count(), "不應有重複格子");
        for (LobbyFloorArea.Cell cell : cells) {
            assertFalse(cell.x() < -16 || cell.x() > 31, "x 超出範圍: " + cell.x());
            assertFalse(cell.z() < -16 || cell.z() > 31, "z 超出範圍: " + cell.z());
        }
    }

    @Test
    void radiusZeroIsSingleChunk() {
        assertEquals(1, LobbyFloorArea.chunkCount(0));
        assertEquals(16 * 16, LobbyFloorArea.cells(0).size());
        assertEquals(0, LobbyFloorArea.minBlock(0, 0));
        assertEquals(15, LobbyFloorArea.maxBlock(0, 0));
    }

    @Test
    void negativeRadiusClampedToZero() {
        assertEquals(1, LobbyFloorArea.chunkCount(-5));
        assertEquals(16 * 16, LobbyFloorArea.cells(-5).size());
    }

    @Test
    void supportsOffsetCenterChunk() {
        // 中心區塊 (2,3)，radius 0 → 方塊 x:32..47, z:48..63
        assertEquals(32, LobbyFloorArea.minBlock(2, 0));
        assertEquals(47, LobbyFloorArea.maxBlock(2, 0));
        assertEquals(48, LobbyFloorArea.minBlock(3, 0));
        assertEquals(63, LobbyFloorArea.maxBlock(3, 0));
    }
}
