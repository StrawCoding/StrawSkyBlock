package com.strawserver.strawskyblock.shop;

import java.util.Map;

/**
 * 官方商城計價的純函式邏輯，與 Bukkit API 解耦以便單元測試。
 */
public final class ShopPricing {

    private ShopPricing() {
    }

    /**
     * 計算單一礦物的小計金額。
     *
     * @param amount    數量（負數視為 0）
     * @param unitPrice 每 1 個的收購單價（負數視為 0）
     * @return 小計金額
     */
    public static double subtotal(int amount, double unitPrice) {
        if (amount <= 0 || unitPrice <= 0) {
            return 0.0;
        }
        return amount * unitPrice;
    }

    /**
     * 依價格表計算一批礦物的總收購金額。
     *
     * @param counts 材質名稱（大寫）→ 數量
     * @param prices 材質名稱（大寫）→ 單價
     * @return 總金額；不在價格表內的材質不計入
     */
    public static double totalPayout(Map<String, Integer> counts, Map<String, Double> prices) {
        if (counts == null || prices == null) {
            return 0.0;
        }
        double total = 0.0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Double unit = prices.get(entry.getKey());
            if (unit != null) {
                total += subtotal(entry.getValue(), unit);
            }
        }
        return total;
    }
}
