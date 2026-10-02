package ru.mysticchest.effects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.core.Animator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Navigation: /mystic track (or the [Track] button) shows an arrow and the distance to a chest in the action bar,
 * pointing relative to where you are looking, and sets the compass target. It ends on arrival, when the chest
 * is gone or after navigation.duration-seconds. One shared animation, alive only while somebody is tracking.
 */
public final class Tracker implements Animator.Animation {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static final class Session {
        final ChestManager.Active chest;
        final long until;
        Session(ChestManager.Active c, long u) { chest = c; until = u; }
    }

    private final MysticChestPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();
    private boolean running;
    private int tick;

    public Tracker(MysticChestPlugin plugin) { this.plugin = plugin; }

    public boolean start(Player p, ChestManager.Active a) {
        if (!plugin.settings().root.sub("navigation").bool("enabled", true)) return false;
        if (a == null || a.loc.getWorld() != p.getWorld()) return false;
        int secs = plugin.settings().root.sub("navigation").integer("duration-seconds", 180, 5, 3600);
        sessions.put(p.getUniqueId(), new Session(a, System.currentTimeMillis() + secs * 1000L));
        try { p.setCompassTarget(a.loc); } catch (Throwable ignored) {}
        if (!running) { running = true; plugin.animator().add(this); }
        return true;
    }

    public void stop(UUID id) { sessions.remove(id); }

    public boolean tick() {
        if (sessions.isEmpty()) { running = false; return false; }
        int every = plugin.settings().root.sub("navigation").integer("update-ticks", 8, 2, 40);
        if (++tick % every != 0) return true;
        int stopAt = plugin.settings().root.sub("navigation").integer("arrive-radius", 4, 1, 50);
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Session s = e.getValue();
            if (p == null || !p.isOnline() || now > s.until || !plugin.chests().isActive(s.chest.loc.getBlock()) || p.getWorld() != s.chest.loc.getWorld()) {
                if (p != null && p.isOnline()) plugin.lang().send(p, "nav.ended");
                it.remove();
                continue;
            }
            Location pl = p.getLocation(), t = s.chest.loc;
            double dx = t.getX() + 0.5 - pl.getX(), dz = t.getZ() + 0.5 - pl.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= stopAt) {
                plugin.lang().send(p, "nav.arrived", "tier", s.chest.tier.name(plugin.lang().code(p)));
                plugin.effects().soundTo(p, "ENTITY_PLAYER_LEVELUP", 1f, 1.5f);
                it.remove();
                continue;
            }
            double target = Math.toDegrees(Math.atan2(-dx, dz));          // yaw that would face the chest
            double diff = target - pl.getYaw();
            while (diff > 180) diff -= 360;
            while (diff < -180) diff += 360;
            String arrow = ARROWS[((int) Math.round(diff / 45.0) % 8 + 8) % 8];
            String color = dist < 20 ? "&a" : (dist < 60 ? "&e" : "&6");
            Effects.actionBar(p, plugin.lang().get(p, "nav.bar", "arrow", ru.mysticchest.util.Text.color(color + "&l" + arrow),
                    "distance", String.valueOf((int) Math.round(dist)), "tier", s.chest.tier.name(plugin.lang().code(p)),
                    "time", plugin.lang().time(p, plugin.chests().secondsLeft(s.chest))));
        }
        return true;
    }

    public void abort() { sessions.clear(); running = false; }
}
