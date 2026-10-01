package ru.mysticchest.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.core.Scheduler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** "Type your answer in chat" prompts with a timeout; cleaned up on quit and shutdown. */
public final class ChatPrompt {
    public interface Callback { void answered(Player p, String text); }

    private static final long TIMEOUT_MS = 60000L;
    private final MysticChestPlugin plugin;
    private final Map<UUID, Callback> waiting = new ConcurrentHashMap<UUID, Callback>();
    private final Map<UUID, Scheduler.Handle> timers = new ConcurrentHashMap<UUID, Scheduler.Handle>();

    public ChatPrompt(MysticChestPlugin plugin) { this.plugin = plugin; }

    public void ask(final Player p, String lang, Callback cb, String... kv) {
        cancel(p.getUniqueId());
        waiting.put(p.getUniqueId(), cb);
        plugin.lang().send(p, lang, kv);
        timers.put(p.getUniqueId(), plugin.scheduler().later(TIMEOUT_MS, new Runnable() {
            public void run() {
                if (waiting.remove(p.getUniqueId()) != null) {
                    timers.remove(p.getUniqueId());
                    if (p.isOnline()) plugin.lang().send(p, "prompt.timeout");
                }
            }
        }));
    }

    public boolean isWaiting(UUID id) { return waiting.containsKey(id); }

    /** Called from the async chat thread. */
    public void answer(final Player p, final String text) {
        final Callback cb = waiting.remove(p.getUniqueId());
        if (cb == null) return;
        Scheduler.Handle h = timers.remove(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            public void run() {
                if (h != null) h.cancel();
                cb.answered(p, text);
            }
        });
    }

    public void cancel(UUID id) {
        waiting.remove(id);
        Scheduler.Handle h = timers.remove(id);
        if (h != null) h.cancel();
    }

    public void clear() {
        for (Scheduler.Handle h : timers.values()) h.cancel();
        timers.clear();
        waiting.clear();
    }
}
