package com.strawserver.strawskyblock.placeholder;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.island.Island;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI 變數提供者。
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final StrawSkyBlockPlugin plugin;

    public PlaceholderHook(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "strawskyblock";
    }

    @Override
    public @NotNull String getAuthor() {
        return "StrawServer";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());
        switch (params.toLowerCase()) {
            case "has_island":
                return island != null ? "true" : "false";
            case "owner":
                return island != null ? island.getOwnerName() : "";
            case "members":
                return island != null ? String.valueOf(island.getMembers().size()) : "0";
            default:
                return null;
        }
    }
}
