package com.strawserver.strawskyblock.util;

import java.util.Locale;

/**
 * 物品顯示名稱工具。
 *
 * <p>提供將材質列舉名稱（例如 {@code RAW_COPPER}）轉成人類可讀字串的純邏輯方法，
 * 供作為 messages.yml 找不到對應中文名稱時的後備顯示，避免玩家直接看到原始列舉名稱。
 * 本類別不依賴 Bukkit 執行環境，方便單元測試。</p>
 */
public final class ItemDisplayNames {

    private ItemDisplayNames() {
    }

    /**
     * 將材質列舉名稱轉為較友善的字串，例如 {@code RAW_COPPER} -> {@code Raw Copper}。
     * 用於 messages.yml 未提供對應翻譯時的後備值。
     *
     * @param materialKey 材質列舉名稱（可含底線），允許 {@code null}
     * @return 友善化後的字串；若輸入為 {@code null} 或空白則回傳空字串
     */
    public static String humanize(String materialKey) {
        if (materialKey == null) {
            return "";
        }
        String trimmed = materialKey.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // 去除可能的命名空間前綴（例如 minecraft:raw_copper）
        int colon = trimmed.indexOf(':');
        if (colon >= 0 && colon + 1 < trimmed.length()) {
            trimmed = trimmed.substring(colon + 1);
        }
        String[] parts = trimmed.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
