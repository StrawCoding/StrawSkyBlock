package com.strawserver.strawskyblock.util;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 空島傳送輔助：在 Paper 1.21 上跨世界傳送時，
 * 必須先確保目標區塊（以及跨維度時的世界出生區塊）已載入，並處理傳送結果。
 *
 * <p>核心問題（v1.0.9 修正）：跨世界傳送（空島 ↔ 主世界／spawn）時，
 * 伺服器端 {@code teleportAsync} 常回報成功（實體已在落點、區塊已載入），但客戶端仍卡在
 * 「載入地形」。v1.0.8 僅以伺服器端世界／座標／區塊載入／腳下方塊驗證，當這些條件皆成立時
 * 不會產生診斷；實際證據（RCON：OnGround=0、/is admin diag 無紀錄）顯示客戶端維度交握
 * 未完成才是根因。Paper 提供 {@link Player#isChunkSent(long)} 可偵測落點區塊是否已送達客戶端。</p>
 *
 * <p>修正策略：</p>
 * <ol>
 *   <li><b>3x3 區塊票證</b>：跨世界傳送期間於落點周圍區塊加上 plugin chunk ticket。</li>
 *   <li><b>多次重新同步</b>：延遲多次 {@code teleportAsync}，交替同座標與安全 Y 微調，強制重送封包。</li>
 *   <li><b>Paper 區塊送達檢查</b>：驗證時檢查 {@code isChunkSent}、{@code isOnGround} 與傳送後位移。</li>
 *   <li><b>卡住恢復</b>：驗證仍可疑時，預載區塊並以 Y 微調來回傳送 + {@code updateInventory} 嘗試脫困。</li>
 * </ol>
 */
public final class IslandTeleportHelper {

    /** 以傳送點為中心預載的區塊半徑（含中心 = (2r+1)^2 個區塊）。 */
    public static final int DEFAULT_CHUNK_RADIUS = 1;

    /** 驗證時判定「玩家仍在落點」的水平距離平方門檻（超過視為玩家已自行移動，屬正常）。 */
    public static final double NEAR_DESTINATION_DISTANCE_SQUARED = 48.0D * 48.0D;

    /** 驗證時判定「傳送後幾乎無位移」的距離平方門檻（0.5 格）。 */
    public static final double STABILIZED_MOVEMENT_DISTANCE_SQUARED = 0.5D * 0.5D;

    /** 驗證時往落點下方探測可站立方塊的最大深度（格）。 */
    public static final int GROUND_PROBE_DEPTH = 4;

    /** 重新同步時用於強制客戶端重收區塊的安全 Y 微調量（格）。 */
    public static final double RESYNC_NUDGE_Y = 0.5D;

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
        final List<int[]> heldTickets = crossWorld
                ? acquireChunkTickets(plugin, destWorld, destination, DEFAULT_CHUNK_RADIUS)
                : Collections.emptyList();

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
                                releaseChunkTickets(plugin, destWorld, heldTickets);
                                return;
                            }
                            if (throwable != null) {
                                plugin.getDiagnosticService().reportTeleportFailure(operation, player,
                                        destination, "teleportAsync 以例外結束（completed exceptionally）",
                                        throwable);
                                plugin.getMessageManager().send(player, "island.teleport-failed");
                                releaseChunkTickets(plugin, destWorld, heldTickets);
                                return;
                            }
                            if (!Boolean.TRUE.equals(success)) {
                                plugin.getDiagnosticService().reportTeleportFailure(operation, player,
                                        destination, "teleportAsync 回傳 false（傳送被拒絕，可能被其他插件取消或目標區塊不可用）",
                                        null);
                                plugin.getMessageManager().send(player, "island.teleport-failed");
                                releaseChunkTickets(plugin, destWorld, heldTickets);
                                return;
                            }
                            if (successMessageKey != null) {
                                plugin.getMessageManager().send(player, successMessageKey);
                            }
                            Location anchor = player.getLocation().clone();
                            scheduleClientSyncSafeguard(plugin, player, destination, operation,
                                    crossWorld, heldTickets, anchor);
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
            releaseChunkTickets(plugin, destWorld, heldTickets);
            return false;
        }
        return true;
    }

    /**
     * 成功後保護機制：跨世界傳送時，多次延遲重新同步（同座標與 Y 微調交替），
     * 再於驗證延遲後以 Paper 區塊送達檢查；若仍可疑則嘗試卡住恢復並輸出診斷。
     */
    private static void scheduleClientSyncSafeguard(StrawSkyBlockPlugin plugin,
                                                    Player player,
                                                    Location destination,
                                                    String operation,
                                                    boolean crossWorld,
                                                    List<int[]> heldTickets,
                                                    Location anchor) {
        long resyncDelay = Math.max(0L, plugin.getConfigManager().getTeleportResyncDelayTicks());
        long verifyDelay = Math.max(1L, plugin.getConfigManager().getTeleportVerifyDelayTicks());
        long interval = plugin.getConfigManager().getTeleportResyncIntervalTicks();
        int maxAttempts = plugin.getConfigManager().getTeleportResyncMaxAttempts();
        boolean resyncEnabled = crossWorld && plugin.getConfigManager().isTeleportResyncEnabled();
        World destWorld = destination.getWorld();

        long lastResyncTick = resyncDelay;
        if (resyncEnabled) {
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                long delay = resyncDelay + attempt * interval;
                lastResyncTick = delay;
                final int attemptIndex = attempt;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline() || destWorld == null || !destWorld.equals(player.getWorld())) {
                        return;
                    }
                    performResyncAttempt(plugin, player, destination, operation, attemptIndex);
                }, delay);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                verifyAndRecover(plugin, player, destination, operation, anchor, crossWorld);
            } finally {
                releaseChunkTickets(plugin, destWorld, heldTickets);
            }
        }, lastResyncTick + verifyDelay);
    }

    private static void performResyncAttempt(StrawSkyBlockPlugin plugin,
                                             Player player,
                                             Location destination,
                                             String operation,
                                             int attemptIndex) {
        prepareForTeleport(player, destination, DEFAULT_CHUNK_RADIUS);
        Location target = destination.clone();
        if (attemptIndex > 0 && attemptIndex % 2 == 1) {
            target.add(0.0D, RESYNC_NUDGE_Y, 0.0D);
        }
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("空島傳送重新同步：玩家=" + player.getName()
                    + " 第 " + (attemptIndex + 1) + " 次於 " + formatWorldLoc(target) + " 重送座標／區塊封包");
        }
        try {
            player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN)
                    .whenComplete((ok, err) -> {
                        if (attemptIndex > 0 && attemptIndex % 2 == 1 && Boolean.TRUE.equals(ok)) {
                            Runnable returnToDest = () -> {
                                if (player.isOnline() && destination.getWorld() != null
                                        && destination.getWorld().equals(player.getWorld())) {
                                    try {
                                        player.teleportAsync(destination,
                                                PlayerTeleportEvent.TeleportCause.PLUGIN);
                                    } catch (RuntimeException ignored) {
                                        // 回到精確落點失敗不致命；下一輪驗證會再判定。
                                    }
                                }
                            };
                            if (Bukkit.isPrimaryThread()) {
                                Bukkit.getScheduler().runTaskLater(plugin, returnToDest, 1L);
                            } else {
                                Bukkit.getScheduler().runTask(plugin,
                                        () -> Bukkit.getScheduler().runTaskLater(plugin, returnToDest, 1L));
                            }
                        }
                    });
        } catch (RuntimeException e) {
            plugin.getDiagnosticService().reportTeleportFailure(operation, player, destination,
                    "重新同步傳送時拋出例外", e);
        }
    }

    /**
     * 成功後驗證與卡住恢復：比對伺服器端與 Paper 客戶端區塊送達狀態；
     * 若仍可疑且啟用恢復，嘗試更強的脫困流程後再驗證一次。
     */
    private static void verifyAndRecover(StrawSkyBlockPlugin plugin,
                                         Player player,
                                         Location destination,
                                         String operation,
                                         Location anchor,
                                         boolean crossWorld) {
        PostTeleportVerdict verdict = collectPostTeleportVerdict(player, destination, anchor);
        if (!verdict.suspicious()) {
            return;
        }

        if (crossWorld && plugin.getConfigManager().isTeleportRecoveryEnabled() && player.isOnline()) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("空島傳送卡住恢復：玩家=" + player.getName()
                        + " 於 " + formatWorldLoc(player.getLocation())
                        + " 嘗試強制重載區塊與位移微調（原因：" + verdict.reason() + "）");
            }
            performStuckRecovery(plugin, player, destination);
            long reverifyDelay = plugin.getConfigManager().getTeleportRecoveryReverifyDelayTicks();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                PostTeleportVerdict retry = collectPostTeleportVerdict(player, destination, anchor);
                if (retry.suspicious()) {
                    reportSuspiciousVerdict(plugin, player, destination, operation, retry);
                }
            }, reverifyDelay);
            return;
        }

        reportSuspiciousVerdict(plugin, player, destination, operation, verdict);
    }

    private static void reportSuspiciousVerdict(StrawSkyBlockPlugin plugin,
                                                Player player,
                                                Location destination,
                                                String operation,
                                                PostTeleportVerdict verdict) {
        plugin.getDiagnosticService().reportTeleportFailure(operation, player, destination,
                "傳送回報成功，但成功後驗證偵測到可疑狀態：" + verdict.reason(), null);
        if (player.isOnline()) {
            plugin.getMessageManager().send(player, "island.teleport-failed");
        }
    }

    /**
     * 更強的卡住恢復：預載 3x3 區塊、Y 微調來回傳送，並刷新客戶端背包封包。
     */
    private static void performStuckRecovery(StrawSkyBlockPlugin plugin,
                                             Player player,
                                             Location destination) {
        if (!player.isOnline() || destination.getWorld() == null) {
            return;
        }
        prepareForTeleport(player, destination, DEFAULT_CHUNK_RADIUS);
        Location nudgeUp = destination.clone().add(0.0D, RESYNC_NUDGE_Y, 0.0D);
        try {
            player.teleportAsync(nudgeUp, PlayerTeleportEvent.TeleportCause.PLUGIN)
                    .whenComplete((ok, err) -> {
                        Runnable finish = () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            try {
                                prepareForTeleport(player, destination, DEFAULT_CHUNK_RADIUS);
                                player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                                player.updateInventory();
                            } catch (RuntimeException ignored) {
                                // 恢復失敗時由後續再驗證輸出診斷。
                            }
                        };
                        if (Bukkit.isPrimaryThread()) {
                            Bukkit.getScheduler().runTaskLater(plugin, finish, 2L);
                        } else {
                            Bukkit.getScheduler().runTask(plugin,
                                    () -> Bukkit.getScheduler().runTaskLater(plugin, finish, 2L));
                        }
                    });
        } catch (RuntimeException ignored) {
            // 由再驗證階段輸出診斷。
        }
    }

    private static PostTeleportVerdict collectPostTeleportVerdict(Player player,
                                                                  Location destination,
                                                                  Location anchor) {
        boolean online = player.isOnline();
        World destWorld = destination.getWorld();
        Location current = online ? player.getLocation() : null;

        boolean sameWorld = online && current != null && current.getWorld() != null
                && current.getWorld().equals(destWorld);
        boolean nearDestination = sameWorld && current.distanceSquared(destination)
                <= NEAR_DESTINATION_DISTANCE_SQUARED;
        boolean noMovementSinceLanding = sameWorld && anchor != null
                && current.distanceSquared(anchor) <= STABILIZED_MOVEMENT_DISTANCE_SQUARED;
        boolean chunkLoaded = destWorld != null
                && destWorld.isChunkLoaded(chunkX(destination.getBlockX()), chunkZ(destination.getBlockZ()));
        boolean groundBelow = hasGroundBelow(destWorld, destination, GROUND_PROBE_DEPTH);
        boolean onGround = online && player.isOnGround();
        boolean clientChunkSent = online && destWorld != null
                && isLandingChunkSentToClient(player, destination);

        return evaluatePostTeleport(online, sameWorld, nearDestination, chunkLoaded, groundBelow,
                onGround, noMovementSinceLanding, clientChunkSent);
    }

    private static boolean isLandingChunkSentToClient(Player player, Location destination) {
        if (destination == null || destination.getWorld() == null) {
            return false;
        }
        long chunkKey = Chunk.getChunkKey(chunkX(destination.getBlockX()), chunkZ(destination.getBlockZ()));
        return player.isChunkSent(chunkKey);
    }

    /**
     * 純粹的成功後狀態判定邏輯，不依賴 Bukkit（除 isChunkSent 於呼叫端採集），方便單元測試。
     *
     * <p>判定原則（盡量降低誤報）：</p>
     * <ul>
     *   <li>玩家已離線 → 不視為可疑。</li>
     *   <li>不在目的地世界 → 可疑（跨維度同步失敗）。</li>
     *   <li>已明顯離開落點 → 視為正常（玩家能移動代表未卡住）。</li>
     *   <li>仍停在落點：區塊未載入、Paper 回報區塊未送達客戶端、腳下無方塊、
     *       或長時間未著地且幾乎無位移 → 可疑（客戶端可能仍卡在載入地形）。</li>
     * </ul>
     */
    public static PostTeleportVerdict evaluatePostTeleport(boolean online,
                                                           boolean sameWorld,
                                                           boolean nearDestination,
                                                           boolean chunkLoaded,
                                                           boolean groundBelow,
                                                           boolean onGround,
                                                           boolean noMovementSinceLanding,
                                                           boolean clientChunkSent) {
        if (!online) {
            return PostTeleportVerdict.ok();
        }
        if (!sameWorld) {
            return PostTeleportVerdict.suspicious(
                    "玩家不在目的地世界（疑似跨維度同步失敗，客戶端可能仍卡在載入地形）");
        }
        if (!nearDestination) {
            return PostTeleportVerdict.ok();
        }
        StringBuilder reason = new StringBuilder();
        if (!chunkLoaded) {
            reason.append("落點區塊未保持載入（客戶端可能持續卡在載入地形）");
        }
        if (!clientChunkSent) {
            appendReason(reason,
                    "落點區塊尚未送達客戶端（Paper isChunkSent=false，客戶端可能仍卡在載入地形）");
        }
        if (!groundBelow) {
            appendReason(reason, "落點下方無可站立方塊（玩家可能掉入虛空）");
        }
        if (noMovementSinceLanding && !onGround && groundBelow) {
            appendReason(reason,
                    "伺服器端實體未著地（OnGround=0）且傳送後幾乎無位移（疑似客戶端維度交握未完成，仍卡在載入地形）");
        }
        if (reason.length() == 0) {
            return PostTeleportVerdict.ok();
        }
        return PostTeleportVerdict.suspicious(reason.toString());
    }

    private static void appendReason(StringBuilder reason, String fragment) {
        if (reason.length() > 0) {
            reason.append("；");
        }
        reason.append(fragment);
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

    private static List<int[]> acquireChunkTickets(StrawSkyBlockPlugin plugin,
                                                   World world,
                                                   Location destination,
                                                   int radiusChunks) {
        if (!plugin.getConfigManager().isTeleportChunkTicket() || world == null || destination == null) {
            return Collections.emptyList();
        }
        List<int[]> held = new ArrayList<>();
        int centerChunkX = chunkX(destination.getBlockX());
        int centerChunkZ = chunkZ(destination.getBlockZ());
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                try {
                    if (world.addPluginChunkTicket(cx, cz, plugin)) {
                        held.add(new int[]{cx, cz});
                    }
                } catch (RuntimeException ignored) {
                    // 單一區塊票證失敗不阻斷整體傳送。
                }
            }
        }
        return held;
    }

    private static void releaseChunkTickets(StrawSkyBlockPlugin plugin,
                                            @Nullable World world,
                                            List<int[]> heldTickets) {
        if (world == null || heldTickets == null || heldTickets.isEmpty()) {
            return;
        }
        for (int[] coords : heldTickets) {
            if (coords == null || coords.length < 2) {
                continue;
            }
            try {
                world.removePluginChunkTicket(coords[0], coords[1], plugin);
            } catch (RuntimeException ignored) {
                // 票證移除失敗不致命。
            }
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
