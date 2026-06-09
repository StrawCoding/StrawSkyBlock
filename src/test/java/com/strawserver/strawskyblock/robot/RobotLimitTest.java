package com.strawserver.strawskyblock.robot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
