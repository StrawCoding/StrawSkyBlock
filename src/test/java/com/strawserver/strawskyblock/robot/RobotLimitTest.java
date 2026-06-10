package com.strawserver.strawskyblock.robot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobotLimitTest {

    @Test
    void noPermissionUsesDefault() {
        assertEquals(5, RobotLimit.resolve(5, Set.of()));
        assertEquals(5, RobotLimit.resolve(5, null));
    }

    @Test
    void unrelatedPermissionsUseDefault() {
        assertEquals(5, RobotLimit.resolve(5,
                List.of("strawskyblock.user.robot", "essentials.fly")));
    }

    @Test
    void takesMaxNumericNode() {
        assertEquals(10, RobotLimit.resolve(5,
                List.of("strawskyblock.robot.limit.3", "strawskyblock.robot.limit.10")));
    }

    @Test
    void permissionCanLowerBelowDefault() {
        assertEquals(2, RobotLimit.resolve(5, List.of("strawskyblock.robot.limit.2")));
    }

    @Test
    void wildcardOrInvalidNodesIgnored() {
        assertEquals(5, RobotLimit.resolve(5,
                List.of("strawskyblock.robot.limit.*", "strawskyblock.robot.limit.abc")));
    }

    @Test
    void caseInsensitivePrefix() {
        assertEquals(7, RobotLimit.resolve(5, List.of("STRAWSKYBLOCK.ROBOT.LIMIT.7")));
    }

    @Test
    void zeroDefaultClampedNonNegative() {
        assertEquals(0, RobotLimit.resolve(-3, Set.of()));
    }

    @Test
    void allowsPlacementUnderLimit() {
        assertTrue(RobotLimit.allowsPlacement(2, 5, false));
    }

    @Test
    void blocksPlacementAtLimit() {
        assertFalse(RobotLimit.allowsPlacement(5, 5, false));
    }

    @Test
    void unlimitedWhenLimitZeroOrNegative() {
        assertTrue(RobotLimit.allowsPlacement(99, 0, false));
        assertTrue(RobotLimit.allowsPlacement(99, -1, false));
    }

    @Test
    void bypassAllowsPlacementBeyondLimit() {
        // 管理員繞過：已達或超過上限仍可放置。
        assertTrue(RobotLimit.allowsPlacement(5, 5, true));
        assertTrue(RobotLimit.allowsPlacement(999, 5, true));
    }
}
