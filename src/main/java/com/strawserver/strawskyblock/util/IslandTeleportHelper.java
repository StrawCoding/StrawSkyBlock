package com.strawserver.strawskyblock.util;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import net.kyori.adventure.text.Component;
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
 *
 * <p>v1.0.10 補強（回到空島時的剩餘盲區）：實測顯示玩家以 {@code /is} 回到空島後，
 * 伺服器端可能全部正常（同世界、近落點、{@code isChunkSent=true}、{@code OnGround=1}、腳下有地、
 * 幾乎無位移），v1.0.9 因此判定為 OK，既不恢復也不產生診斷，導致客戶端仍卡在「載入地形」卻無從察覺。
 * 因此新增：</p>
 * <ol>
 *   <li><b>客戶端交握偵測</b>：以 {@link TeleportActivityTracker} 於跨世界傳送後觀察客戶端主動封包
 *       （移動／視角／互動／指令／背包／揮手）。停在落點且毫無客戶端活動 → 判定可疑。</li>
 *   <li><b>判定可觀察性</b>：debug 開啟時，即使判定為「正常」也會輸出完整判定狀態，消除黑箱。</li>
 * </ol>
 *
 * <p>v1.0.11 補強（空島 /spawn 離島仍凍結）：實測顯示跨世界傳送後，伺服器端可能全部正常
 * （同世界、近落點、區塊已載入、{@code isChunkSent=true}、甚至有客戶端活動），但 {@code OnGround=0}
 * 且幾乎無位移，Y 微調／區塊重載恢復仍無法讓客戶端完成維度交握。此時僅輸出診斷無法脫困；
 * 因此在<b>有界卡住恢復後再驗證仍可疑</b>時，可選擇執行最終處置：強制重新連線（kick），
 * 以重置客戶端維度狀態（與管理員手動 kick 後恢復正常一致）。</p>
 */
public final class IslandTeleportHelper {

    /** 最終恢復（kick）寫入診斷報告時的後綴說明。 */
    public static final String FINAL_FALLBACK_DIAGNOSTIC_SUFFIX =
            "已執行最終恢復：強制重新連線以重置客戶端維度交握（kick）";

    /**
     * 跨世界傳送策略：預載並保留目的地區塊 → 等待數 tick → 主執行緒同步傳送 →
     * 等待客戶端活動穩定 → 必要時才走恢復／最終 kick（v1.0.12 預設，治本方向）。
     */
    public static final String STRATEGY_PRELOAD_WAIT_SYNC = "PRELOAD_WAIT_SYNC";

    /** 跨世界傳送策略：v1.0.11 舊行為（teleportAsync + 多次重新同步 + 驗證）。 */
    public static final String STRATEGY_LEGACY_ASYNC_RESYNC = "LEGACY_ASYNC_RESYNC";

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

        // 於任何傳送發生「之前」擷取原始來源座標，供成功後診斷使用，
        // 避免 v1.0.11 觀察到的 bug：傳送成功後玩家位置已是目的地，診斷來源被誤標為目的地。
        final Location source = player.getLocation().clone();

        prepareForTeleport(player, destination, DEFAULT_CHUNK_RADIUS);

        final World destWorld = destination.getWorld();
        final boolean crossWorld = !destWorld.equals(player.getWorld());
        final List<int[]> heldTickets = crossWorld
                ? acquireChunkTickets(plugin, destWorld, destination, DEFAULT_CHUNK_RADIUS)
                : Collections.emptyList();

