package com.strawserver.strawskyblock.config;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.util.ItemDisplayNames;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 載入 messages.yml 並提供前綴訊息發送。
 */
public class MessageManager {

    private final StrawSkyBlockPlugin plugin;
    private YamlConfiguration messages;
    private String prefix = "";

    public MessageManager(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);

        // 與內建預設合併，確保新版本新增的訊息不會缺漏
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration def = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
            this.messages.setDefaults(def);
            this.messages.options().copyDefaults(true);
            try {
                this.messages.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("無法儲存 messages.yml: " + e.getMessage());
            }
        }
        this.prefix = messages.getString("prefix", "");
    }

    public String rawString(String path) {
        return messages.getString(path, path);
    }

    /**
     * 取得材質的中文顯示名稱。
     *
     * <p>優先讀取 messages.yml 的 {@code item-names.<MATERIAL>}；若未設定則退回以
     * {@link ItemDisplayNames#humanize(String)} 友善化後的列舉名稱，確保玩家永遠不會
     * 直接看到原始列舉字串（例如 {@code RAW_COPPER}）。</p>
     *
     * @param material 材質，允許 {@code null}
     * @return 用於顯示的名稱字串
     */
    public String getItemName(Material material) {
        if (material == null) {
            return "";
        }
        String key = material.name();
        String configured = messages.getString("item-names." + key);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return ItemDisplayNames.humanize(key);
    }

    public Component get(String path) {
        return MiniMessageUtil.parse(rawString(path));
    }

    public Component get(String path, Map<String, String> placeholders) {
        return MiniMessageUtil.parse(rawString(path), placeholders);
    }

    public Component getPrefixed(String path) {
        return MiniMessageUtil.parse(prefix + rawString(path));
    }

    public Component getPrefixed(String path, Map<String, String> placeholders) {
        return MiniMessageUtil.parse(prefix + rawString(path), placeholders);
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(getPrefixed(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(getPrefixed(path, placeholders));
    }

    public static Map<String, String> placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
