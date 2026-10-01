package ru.mysticchest.chest;

import ru.mysticchest.util.WeightedPool;

import java.util.List;
import java.util.Random;

/** Weighted pool + a lazily-built "rare only" pool used by pity. */
public final class LootPool {
    private static final WeightedPool.Weigher<LootEntry> W = new WeightedPool.Weigher<LootEntry>() {
        public int weight(LootEntry e) { return e.weight; }
    };
    private static final WeightedPool.Filter<LootEntry> RARE = new WeightedPool.Filter<LootEntry>() {
        public boolean test(LootEntry e) { return e.rare; }
    };

    public final WeightedPool<LootEntry> all;
    private WeightedPool<LootEntry> rare;

    public LootPool(List<LootEntry> entries, double rareBelowPercent) {
        this.all = new WeightedPool<LootEntry>(entries, W);
        for (int i = 0; i < all.size(); i++) {
            LootEntry e = all.get(i);
            e.chance = all.percent(i);
            e.rare = e.chance <= rareBelowPercent;
        }
    }

    public boolean isEmpty() { return all.isEmpty(); }

    public LootEntry pick(Random r) { return all.pick(r); }

    public boolean hasRare() { return rarePool().size() > 0; }

    public LootEntry pickRare(Random r) {
        WeightedPool<LootEntry> p = rarePool();
        return p.isEmpty() ? all.pick(r) : p.pick(r);
    }

    private WeightedPool<LootEntry> rarePool() {
        if (rare == null) rare = all.filtered(RARE, W);
        return rare;
    }
}
