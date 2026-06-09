package com.strawserver.strawskyblock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;

/**
 * MiniMessage 解析輔助工具。所有面向玩家的文字都透過此類別轉成 Component。
 */
public final class MiniMessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MiniMessageUtil() {
    }

    public static Component parse(String input) {
        if (input == null) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(input);
    }

    public static Component parse(String input, TagResolver... resolvers) {
        if (input == null) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(input, resolvers);
    }

    /**
     * 以 {@code {key}} 形式的佔位符解析字串。
     *
     * <p>佔位符採大括號語法（如 {@code {x}}），於 MiniMessage 解析前先以字面值替換，
     * 避免與 MiniMessage 的 {@code <tag>} 標籤語法混淆而導致佔位符未被取代。</p>
     */
    public static Component parse(String input, Map<String, String> placeholders) {
        if (input == null) {
            return Component.empty();
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return MINI_MESSAGE.deserialize(input);
        }
        String replaced = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            replaced = replaced.replace("{" + entry.getKey() + "}", value);
        }
        return MINI_MESSAGE.deserialize(replaced);
    }

    /**
     * 為物品 / GUI 文字移除預設斜體。
     */
    public static Component item(String input) {
        return parse(input).decoration(TextDecoration.ITALIC, false);
    }

    public static Component item(String input, Map<String, String> placeholders) {
        return parse(input, placeholders).decoration(TextDecoration.ITALIC, false);
    }
}
