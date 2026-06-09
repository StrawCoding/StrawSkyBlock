package com.strawserver.strawskyblock.util;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 跨世界傳送「客戶端交握偵測」：v1.0.9 的成功後驗證僅檢查伺服器端狀態
 * （同世界、近落點、區塊載入、Paper isChunkSent、腳下方塊、OnGround），
 * 當這些條件都成立但客戶端仍卡在「載入地形」時毫無線索（盲區）。
 *
 * <p>本追蹤器補上唯一能證明「客戶端確實完成維度交握」的訊號：
 * <b>客戶端主動送出的封包</b>。卡在載入地形的客戶端不會送出移動／視角／互動／指令／
 * 背包點擊／揮手等封包。因此在跨世界傳送開啟一段「session」後，只要觀察到任一筆
 * 客戶端主動事件，即可判定客戶端已交握成功；反之在驗證視窗內毫無動靜（且玩家仍停在落點），
 * 即為「伺服器狀態正常但客戶端未確認」的卡住情境。</p>
 *
 * <p>實作刻意避免依賴掛鉤伺服器 tick 時間，改用單調遞增的邏輯序號（{@link AtomicLong}），
 * 讓核心判定邏輯可被純單元測試覆蓋。所有事件處理皆為 {@link EventPriority#MONITOR}
 * 且不取消、不修改事件，純觀察。</p>
 */
public class TeleportActivityTracker implements Listener {

    /** session 開始時的邏輯序號（玩家 UUID -> 序號）。 */
    private final Map<UUID, Long> sessionMark = new ConcurrentHashMap<>();
    /** 該玩家最後一次客戶端主動活動的邏輯序號。 */
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    /** 單調遞增的邏輯時鐘，避免依賴系統時間造成測試不穩定。 */
    private final AtomicLong sequence = new AtomicLong();

    /**
     * 開始一段傳送觀察 session（通常於跨世界傳送落點當下呼叫）。
     * 會清除先前累積的活動紀錄，僅計算 session 之後的客戶端活動。
     */
    public void beginSession(UUID playerId) {
        if (playerId == null) {
            return;
        }
        sessionMark.put(playerId, sequence.incrementAndGet());
        lastActivity.remove(playerId);
    }

    /**
     * 記錄一筆客戶端主動活動；僅在該玩家有進行中的 session 時才需累計。
     */
    public void markActivity(UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (sessionMark.containsKey(playerId)) {
            lastActivity.put(playerId, sequence.incrementAndGet());
        }
    }

    /**
     * @return 自最近一次 {@link #beginSession} 以來是否觀察到客戶端主動活動。
     *         若該玩家沒有進行中的 session（例如非跨世界傳送），回傳 {@code true}，
     *         代表「不予判定為卡住」，避免誤報。
     */
    public boolean hasActivitySince(UUID playerId) {
        if (playerId == null) {
            return true;
        }
        Long start = sessionMark.get(playerId);
        if (start == null) {
            return true;
        }
        Long activity = lastActivity.get(playerId);
        return activity != null && activity > start;
    }

    /** 是否有進行中的觀察 session。 */
    public boolean isTracking(UUID playerId) {
        return playerId != null && sessionMark.containsKey(playerId);
    }

    /** 結束並清除該玩家的觀察 session。 */
    public void endSession(UUID playerId) {
        if (playerId == null) {
            return;
        }
        sessionMark.remove(playerId);
        lastActivity.remove(playerId);
    }

    // ---- 事件觀察（純觀察，不取消、不修改）----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        // 伺服器端的傳送會以 PlayerTeleportEvent（PlayerMoveEvent 子類）觸發，
        // 必須排除，否則重新同步／Y 微調的傳送會被誤判為客戶端活動。
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        // PlayerMoveEvent 只在客戶端送出位置／視角封包且有變化時觸發，
        // 卡在載入地形的客戶端不會送出，因此這是強而有力的交握訊號。
        markActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        markActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        markActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onAnimation(PlayerAnimationEvent event) {
        markActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (who instanceof Player) {
            markActivity(who.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onQuit(PlayerQuitEvent event) {
        endSession(event.getPlayer().getUniqueId());
    }
}
