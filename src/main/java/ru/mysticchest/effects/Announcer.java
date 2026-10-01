package ru.mysticchest.effects;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.config.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends a localized message to each recipient in their own language. Besides the placeholders you pass,
 * it fills {loot} (rarest rewards of the tier), {mode} (from "modeid"), {ttl} (from "ttlsec") and
 * {structure} (a ready-made line from "structid", empty when there is none). Lines that end up empty are dropped,
 * and a language value may be a list (a multi-line announcement).
 */
public final class Announcer {
    private final MysticChestPlugin plugin;

    public Announcer(MysticChestPlugin plugin) { this.plugin = plugin; }

    private static String find(String[] kv, String key) {
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i].equals(key)) return kv[i + 1];
        return null;
    }

    private String[] enrich(Player p, Tier tier, String[] kv) {
        List<String> all = new ArrayList<String>();
        for (String s : kv) all.add(s);
        all.add("tier"); all.add(tier == null ? "" : tier.name(plugin.lang().code(p)));
        all.add("loot"); all.add(tier == null ? "" : tier.highlights());
        String mode = find(kv, "modeid");
        if (mode != null) { all.add("mode"); all.add(plugin.lang().get(p, "mode." + mode.toLowerCase())); }
        String ttl = find(kv, "ttlsec");
        if (ttl != null) { all.add("ttl"); all.add(plugin.lang().time(p, Long.parseLong(ttl))); }
        String st = find(kv, "structid");
        if (st != null) {
            all.add("structure");
            if (st.isEmpty()) all.add("");
            else {
                String key = "structure.name." + st;
                all.add(plugin.lang().get(p, "announce.structure-line", "name", plugin.lang().has(p, key) ? plugin.lang().get(p, key) : st));
            }
        }
        return all.toArray(new String[0]);
    }

    private static boolean blank(String line) {
        return org.bukkit.ChatColor.stripColor(line).trim().isEmpty();
    }

    public void send(Settings.Announce a, Location origin, String key, Tier tier, String... kv) {
        if (a.type == Settings.AnnounceType.NONE) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!inRange(a, origin, p)) continue;
            String[] all = enrich(p, tier, kv);
            List<String> lines = new ArrayList<String>();
            if (plugin.lang().isList(p, key)) {
                for (String l : plugin.lang().list(p, key, all)) if (!blank(l)) lines.add(l);
            } else {
                lines.add(plugin.lang().get(p, key, all));
            }
            deliver(a, p, lines);
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
        List<String> l = new ArrayList<String>();
        l.add(msg);
        deliver(a, p, l);
    }

    public void deliver(Settings.Announce a, Player p, List<String> lines) {
        if (lines.isEmpty()) return;
        switch (a.type) {
            case TITLE:
                try { p.sendTitle(lines.get(0), lines.size() > 1 ? lines.get(1) : "", 5, 70, 10); } catch (Throwable t) { p.sendMessage(lines.get(0)); }
                break;
            case ACTIONBAR:
                Effects.actionBar(p, lines.get(0));
                break;
            case NONE:
                return;
            default:
                String prefix = plugin.lang().get(p, "prefix");
                for (int i = 0; i < lines.size(); i++) p.sendMessage((lines.size() == 1 ? prefix : "") + lines.get(i));
        }
        plugin.effects().soundTo(p, a.sound, a.volume, a.pitch);
    }
}
