package com.strawserver.strawskyblock.shop;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.economy.EconomyHook;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 官方商城：玩家可將刷石機產出的礦物賣給伺服器換取金錢。
 *
 * <p>計價的純邏輯位於 {@link ShopPricing}，本服務負責整合背包與 Vault 經濟系統。</p>
 */
public class ShopService {

    private final StrawSkyBlockPlugin plugin;

    public ShopService(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 商城是否可用（設定開啟且經濟系統存在）。
     */
    public boolean isAvailable() {
        EconomyHook economy = plugin.getEconomyHook();
        return plugin.getConfigManager().isShopEnabled()
                && economy != null
                && economy.isEnabled();
    }

    public boolean isShopEnabled() {
        return plugin.getConfigManager().isShopEnabled();
    }

    /**
     * 取得收購價格表（依設定順序），key 為材質、value 為單價。
     */
    public Map<Material, Double> getPriceTable() {
        Map<Material, Double> table = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : plugin.getConfigManager().getShopBuyPrices().entrySet()) {
            Material material = Material.matchMaterial(entry.getKey().toUpperCase(Locale.ROOT));
            if (material != null) {
                table.put(material, entry.getValue());
            }
        }
        return table;
    }

    /**
     * 取得某材質的收購單價，未收購則回傳 0。
     */
    public double priceOf(Material material) {
        return getPriceTable().getOrDefault(material, 0.0);
    }

    public boolean isBuyable(Material material) {
        return priceOf(material) > 0;
    }

    /**
     * 計算玩家背包內某材質可賣出的數量。
     */
    public int countSellable(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isSellableStack(stack, material)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * 賣出玩家背包內某材質的全部數量。
     */
    public SellResult sellMaterial(Player player, Material material) {
        double unitPrice = priceOf(material);
        if (unitPrice <= 0) {
            return SellResult.EMPTY;
        }
        return doSell(player, Map.of(material, unitPrice));
    }

    /**
     * 賣出玩家背包內所有可收購的礦物。
     */
    public SellResult sellAll(Player player) {
        return doSell(player, getPriceTable());
    }

    private SellResult doSell(Player player, Map<Material, Double> prices) {
        EconomyHook economy = plugin.getEconomyHook();
        if (economy == null || !economy.isEnabled()) {
            return SellResult.EMPTY;
        }
        Inventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int totalItems = 0;
        double totalMoney = 0.0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) {
                continue;
            }
            Double unit = prices.get(stack.getType());
            if (unit == null || unit <= 0 || !isSellableStack(stack, stack.getType())) {
                continue;
            }
            int amount = stack.getAmount();
            totalItems += amount;
            totalMoney += ShopPricing.subtotal(amount, unit);
            inv.setItem(i, null);
        }
        if (totalItems <= 0) {
            return SellResult.EMPTY;
        }
        economy.deposit(player, totalMoney);
        return new SellResult(totalItems, totalMoney);
    }

    /**
     * 僅出售「乾淨」物品：無自訂名稱、無附魔，避免誤賣玩家命名或附魔的特殊物品。
     */
    private boolean isSellableStack(ItemStack stack, Material material) {
        if (stack == null || stack.getType() != material) {
            return false;
        }
        if (!stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && !meta.hasDisplayName() && !meta.hasEnchants();
    }

    /**
     * 賣出結果。
     *
     * @param items 賣出的物品總數
     * @param money 獲得的金額
     */
    public record SellResult(int items, double money) {
        public static final SellResult EMPTY = new SellResult(0, 0.0);

        public boolean isEmpty() {
            return items <= 0;
        }
    }
}
