package com.strawserver.strawskyblock.util;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;

/**
 * 空島傳送輔助：在 Paper 1.21 上跨世界傳送至虛空空島世界時，
 * 必須先確保目標區塊（以及跨維度時的世界出生區塊）已載入，並處理傳送結果。
 */
public final class IslandTeleportHelper {

    /** 以傳送點為中心預載的區塊半徑（含中心 = (2r+1)^2 個區塊）。 */
    public static final int DEFAULT_CHUNK_RADIUS = 1;

    private IslandTeleportHelper() {
    }

    public static int chunkX(int blockX) {
        return blockX >> 4;
    }

    public static int chunkZ(int blockZ) {
        return blockZ >> 4;
    }

    /**
     * 於主執行緒同步載入以 {@code center} 為中心的區塊方格。
     */
    public static void ensureChunksLoaded(World world, Location center, int radiusChunks) {
        if (world == null || center == null) {
            return;
        }
        int centerChunkX = chunkX(center.getBlockX());
        int centerChunkZ = chunkZ(center.getBlockZ());
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
            }
        }
    }

    /**
     * 跨世界傳送時，客戶端會先進入目標世界的出生區塊；虛空世界若出生區塊未載入，
     * 容易卡在「載入地形」。此方法一併預載目的地與（必要時的）世界出生點周圍區塊。
     */
    public static void prepareForTeleport(Player player, Location destination, int radiusChunks) {
        if (destination == null || destination.getWorld() == null) {
            return;
        }
        World destWorld = destination.getWorld();
        ensureChunksLoaded(destWorld, destination, radiusChunks);

        World currentWorld = player.getWorld();
        if (!destWorld.equals(currentWorld)) {
            ensureChunksLoaded(destWorld, destWorld.getSpawnLocation(), radiusChunks);
        }
    }

    /**
     * 將玩家傳送至空島位置。成功時可選擇發送訊息；失敗時記錄日誌並通知玩家。
     *
     * @return 是否已發起傳送（目的地無效時回傳 false）
     */
    public static boolean teleportPlayer(StrawSkyBlockPlugin plugin,
                                         Player player,
                                         Location destination,
                                         @Nullable String successMessageKey) {
        if (destination == null || destination.getWorld() == null) {
            plugin.getLogger().warning("空島傳送失敗：目的地無效（玩家=" + player.getName() + "）");
            plugin.getMessageManager().send(player, "island.teleport-failed");
            return false;
        }

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> teleportPlayer(plugin, player, destination, successMessageKey));
            return true;
        }

        prepareForTeleport(player, destination, DEFAULT_CHUNK_RADIUS);

        if (plugin.getConfigManager().isDebug()) {
            Location dest = destination;
            plugin.getLogger().info("空島傳送：玩家=" + player.getName()
                    + " 從 " + formatWorldLoc(player.getLocation())
                    + " 至 " + formatWorldLoc(dest));
        }

        player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenComplete((success, throwable) -> {
                    Runnable finish = () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (throwable != null) {
                            plugin.getLogger().log(Level.WARNING,
                                    "空島傳送例外：玩家=" + player.getName()
                                            + " 目的地=" + formatWorldLoc(destination),
                                    throwable);
                            plugin.getMessageManager().send(player, "island.teleport-failed");
                            return;
                        }
                        if (!Boolean.TRUE.equals(success)) {
                            plugin.getLogger().warning("空島傳送被拒絕：玩家=" + player.getName()
                                    + " 目的地=" + formatWorldLoc(destination)
                                    + " 目前位置=" + formatWorldLoc(player.getLocation()));
                            plugin.getMessageManager().send(player, "island.teleport-failed");
                            return;
                        }
                        if (successMessageKey != null) {
                            plugin.getMessageManager().send(player, successMessageKey);
                        }
                    };
                    if (Bukkit.isPrimaryThread()) {
                        finish.run();
                    } else {
                        Bukkit.getScheduler().runTask(plugin, finish);
                    }
                });
        return true;
    }

    private static String formatWorldLoc(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }
        return location.getWorld().getName() + "@"
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
