package com.strawserver.strawskyblock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiniMessageUtilTest {

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void replacesCurlyBracePlaceholders() {
        Map<String, String> ph = new LinkedHashMap<>();
        ph.put("x", "1");
        ph.put("y", "2");
        ph.put("z", "3");
        Component c = MiniMessageUtil.parse("座標 <yellow>{x}, {y}, {z}</yellow>", ph);
        assertEquals("座標 1, 2, 3", plain(c));
    }

    @Test
    void replacesRepeatedAndMultiplePlaceholders() {
        Map<String, String> ph = new LinkedHashMap<>();
        ph.put("player", "Steve");
        Component c = MiniMessageUtil.parse("{player} 與 {player}", ph);
        assertEquals("Steve 與 Steve", plain(c));
    }

    @Test
    void nullValueBecomesEmpty() {
        Map<String, String> ph = new LinkedHashMap<>();
        ph.put("cost", null);
        Component c = MiniMessageUtil.parse("需要 {cost} 元", ph);
        assertEquals("需要  元", plain(c));
    }

    @Test
    void noPlaceholdersLeavesTextIntact() {
        Component c = MiniMessageUtil.parse("<green>你好");
        assertEquals("你好", plain(c));
    }
}
