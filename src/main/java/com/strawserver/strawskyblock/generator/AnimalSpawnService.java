package com.strawserver.strawskyblock.generator;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.util.WeightedRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 玩家挖刷石機鵝卵石時，依機率在其島嶼安全位置生成動物。
 */
public class AnimalSpawnService {

    private final StrawSkyBlockPlugin plugin;
    private WeightedRandom<EntityType> animalTable = new WeightedRandom<>();
    private final ConcurrentHashMap<UUID, Long> islandCooldown = new ConcurrentHashMap<>();

    public AnimalSpawnService(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        WeightedRandom<EntityType> built = new WeightedRandom<>();
        Map<String, Integer> weights = plugin.getConfigManager().getAnimalWeights();
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            EntityType type = matchType(entry.getKey());
            if (type == null) {
                plugin.getLogger().warning("[AnimalSpawn] 未知動物類型：" + entry.getKey() + "，已略過。");
                continue;
            }
            built.add(type, entry.getValue());
        }
        this.animalTable = built;
    }

    private EntityType matchType(String key) {
        try {
            return EntityType.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public EntityType rollAnimalType() {
        return animalTable.roll();
    }

    /**
     * 嘗試生成動物。回傳是否成功生成（供統計使用）。必須於主執行緒呼叫。
     */
    public boolean trySpawnAnimal(Player player, Island island, Location brokenBlockLocation) {
        if (!plugin.getConfigManager().isAnimalSpawnEnabled()) {
            return false;
        }
        double chance = plugin.getConfigManager().getAnimalChance();
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return false;
        }
        // 冷卻
        int cooldownSeconds = plugin.getConfigManager().getAnimalCooldownSeconds();
        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            Long last = islandCooldown.get(island.getIslandUuid());
            if (last != null && now - last < cooldownSeconds * 1000L) {
                return false;
            }
        }
        // 數量上限
        int max = plugin.getConfigManager().getMaxPassiveMobsPerIsland();
        if (max >= 0 && countPassiveMobs(island) >= max) {
            return false;
        }

        Optional<Location> spawnLoc = findSafeSpawnLocation(island, brokenBlockLocation);
        if (spawnLoc.isEmpty()) {
            return false;
        }
        EntityType type = rollAnimalType();
        if (type == null) {
            return false;
        }

        World world = spawnLoc.get().getWorld();
        if (world == null) {
            return false;
        }
        world.spawnEntity(spawnLoc.get(), type);
        islandCooldown.put(island.getIslandUuid(), System.currentTimeMillis());

        plugin.getMessageManager().send(player, "generator.animal-spawned",
                MessageManager.placeholders("animal", type.name()));
        return true;
    }

    private int countPassiveMobs(Island island) {
        World world = plugin.getWorldManager().getIslandWorld();
        if (world == null) {
            return 0;
        }
        Location center = new Location(world, island.getCenterX(), island.getCenterY(), island.getCenterZ());
        int half = island.getSize() / 2;
        int count = 0;
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, half, world.getMaxHeight(), half)) {
            if (entity instanceof Animals && island.contains(entity.getLocation())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 在玩家附近 3~8 格尋找安全生成點：下方為實心、本身與上方為空氣、不在液體或虛空。
     */
    public Optional<Location> findSafeSpawnLocation(Island island, Location center) {
        World world = center.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = 3 + random.nextDouble(5); // 3~8
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

            if (!island.contains(x, z)) {
                continue;
            }
            // 由上而下找地面
            for (int y = center.getBlockY() + 4; y >= center.getBlockY() - 4; y--) {
                if (y <= world.getMinHeight()) {
                    break;
                }
                Material ground = world.getBlockAt(x, y - 1, z).getType();
                Material feet = world.getBlockAt(x, y, z).getType();
                Material head = world.getBlockAt(x, y + 1, z).getType();
                if (ground.isSolid()
                        && feet == Material.AIR
                        && head == Material.AIR
                        && !isLiquid(ground)) {
                    return Optional.of(new Location(world, x + 0.5, y, z + 0.5));
                }
            }
        }
        return Optional.empty();
    }

    private boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }
}
