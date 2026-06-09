package com.strawserver.strawskyblock.util;

import com.strawserver.strawskyblock.util.IslandTeleportHelper.PostTeleportVerdict;
import com.strawserver.strawskyblock.util.ServerSpawnResolver;
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
        // 伺服器端全部正常 + 已觀察到客戶端活動 → 真正健康。
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, true, true, true, true);
        assertFalse(verdict.suspicious());
    }

    @Test
    void offlinePlayerIsNeverSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                false, false, false, false, false, false, false, false, false);
        assertFalse(verdict.suspicious());
    }

    @Test
    void wrongWorldAfterSuccessIsSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, false, false, true, true, false, true, false, true);
        assertTrue(verdict.suspicious());
        assertNotNull(verdict.reason());
        assertTrue(verdict.reason().contains("目的地世界"));
    }

    @Test
    void playerWhoMovedAwayIsHealthyEvenWithUnloadedLandingChunk() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, false, false, false, false, false, false, false);
        assertFalse(verdict.suspicious());
    }

    @Test
    void stuckAtLandingWithUnloadedChunkIsSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, false, true, true, true, true, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("載入地形"));
    }

    @Test
    void stuckAtLandingWithoutGroundIsSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, false, false, true, true, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("虛空"));
    }

    @Test
    void chunkNotSentToClientIsSuspicious() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, false, true, false, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("isChunkSent"));
    }

    @Test
    void airborneWithoutMovementNearSolidGroundIsSuspicious() {
        // 模擬 live 證據：OnGround=0、y=64 附近有地面、玩家幾乎無位移。
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, false, true, true, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("OnGround=0"));
        assertTrue(verdict.reason().contains("載入地形"));
    }

    @Test
    void airborneWithMovementIsNotSuspiciousWhenChunkSent() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, false, false, true, true);
        assertFalse(verdict.suspicious());
    }

    @Test
    void multipleProblemsAreCombinedInReason() {
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, false, false, false, true, false, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("載入地形"));
        assertTrue(verdict.reason().contains("虛空"));
        assertTrue(verdict.reason().contains("isChunkSent"));
    }

    // ---- v1.0.10：客戶端交握偵測（回到空島盲區）----

    @Test
    void serverHealthyButNoClientAckNearLandingIsSuspicious() {
        // 重現 v1.0.9 盲區：回到空島後伺服器端全部正常（sameWorld、near、chunkLoaded、
        // clientChunkSent、onGround=1、腳下有地、幾乎無位移），但客戶端未送出任何主動封包。
        // v1.0.9 會判定為 OK（無診斷／無恢復）；v1.0.10 必須判定為可疑。
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, true, true, true, false);
        assertTrue(verdict.suspicious());
        assertNotNull(verdict.reason());
        assertTrue(verdict.reason().contains("客戶端主動封包"));
        assertTrue(verdict.reason().contains("載入地形"));
    }

    @Test
    void noClientAckButPlayerMovedAwayIsHealthy() {
        // 玩家已移動（noMovement=false）即代表客戶端有送封包，即使追蹤器尚未標記也視為健康。
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, true, false, true, false);
        assertFalse(verdict.suspicious());
    }

    @Test
    void clientAckObservedNearLandingIsHealthy() {
        // 與盲區案例相同的伺服器端狀態，但已觀察到客戶端活動 → 健康。
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, true, true, true, true);
        assertFalse(verdict.suspicious());
    }

    // ---- v1.0.11：卡住恢復後仍可疑 → 最終 kick 回退 ----

    @Test
    void finalFallbackKickTriggersOnlyForCrossWorldPostRecoverySuspicious() {
        assertTrue(IslandTeleportHelper.shouldPerformFinalFallbackKick(true, true, true));
        assertFalse(IslandTeleportHelper.shouldPerformFinalFallbackKick(false, true, true));
        assertFalse(IslandTeleportHelper.shouldPerformFinalFallbackKick(true, false, true));
        assertFalse(IslandTeleportHelper.shouldPerformFinalFallbackKick(true, true, false));
    }

    @Test
    void spawnFromIslandOnGroundFalseNoMovementIsEligibleForFinalFallback() {
        // 重現 2026-06-09 live 案例：/spawn 從 straw_skyblock_world 至 world，
        // sameWorld=true nearDestination=true chunkLoaded=true clientChunkSent=true
        // onGround=false noMovement=true groundBelow=true clientActivity=true => 可疑。
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, false, true, true, true);
        assertTrue(verdict.suspicious());
        assertTrue(verdict.reason().contains("OnGround=0"));
        assertTrue(IslandTeleportHelper.shouldPerformFinalFallbackKick(
                true, verdict.suspicious(), true));
    }

    @Test
    void sameWorldRecoverySuspiciousDoesNotTriggerFinalFallbackKick() {
        // 同世界傳送（例如主世界內短距）不應觸發跨世界最終 kick，避免回歸。
        PostTeleportVerdict verdict = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, false, true, true, true);
        assertTrue(verdict.suspicious());
        assertFalse(IslandTeleportHelper.shouldPerformFinalFallbackKick(
                false, verdict.suspicious(), true));
    }

    @Test
    void postRecoveryDiagnosticReasonDocumentsKickFallback() {
        PostTeleportVerdict verdict = PostTeleportVerdict.suspicious(
                "伺服器端實體未著地（OnGround=0）且傳送後幾乎無位移（疑似客戶端維度交握未完成，仍卡在載入地形）");
        String reason = IslandTeleportHelper.buildPostRecoveryDiagnosticReason(verdict);
        assertTrue(reason.contains("卡住恢復後驗證仍可疑"));
        assertTrue(reason.contains("OnGround=0"));
        assertTrue(reason.contains(IslandTeleportHelper.FINAL_FALLBACK_DIAGNOSTIC_SUFFIX));
    }

    @Test
    void spawnFromIslandOperationConstantIsStable() {
        assertEquals("spawn-from-island-teleport", ServerSpawnResolver.OPERATION);
    }

    // ---- v1.0.12：PRELOAD_WAIT_SYNC 策略選擇 ----

    @Test
    void crossWorldDefaultsToPreloadWaitSyncStrategy() {
        // null／空字串／未指定 → 跨世界一律採用新策略（治本預設）。
        assertTrue(IslandTeleportHelper.usePreloadWaitSyncStrategy(true, null));
        assertTrue(IslandTeleportHelper.usePreloadWaitSyncStrategy(true, ""));
        assertTrue(IslandTeleportHelper.usePreloadWaitSyncStrategy(true, "   "));
        assertTrue(IslandTeleportHelper.usePreloadWaitSyncStrategy(
                true, IslandTeleportHelper.STRATEGY_PRELOAD_WAIT_SYNC));
        assertTrue(IslandTeleportHelper.usePreloadWaitSyncStrategy(true, "preload_wait_sync"));
    }

    @Test
    void sameWorldNeverUsesPreloadWaitSyncStrategy() {
        // 同世界傳送不額外延遲，沿用快速路徑。
        assertFalse(IslandTeleportHelper.usePreloadWaitSyncStrategy(false, null));
        assertFalse(IslandTeleportHelper.usePreloadWaitSyncStrategy(
                false, IslandTeleportHelper.STRATEGY_PRELOAD_WAIT_SYNC));
    }

    @Test
    void legacyStrategyFallsBackToAsyncResync() {
        assertFalse(IslandTeleportHelper.usePreloadWaitSyncStrategy(
                true, IslandTeleportHelper.STRATEGY_LEGACY_ASYNC_RESYNC));
        assertFalse(IslandTeleportHelper.usePreloadWaitSyncStrategy(true, "legacy_async_resync"));
    }

    @Test
    void syncTeleportOnlyForCrossWorldWhenEnabled() {
        assertTrue(IslandTeleportHelper.shouldUseSyncTeleport(true, true));
        assertFalse(IslandTeleportHelper.shouldUseSyncTeleport(true, false));
        assertFalse(IslandTeleportHelper.shouldUseSyncTeleport(false, true));
        assertFalse(IslandTeleportHelper.shouldUseSyncTeleport(false, false));
    }

    // ---- v1.0.12：恢復必須延後到客戶端活動視窗之後 ----

    @Test
    void recoveryIsDelayedUntilAfterClientActivityWindow() {
        long preload = 10L;
        long activityWait = 40L;
        long earliestRecovery = IslandTeleportHelper.computeRecoveryEarliestTick(preload, activityWait);
        // 恢復／驗證最早只能發生於「同步傳送之後再加上整段客戶端活動視窗」。
        assertEquals(50L, earliestRecovery);
        assertTrue(earliestRecovery > preload,
                "恢復必須晚於傳送時點（preload tick）");
        assertTrue(earliestRecovery - preload >= activityWait,
                "傳送到恢復之間必須涵蓋整段客戶端活動視窗");
    }

    @Test
    void recoveryEarliestTickClampsNonPositiveInputs() {
        // 客戶端活動視窗至少 1 tick，避免「傳送後立刻恢復」的退化情況。
        assertEquals(1L, IslandTeleportHelper.computeRecoveryEarliestTick(0L, 0L));
        assertEquals(1L, IslandTeleportHelper.computeRecoveryEarliestTick(-5L, -5L));
    }

    @Test
    void recoveryAttemptedOnlyForCrossWorldSuspiciousWhenEnabled() {
        assertTrue(IslandTeleportHelper.shouldAttemptRecovery(true, true, true));
        assertFalse(IslandTeleportHelper.shouldAttemptRecovery(false, true, true));
        assertFalse(IslandTeleportHelper.shouldAttemptRecovery(true, false, true));
        assertFalse(IslandTeleportHelper.shouldAttemptRecovery(true, true, false));
    }

    // ---- v1.0.26：傳送偵測總開關 ----

    @Test
    void postTeleportDetectionGatedByMasterSwitch() {
        // 關閉時：完全不執行偵測（傳送仍照常完成）。
        assertFalse(IslandTeleportHelper.shouldRunPostTeleportDetection(false));
        // 開啟時：才進入後續偵測流程。
        assertTrue(IslandTeleportHelper.shouldRunPostTeleportDetection(true));
    }

    @Test
    void finalFallbackStillTriggersAfterPostRecoverySuspiciousUnderNewStrategy() {
        // 新策略下，客戶端活動視窗過後仍可疑 → 有界恢復；恢復後再驗證仍可疑 → 最終 kick。
        // 重現 live：onGround=false noMovement=true clientActivity=false 的跨世界凍結案例。
        PostTeleportVerdict postRecovery = IslandTeleportHelper.evaluatePostTeleport(
                true, true, true, true, true, false, true, true, false);
        assertTrue(postRecovery.suspicious());
        assertTrue(IslandTeleportHelper.shouldAttemptRecovery(true, postRecovery.suspicious(), true));
        assertTrue(IslandTeleportHelper.shouldPerformFinalFallbackKick(
                true, postRecovery.suspicious(), true));
    }
}
