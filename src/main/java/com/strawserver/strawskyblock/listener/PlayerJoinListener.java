package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.island.IslandMember;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 玩家加入時，同步快取中的玩家名稱（避免改名後顯示舊名）。
 */
public class PlayerJoinListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public PlayerJoinListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());
        if (island == null) {
            return;
        }
        IslandMember member = island.getMember(player.getUniqueId());
        if (member != null && !member.getPlayerName().equals(player.getName())) {
            member.setPlayerName(player.getName());
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getIslandService().getRepository().addOrUpdateMember(island.getIslandUuid(), member);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
