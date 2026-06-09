package com.strawserver.strawskyblock.world;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

/**
 * 負責於啟動時建立 / 載入空島專用世界。
 */
public class WorldManager {

    private final StrawSkyBlockPlugin plugin;
    private final VoidChunkGenerator generator;

    public WorldManager(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
        this.generator = new VoidChunkGenerator(plugin.getConfigManager().getIslandY());
    }

    public ChunkGenerator getGenerator() {
        return generator;
    }

    /**
     * 確保空島世界存在，必須在主執行緒呼叫。
     */
    public World loadOrCreateIslandWorld() {
        String worldName = plugin.getConfigManager().getIslandWorld();
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            applyWorldSettings(existing);
            return existing;
        }
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        if (plugin.getConfigManager().isVoidGenerator()) {
            creator.generator(generator);
        }
        World world = creator.createWorld();
        if (world != null) {
            applyWorldSettings(world);
            plugin.getLogger().info("空島世界已載入：" + worldName);
        } else {
            plugin.getLogger().severe("無法建立空島世界：" + worldName);
        }
        return world;
    }

    /**
     * 確保下界虛空世界存在（v1.0.28，下界獨立空島模式），必須在主執行緒呼叫。
     * 未啟用時回傳 null。
     */
    public World loadOrCreateNetherWorld() {
        if (!plugin.getConfigManager().isNetherEnabled()) {
            return null;
        }
        String worldName = plugin.getConfigManager().getNetherWorld();
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            applyWorldSettings(existing);
            return existing;
        }
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NETHER);
        if (plugin.getConfigManager().isVoidGenerator()) {
            creator.generator(generator);
        }
        World world = creator.createWorld();
        if (world != null) {
            applyWorldSettings(world);
            plugin.getLogger().info("下界空島世界已載入：" + worldName);
        } else {
            plugin.getLogger().severe("無法建立下界空島世界：" + worldName);
        }
        return world;
    }

    public World getNetherWorld() {
        if (!plugin.getConfigManager().isNetherEnabled()) {
            return null;
        }
        return Bukkit.getWorld(plugin.getConfigManager().getNetherWorld());
    }

    /**
     * 將伺服器原版世界（主世界/地獄/終界）設定為純虛空（v1.0.29），必須在主執行緒呼叫。
     *
     * <p>主世界（大廳）僅於出生點放置一個草方塊供站立，不帶任何 SkyBlock 功能；
     * 地獄／終界則為完全空的虛空。世界本身的虛空生成由 {@code getDefaultWorldGenerator}
     * 搭配 bukkit.yml 指定，本方法負責出生點與大廳草方塊。</p>
     */
    public void setupVanillaVoidWorlds() {
        if (!plugin.getConfigManager().isVanillaVoidEnabled()) {
            return;
        }
        World overworld = Bukkit.getWorld(plugin.getConfigManager().getVanillaOverworld());
        if (overworld != null) {
            setupLobbyWorld(overworld);
        }
        for (String name : plugin.getConfigManager().getVanillaVoidWorlds()) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                applyVoidWorldSettings(world);
                plugin.getLogger().info("原版世界已淨空為虛空：" + name);
            }
        }
    }

    /**
     * 主世界（大廳）：放置單一草方塊、設定出生點於其上、保持出生區塊載入，並關閉生物生成等。
     */
    private void setupLobbyWorld(World world) {
        int y = plugin.getConfigManager().getIslandY();
        if (plugin.getConfigManager().isVanillaOverworldGrass()) {
            world.getBlockAt(0, y, 0).setType(Material.GRASS_BLOCK, false);
        }
        world.setSpawnLocation(new Location(world, 0.5, y + 1, 0.5));
        applyVoidWorldSettings(world);
        world.setKeepSpawnInMemory(true);
        world.getChunkAt(0, 0);
        plugin.getLogger().info("主世界已設定為大廳虛空：" + world.getName());
    }

    /**
     * 淨空世界的共用設定：關閉生物生成、火焰蔓延與天氣，保持空蕩。
     */
    private void applyVoidWorldSettings(World world) {
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setSpawnFlags(false, false);
    }

    private void applyWorldSettings(World world) {
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setSpawnFlags(false, true);
        // 虛空空島世界在跨維度傳送時會先進入世界出生區塊；必須保持載入，否則客戶端易卡在「載入地形」。
        world.setKeepSpawnInMemory(true);
        ensureSafeWorldSpawn(world);
        ensureSpawnChunksLoaded(world);
    }

    /**
     * 在世界出生點下方鋪一小塊基岩平台，避免任何被送往世界出生點的玩家
     * 直接掉入虛空，並將出生點設定為固定座標。
     */
    private void ensureSafeWorldSpawn(World world) {
        int y = plugin.getConfigManager().getIslandY();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(dx, y - 1, dz).setType(Material.BEDROCK, false);
            }
        }
        world.setSpawnLocation(new Location(world, 0.5, y, 0.5));
    }

    /**
     * 啟動時預載世界出生區塊，避免首位跨世界進入的玩家在維度切換時等待虛空出生點生成。
     */
    private void ensureSpawnChunksLoaded(World world) {
        Location spawn = world.getSpawnLocation();
        world.getChunkAt(spawn);
    }

    public World getIslandWorld() {
        return Bukkit.getWorld(plugin.getConfigManager().getIslandWorld());
    }

    /**
     * Bukkit 於世界初次建立時會呼叫此方法以取得自訂生成器。
     */
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return generator;
    }
}
