package ru.mysticchest.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Cumulative-weight pool: O(log n) picks, rebuilt only when the content changes. */
public final class WeightedPool<T> {
    public interface Weigher<T> { int weight(T t); }
    public interface Filter<T> { boolean test(T t); }

    private final List<T> items;
    private final long[] cum;
    private final long total;

    public WeightedPool(List<T> source, Weigher<T> w) {
        this.items = new ArrayList<T>(source);
        this.cum = new long[items.size()];
        long t = 0;
        for (int i = 0; i < cum.length; i++) {
            t += Math.max(1, w.weight(items.get(i)));
            cum[i] = t;
        }
        this.total = t;
    }

    public int size() { return items.size(); }
    public boolean isEmpty() { return items.isEmpty(); }
    public long total() { return total; }
    public T get(int i) { return items.get(i); }
    public List<T> all() { return items; }

    public T pick(Random r) {
        if (items.isEmpty()) return null;
        long x = (long) (r.nextDouble() * total);
        int lo = 0, hi = cum.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] > x) hi = mid; else lo = mid + 1;
        }
        return items.get(lo);
    }

    /** Chance of item #i in percent. */
    public double percent(int i) {
        if (total == 0) return 0;
        long w = cum[i] - (i == 0 ? 0 : cum[i - 1]);
        return w * 100.0 / total;
    }

    public WeightedPool<T> filtered(Filter<T> f, Weigher<T> w) {
        List<T> out = new ArrayList<T>();
        for (T t : items) if (f.test(t)) out.add(t);
        return new WeightedPool<T>(out, w);
    }
}
