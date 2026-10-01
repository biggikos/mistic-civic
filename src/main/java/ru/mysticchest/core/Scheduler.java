package ru.mysticchest.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * One deadline queue for everything time-based (chest TTLs, spawn timers, debounced saves).
 * Exactly one BukkitTask exists while the queue is non-empty and none while it is empty.
 */
public final class Scheduler {
    public final class Handle {
        final long at;
        final Runnable run;
        boolean done;

        Handle(long at, Runnable run) { this.at = at; this.run = run; }

        public void cancel() { Scheduler.this.cancel(this); }
    }

    private final Plugin plugin;
    private final PriorityQueue<Handle> queue = new PriorityQueue<Handle>(16, new Comparator<Handle>() {
        public int compare(Handle a, Handle b) { return a.at < b.at ? -1 : (a.at == b.at ? 0 : 1); }
    });
    private BukkitTask wake;
    private long wakeAt = Long.MAX_VALUE;

    public Scheduler(Plugin plugin) { this.plugin = plugin; }

    public Handle later(long delayMs, Runnable r) {
        Handle h = new Handle(System.currentTimeMillis() + Math.max(0, delayMs), r);
        queue.add(h);
        rearm();
        return h;
    }

    public int pending() { return queue.size(); }
    public boolean awake() { return wake != null; }

    private void cancel(Handle h) {
        if (h.done) return;
        h.done = true;
        queue.remove(h);
        rearm();
    }

    private void rearm() {
        if (queue.isEmpty()) {
            if (wake != null) { wake.cancel(); wake = null; }
            wakeAt = Long.MAX_VALUE;
            return;
        }
        long at = queue.peek().at;
        if (wake != null && wakeAt <= at) return;       // an earlier wake-up will re-arm itself
        if (wake != null) wake.cancel();
        long ticks = Math.max(1L, (at - System.currentTimeMillis() + 49) / 50);
        wakeAt = at;
        wake = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() { fire(); }
        }, ticks);
    }

    private void fire() {
        wake = null;
        wakeAt = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        while (!queue.isEmpty() && queue.peek().at <= now) {
            Handle h = queue.poll();
            h.done = true;
            try {
                h.run.run();
            } catch (Throwable t) {
                plugin.getLogger().warning("Scheduled task failed: " + t);
            }
        }
        rearm();
    }

    public void shutdown() {
        if (wake != null) wake.cancel();
        wake = null;
        wakeAt = Long.MAX_VALUE;
        queue.clear();
    }
}
