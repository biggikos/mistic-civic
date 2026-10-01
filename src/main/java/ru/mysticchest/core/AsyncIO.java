package ru.mysticchest.core;

import ru.mysticchest.MysticChestPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Debounced, atomic file writer. Content is produced on the main thread (cheap string building),
 * the disk write happens on a single daemon thread. A crash can never leave a half-written file
 * because data goes to "<file>.tmp" first and is then moved over the target.
 */
public final class AsyncIO {
    public interface Source { String content(); }

    private final MysticChestPlugin plugin;
    private final Scheduler scheduler;
    private final Map<File, Source> pending = new LinkedHashMap<File, Source>();
    private ExecutorService executor;
    private boolean async = true;
    private long intervalMs = 5000;

    public AsyncIO(MysticChestPlugin plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public void configure(boolean async, int intervalSeconds) {
        this.async = async;
        this.intervalMs = Math.max(1, intervalSeconds) * 1000L;
        if (async && executor == null) {
            executor = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "MysticChest-IO");
                    t.setDaemon(true);
                    return t;
                }
            });
        }
    }

    public int pendingCount() { return pending.size(); }

    /** Marks a file dirty; the newest source wins and is written after the debounce interval. */
    public void request(final File file, Source src) {
        boolean first = !pending.containsKey(file);
        pending.put(file, src);
        if (first) {
            scheduler.later(intervalMs, new Runnable() {
                public void run() { flush(file); }
            });
        }
    }

    private void flush(File file) {
        Source s = pending.remove(file);
        if (s == null) return;
        final String content = s.content();
        if (async && executor != null) {
            executor.execute(new Runnable() {
                public void run() { write(file, content); }
            });
        } else {
            write(file, content);
        }
    }

    /** Synchronous: writes everything that is still pending and waits for the writer thread. */
    public void shutdown() {
        for (File f : new java.util.ArrayList<File>(pending.keySet())) {
            Source s = pending.remove(f);
            if (s != null) write(f, s.content());
        }
        if (executor != null) {
            executor.shutdown();
            try { executor.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            executor = null;
        }
    }

    public void writeNow(File file, String content) { write(file, content); }

    private void write(File file, String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            File tmp = new File(file.getPath() + ".tmp");
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(tmp.toPath(), bytes);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Metrics.SAVES.incrementAndGet();
            Metrics.SAVE_BYTES.addAndGet(bytes.length);
            Metrics.lastSaveAt = System.currentTimeMillis();
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot write " + file.getName() + ": " + e.getMessage());
        }
    }
}
