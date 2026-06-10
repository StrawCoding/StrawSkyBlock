package com.strawserver.strawskyblock.listener;

import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VoidProtectionListener 的純邏輯測試（不依賴 Bukkit）。
 */
class VoidProtectionListenerTest {

    @Test
    void shouldCancelVoidDamage_whenInIslandWorldAndBelowThreshold() {
        // Y=-5, threshold=0 → 應取消
        assertTrue(VoidProtectionListener.shouldCancelVoidDamage(
                EntityDamageEvent.DamageCause.VOID, true, -5.0, 0));
    }

    @Test
    void shouldCancelVoidDamage_whenAtThreshold() {
        // Y=0, threshold=0 → 應取消（<=）
        assertTrue(VoidProtectionListener.shouldCancelVoidDamage(
                EntityDamageEvent.DamageCause.VOID, true, 0.0, 0));
    }

    @Test
    void shouldCancelVoidDamage_whenInMainWorldAndBelowThreshold() {
        // 主世界大廳也應受保護
        assertTrue(VoidProtectionListener.shouldCancelVoidDamage(
                EntityDamageEvent.DamageCause.VOID, true, -5.0, 0));
    }

    @Test
    void shouldNotCancelVoidDamage_whenAboveThreshold() {
        // Y=1, threshold=0 → 不應取消
        assertFalse(VoidProtectionListener.shouldCancelVoidDamage(
                EntityDamageEvent.DamageCause.VOID, true, 1.0, 0));
    }

    @Test
    void shouldNotCancelVoidDamage_whenNotInProtectedWorld() {
        // 雖然 Y=-5 且 cause=VOID，但不在受保護世界
        assertFalse(VoidProtectionListener.shouldCancelVoidDamage(
                EntityDamageEvent.DamageCause.VOID, false, -5.0, 0));
    }

    @Test
    void shouldNotCancelVoidDamage_whenCauseIsNotVoid() {
        assertFalse(VoidProtectionListener.shouldCancelVoidDamage(
                EntityDamageEvent.DamageCause.FALL, true, -5.0, 0));
        assertFalse(VoidProtectionListener.shouldCancelVoidDamage(
                EntityDamageEvent.DamageCause.LAVA, true, -5.0, 0));
        assertFalse(VoidProtectionListener.shouldCancelVoidDamage(
                EntityDamageEvent.DamageCause.DROWNING, true, -5.0, 0));
    }

    @Test
    void shouldTeleportToIslandHome_whenHasIslandAndHomeAvailable() {
        assertTrue(VoidProtectionListener.shouldTeleportToIslandHome(true, true));
    }

    @Test
    void shouldNotTeleportToIslandHome_whenNoIsland() {
        assertFalse(VoidProtectionListener.shouldTeleportToIslandHome(false, true));
    }

    @Test
    void shouldNotTeleportToIslandHome_whenHomeUnavailable() {
        assertFalse(VoidProtectionListener.shouldTeleportToIslandHome(true, false));
    }

    @Test
    void shouldNotTeleportToIslandHome_whenNeitherAvailable() {
        assertFalse(VoidProtectionListener.shouldTeleportToIslandHome(false, false));
    }

    // ---- resolveDestinationType 測試 ----

    @Test
    void resolveDestinationType_mainWorldChoosesMainWorldSpawn_evenWhenIslandHomeExists() {
        assertEquals(
                VoidProtectionListener.VoidTeleportDestination.MAIN_WORLD_SPAWN,
                VoidProtectionListener.resolveDestinationType(
                        true, false, true, true));
    }

    @Test
    void resolveDestinationType_islandWorldChoosesIslandHome_whenAvailable() {
        assertEquals(
                VoidProtectionListener.VoidTeleportDestination.ISLAND_HOME,
                VoidProtectionListener.resolveDestinationType(
                        false, true, true, true));
    }

    @Test
    void resolveDestinationType_islandWorldChoosesFallback_whenNoHome() {
        assertEquals(
                VoidProtectionListener.VoidTeleportDestination.FALLBACK_SPAWN,
                VoidProtectionListener.resolveDestinationType(
                        false, true, true, false));
    }

    @Test
    void resolveDestinationType_islandWorldChoosesFallback_whenNoIsland() {
        assertEquals(
                VoidProtectionListener.VoidTeleportDestination.FALLBACK_SPAWN,
                VoidProtectionListener.resolveDestinationType(
                        false, true, false, false));
    }

    @Test
    void resolveDestinationType_unrelatedContextReturnsNone() {
        assertEquals(
                VoidProtectionListener.VoidTeleportDestination.NONE,
                VoidProtectionListener.resolveDestinationType(
                        false, false, true, true));
    }

    @Test
    void resolveDestinationType_mainWorldTakesPrecedenceOverIslandWorld() {
        // 理論上不會同時成立，但測試主世界優先級
        assertEquals(
                VoidProtectionListener.VoidTeleportDestination.MAIN_WORLD_SPAWN,
                VoidProtectionListener.resolveDestinationType(
                        true, true, true, true));
    }
}
