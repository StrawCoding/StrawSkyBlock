package com.strawserver.strawskyblock.island;

import java.util.UUID;

/**
 * 島嶼成員資料。
 */
public class IslandMember {

    private final UUID playerUuid;
    private String playerName;
    private IslandRole role;

    public IslandMember(UUID playerUuid, String playerName, IslandRole role) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.role = role;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public IslandRole getRole() {
        return role;
    }

    public void setRole(IslandRole role) {
        this.role = role;
    }
}
