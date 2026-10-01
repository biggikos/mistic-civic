package ru.mysticchest.core;

import java.util.concurrent.atomic.AtomicLong;

/** Cheap nanosecond counters shown by /mystic perf. */
public final class Metrics {
    public static final AtomicLong ROLLS = new AtomicLong(), ROLL_NS = new AtomicLong();
    public static final AtomicLong OPENS = new AtomicLong(), OPEN_NS = new AtomicLong();
    public static final AtomicLong SAVES = new AtomicLong(), SAVE_BYTES = new AtomicLong();
    public static volatile long lastSaveAt;

    private Metrics() {}

    /** Exponential moving averages (ns): what the plugin costs NOW, without the cold first calls. */
    public static volatile double rollRecent, openRecent;
    public static volatile long rollMax, openMax;

    public static void roll(long startNs) {
        long d = System.nanoTime() - startNs;
        ROLLS.incrementAndGet(); ROLL_NS.addAndGet(d);
        rollRecent = rollRecent == 0 ? d : rollRecent * 0.9 + d * 0.1;
        if (d > rollMax) rollMax = d;
    }

    public static void open(long startNs) {
        long d = System.nanoTime() - startNs;
        OPENS.incrementAndGet(); OPEN_NS.addAndGet(d);
        openRecent = openRecent == 0 ? d : openRecent * 0.9 + d * 0.1;
        if (d > openMax) openMax = d;
    }

    public static double avgMicros(AtomicLong ns, AtomicLong n) {
        long c = n.get();
        return c == 0 ? 0 : ns.get() / 1000.0 / c;
    }
}
