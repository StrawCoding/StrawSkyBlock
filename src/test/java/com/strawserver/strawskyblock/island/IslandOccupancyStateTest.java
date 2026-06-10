package com.strawserver.strawskyblock.island;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IslandOccupancyState 純邏輯單元測試（無 Bukkit 依賴）。v1.0.39
 */
class IslandOccupancyStateTest {

    private static UUID uuid(int seed) {
        return new UUID(0L, seed);
    }

    @Test
    void noPlayersMeansAllIslandsInactive() {
        IslandOccupancyState state = new IslandOccupancyState();
        UUID island = uuid(1);
        assertFalse(state.isActive(island));
        assertEquals(0, state.playerCount(island));
        assertTrue(state.activeIslands().isEmpty());
    }

    @Test
    void onePlayerJoinsMakesIslandActive() {
        IslandOccupancyState state = new IslandOccupancyState();
        UUID island = uuid(1);
        UUID player = uuid(100);
        boolean changed = state.setPlayerIsland(player, island);
        assertTrue(changed);
        assertTrue(state.isActive(island));
        assertEquals(1, state.playerCount(island));
        assertEquals(island, state.currentIsland(player));
        assertTrue(state.activeIslands().contains(island));
    }

    @Test
    void lastPlayerLeavesPausesIsland() {
        IslandOccupancyState state = new IslandOccupancyState();
        UUID island = uuid(1);
        UUID player = uuid(100);
        state.setPlayerIsland(player, island);
        boolean changed = state.removePlayer(player);
        assertTrue(changed);
        assertFalse(state.isActive(island));
        assertEquals(0, state.playerCount(island));
        assertNull(state.currentIsland(player));
        assertFalse(state.activeIslands().contains(island));
    }

    @Test
    void twoPlayersActiveUntilLastLeaves() {
        IslandOccupancyState state = new IslandOccupancyState();
        UUID island = uuid(1);
        UUID p1 = uuid(100);
        UUID p2 = uuid(101);
        state.setPlayerIsland(p1, island);
        boolean changed2 = state.setPlayerIsland(p2, island);
        assertFalse(changed2); // 島已經啟用，加入第二人狀態不變
        assertEquals(2, state.playerCount(island));
        assertTrue(state.isActive(island));

        state.removePlayer(p1);
        assertTrue(state.isActive(island));
        assertEquals(1, state.playerCount(island));

        boolean changedLast = state.removePlayer(p2);
        assertTrue(changedLast);
        assertFalse(state.isActive(island));
        assertEquals(0, state.playerCount(island));
    }

    @Test
    void movingBetweenIslandsUpdatesBoth() {
        IslandOccupancyState state = new IslandOccupancyState();
        UUID islandA = uuid(1);
        UUID islandB = uuid(2);
        UUID player = uuid(100);
        state.setPlayerIsland(player, islandA);
        assertTrue(state.isActive(islandA));
        assertFalse(state.isActive(islandB));

        boolean changed = state.setPlayerIsland(player, islandB);
        assertTrue(changed);
        assertFalse(state.isActive(islandA));
        assertTrue(state.isActive(islandB));
        assertEquals(islandB, state.currentIsland(player));
    }

    @Test
    void nullOrNonIslandRemovesPlayer() {
        IslandOccupancyState state = new IslandOccupancyState();
        UUID island = uuid(1);
        UUID player = uuid(100);
        state.setPlayerIsland(player, island);
        boolean changed = state.setPlayerIsland(player, null);
        assertTrue(changed);
        assertFalse(state.isActive(island));
        assertNull(state.currentIsland(player));
    }

    @Test
    void shouldRunUnattendedWhenPauseDisabled() {
        // 暫停系統關閉時，不論島是否啟用都應運作
        assertTrue(IslandOccupancyState.shouldRunUnattended(false, false));
        assertTrue(IslandOccupancyState.shouldRunUnattended(false, true));
    }

    @Test
    void shouldRunUnattendedWhenPauseEnabled() {
        // 暫停系統啟用時，僅在島啟用（有人）才運作
        assertFalse(IslandOccupancyState.shouldRunUnattended(true, false));
        assertTrue(IslandOccupancyState.shouldRunUnattended(true, true));
    }

    @Test
    void clearResetsEverything() {
        IslandOccupancyState state = new IslandOccupancyState();
        UUID island = uuid(1);
        UUID player = uuid(100);
        state.setPlayerIsland(player, island);
        assertTrue(state.isActive(island));
        state.clear();
        assertFalse(state.isActive(island));
        assertNull(state.currentIsland(player));
        assertTrue(state.activeIslands().isEmpty());
    }

    @Test
    void duplicateSetDoesNotChangeState() {
        IslandOccupancyState state = new IslandOccupancyState();
        UUID island = uuid(1);
        UUID player = uuid(100);
        state.setPlayerIsland(player, island);
        boolean changed = state.setPlayerIsland(player, island);
        assertFalse(changed);
        assertEquals(1, state.playerCount(island));
    }
}
