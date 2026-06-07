package com.strawserver.strawskyblock.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Location 與字串互轉工具（保留供未來序列化使用）。
 */
public final class LocationSerializer {

    private LocationSerializer() {
    }

    public static String serialize(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ";"
                + location.getX() + ";"
                + location.getY() + ";"
                + location.getZ() + ";"
                + location.getYaw() + ";"
                + location.getPitch();
    }

    public static Location deserialize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String[] parts = input.split(";");
        if (parts.length < 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        return new Location(world,
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5]));
    }
}
