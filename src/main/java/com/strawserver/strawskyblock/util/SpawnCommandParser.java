package com.strawserver.strawskyblock.util;

/**
 * 解析玩家輸入的 /spawn 指令，僅在「玩家自行傳送至出生點」時才攔截。
 */
public final class SpawnCommandParser {

    private SpawnCommandParser() {
    }

    /**
     * 是否為不含參數的 /spawn（含 namespaced 形式，例如 /huskhomes:spawn）。
     */
    public static boolean isBareSpawnCommand(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String trimmed = rawMessage.trim();
        if (!trimmed.startsWith("/")) {
            return false;
        }
        String withoutSlash = trimmed.substring(1).trim();
        if (withoutSlash.isEmpty()) {
            return false;
        }
        String[] parts = withoutSlash.split("\\s+");
        String label = stripNamespace(parts[0]);
        if (!label.equalsIgnoreCase("spawn")) {
            return false;
        }
        return parts.length == 1;
    }

    /**
     * 是否應由 StrawSkyBlock 攔截 /spawn 並改走 chunk-safe 傳送。
     */
    public static boolean shouldInterceptSpawnFromIslandWorld(boolean interceptEnabled,
                                                              boolean playerInIslandWorld,
                                                              String rawMessage) {
        return interceptEnabled && playerInIslandWorld && isBareSpawnCommand(rawMessage);
    }

    static String stripNamespace(String commandLabel) {
        if (commandLabel == null || commandLabel.isEmpty()) {
            return "";
        }
        int colon = commandLabel.indexOf(':');
        if (colon >= 0 && colon < commandLabel.length() - 1) {
            return commandLabel.substring(colon + 1);
        }
        return commandLabel;
    }
}
