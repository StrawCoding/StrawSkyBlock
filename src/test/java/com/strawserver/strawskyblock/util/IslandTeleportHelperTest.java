package com.strawserver.strawskyblock.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IslandTeleportHelperTest {

    @Test
    void chunkCoordinatesMatchBlockCoordinates() {
        assertEquals(0, IslandTeleportHelper.chunkX(0));
        assertEquals(0, IslandTeleportHelper.chunkZ(15));
        assertEquals(1, IslandTeleportHelper.chunkX(16));
        assertEquals(-1, IslandTeleportHelper.chunkX(-1));
        assertEquals(62, IslandTeleportHelper.chunkX(1000));
        assertEquals(125, IslandTeleportHelper.chunkX(2000));
    }

    @Test
    void defaultChunkRadiusIsReasonableForPlayerView() {
        // 3x3 區塊足以覆蓋單人傳送落點周圍的即時載入需求。
        assertEquals(1, IslandTeleportHelper.DEFAULT_CHUNK_RADIUS);
    }
}
