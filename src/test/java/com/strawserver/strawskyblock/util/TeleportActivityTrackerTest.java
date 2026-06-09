package com.strawserver.strawskyblock.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportActivityTrackerTest {

    @Test
    void untrackedPlayerIsTreatedAsAcknowledged() {
        TeleportActivityTracker tracker = new TeleportActivityTracker();
        // 沒有進行中的 session（例如非跨世界傳送）→ 不以此判定為卡住。
        assertTrue(tracker.hasActivitySince(UUID.randomUUID()));
    }

    @Test
    void freshSessionWithoutActivityHasNoAck() {
        TeleportActivityTracker tracker = new TeleportActivityTracker();
        UUID id = UUID.randomUUID();
        tracker.beginSession(id);
        assertTrue(tracker.isTracking(id));
        assertFalse(tracker.hasActivitySince(id));
    }

    @Test
    void activityAfterSessionStartIsObserved() {
        TeleportActivityTracker tracker = new TeleportActivityTracker();
        UUID id = UUID.randomUUID();
        tracker.beginSession(id);
        tracker.markActivity(id);
        assertTrue(tracker.hasActivitySince(id));
    }

    @Test
    void activityBeforeSessionDoesNotCount() {
        TeleportActivityTracker tracker = new TeleportActivityTracker();
        UUID id = UUID.randomUUID();
        // session 尚未開始時的活動不會被記錄（markActivity 只在有 session 時累計）。
        tracker.markActivity(id);
        tracker.beginSession(id);
        assertFalse(tracker.hasActivitySince(id));
    }

    @Test
    void beginSessionResetsPreviousActivity() {
        TeleportActivityTracker tracker = new TeleportActivityTracker();
        UUID id = UUID.randomUUID();
        tracker.beginSession(id);
        tracker.markActivity(id);
        assertTrue(tracker.hasActivitySince(id));
        // 重新開始 session 應清除先前的活動，只計算新 session 之後的活動。
        tracker.beginSession(id);
        assertFalse(tracker.hasActivitySince(id));
    }

    @Test
    void endSessionStopsTracking() {
        TeleportActivityTracker tracker = new TeleportActivityTracker();
        UUID id = UUID.randomUUID();
        tracker.beginSession(id);
        tracker.endSession(id);
        assertFalse(tracker.isTracking(id));
        // 結束後視為未追蹤 → 回到「不予判定卡住」的安全預設。
        assertTrue(tracker.hasActivitySince(id));
    }

    @Test
    void sessionsAreIsolatedPerPlayer() {
        TeleportActivityTracker tracker = new TeleportActivityTracker();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        tracker.beginSession(a);
        tracker.beginSession(b);
        tracker.markActivity(a);
        assertTrue(tracker.hasActivitySince(a));
        assertFalse(tracker.hasActivitySince(b));
    }

    @Test
    void nullPlayerIdIsSafe() {
        TeleportActivityTracker tracker = new TeleportActivityTracker();
        tracker.beginSession(null);
        tracker.markActivity(null);
        tracker.endSession(null);
        assertTrue(tracker.hasActivitySince(null));
        assertFalse(tracker.isTracking(null));
    }
}
