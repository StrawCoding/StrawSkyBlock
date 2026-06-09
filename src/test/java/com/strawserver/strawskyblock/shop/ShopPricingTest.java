package com.strawserver.strawskyblock.shop;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopPricingTest {

    @Test
    void subtotalMultipliesAmountByUnitPrice() {
        assertEquals(20.0, ShopPricing.subtotal(10, 2.0), 1e-9);
    }

    @Test
    void subtotalReturnsZeroForNonPositiveInputs() {
        assertEquals(0.0, ShopPricing.subtotal(0, 5.0), 1e-9);
        assertEquals(0.0, ShopPricing.subtotal(-3, 5.0), 1e-9);
        assertEquals(0.0, ShopPricing.subtotal(10, 0.0), 1e-9);
        assertEquals(0.0, ShopPricing.subtotal(10, -2.0), 1e-9);
    }

    @Test
    void totalPayoutSumsOnlyPricedMaterials() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("COAL", 64);
        counts.put("DIAMOND", 3);
        counts.put("DIRT", 100);

        Map<String, Double> prices = new HashMap<>();
        prices.put("COAL", 2.0);
        prices.put("DIAMOND", 20.0);

        assertEquals(64 * 2.0 + 3 * 20.0, ShopPricing.totalPayout(counts, prices), 1e-9);
    }

    @Test
    void totalPayoutHandlesNullArguments() {
        assertEquals(0.0, ShopPricing.totalPayout(null, new HashMap<>()), 1e-9);
        assertEquals(0.0, ShopPricing.totalPayout(new HashMap<>(), null), 1e-9);
    }

    @Test
    void totalPayoutIgnoresZeroAndNegativeCounts() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("COAL", 0);
        counts.put("IRON_INGOT", -5);

        Map<String, Double> prices = new HashMap<>();
        prices.put("COAL", 2.0);
        prices.put("IRON_INGOT", 5.0);

        assertEquals(0.0, ShopPricing.totalPayout(counts, prices), 1e-9);
    }
}
