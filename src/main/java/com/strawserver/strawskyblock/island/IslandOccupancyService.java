package com.strawserver.strawskyblock.island;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 空島佔用服務：依玩家在場狀況維護每座島的「啟用 / 暫停」狀態。v1.0.39
 *
 * <p>用於「離開空島時馬上暫停空島」：最後一位玩家離開某島後，該島立即視為暫停，
 * 無人值守的插件活動（特別是小機器人挖掘）應停止；任一玩家進入該島即立即恢復。</p>
 *
 * <p>暫停判定僅以「是否有玩家在場」為依據，與管理員 bypass 無關。資料庫 / 島嶼 / 玩家資料皆不刪除，
 * 也不主動載入或卸載區塊，僅停止無人值守活動。</p>
 *
 * <p>純狀態邏輯委派給 {@link IslandOccupancyState}（可單元測試）；本類別負責由玩家位置換算所屬島並對接 Bukkit。</p>
 */
public class IslandOccupancyService {

    private final StrawSkyBlockPlugin plugin;
    private final IslandOccupancyState state = new IslandOccupancyState();

    public IslandOccupancyService(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 暫停系統是否啟用（config: {@code island-pause.enabled}）。
     */
    public boolean isPauseEnabled() {
        return plugin.getConfigManager().isIslandPauseEnabled();
    }

    /**
     * 由座標換算其所屬島嶼 UUID（僅空島世界有效，否則為 {@code null}）。
     */
    private UUID islandUuidAt(Location location) {
        Island island = plugin.getIslandService().getCache().getByLocation(location);
        return island == null ? null : island.getIslandUuid();
    }

    /**
     * 以玩家「指定位置」更新其所屬島（傳送 / 換世界時帶入目的地）。
     */
    public void updatePlayerLocation(UUID playerUuid, Location location) {
        state.setPlayerIsland(playerUuid, islandUuidAt(location));
    }

    /**
     * 以玩家目前位置更新其所屬島。
     */
    public void updatePlayer(Player player) {
        updatePlayerLocation(player.getUniqueId(), player.getLocation());
    }

    /**
     * 玩家離線 / 被踢出：移出所有島（可能使該島轉為暫停）。
     */
    public void removePlayer(UUID playerUuid) {
        state.removePlayer(playerUuid);
    }

    /**
     * 某島是否暫停（暫停系統啟用且該島目前無玩家在場）。
     */
    public boolean isPaused(UUID islandUuid) {
        return isPauseEnabled() && !state.isActive(islandUuid);
    }

    /**
     * 是否應讓某島的無人值守插件活動（例如機器人挖掘）運作。
     *
     * <p>fail-safe：暫停系統未啟用時一律允許；啟用時僅在島上有人才允許。未知島嶼（快取尚未載入）
     * 在啟用時會被視為無人而暫停，不會擲出例外。</p>
     */
    public boolean shouldRunUnattended(UUID islandUuid) {
        return IslandOccupancyState.shouldRunUnattended(isPauseEnabled(), state.isActive(islandUuid));
    }

    public int playerCount(UUID islandUuid) {
        return state.playerCount(islandUuid);
    }

    /**
     * 重新計算所有線上玩家的在場狀態（啟動完成、reload 後呼叫）。必須於主執行緒呼叫。
     */
    public void recomputeAll() {
        state.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            updatePlayer(online);
        }
    }

    IslandOccupancyState getState() {
        return state;
    }
}
