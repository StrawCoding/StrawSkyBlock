package com.strawserver.strawskyblock.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSpawnResolverTest {

    @TempDir
    File tempDir;

    @Test
    void parseHuskHomesSpawnReturnsEmptyWhenWorldMissing() throws IOException {
        File spawnFile = new File(tempDir, "spawn.yml");
        Files.writeString(spawnFile.toPath(), """
                x: -8.0
                y: 64.0
                z: 24.0
                yaw: 90.0
                pitch: 0.0
                world_name: world
                world_uuid: 6408fc8e-a36e-4702-9484-2176f21b048b
                """);

        Optional<?> result = ServerSpawnResolver.parseHuskHomesSpawn(spawnFile);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseHuskHomesSpawnReturnsEmptyWhenCoordinatesMissing() throws IOException {
        File spawnFile = new File(tempDir, "spawn.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world_name", "world");
        yaml.save(spawnFile);

        assertFalse(ServerSpawnResolver.parseHuskHomesSpawn(spawnFile).isPresent());
    }

    @Test
    void parseHuskHomesSpawnReturnsEmptyForInvalidFile() {
        File missing = new File(tempDir, "missing.yml");
        assertFalse(ServerSpawnResolver.parseHuskHomesSpawn(missing).isPresent());
    }
}
