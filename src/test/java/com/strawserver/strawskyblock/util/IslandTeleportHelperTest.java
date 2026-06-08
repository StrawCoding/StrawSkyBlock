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
        // 3x3 區塊足以覆蓋單人傳送落點周圍的即時載入需求。
        assertEquals(1, IslandTeleportHelper.DEFAULT_CHUNK_RADIUS);
    }

    // ---- 成功後驗證純邏輯（evaluatePostTeleport）----

    @Test
    void healthyLandingIsNotSuspicious() {
        // 仍在落點、區塊載入、下方有方塊：正常。
        PostTeleportVerdict verdict =
                IslandTeleportHelper.evaluatePostTeleport(true, true, true, true, true);
        assertFalse(verdict.suspicious());
    }

    @Test
    void offlinePlayerIsNeverSuspicious() {
        // 玩家已離線：無從驗證，且可能只是正常登出，不應誤報。
        PostTeleportVerdict verdict =
                IslandTeleportHelper.evaluatePostTeleport(false, false, false, false, false);
        assertFalse(verdict.suspicious());
    }

    @Test
    void wrongWorldAfterSuccessIsSuspicious() {
        // 回報成功卻不在目的地世界：典型的跨維度同步失敗（客戶端卡在載入地形）。
        PostTeleportVerdict verdict =
                IslandTeleportHelper.evaluatePostTeleport(true, false, false, true, true);
        assertTrue(verdict.suspicious());
        assertNotNull(verdict.reason());
        assertTrue(verdict.reason().contains("目的地世界"));
    }

    @Test
    void playerWhoMovedAwayIsHealthyEvenWithUnloadedLandingChunk() {
        // 玩家已離開落點代表能正常移動 / 已收到地形，即使落點區塊條件不佳也視為正常，降低誤報。
        PostTeleportVerdict verdict =
                IslandTeleportHelper.evaluatePostTeleport(true, true, false, false, false);
        assertFalse(verdict.suspicious());
    }

    @Test
    void stuckAtLandingWithUnloadedChunkIsSuspicious() {
        // 仍停在落點但落點區塊未載入：客戶端可能持續卡在載入地形。
        PostTeleportVerdict verdict =
                IslandTeleportHelper.evaluatePostTeleport(true, true, true, false, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("載入地形"));
    }

    @Test
    void stuckAtLandingWithoutGroundIsSuspicious() {
        // 仍停在落點但下方無可站立方塊：有掉入虛空風險。
        PostTeleportVerdict verdict =
                IslandTeleportHelper.evaluatePostTeleport(true, true, true, true, false);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("虛空"));
    }

    @Test
    void multipleProblemsAreCombinedInReason() {
        PostTeleportVerdict verdict =
                IslandTeleportHelper.evaluatePostTeleport(true, true, true, false, false);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("載入地形"));
        assertTrue(verdict.reason().contains("虛空"));
    }
}
