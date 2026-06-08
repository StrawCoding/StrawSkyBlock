package com.strawserver.strawskyblock.listener;

import org.junit.jupiter.api.Test;

import static com.strawserver.strawskyblock.listener.PlayerRespawnListener.shouldPrepareChunksForIslandHomeRespawn;
import static com.strawserver.strawskyblock.listener.PlayerRespawnListener.shouldRedirectToIslandHome;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驗證重生重導向的純決策邏輯，涵蓋驗收條件 1～3 的判斷面向。
 */
class PlayerRespawnListenerTest {

    @Test
    void redirectsWhenIslandMemberRespawnsInIslandWorldWithoutBed() {
        // 正常情況：屬於某島、家點可用、落點在空島世界、無床/錨 → 應重導向回家點。
        assertTrue(shouldRedirectToIslandHome(true, true, true, false));
    }

    @Test
    void doesNotRedirectWhenPlayerHasNoIsland() {
        assertFalse(shouldRedirectToIslandHome(false, true, true, false));
    }

    @Test
    void doesNotRedirectWhenIslandHomeUnavailable() {
        // 家點為 null 或空島世界尚未載入 → 退回原版行為。
        assertFalse(shouldRedirectToIslandHome(true, false, true, false));
    }

    @Test
    void doesNotRedirectWhenRespawnIsOutsideIslandWorld() {
        // 主世界等其他世界的死亡重生不受影響（驗收條件 3）。
        assertFalse(shouldRedirectToIslandHome(true, true, false, false));
    }

    @Test
    void respectsExplicitBedOrAnchorSpawn() {
        // 玩家自設的床／重生錨重生點需被尊重，不覆寫。
        assertFalse(shouldRedirectToIslandHome(true, true, true, true));
    }

    @Test
    void requiresAllPositiveConditionsTogether() {
        // 只缺其中一項即不重導向。
        assertFalse(shouldRedirectToIslandHome(false, false, false, false));
        assertFalse(shouldRedirectToIslandHome(true, true, true, true));
    }

    @Test
    void chunkPreloadDecisionMatchesRedirectDecision() {
        // 重生區塊預載僅在會重導向至家點時觸發，避免影響床／錨或主世界重生。
        assertTrue(shouldPrepareChunksForIslandHomeRespawn(true, true, true, false));
        assertFalse(shouldPrepareChunksForIslandHomeRespawn(false, true, true, false));
        assertFalse(shouldPrepareChunksForIslandHomeRespawn(true, false, true, false));
        assertFalse(shouldPrepareChunksForIslandHomeRespawn(true, true, false, false));
        assertFalse(shouldPrepareChunksForIslandHomeRespawn(true, true, true, true));
    }
}
