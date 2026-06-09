package com.strawserver.strawskyblock.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetherPortalRouterTest {

    private static final String OVERWORLD = "straw_skyblock_world";
    private static final String NETHER = "straw_skyblock_world_nether";

    @Test
    void overworldToNetherWhenInIslandWorld() {
        assertEquals(NetherPortalRouter.Direction.TO_NETHER,
                NetherPortalRouter.resolve(true, true, OVERWORLD, OVERWORLD, NETHER));
    }

    @Test
    void netherToOverworldWhenInNetherWorld() {
        assertEquals(NetherPortalRouter.Direction.TO_OVERWORLD,
                NetherPortalRouter.resolve(true, true, NETHER, OVERWORLD, NETHER));
    }

    @Test
    void noneWhenNetherDisabled() {
        assertEquals(NetherPortalRouter.Direction.NONE,
                NetherPortalRouter.resolve(false, true, OVERWORLD, OVERWORLD, NETHER));
    }

    @Test
    void noneWhenNotNetherPortalCause() {
        assertEquals(NetherPortalRouter.Direction.NONE,
                NetherPortalRouter.resolve(true, false, OVERWORLD, OVERWORLD, NETHER));
    }

    @Test
    void noneForUnmanagedWorld() {
        assertEquals(NetherPortalRouter.Direction.NONE,
                NetherPortalRouter.resolve(true, true, "world", OVERWORLD, NETHER));
    }

    @Test
    void noneForNullFromWorld() {
        assertEquals(NetherPortalRouter.Direction.NONE,
                NetherPortalRouter.resolve(true, true, null, OVERWORLD, NETHER));
    }

    @Test
    void clampYStaysWithinBuffer() {
        // 一般情況：Y 在範圍內保持不變。
        assertEquals(100, NetherPortalRouter.clampY(100, 0, 256));
    }

    @Test
    void clampYClampsBelowFloor() {
        assertEquals(2, NetherPortalRouter.clampY(-50, 0, 256));
    }

    @Test
    void clampYClampsAboveCeiling() {
        assertEquals(254, NetherPortalRouter.clampY(999, 0, 256));
    }

    @Test
    void clampYDegenerateRangeReturnsLowerBound() {
        // 上界小於下界時回傳下界（minHeight + 2）。
        assertEquals(3, NetherPortalRouter.clampY(10, 1, 2));
    }
}
