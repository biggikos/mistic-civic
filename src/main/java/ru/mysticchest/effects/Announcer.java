package ru.mysticchest.effects;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.config.Settings;

/** Sends a localized message to each recipient in their own language. */
public final class Announcer {
    private final MysticChestPlugin plugin;

    public Announcer(MysticChestPlugin plugin) { this.plugin = plugin; }

    public void send(Settings.Announce a, Location origin, String key, Tier tier, String... kv) {
        if (a.type == Settings.AnnounceType.NONE) return;
        double r2 = a.radius < 0 ? -1 : (double) a.radius * a.radius;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (origin != null) {
                if (a.worldOnly && p.getWorld() != origin.getWorld()) continue;
                if (r2 >= 0 && (p.getWorld() != origin.getWorld() || p.getLocation().distanceSquared(origin) > r2)) continue;
            }
            String[] all = new String[kv.length + 2];
            System.arraycopy(kv, 0, all, 0, kv.length);
            all[kv.length] = "tier";
            all[kv.length + 1] = tier == null ? "" : tier.name(plugin.lang().code(p));
            deliver(a, p, plugin.lang().get(p, key, all));
        }
    }

    public boolean inRange(Settings.Announce a, Location origin, Player p) {
        if (a.type == Settings.AnnounceType.NONE) return false;
        if (origin == null) return true;
        if (a.worldOnly && p.getWorld() != origin.getWorld()) return false;
        return a.radius < 0 || (p.getWorld() == origin.getWorld()
                && p.getLocation().distanceSquared(origin) <= (double) a.radius * a.radius);
    }

    public void deliver(Settings.Announce a, Player p, String msg) {
        switch (a.type) {
            case TITLE:
                try { p.sendTitle("", msg, 5, 50, 10); } catch (Throwable t) { p.sendMessage(msg); }
                break;
            case ACTIONBAR:
                Effects.actionBar(p, msg);
                break;
            case NONE:
                break;
            default:
                p.sendMessage(plugin.lang().get(p, "prefix") + msg);
        }
    }
}
