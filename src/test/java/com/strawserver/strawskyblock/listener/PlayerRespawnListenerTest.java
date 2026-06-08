package com.strawserver.strawskyblock.listener;

import org.junit.jupiter.api.Test;

import static com.strawserver.strawskyblock.listener.PlayerRespawnListener.shouldDeferIslandHomeTeleportAfterRespawn;
import static com.strawserver.strawskyblock.listener.PlayerRespawnListener.shouldRedirectToIslandHome;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驗證重生後延遲傳送家點的純決策邏輯，涵蓋驗收條件 1～3 的判斷面向。
 */
class PlayerRespawnListenerTest {

    @Test
    void defersTeleportWhenIslandMemberRespawnsInIslandWorldWithoutBed() {
        assertTrue(shouldDeferIslandHomeTeleportAfterRespawn(true, true, true, false));
    }

    @Test
    void doesNotDeferWhenPlayerHasNoIsland() {
        assertFalse(shouldDeferIslandHomeTeleportAfterRespawn(false, true, true, false));
    }

    @Test
    void doesNotDeferWhenIslandHomeUnavailable() {
        assertFalse(shouldDeferIslandHomeTeleportAfterRespawn(true, false, true, false));
    }

    @Test
    void doesNotDeferWhenRespawnIsOutsideIslandWorld() {
        assertFalse(shouldDeferIslandHomeTeleportAfterRespawn(true, true, false, false));
    }

    @Test
    void respectsExplicitBedOrAnchorSpawn() {
        assertFalse(shouldDeferIslandHomeTeleportAfterRespawn(true, true, true, true));
    }

    @Test
    void requiresAllPositiveConditionsTogether() {
        assertFalse(shouldDeferIslandHomeTeleportAfterRespawn(false, false, false, false));
        assertFalse(shouldDeferIslandHomeTeleportAfterRespawn(true, true, true, true));
    }

    @Test
    void legacyRedirectAliasMatchesDeferDecision() {
        assertTrue(shouldRedirectToIslandHome(true, true, true, false));
        assertFalse(shouldRedirectToIslandHome(true, true, true, true));
    }
}
