package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.util.IslandTeleportHelper;
import com.strawserver.strawskyblock.util.ServerSpawnResolver;
import com.strawserver.strawskyblock.util.SpawnCommandParser;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Optional;

/**
 * 在空島世界內攔截 HuskHomes 的 /spawn，改以 {@link IslandTeleportHelper} 做 Paper-safe 跨世界傳送。
 *
 * <p>根因：HuskHomes 在 {@code general.teleport_async: false} 時以同步傳送跨出虛空空島世界，
 * 伺服器端實體可已到主世界出生點，但客戶端仍卡在「載入地形」（與 v1.0.4 重生問題同類）。</p>
 */
public class SpawnCommandListener implements Listener {

    public static final String OPERATION = ServerSpawnResolver.OPERATION;

    private final StrawSkyBlockPlugin plugin;

    public SpawnCommandListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!SpawnCommandParser.shouldInterceptSpawnFromIslandWorld(
                plugin.getConfigManager().isSpawnInterceptEnabled(),
                isInIslandWorld(player.getWorld()),
                event.getMessage())) {
            return;
        }

        String permission = plugin.getConfigManager().getSpawnInterceptPermission();
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            return;
        }

        event.setCancelled(true);

        Optional<Location> destination = ServerSpawnResolver.resolve(plugin);
        if (destination.isEmpty()) {
            plugin.getDiagnosticService().reportTeleportFailure(
                    OPERATION, player, null,
                    "找不到有效的伺服器出生點（HuskHomes spawn.yml 與主世界出生點皆不可用）", null);
            plugin.getMessageManager().send(player, "spawn.destination-unavailable");
            return;
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("空島 /spawn 攔截：玩家=" + player.getName()
                    + " 從 " + formatLoc(player.getLocation())
                    + " 至 " + formatLoc(destination.get()));
        }

        IslandTeleportHelper.teleportPlayer(plugin, player, destination.get(),
                "spawn.teleport-success", OPERATION);
    }

    private boolean isInIslandWorld(World world) {
        return world != null && world.getName().equals(plugin.getConfigManager().getIslandWorld());
    }

    private static String formatLoc(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }
        return location.getWorld().getName() + "@"
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
