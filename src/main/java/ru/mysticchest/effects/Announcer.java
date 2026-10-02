package ru.mysticchest.effects;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.config.Settings;
import ru.mysticchest.core.Animator;
import ru.mysticchest.util.Chat;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends a localized announcement to each recipient in their own language, with:
 *  - channels (chat, title, action bar) per event, filters (radius, world, tier list, permission, muted players),
 *  - a typewriter effect for multi-line messages (a line every few ticks, a rising tick sound),
 *  - an animated shimmering title,
 *  - clickable buttons (track, copy coordinates, compass, mute, ...) from lang/*.yml ("button.*").
 * Placeholders filled automatically: {loot}, {mode} (modeid), {ttl} (ttlsec), {structure} (structid), {buttons}.
 */
public final class Announcer {
    public interface Extra { String[] kv(Player p); }

    private static final String[] SHIMMER = {"c", "6", "e", "a", "b", "d"};
    private final MysticChestPlugin plugin;

    public Announcer(MysticChestPlugin plugin) { this.plugin = plugin; }

    private static String find(String[] kv, String key) {
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i].equals(key)) return kv[i + 1];
        return null;
    }

    private static String[] join(String[] a, String[] b) {
        if (b == null) return a;
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
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
        String gc = find(kv, "guardcount");
        all.add("guards");
        all.add(gc == null || gc.isEmpty() || gc.equals("0") ? "" : plugin.lang().get(p, "announce.guards-line", "count", gc, "level", find(kv, "guardlevel") == null ? "" : find(kv, "guardlevel")));
        String id = find(kv, "chestid");
        if (id != null) { all.add("id"); all.add(id); }
        return all.toArray(new String[0]);
    }

    private static boolean blank(String line) { return org.bukkit.ChatColor.stripColor(line).trim().isEmpty(); }

    /** The row of buttons for this recipient. Track/copy/compass only exist when the exact place is public. */
    private String buttons(Settings.Announce a, Player p, String[] all) {
        if (a.buttons.isEmpty()) return "";
        boolean exact = "true".equals(find(all, "exact"));
        boolean hasId = find(all, "id") != null;
        StringBuilder sb = new StringBuilder();
        for (String b : a.buttons) {
            boolean place = b.equals("TRACK") || b.equals("COPY") || b.equals("COMPASS");
            if (place && (!exact || (!hasId && !b.equals("COMPASS")))) continue;
            String key = "button." + b.toLowerCase();
            if (!plugin.lang().has(p, key)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(plugin.lang().get(p, key, all));
        }
        return sb.toString();
    }

    public void send(Settings.Announce a, Location origin, String key, Tier tier, String... kv) {
        sendEach(a, origin, key, tier, kv, null);
    }

    public void sendEach(Settings.Announce a, Location origin, String key, Tier tier, String[] kv, Extra extra) {
        if (!a.enabled) return;
        List<Delivery> out = new ArrayList<Delivery>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!inRange(a, origin, p)) continue;
            if (!a.tiers.isEmpty() && tier != null && !a.tiers.contains(tier.id)) continue;
            if (!a.permission.isEmpty() && !p.hasPermission(a.permission)) continue;
            if (a.mutable && plugin.prefs().muted(p.getUniqueId())) continue;
            String[] all = enrich(p, tier, join(kv, extra == null ? null : extra.kv(p)));
            String btn = buttons(a, p, all);
            String[] withBtn = join(all, new String[]{"buttons", btn});
            List<String> lines = new ArrayList<String>();
            if (plugin.lang().isList(p, key)) {
                for (String l : plugin.lang().list(p, key, withBtn)) if (!blank(l)) lines.add(l);
            } else {
                lines.add(plugin.lang().get(p, key, withBtn));
            }
            if (lines.isEmpty()) continue;
            boolean has = false;
            for (String l : lines) if (!btn.isEmpty() && l.contains(btn)) has = true;
            if (!btn.isEmpty() && !has) lines.add(btn);
            // title / subtitle: dedicated lang entries per event, else the first two lines
            String event = key.indexOf('.') > 0 ? key.split("\\.")[1] : key;
            String tKey = "announce.titles." + event;
            String title = plugin.lang().has(p, tKey + ".title") ? plugin.lang().get(p, tKey + ".title", withBtn) : lines.get(0);
            String sub = plugin.lang().has(p, tKey + ".subtitle") ? plugin.lang().get(p, tKey + ".subtitle", withBtn) : (lines.size() > 1 ? lines.get(1) : "");
            out.add(new Delivery(p, lines, title, sub));
        }
        run(a, out);
    }

    public boolean inRange(Settings.Announce a, Location origin, Player p) {
        if (!a.enabled) return false;
        if (origin == null) return true;
        if (a.worldOnly && p.getWorld() != origin.getWorld()) return false;
        return a.radius < 0 || (p.getWorld() == origin.getWorld()
                && p.getLocation().distanceSquared(origin) <= (double) a.radius * a.radius);
    }

    /** Single message without lang processing (rarely needed). */
    public void deliver(Settings.Announce a, Player p, String msg) {
        List<String> l = new ArrayList<String>();
        l.add(msg);
        List<Delivery> d = new ArrayList<Delivery>();
        d.add(new Delivery(p, l, msg, ""));
        run(a, d);
    }

    private static final class Delivery {
        final Player p; final List<String> lines; final String title, sub;
        Delivery(Player p, List<String> lines, String title, String sub) { this.p = p; this.lines = lines; this.title = title; this.sub = sub; }
    }

    private void run(final Settings.Announce a, final List<Delivery> ds) {
        if (ds.isEmpty()) return;
        for (Delivery d : ds) plugin.effects().soundTo(d.p, a.sound, a.volume, a.pitch);
        if (a.channels.contains(Settings.Channel.ACTIONBAR)) {
            for (Delivery d : ds) Effects.actionBar(d.p, d.lines.get(0));
        }
        if (a.channels.contains(Settings.Channel.TITLE)) {
            if (a.titleAnim == Settings.TitleAnim.SHIMMER && plugin.animator().size() < plugin.settings().maxAnimations) {
                plugin.animator().add(new TitleShimmer(ds));
            } else {
                for (Delivery d : ds) {
                    try { d.p.sendTitle(d.title, d.sub, 8, 60, 14); } catch (Throwable t) { d.p.sendMessage(d.title); }
                }
            }
        }
        if (a.channels.contains(Settings.Channel.CHAT)) {
            boolean multi = false;
            for (Delivery d : ds) if (d.lines.size() > 1) multi = true;
            if (a.animated && multi && plugin.animator().size() < plugin.settings().maxAnimations) {
                plugin.animator().add(new Typewriter(a, ds));
            } else {
                for (Delivery d : ds) chat(d);
            }
        }
    }

    private void chat(Delivery d) {
        String prefix = plugin.lang().get(d.p, "prefix");
        for (int i = 0; i < d.lines.size(); i++) Chat.send(d.p, (d.lines.size() == 1 ? prefix : "") + d.lines.get(i));
    }

    /** Lines appear one after another, each with a rising tick. */
    private final class Typewriter implements Animator.Animation {
        final Settings.Announce a; final List<Delivery> ds; int tick, shown;
        Typewriter(Settings.Announce a, List<Delivery> ds) { this.a = a; this.ds = ds; }
        public boolean tick() {
            if (tick++ % a.lineDelay != 0) return true;
            boolean any = false;
            for (Delivery d : ds) {
                if (!d.p.isOnline() || shown >= d.lines.size()) continue;
                any = true;
                Chat.send(d.p, d.lines.get(shown));
                plugin.effects().soundTo(d.p, a.tickSound, a.tickVolume, Math.min(2f, 0.8f + 0.09f * shown));
            }
            shown++;
            return any;
        }
        public void abort() { for (Delivery d : ds) if (d.p.isOnline()) for (int i = shown; i < d.lines.size(); i++) Chat.send(d.p, d.lines.get(i)); }
    }

    /** The title letters cycle through colours for about a second, then settle. */
    private final class TitleShimmer implements Animator.Animation {
        final List<Delivery> ds; int frame;
        TitleShimmer(List<Delivery> ds) { this.ds = ds; }
        public boolean tick() {
            if (frame % 2 != 0) { frame++; return true; }
            int f = frame / 2;
            boolean last = f >= 14;
            for (Delivery d : ds) {
                if (!d.p.isOnline()) continue;
                String plain = org.bukkit.ChatColor.stripColor(Text.color(d.title));
                String shown;
                if (last) shown = d.title;
                else {
                    StringBuilder sb = new StringBuilder("&l");
                    for (int i = 0; i < plain.length(); i++) sb.append('&').append(SHIMMER[(i + f) % SHIMMER.length]).append(plain.charAt(i));
                    shown = Text.color(sb.toString());
                }
                try { d.p.sendTitle(shown, d.sub, last ? 0 : 0, last ? 50 : 8, last ? 14 : 0); } catch (Throwable ignored) {}
            }
            frame++;
            return !last;
        }
        public void abort() {}
    }
}
