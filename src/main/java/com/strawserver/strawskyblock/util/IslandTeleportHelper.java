package com.strawserver.strawskyblock.util;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 空島傳送輔助：在 Paper 1.21 上跨世界傳送至虛空空島世界時，
 * 必須先確保目標區塊（以及跨維度時的世界出生區塊）已載入，並處理傳送結果。
 *
 * <p>核心問題（v1.0.8 修正）：玩家「傳送到空島」（主世界 → straw_skyblock_world）時，
 * 伺服器端 {@code teleportAsync} 常回報成功（實體已在落點、onGround=1），但客戶端仍卡在
 * 「載入地形」。原因是跨維度切換時，客戶端需先完成維度交握、再接收其所在區塊封包才會離開
 * 載入畫面；若落點區塊在傳送瞬間未持有票證、或座標／區塊封包與維度交握發生競態，客戶端便不會
 * 收到可結束載入畫面的封包，於是無聲卡住（伺服器端毫無錯誤可記錄）。</p>
 *
 * <p>修正策略（治本、非隨意嘗試）：</p>
 * <ol>
 *   <li><b>區塊票證</b>：跨世界傳送期間於落點加上 plugin chunk ticket，確保落點區塊在整個
 *       傳送 + 驗證視窗內保持載入且可送達客戶端，而非僅 {@code getChunkAt} 後可能被卸載。</li>
 *   <li><b>延遲重新同步</b>：跨世界進入空島世界後，延遲數 tick 對相同座標再次發起一次
 *       {@code teleportAsync}，強制伺服器重送座標與區塊封包，使卡在載入畫面的客戶端脫離。</li>
 *   <li><b>成功後驗證</b>：即使 {@code teleportAsync} 回報成功，仍於短暫延遲後檢查伺服器端
 *       狀態（世界、座標、落點區塊是否載入、落點下方是否有可站立方塊）。若仍為可疑狀態，
 *       輸出一筆繁體中文錯誤診斷區塊，避免「成功卻無聲卡住」時毫無記錄可查。</li>
 * </ol>
 */
public final class IslandTeleportHelper {

    /** 以傳送點為中心預載的區塊半徑（含中心 = (2r+1)^2 個區塊）。 */
    public static final int DEFAULT_CHUNK_RADIUS = 1;

    /** 驗證時判定「玩家仍在落點」的水平距離平方門檻（超過視為玩家已自行移動，屬正常）。 */
    public static final double NEAR_DESTINATION_DISTANCE_SQUARED = 48.0D * 48.0D;

    /** 驗證時往落點下方探測可站立方塊的最大深度（格）。 */
    public static final int GROUND_PROBE_DEPTH = 4;

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

    /** 預設 operation 名稱（未指定來源情境時）。 */
    public static final String DEFAULT_OPERATION = "island-teleport";

    /**
     * 將玩家傳送至空島位置。成功時可選擇發送訊息；失敗時記錄日誌並通知玩家。
     *
     * @return 是否已發起傳送（目的地無效時回傳 false）
     */
    public static boolean teleportPlayer(StrawSkyBlockPlugin plugin,
                                         Player player,
                                         Location destination,
                                         @Nullable String successMessageKey) {
        return teleportPlayer(plugin, player, destination, successMessageKey, DEFAULT_OPERATION);
    }

