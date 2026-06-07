package com.strawserver.strawskyblock.world;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * 虛空世界生成器：不生成任何地形、洞穴、結構或裝飾，島嶼內容完全由插件貼上。
 */
public class VoidChunkGenerator extends ChunkGenerator {

    private final int spawnY;

    public VoidChunkGenerator(int spawnY) {
        this.spawnY = spawnY;
    }

    /**
     * 提供固定出生點，避免伺服器在虛空世界中無限搜尋安全出生位置而造成
     * 玩家傳送時卡在「載入地形」。
     */
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, spawnY, 0.5);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        return worldInfo.getMinHeight();
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // 不生成任何方塊
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // 不生成地表
    }
}
