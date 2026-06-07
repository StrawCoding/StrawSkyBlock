package com.strawserver.strawskyblock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
     */
    public static Component parse(String input, Map<String, String> placeholders) {
        if (input == null) {
            return Component.empty();
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return MINI_MESSAGE.deserialize(input);
        }
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(e -> Placeholder.parsed(e.getKey(), e.getValue() == null ? "" : e.getValue()))
                .toArray(TagResolver[]::new);
        return MINI_MESSAGE.deserialize(input, resolvers);
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