        final boolean preloadWaitSync = usePreloadWaitSyncStrategy(
                crossWorld, plugin.getConfigManager().getTeleportStrategy());

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("空島傳送：玩家=" + player.getName()
                    + " 從 " + formatWorldLoc(source)
                    + " 至 " + formatWorldLoc(destination)
                    + "（跨世界=" + crossWorld
                    + "，策略=" + (preloadWaitSync ? STRATEGY_PRELOAD_WAIT_SYNC : STRATEGY_LEGACY_ASYNC_RESYNC)
                    + "）");
        }

        if (preloadWaitSync) {
            return executePreloadWaitSyncTeleport(plugin, player, destination, successMessageKey,
                    operation, source, heldTickets);
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
                                        source, destination, "teleportAsync 以例外結束（completed exceptionally）",
                                        throwable);
                                plugin.getMessageManager().send(player, "island.teleport-failed");
                                releaseChunkTickets(plugin, destWorld, heldTickets);
                                return;
                            }
                            if (!Boolean.TRUE.equals(success)) {
                                plugin.getDiagnosticService().reportTeleportFailure(operation, player,
                                        source, destination, "teleportAsync 回傳 false（傳送被拒絕，可能被其他插件取消或目標區塊不可用）",
                                        null);
                                plugin.getMessageManager().send(player, "island.teleport-failed");
                                releaseChunkTickets(plugin, destWorld, heldTickets);
                                return;
                            }
                            if (successMessageKey != null) {
                                plugin.getMessageManager().send(player, successMessageKey);
                            }
                            // 偵測關閉：傳送已完成，直接釋放票證，不啟動追蹤／驗證／恢復。
                            if (!shouldRunPostTeleportDetection(
                                    plugin.getConfigManager().isTeleportDetectionEnabled())) {
                                releaseChunkTickets(plugin, destWorld, heldTickets);
                                return;
                            }
                            Location anchor = player.getLocation().clone();
                            if (crossWorld && plugin.getTeleportActivityTracker() != null) {
                                // 自落點起開始觀察客戶端是否送出主動封包（用於驗證階段判定交握是否完成）。
                                plugin.getTeleportActivityTracker().beginSession(player.getUniqueId());
                            }
                            scheduleClientSyncSafeguard(plugin, player, destination, operation,
                                    crossWorld, heldTickets, anchor, source);
                        };
                        if (Bukkit.isPrimaryThread()) {
                            finish.run();
                        } else {
                            Bukkit.getScheduler().runTask(plugin, finish);
                        }
                    });
        } catch (RuntimeException e) {
            plugin.getDiagnosticService().reportTeleportFailure(operation, player, source, destination,
                    "發起 teleportAsync 時拋出例外", e);
            plugin.getMessageManager().send(player, "island.teleport-failed");
            releaseChunkTickets(plugin, destWorld, heldTickets);
            return false;
        }
        return true;
    }

    /**
     * PRELOAD_WAIT_SYNC 策略（v1.0.12，治本方向）：先預載並保留目的地區塊，等待數 tick 讓伺服器
     * 將區塊資料備妥後，於主執行緒「同步」傳送；傳送後再等待客戶端活動穩定視窗，唯有視窗過後仍可疑
     * 才進行有界的卡住恢復與最終 kick。藉由「先確保區塊送達再傳送」降低客戶端卡在「載入地形」的機率，
     * 並大幅減少對重新同步轟炸的依賴。
     *
     * <p>所有等待／恢復／最終處置皆為有界排程（無迴圈、無遞迴重送），確保不會無限循環。</p>
     */
    private static boolean executePreloadWaitSyncTeleport(StrawSkyBlockPlugin plugin,
                                                          Player player,
                                                          Location destination,
                                                          @Nullable String successMessageKey,
                                                          String operation,
                                                          Location source,
                                                          List<int[]> heldTickets) {
        final World destWorld = destination.getWorld();
        final long preloadWaitTicks = Math.max(0L, plugin.getConfigManager().getTeleportPreloadWaitTicks());
        final long activityWaitTicks =
                Math.max(1L, plugin.getConfigManager().getTeleportClientActivityWaitTicks());
        final boolean useSync = shouldUseSyncTeleport(true,
                plugin.getConfigManager().isTeleportUseSyncCrossWorldTeleport());

        // 步驟 1+2：預載已於呼叫端完成並以 chunk ticket 保留；等待 preloadWaitTicks 後再執行傳送。
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                releaseChunkTickets(plugin, destWorld, heldTickets);
                return;
            }
            // 等待後再次確保區塊載入（票證期間應仍載入，此處為保險）。
            prepareForTeleport(player, destination, DEFAULT_CHUNK_RADIUS);

            final boolean detectionEnabled = shouldRunPostTeleportDetection(
                    plugin.getConfigManager().isTeleportDetectionEnabled());
            // 在傳送前開始觀察客戶端活動：同步傳送本身以 PlayerTeleportEvent 觸發，已被追蹤器排除，
            // 因此 session 只會計入傳送後客戶端真正送出的主動封包。偵測關閉時不啟動追蹤。
            if (detectionEnabled && plugin.getTeleportActivityTracker() != null) {
                plugin.getTeleportActivityTracker().beginSession(player.getUniqueId());
            }

            // 步驟 3：主執行緒同步傳送（此 runnable 已在主執行緒）。
            boolean ok;
            try {
                if (useSync) {
                    ok = player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                } else {
                    player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    ok = true;
                }
            } catch (RuntimeException e) {
                plugin.getDiagnosticService().reportTeleportFailure(operation, player, source,
                        destination, "PRELOAD_WAIT_SYNC 同步傳送時拋出例外", e);
                plugin.getMessageManager().send(player, "island.teleport-failed");
                releaseChunkTickets(plugin, destWorld, heldTickets);
                endActivitySession(plugin, player);
                return;
            }
            if (!ok) {
                plugin.getDiagnosticService().reportTeleportFailure(operation, player, source,
                        destination, "同步傳送回傳 false（傳送被拒絕，可能被其他插件取消或目標區塊不可用）", null);
                plugin.getMessageManager().send(player, "island.teleport-failed");
                releaseChunkTickets(plugin, destWorld, heldTickets);
                endActivitySession(plugin, player);
                return;
            }
            if (successMessageKey != null) {
                plugin.getMessageManager().send(player, successMessageKey);
            }
            final Location anchor = player.getLocation().clone();

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("空島傳送（PRELOAD_WAIT_SYNC）：玩家=" + player.getName()
                        + " 已於 " + formatWorldLoc(player.getLocation())
                        + " 完成" + (useSync ? "同步" : "非同步") + "傳送，等待 " + activityWaitTicks
                        + " tick 客戶端活動穩定後再驗證");
            }

            // 偵測關閉：傳送已完成，僅在客戶端活動視窗後釋放區塊票證，不進行任何驗證／恢復／kick。
            if (!detectionEnabled) {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> releaseChunkTickets(plugin, destWorld, heldTickets), activityWaitTicks);
                return;
            }

            // 步驟 4+5：等待客戶端活動穩定視窗後才驗證；唯有此時仍可疑才進行有界恢復／最終 kick。
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    verifyAndRecover(plugin, player, destination, operation, anchor, true, source);
                } finally {
                    releaseChunkTickets(plugin, destWorld, heldTickets);
                }
            }, activityWaitTicks);
        }, preloadWaitTicks);
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
                                                    Location anchor,
                                                    Location source) {
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
                    performResyncAttempt(plugin, player, destination, operation, attemptIndex, source);
                }, delay);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                verifyAndRecover(plugin, player, destination, operation, anchor, crossWorld, source);
            } finally {
                releaseChunkTickets(plugin, destWorld, heldTickets);
            }
        }, lastResyncTick + verifyDelay);
    }

    private static void performResyncAttempt(StrawSkyBlockPlugin plugin,
                                             Player player,
                                             Location destination,
                                             String operation,
                                             int attemptIndex,
                                             Location source) {
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
            plugin.getDiagnosticService().reportTeleportFailure(operation, player, source, destination,
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
                                         boolean crossWorld,
                                         Location source) {
        PostTeleportVerdict verdict = collectPostTeleportVerdict(plugin, player, destination, anchor,
                crossWorld);
        if (!verdict.suspicious()) {
            endActivitySession(plugin, player);
            return;
        }

        if (shouldAttemptRecovery(crossWorld, verdict.suspicious(),
                plugin.getConfigManager().isTeleportRecoveryEnabled()) && player.isOnline()) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("空島傳送卡住恢復：玩家=" + player.getName()
                        + " 於 " + formatWorldLoc(player.getLocation())
                        + " 嘗試強制重載區塊與位移微調（原因：" + verdict.reason() + "）");
            }
            performStuckRecovery(plugin, player, destination);
            // 有界恢復：僅嘗試一次脫困後再驗證一次；仍可疑則輸出診斷，不會無限重送傳送。
            long reverifyDelay = plugin.getConfigManager().getTeleportRecoveryReverifyDelayTicks();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    PostTeleportVerdict retry = collectPostTeleportVerdict(plugin, player, destination,
                            anchor, crossWorld);
                    if (retry.suspicious()) {
                        handlePostRecoveryStillSuspicious(plugin, player, destination, operation,
                                retry, crossWorld, source);
                    }
                } finally {
                    endActivitySession(plugin, player);
                }
            }, reverifyDelay);
            return;
        }

        reportSuspiciousVerdict(plugin, player, destination, operation, verdict, source);
        endActivitySession(plugin, player);
    }

    private static void endActivitySession(StrawSkyBlockPlugin plugin, Player player) {
        TeleportActivityTracker tracker = plugin.getTeleportActivityTracker();
        if (tracker != null && player != null) {
            tracker.endSession(player.getUniqueId());
        }
    }

    /**
     * 卡住恢復後再驗證仍可疑時的最終處置：可選強制重新連線（kick），否則僅輸出診斷。
     */
    private static void handlePostRecoveryStillSuspicious(StrawSkyBlockPlugin plugin,
                                                          Player player,
                                                          Location destination,
                                                          String operation,
                                                          PostTeleportVerdict verdict,
                                                          boolean crossWorld,
                                                          Location source) {
        boolean kickEnabled = plugin.getConfigManager().isTeleportFinalFallbackKickEnabled();
        if (shouldPerformFinalFallbackKick(crossWorld, verdict.suspicious(), kickEnabled)
                && player.isOnline()) {
            String diagnosticReason = buildPostRecoveryDiagnosticReason(verdict);
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("空島傳送最終恢復：玩家=" + player.getName()
                        + " 於 " + formatWorldLoc(player.getLocation())
                        + " 執行強制重新連線（原因：" + verdict.reason() + "）");
            }
            plugin.getDiagnosticService().reportTeleportFailure(operation, player, source, destination,
                    diagnosticReason, null);
            Component kickMessage = MiniMessageUtil.parse(
                    plugin.getConfigManager().getTeleportFinalFallbackKickMessage());
            player.kick(kickMessage);
            return;
        }
        reportSuspiciousVerdict(plugin, player, destination, operation, verdict, source);
    }

    /**
     * 是否對此次傳送採用 PRELOAD_WAIT_SYNC 策略（純邏輯，可單元測試）。
     *
     * <p>同世界傳送一律回傳 {@code false}（沿用快速的非同步路徑，不額外延遲）；
     * 跨世界傳送時，{@code null}／空字串／{@code PRELOAD_WAIT_SYNC}（不分大小寫）皆視為採用，
     * 其餘字串（例如 {@code LEGACY_ASYNC_RESYNC}）則回退舊行為。</p>
     */
    public static boolean usePreloadWaitSyncStrategy(boolean crossWorld, String strategy) {
        if (!crossWorld) {
            return false;
        }
        if (strategy == null || strategy.isBlank()) {
            return true;
        }
        return STRATEGY_PRELOAD_WAIT_SYNC.equalsIgnoreCase(strategy.trim());
    }

    /**
     * 跨世界傳送是否使用主執行緒同步傳送（純邏輯，可單元測試）。
     */
    public static boolean shouldUseSyncTeleport(boolean crossWorld, boolean useSyncConfig) {
        return crossWorld && useSyncConfig;
    }

    /**
     * 計算 PRELOAD_WAIT_SYNC 策略下，「成功後驗證／恢復」最早可能發生的相對 tick（純邏輯，可單元測試）。
     *
     * <p>自發起傳送起算：等待 {@code preloadWaitTicks} 後才同步傳送，再等待
     * {@code clientActivityWaitTicks}（客戶端活動穩定視窗）後才進行第一次驗證。
     * 因此恢復絕不會早於 {@code preloadWaitTicks + clientActivityWaitTicks}，
     * 保證恢復被延後到客戶端活動視窗之後（治本方向：先給客戶端時間完成交握）。</p>
     */
    public static long computeRecoveryEarliestTick(long preloadWaitTicks, long clientActivityWaitTicks) {
        long preload = Math.max(0L, preloadWaitTicks);
        long activity = Math.max(1L, clientActivityWaitTicks);
        return preload + activity;
    }

    /**
     * 傳送後是否執行偵測流程（客戶端交握偵測／成功後驗證／卡住恢復／最終 kick）（純邏輯，可單元測試）。
     *
     * <p>偵測總開關關閉時一律回傳 {@code false}（傳送照常完成，但完全不做偵測與反應）；
     * 開啟時回傳 {@code true}，再由 {@link #shouldAttemptRecovery} 等個別旗標進一步判定。</p>
     */
    public static boolean shouldRunPostTeleportDetection(boolean detectionEnabled) {
        return detectionEnabled;
    }

    /**
     * 是否應在 PRELOAD_WAIT_SYNC 策略下、客戶端活動視窗過後仍可疑時嘗試卡住恢復（純邏輯，可單元測試）。
     */
    public static boolean shouldAttemptRecovery(boolean crossWorld,
                                                boolean stillSuspicious,
                                                boolean recoveryEnabled) {
        return crossWorld && stillSuspicious && recoveryEnabled;
    }

    /**
     * 是否應在卡住恢復後仍可疑時執行最終 kick（純邏輯，可單元測試）。
     */
    public static boolean shouldPerformFinalFallbackKick(boolean crossWorld,
                                                         boolean stillSuspicious,
                                                         boolean kickEnabled) {
        return crossWorld && stillSuspicious && kickEnabled;
    }

    /**
     * 組合卡住恢復後仍可疑時寫入診斷的完整原因（純字串，可單元測試）。
     */
    public static String buildPostRecoveryDiagnosticReason(PostTeleportVerdict verdict) {
        String base = verdict != null && verdict.reason() != null ? verdict.reason() : "未知原因";
        return "卡住恢復後驗證仍可疑：" + base + "；" + FINAL_FALLBACK_DIAGNOSTIC_SUFFIX;
    }

    private static void reportSuspiciousVerdict(StrawSkyBlockPlugin plugin,
                                                Player player,
                                                Location destination,
                                                String operation,
                                                PostTeleportVerdict verdict,
                                                Location source) {
        plugin.getDiagnosticService().reportTeleportFailure(operation, player, source, destination,
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

    private static PostTeleportVerdict collectPostTeleportVerdict(StrawSkyBlockPlugin plugin,
                                                                  Player player,
                                                                  Location destination,
                                                                  Location anchor,
                                                                  boolean crossWorld) {
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
        boolean clientActivityObserved = resolveClientActivityObserved(plugin, player, crossWorld);

        PostTeleportVerdict verdict = evaluatePostTeleport(online, sameWorld, nearDestination,
                chunkLoaded, groundBelow, onGround, noMovementSinceLanding, clientChunkSent,
                clientActivityObserved);

        if (plugin.getConfigManager().isDebug()) {
            // v1.0.10：即使判定為「正常」也輸出完整判定狀態，消除 v1.0.9 宣告 OK 時毫無線索的黑箱。
            plugin.getLogger().info("空島傳送驗證判定：玩家=" + player.getName()
                    + " 位置=" + formatWorldLoc(online ? player.getLocation() : null)
                    + " 目的地=" + formatWorldLoc(destination)
                    + "｜sameWorld=" + sameWorld
                    + " nearDestination=" + nearDestination
                    + " chunkLoaded=" + chunkLoaded
                    + " clientChunkSent=" + clientChunkSent
                    + " onGround=" + onGround
                    + " noMovement=" + noMovementSinceLanding
                    + " groundBelow=" + groundBelow
                    + " clientActivity=" + clientActivityObserved
                    + "（跨世界=" + crossWorld + "）"
                    + " => " + (verdict.suspicious()
                            ? "可疑（" + verdict.reason() + "）"
                            : "正常"));
        }
        return verdict;
    }

    /**
     * 解析「是否觀察到客戶端主動活動」。非跨世界傳送、追蹤器不可用或設定關閉時，
     * 一律回傳 {@code true}（不以此條判定為卡住），避免誤報。
     */
    private static boolean resolveClientActivityObserved(StrawSkyBlockPlugin plugin,
                                                         Player player,
                                                         boolean crossWorld) {
        if (!crossWorld) {
            return true;
        }
        if (!plugin.getConfigManager().isTeleportClientAckRequired()) {
            return true;
        }
        TeleportActivityTracker tracker = plugin.getTeleportActivityTracker();
        if (tracker == null) {
            return true;
        }
        return tracker.hasActivitySince(player.getUniqueId());
    }

    private static boolean isLandingChunkSentToClient(Player player, Location destination) {
        if (destination == null || destination.getWorld() == null) {
            return false;
        }
        long chunkKey = Chunk.getChunkKey(chunkX(destination.getBlockX()), chunkZ(destination.getBlockZ()));
        return player.isChunkSent(chunkKey);
    }

    /**
     * 純粹的成功後狀態判定邏輯，不依賴 Bukkit（除 isChunkSent／客戶端活動於呼叫端採集），方便單元測試。
     *
     * <p>判定原則（盡量降低誤報）：</p>
     * <ul>
     *   <li>玩家已離線 → 不視為可疑。</li>
     *   <li>不在目的地世界 → 可疑（跨維度同步失敗）。</li>
     *   <li>已明顯離開落點 → 視為正常（玩家能移動代表未卡住）。</li>
     *   <li>仍停在落點：區塊未載入、Paper 回報區塊未送達客戶端、腳下無方塊、
     *       長時間未著地且幾乎無位移、
     *       或<b>傳送後完全未觀察到客戶端主動封包</b> → 可疑（客戶端可能仍卡在載入地形）。</li>
     * </ul>
     *
     * <p>v1.0.10 新增 {@code clientActivityObserved}：這是唯一能正面證明客戶端已完成
     * 維度交握的訊號。v1.0.9 的盲區在於即使伺服器端一切正常（OnGround=1、isChunkSent=true、
     * 腳下有地），客戶端仍可能卡在「載入地形」而毫無回應；此時玩家停在落點且未送出任何封包，
     * 即可被本條判定為可疑。</p>
     */
    public static PostTeleportVerdict evaluatePostTeleport(boolean online,
                                                           boolean sameWorld,
                                                           boolean nearDestination,
                                                           boolean chunkLoaded,
                                                           boolean groundBelow,
                                                           boolean onGround,
                                                           boolean noMovementSinceLanding,
                                                           boolean clientChunkSent,
                                                           boolean clientActivityObserved) {
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
        if (noMovementSinceLanding && !clientActivityObserved) {
            appendReason(reason,
                    "伺服器狀態正常，但跨世界傳送後在驗證視窗內未觀察到任何客戶端主動封包"
                            + "（移動／視角／互動／指令／背包／揮手），且玩家仍停在落點"
                            + "（客戶端可能尚未確認維度切換，仍卡在載入地形）");
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
