package com.strawserver.strawskyblock.util;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 解析伺服器出生點：優先讀取 HuskHomes {@code spawn.yml}，否則退回主世界出生點。
 */
public final class ServerSpawnResolver {

    public static final String OPERATION = "spawn-from-island-teleport";
    private static final Logger LOGGER = Logger.getLogger("StrawSkyBlock");

    private ServerSpawnResolver() {
    }

    public static Optional<Location> resolve(StrawSkyBlockPlugin plugin) {
        File spawnFile = resolveSpawnFile(plugin);
        if (spawnFile != null && spawnFile.isFile()) {
            Optional<Location> fromHuskHomes = parseHuskHomesSpawn(spawnFile);
            if (fromHuskHomes.isPresent()) {
                return fromHuskHomes;
            }
            plugin.getLogger().warning("[Spawn] 無法解析 HuskHomes spawn.yml（"
                    + spawnFile.getPath() + "），改使用主世界出生點。");
        }
        return fallbackMainWorldSpawn(plugin);
    }

    @Nullable
    static File resolveSpawnFile(StrawSkyBlockPlugin plugin) {
        String configured = plugin.getConfigManager().getSpawnInterceptHuskHomesFile();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        File path = new File(configured);
        if (path.isAbsolute()) {
            return path;
        }
        File serverRoot = plugin.getServer().getWorldContainer().getParentFile();
        return new File(serverRoot, configured);
    }

    static Optional<Location> parseHuskHomesSpawn(File spawnFile) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(spawnFile);
            if (!yaml.contains("x") || !yaml.contains("y") || !yaml.contains("z")) {
                return Optional.empty();
            }
            double x = yaml.getDouble("x");
            double y = yaml.getDouble("y");
            double z = yaml.getDouble("z");
            float yaw = (float) yaml.getDouble("yaw", 0.0D);
            float pitch = (float) yaml.getDouble("pitch", 0.0D);

            String worldName = yaml.getString("world_name");
            UUID worldUuid = parseUuid(yaml.getString("world_uuid"));
            World world = resolveWorld(worldName, worldUuid);
            if (world == null) {
                return Optional.empty();
            }
            return Optional.of(new Location(world, x, y, z, yaw, pitch));
        } catch (RuntimeException e) {
            LOGGER.warning("[Spawn] 讀取 spawn.yml 失敗：" + e.getMessage());
            return Optional.empty();
        }
    }

    @Nullable
    static World resolveWorld(@Nullable String worldName, @Nullable UUID worldUuid) {
        if (Bukkit.getServer() == null) {
            return null;
        }
        if (worldUuid != null) {
            World byUuid = Bukkit.getWorld(worldUuid);
            if (byUuid != null) {
                return byUuid;
            }
        }
        if (worldName != null && !worldName.isBlank()) {
            World byName = Bukkit.getWorld(worldName);
            if (byName != null) {
                return byName;
            }
        }
        return null;
    }

    static Optional<Location> fallbackMainWorldSpawn(StrawSkyBlockPlugin plugin) {
        if (Bukkit.getWorlds().isEmpty()) {
            return Optional.empty();
        }
        World main = Bukkit.getWorlds().getFirst();
        Location spawn = main.getSpawnLocation();
        if (spawn.getWorld() == null) {
            return Optional.empty();
        }
        return Optional.of(spawn.clone());
    }

    @Nullable
    private static UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
