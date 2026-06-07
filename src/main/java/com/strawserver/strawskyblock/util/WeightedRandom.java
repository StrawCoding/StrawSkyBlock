package com.strawserver.strawskyblock.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 權重隨機抽取器。用於礦物掉落與動物生成。
 *
 * @param <T> 抽取的項目型別
 */
public class WeightedRandom<T> {

    private record Entry<T>(T value, double weight) {
    }

    private final List<Entry<T>> entries = new ArrayList<>();
    private double totalWeight = 0.0D;

    public WeightedRandom<T> add(T value, double weight) {
        if (weight <= 0) {
            return this;
        }
        entries.add(new Entry<>(value, weight));
        totalWeight += weight;
        return this;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public double totalWeight() {
        return totalWeight;
    }

    /**
     * 依照權重抽取一個項目，若為空則回傳 null。
     */
    public T roll() {
        if (entries.isEmpty()) {
            return null;
        }
        double r = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cursor = 0.0D;
        for (Entry<T> entry : entries) {
            cursor += entry.weight();
            if (r < cursor) {
                return entry.value();
            }
        }
        return entries.get(entries.size() - 1).value();
    }
}