    /**
     * 同 {@link #teleportPlayer(StrawSkyBlockPlugin, Player, Location, String)}，但可指定
     * {@code operation} 名稱，使失敗時的錯誤診斷區塊能標示是哪一個流程出問題
     * （例如 respawn-home-teleport、island-home-teleport）。
     *
     * @return 是否已發起傳送（目的地無效時回傳 false）
     */
    public static boolean teleportPlayer(StrawSkyBlockPlugin plugin,
                                         Player player,
                                         Location destination,
                                         @Nullable String successMessageKey,
                                         String operation) {
        if (destination == null || destination.getWorld() == null) {
            plugin.getDiagnosticService().reportTeleportFailure(operation, player, destination,
                    "目的地無效（destination 或其所在世界為 null）", null);
            plugin.getMessageManager().send(player, "island.teleport-failed");
            return false;
        }

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> teleportPlayer(plugin, player, destination, successMessageKey, operation));
            return true;
        }

        prepareForTeleport(player, destination, DEFAULT_CHUNK_RADIUS);

        final World destWorld = destination.getWorld();
        final boolean crossWorld = !destWorld.equals(player.getWorld());
        // 跨世界進入時於落點加上區塊票證，確保整個傳送 + 重新同步 + 驗證視窗內區塊保持載入。
        final boolean ticketHeld = crossWorld && acquireChunkTicket(plugin, destWorld, destination);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("空島傳送：玩家=" + player.getName()
                    + " 從 " + formatWorldLoc(player.getLocation())
                    + " 至 " + formatWorldLoc(destination)
                    + "（跨世界=" + crossWorld + "）");
        }

        try {
            player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                    .whenComplete((success, throwable) -> {
                        Runnable finish = () -> {
                            if (!player.isOnline()) {
                                releaseChunkTicket(plugin, destWorld, destination, ticketHeld);
                                return;
                            }
                            if (throwable != null) {
                                plugin.getDiagnosticService().reportTeleportFailure(operation, player,
                                        destination, "teleportAsync 以例外結束（completed exceptionally）",
                                        throwable);
                                plugin.getMessageManager().send(player, "island.teleport-failed");
                                releaseChunkTicket(plugin, destWorld, destination, ticketHeld);
                                return;
                            }
                            if (!Boolean.TRUE.equals(success)) {
                                plugin.getDiagnosticService().reportTeleportFailure(operation, player,
                                        destination, "teleportAsync 回傳 false（傳送被拒絕，可能被其他插件取消或目標區塊不可用）",
                                        null);
                                plugin.getMessageManager().send(player, "island.teleport-failed");
                                releaseChunkTicket(plugin, destWorld, destination, ticketHeld);
                                return;
                            }
                            if (successMessageKey != null) {
                                plugin.getMessageManager().send(player, successMessageKey);
                            }
                            // 回報成功，但客戶端仍可能卡在「載入地形」：安排重新同步與成功後驗證。
                            scheduleClientSyncSafeguard(plugin, player, destination, operation,
                                    crossWorld, ticketHeld);
                        };
                        if (Bukkit.isPrimaryThread()) {
                            finish.run();
                        } else {
                            Bukkit.getScheduler().runTask(plugin, finish);
                        }
                    });
        } catch (RuntimeException e) {
            plugin.getDiagnosticService().reportTeleportFailure(operation, player, destination,
                    "發起 teleportAsync 時拋出例外", e);
            plugin.getMessageManager().send(player, "island.teleport-failed");
            releaseChunkTicket(plugin, destWorld, destination, ticketHeld);
            return false;
        }
        return true;
    }

    /**
     * 成功後保護機制：跨世界進入空島世界時，延遲對相同座標重新同步一次（強制重送座標／區塊封包），
     * 再於驗證延遲後檢查伺服器端狀態；若仍為可疑狀態則輸出診斷並提示玩家。
     */
    private static void scheduleClientSyncSafeguard(StrawSkyBlockPlugin plugin,
                                                    Player player,
                                                    Location destination,
                                                    String operation,
                                                    boolean crossWorld,
                                                    boolean ticketHeld) {
        long resyncDelay = Math.max(0L, plugin.getConfigManager().getTeleportResyncDelayTicks());
        long verifyDelay = Math.max(1L, plugin.getConfigManager().getTeleportVerifyDelayTicks());
        boolean resyncEnabled = crossWorld && plugin.getConfigManager().isTeleportResyncEnabled();

        Runnable afterResync = () -> Bukkit.getScheduler().runTaskLater(plugin,
                () -> {
                    try {
                        verifyAndReport(plugin, player, destination, operation);
                    } finally {
                        releaseChunkTicket(plugin, destination.getWorld(), destination, ticketHeld);
                    }
                }, verifyDelay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && resyncEnabled
                    && destination.getWorld() != null
                    && destination.getWorld().equals(player.getWorld())) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("空島傳送重新同步：玩家=" + player.getName()
                            + " 於 " + formatWorldLoc(destination) + " 重送座標／區塊封包");
                }
                try {
                    player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                } catch (RuntimeException e) {
                    plugin.getDiagnosticService().reportTeleportFailure(operation, player, destination,
                            "重新同步傳送時拋出例外", e);
                }
            }
            afterResync.run();
        }, resyncDelay);
    }

    /**
     * 成功後驗證：比對伺服器端實際狀態與預期落點，將判定委派給可單元測試的純邏輯
     * {@link #evaluatePostTeleport}；若可疑則輸出診斷並提示玩家。
     */
    private static void verifyAndReport(StrawSkyBlockPlugin plugin,
                                        Player player,
                                        Location destination,
                                        String operation) {
        boolean online = player.isOnline();
        World destWorld = destination.getWorld();
        Location current = online ? player.getLocation() : null;

        boolean sameWorld = online && current != null && current.getWorld() != null
                && current.getWorld().equals(destWorld);
        boolean nearDestination = sameWorld && current.distanceSquared(destination)
                <= NEAR_DESTINATION_DISTANCE_SQUARED;
        boolean chunkLoaded = destWorld != null
                && destWorld.isChunkLoaded(chunkX(destination.getBlockX()), chunkZ(destination.getBlockZ()));
        boolean groundBelow = hasGroundBelow(destWorld, destination, GROUND_PROBE_DEPTH);

        PostTeleportVerdict verdict =
                evaluatePostTeleport(online, sameWorld, nearDestination, chunkLoaded, groundBelow);
        if (!verdict.suspicious()) {
            return;
        }

        plugin.getDiagnosticService().reportTeleportFailure(operation, player, destination,
                "傳送回報成功，但成功後驗證偵測到可疑狀態：" + verdict.reason(), null);
        if (online) {
            plugin.getMessageManager().send(player, "island.teleport-failed");
        }
    }

    /**
     * 純粹的成功後狀態判定邏輯，不依賴 Bukkit，方便單元測試。
     *
     * <p>判定原則（盡量降低誤報）：</p>
     * <ul>
     *   <li>玩家已離線 → 不視為可疑（無從驗證，且可能只是正常登出）。</li>
     *   <li>不在目的地世界 → 可疑（傳送回報成功卻未真正換到目標世界，常見於跨維度同步失敗）。</li>
     *   <li>已明顯離開落點（同世界但距離較遠）→ 視為正常（玩家能移動代表未卡住）。</li>
     *   <li>仍停在落點，但落點區塊未載入或下方無可站立方塊 → 可疑（客戶端可能持續卡在
     *       載入地形，或玩家有掉入虛空風險）。</li>
     * </ul>
     *
     * @param online         驗證時玩家是否仍在線
     * @param sameWorld      玩家是否位於目的地世界
     * @param nearDestination 玩家是否仍停在落點附近（未自行移動離開）
     * @param chunkLoaded    落點區塊是否仍保持載入
     * @param groundBelow    落點下方是否有可站立方塊
     */
    public static PostTeleportVerdict evaluatePostTeleport(boolean online,
                                                           boolean sameWorld,
                                                           boolean nearDestination,
                                                           boolean chunkLoaded,
                                                           boolean groundBelow) {
        if (!online) {
            return PostTeleportVerdict.ok();
        }
        if (!sameWorld) {
            return PostTeleportVerdict.suspicious(
                    "玩家不在目的地世界（疑似跨維度同步失敗，客戶端可能仍卡在載入地形）");
        }
        if (!nearDestination) {
            // 玩家能離開落點代表客戶端已正常接收地形，視為成功。
            return PostTeleportVerdict.ok();
        }
        StringBuilder reason = new StringBuilder();
        if (!chunkLoaded) {
            reason.append("落點區塊未保持載入（客戶端可能持續卡在載入地形）");
        }
        if (!groundBelow) {
            if (reason.length() > 0) {
                reason.append("；");
            }
            reason.append("落點下方無可站立方塊（玩家可能掉入虛空）");
        }
        if (reason.length() == 0) {
            return PostTeleportVerdict.ok();
        }
        return PostTeleportVerdict.suspicious(reason.toString());
    }

    private static boolean hasGroundBelow(World world, Location destination, int depth) {
        if (world == null || destination == null) {
            return false;
        }
        int x = destination.getBlockX();
        int z = destination.getBlockZ();
        int startY = destination.getBlockY();
        int minY = Math.max(world.getMinHeight(), startY - Math.max(1, depth));
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type.isSolid()) {
                return true;
            }
        }
        return false;
    }

    private static boolean acquireChunkTicket(StrawSkyBlockPlugin plugin, World world, Location destination) {
        if (!plugin.getConfigManager().isTeleportChunkTicket() || world == null || destination == null) {
            return false;
        }
        try {
            return world.addPluginChunkTicket(
                    chunkX(destination.getBlockX()), chunkZ(destination.getBlockZ()), plugin);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void releaseChunkTicket(StrawSkyBlockPlugin plugin, World world,
                                           Location destination, boolean ticketHeld) {
        if (!ticketHeld || world == null || destination == null) {
            return;
        }
        try {
            world.removePluginChunkTicket(
                    chunkX(destination.getBlockX()), chunkZ(destination.getBlockZ()), plugin);
        } catch (RuntimeException ignored) {
            // 票證移除失敗不致命：最終仍會因無玩家停留而自然卸載。
        }
    }

    private static String formatWorldLoc(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }
        return location.getWorld().getName() + "@"
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    /**
     * 成功後驗證的判定結果（純資料），便於單元測試。
     */
    public static final class PostTeleportVerdict {
        private final boolean suspicious;
        private final String reason;

        private PostTeleportVerdict(boolean suspicious, String reason) {
            this.suspicious = suspicious;
            this.reason = reason;
        }

        public static PostTeleportVerdict ok() {
            return new PostTeleportVerdict(false, null);
        }

        public static PostTeleportVerdict suspicious(String reason) {
            return new PostTeleportVerdict(true, reason);
        }

        public boolean suspicious() {
            return suspicious;
        }

        @Nullable
        public String reason() {
            return reason;
        }
    }
}
