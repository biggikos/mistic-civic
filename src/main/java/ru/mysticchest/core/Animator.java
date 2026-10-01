package ru.mysticchest.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/** Single shared 1-tick ticker for all animations; it only exists while something is animating. */
public final class Animator {
    public interface Animation {
        /** @return false when finished */
        boolean tick();
        /** Called when the animator is shut down; must leave no loot behind. */
        void abort();
    }

    private final Plugin plugin;
    private final List<Animation> running = new ArrayList<Animation>();
    private BukkitTask task;

    public Animator(Plugin plugin) { this.plugin = plugin; }

    public int size() { return running.size(); }

    public void add(Animation a) {
        running.add(a);
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                public void run() { tick(); }
            }, 1L, 1L);
        }
    }

    private void tick() {
        for (int i = running.size() - 1; i >= 0; i--) {
            Animation a = running.get(i);
            boolean alive;
            try {
                alive = a.tick();
            } catch (Throwable t) {
                plugin.getLogger().warning("Animation failed: " + t);
                try { a.abort(); } catch (Throwable ignored) {}
                alive = false;
            }
            if (!alive) running.remove(i);
        }
        if (running.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        for (Animation a : new ArrayList<Animation>(running)) {
            try { a.abort(); } catch (Throwable ignored) {}
        }
        running.clear();
        if (task != null) { task.cancel(); task = null; }
    }
}
