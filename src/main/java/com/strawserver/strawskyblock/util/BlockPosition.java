package com.strawserver.strawskyblock.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * 不含世界引用的方塊座標，作為快取的 key 使用（刷石機鵝卵石採用單一空島世界）。
 */
public record BlockPosition(int x, int y, int z) {

    public static BlockPosition of(Block block) {
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPosition of(Location location) {
        return new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
