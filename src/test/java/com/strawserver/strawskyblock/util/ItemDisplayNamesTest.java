package com.strawserver.strawskyblock.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemDisplayNamesTest {

    @Test
    void humanizeSingleWord() {
        assertEquals("Coal", ItemDisplayNames.humanize("COAL"));
        assertEquals("Diamond", ItemDisplayNames.humanize("DIAMOND"));
    }

    @Test
    void humanizeMultiWord() {
        assertEquals("Raw Copper", ItemDisplayNames.humanize("RAW_COPPER"));
        assertEquals("Lapis Lazuli", ItemDisplayNames.humanize("LAPIS_LAZULI"));
        assertEquals("Amethyst Shard", ItemDisplayNames.humanize("AMETHYST_SHARD"));
        assertEquals("Ancient Debris", ItemDisplayNames.humanize("ANCIENT_DEBRIS"));
    }

    @Test
    void humanizeStripsNamespace() {
        assertEquals("Raw Copper", ItemDisplayNames.humanize("minecraft:raw_copper"));
    }

    @Test
    void humanizeHandlesNullAndBlank() {
        assertEquals("", ItemDisplayNames.humanize(null));
        assertEquals("", ItemDisplayNames.humanize("   "));
        assertEquals("", ItemDisplayNames.humanize(""));
    }

    @Test
    void humanizeHandlesExtraUnderscores() {
        assertEquals("Raw Iron", ItemDisplayNames.humanize("RAW__IRON"));
        assertEquals("Raw Gold", ItemDisplayNames.humanize("_RAW_GOLD_"));
    }
}
