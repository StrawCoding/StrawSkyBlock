package com.strawserver.strawskyblock.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBypassTest {

    @Test
    void permissionNodeIsStable() {
        assertEquals("strawskyblock.admin.bypass", AdminBypass.PERMISSION);
    }

    @Test
    void grantedWhenPermissionPresent() {
        assertTrue(AdminBypass.grantedBy(List.of("strawskyblock.admin.bypass")));
        assertTrue(AdminBypass.grantedBy(Set.of(
                "essentials.fly", "strawskyblock.admin.bypass")));
    }

    @Test
    void notGrantedWithoutPermission() {
        assertFalse(AdminBypass.grantedBy(Set.of()));
        assertFalse(AdminBypass.grantedBy(null));
        assertFalse(AdminBypass.grantedBy(List.of(
                "strawskyblock.user.robot", "strawskyblock.admin.reload")));
    }

    @Test
    void caseInsensitiveAndTrimmed() {
        assertTrue(AdminBypass.grantedBy(List.of("STRAWSKYBLOCK.ADMIN.BYPASS")));
        assertTrue(AdminBypass.grantedBy(List.of("  strawskyblock.admin.bypass  ")));
    }

    @Test
    void wildcardNodeDoesNotMatchLiteralHelper() {
        // grantedBy 為「明確節點」比對，萬用字元由 Bukkit 權限系統處理。
        assertFalse(AdminBypass.grantedBy(List.of("strawskyblock.admin.*")));
    }
}
