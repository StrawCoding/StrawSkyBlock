package com.strawserver.strawskyblock.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnCommandParserTest {

    @Test
    void acceptsBareSpawnCommand() {
        assertTrue(SpawnCommandParser.isBareSpawnCommand("/spawn"));
        assertTrue(SpawnCommandParser.isBareSpawnCommand("  /spawn  "));
    }

    @Test
    void acceptsNamespacedSpawnCommand() {
        assertTrue(SpawnCommandParser.isBareSpawnCommand("/huskhomes:spawn"));
        assertTrue(SpawnCommandParser.isBareSpawnCommand("/HuskHomes:spawn"));
    }

    @Test
    void rejectsSpawnWithArguments() {
        assertFalse(SpawnCommandParser.isBareSpawnCommand("/spawn player"));
        assertFalse(SpawnCommandParser.isBareSpawnCommand("/spawn x y z"));
    }

    @Test
    void rejectsUnrelatedCommands() {
        assertFalse(SpawnCommandParser.isBareSpawnCommand("/setspawn"));
        assertFalse(SpawnCommandParser.isBareSpawnCommand("/home"));
        assertFalse(SpawnCommandParser.isBareSpawnCommand("/is home"));
        assertFalse(SpawnCommandParser.isBareSpawnCommand("spawn"));
        assertFalse(SpawnCommandParser.isBareSpawnCommand(""));
        assertFalse(SpawnCommandParser.isBareSpawnCommand(null));
    }

    @Test
    void shouldInterceptOnlyInIslandWorldWhenEnabled() {
        assertTrue(SpawnCommandParser.shouldInterceptSpawnFromIslandWorld(true, true, "/spawn"));
        assertFalse(SpawnCommandParser.shouldInterceptSpawnFromIslandWorld(false, true, "/spawn"));
        assertFalse(SpawnCommandParser.shouldInterceptSpawnFromIslandWorld(true, false, "/spawn"));
        assertFalse(SpawnCommandParser.shouldInterceptSpawnFromIslandWorld(true, true, "/home"));
    }
}
