package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.economy.EconomyHook;
import com.strawserver.strawskyblock.shop.ShopService;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 官方商城 GUI：玩家可點擊礦物將背包內該礦物全部賣給伺服器。
 */
public class IslandShopGui extends Gui {

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SELL_ALL_SLOT = 49;
    private static final int BACK_SLOT = 45;

    private final Map<Integer, Material> slotMaterials = new HashMap<>();

    public IslandShopGui(StrawSkyBlockPlugin plugin) {
        super(plugin, 54, MiniMessageUtil.parse("<gold>官方商城 - 礦物收購"));
    }

    @Override
    protected void build(Player player) {
        inventory.clear();
        slotMaterials.clear();

        ShopService shop = plugin.getShopService();
        EconomyHook economy = plugin.getEconomyHook();
        fillBackground();

        Map<Material, Double> prices = shop.getPriceTable();
        int index = 0;
        for (Map.Entry<Material, Double> entry : prices.entrySet()) {
            if (index >= CONTENT_SLOTS.length) {
                break;
            }
            Material material = entry.getKey();
            double unit = entry.getValue();
            int owned = shop.countSellable(player, material);
            int slot = CONTENT_SLOTS[index];
            String unitText = economy != null ? economy.format(unit) : String.valueOf(unit);
            String totalText = economy != null ? economy.format(unit * owned) : String.valueOf(unit * owned);
            set(slot, new ItemBuilder(material)
                    .name("<yellow>" + plugin.getMessageManager().getItemName(material))
                    .lore(
                            "<gray>收購單價：<green>" + unitText,
                            "<gray>背包數量：<aqua>" + owned,
                            "<gray>可換取：<gold>" + totalText,
                            "",
                            "<green>▶ 點擊賣出背包內全部")
                    .build());
            slotMaterials.put(slot, material);
            index++;
        }

        set(SELL_ALL_SLOT, new ItemBuilder(Material.HOPPER)
                .name("<gold>一鍵賣出全部礦物")
                .lore("<gray>賣出背包內所有可收購礦物。")
                .glow(true)
                .build());
        set(BACK_SLOT, new ItemBuilder(Material.ARROW).name("<gray>返回主選單").build());
    }

    private void fillBackground() {
        ItemBuilder filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ");
        for (int i = 0; i < inventory.getSize(); i++) {
            set(i, filler.build());
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            new IslandMainGui(plugin).open(player);
            return;
        }

        ShopService shop = plugin.getShopService();
        if (!shop.isAvailable()) {
            player.closeInventory();
            plugin.getMessageManager().send(player, "shop.no-economy");
            return;
        }

        if (slot == SELL_ALL_SLOT) {
            ShopService.SellResult result = shop.sellAll(player);
            announce(player, result);
            build(player);
            return;
        }

        Material material = slotMaterials.get(slot);
        if (material != null) {
            ShopService.SellResult result = shop.sellMaterial(player, material);
            announce(player, result);
            build(player);
        }
    }

    private void announce(Player player, ShopService.SellResult result) {
        if (result.isEmpty()) {
            plugin.getMessageManager().send(player, "shop.nothing");
            return;
        }
        EconomyHook economy = plugin.getEconomyHook();
        String money = economy != null ? economy.format(result.money()) : String.valueOf(result.money());
        plugin.getMessageManager().send(player, "shop.sold", MessageManager.placeholders(
                "amount", String.valueOf(result.items()),
                "money", money));
    }
}
