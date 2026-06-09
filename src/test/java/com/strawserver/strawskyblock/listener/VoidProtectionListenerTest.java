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
    void shouldNotCancelVoidDamage_whenAboveThreshold() {
        // Y=1, threshold=0 → 不應取消
        assertFalse(VoidProtectionListener.shouldCancelVoidDamage(
                EntityDamageEvent.DamageCause.VOID, true, 1.0, 0));
    }

    @Test
    void shouldNotCancelVoidDamage_whenNotInIslandWorld() {
        // 雖然 Y=-5 且 cause=VOID，但不在空島世界
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
}
