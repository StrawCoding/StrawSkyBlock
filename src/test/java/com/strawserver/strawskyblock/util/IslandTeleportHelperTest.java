package com.strawserver.strawskyblock.util;

import com.strawserver.strawskyblock.util.IslandTeleportHelper.PostTeleportVerdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(1, IslandTeleportHelper.DEFAULT_CHUNK_RADIUS);
    }

    // ---- 成功後驗證純邏輯（evaluatePostTeleport）----

    @Test
    void healthyLandingIsNotSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, true, true, true);
        assertFalse(verdict.suspicious());
    }

    @Test
    void offlinePlayerIsNeverSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                false, false, false, false, false, false, false, false);
        assertFalse(verdict.suspicious());
    }

    @Test
    void wrongWorldAfterSuccessIsSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, false, false, true, true, false, true, false);
        assertTrue(verdict.suspicious());
        assertNotNull(verdict.reason());
        assertTrue(verdict.reason().contains("目的地世界"));
    }

    @Test
    void playerWhoMovedAwayIsHealthyEvenWithUnloadedLandingChunk() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, false, false, false, false, false, false);
        assertFalse(verdict.suspicious());
    }

    @Test
    void stuckAtLandingWithUnloadedChunkIsSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, false, true, true, true, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("載入地形"));
    }

    @Test
    void stuckAtLandingWithoutGroundIsSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, false, false, true, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("虛空"));
    }

    @Test
    void chunkNotSentToClientIsSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, false, true, false);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("isChunkSent"));
    }

    @Test
    void airborneWithoutMovementNearSolidGroundIsSuspicious() {
        // 模擬 live 證據：OnGround=0、y=64 附近有地面、玩家幾乎無位移。
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, false, true, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("OnGround=0"));
        assertTrue(verdict.reason().contains("載入地形"));
    }

    @Test
    void airborneWithMovementIsNotSuspiciousWhenChunkSent() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, false, false, true);
        assertFalse(verdict.suspicious());
    }

    @Test
    void multipleProblemsAreCombinedInReason() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, false, false, false, true, false);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("載入地形"));
        assertTrue(verdict.reason().contains("虛空"));
        assertTrue(verdict.reason().contains("isChunkSent"));
    }
}
