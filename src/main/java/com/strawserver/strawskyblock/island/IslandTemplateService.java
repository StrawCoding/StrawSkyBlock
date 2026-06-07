package com.strawserver.strawskyblock.island;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 建立 / 重置初始島嶼模板（內建程式化模板）。
 * 所有方塊操作皆於主執行緒進行；重置時以批次方式分散負載避免卡服。
 */
public class IslandTemplateService {

    private final StrawSkyBlockPlugin plugin;

    public IslandTemplateService(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 在指定中心貼上預設島嶼，回傳玩家 Home 位置。必須於主執行緒呼叫。
     */
    public Location pasteDefaultIsland(World world, int centerX, int islandY, int centerZ) {
        // 5x5 草地平台（含兩層泥土基底）
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                setBlock(world, centerX + dx, islandY - 2, centerZ + dz, Material.DIRT);
                setBlock(world, centerX + dx, islandY - 1, centerZ + dz, Material.DIRT);
                setBlock(world, centerX + dx, islandY, centerZ + dz, Material.GRASS_BLOCK);
            }
        }

        // 橡木樹（角落）
        int treeX = centerX - 2;
        int treeZ = centerZ - 2;
        for (int dy = 1; dy <= 4; dy++) {
            setBlock(world, treeX, islandY + dy, treeZ, Material.OAK_LOG);
        }
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                setBlock(world, treeX + lx, islandY + 4, treeZ + lz, Material.OAK_LEAVES);
                setBlock(world, treeX + lx, islandY + 5, treeZ + lz, Material.OAK_LEAVES);
            }
        }
        setBlock(world, treeX, islandY + 6, treeZ, Material.OAK_LEAVES);

        // 水與岩漿改以桶的形式放入初始箱子（見 fillStarterChest），島上不直接放置液體源。

        // 初始箱子
        Block chestBlock = world.getBlockAt(centerX + 2, islandY + 1, centerZ + 2);
        chestBlock.setType(Material.CHEST);
        BlockState state = chestBlock.getState();
        if (state instanceof Chest chest) {
            fillStarterChest(chest.getBlockInventory());
            chest.update(true, false);
        }

        int offsetX = plugin.getConfigManager().getSpawnOffsetX();
        int offsetY = plugin.getConfigManager().getSpawnOffsetY();
        int offsetZ = plugin.getConfigManager().getSpawnOffsetZ();
        return new Location(world,
                centerX + offsetX + 0.5,
                islandY + offsetY,
                centerZ + offsetZ + 0.5,
                0F, 0F);
    }

    private void fillStarterChest(Inventory inv) {
        inv.addItem(new ItemStack(Material.WATER_BUCKET, 1));
        inv.addItem(new ItemStack(Material.LAVA_BUCKET, 1));
        inv.addItem(new ItemStack(Material.OAK_SAPLING, 1));
        inv.addItem(new ItemStack(Material.BONE_MEAL, 8));
        inv.addItem(new ItemStack(Material.BREAD, 8));
        inv.addItem(new ItemStack(Material.DIRT, 16));
        inv.addItem(new ItemStack(Material.ICE, 2));
        inv.addItem(new ItemStack(Material.TORCH, 8));
    }

    private void setBlock(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    /**
     * 重置島嶼：以批次清除核心建築範圍後重貼模板，完成後執行回呼（主執行緒）。
     */
    public void resetIsland(Island island, Runnable onComplete) {
        World world = plugin.getWorldManager().getIslandWorld();
        if (world == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        final int centerX = island.getCenterX();
        final int centerZ = island.getCenterZ();
        final int islandY = island.getCenterY();
        final int radius = Math.min(island.getSize() / 2, 50);
        final int yMin = Math.max(world.getMinHeight(), islandY - 5);
        final int yMax = Math.min(world.getMaxHeight() - 1, islandY + 80);

        new BukkitRunnable() {
            int x = centerX - radius;

            @Override
            public void run() {
                int columnsThisTick = 0;
                while (x <= centerX + radius && columnsThisTick < 4) {
                    for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                        for (int y = yMin; y <= yMax; y++) {
                            Block block = world.getBlockAt(x, y, z);
                            if (block.getType() != Material.AIR) {
                                block.setType(Material.AIR, false);
                            }
                        }
                    }
                    x++;
                    columnsThisTick++;
                }
                if (x > centerX + radius) {
                    pasteDefaultIsland(world, centerX, islandY, centerZ);
                    cancel();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
