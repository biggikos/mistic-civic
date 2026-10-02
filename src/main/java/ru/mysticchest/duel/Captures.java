package ru.mysticchest.duel;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.config.Layered;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Capture the mystic": when other players are around, opening a chest starts a capture. The progress bar
 * only grows while the capturer is alone in the zone; any enemy in the zone drains it. The capture fails if
 * the capturer dies or leaves. Alone, a player simply opens the chest (unless trigger is ALWAYS).
 */
public final class Captures {
    private static final class Cap {
        final ChestManager.Active chest;
        final UUID capturer;
        final String name;
        double progress;
        BossBar bar;
        Cap(ChestManager.Active chest, Player p) { this.chest = chest; this.capturer = p.getUniqueId(); this.name = p.getName(); }
    }

    private final MysticChestPlugin plugin;
    private final Map<ChestManager.Active, Cap> caps = new HashMap<ChestManager.Active, Cap>();

    public Captures(MysticChestPlugin plugin) { this.plugin = plugin; }

    private int others(Player p, ChestManager.Active a, double radius) {
        int n = 0;
        for (Player o : a.loc.getWorld().getPlayers()) {
            if (o.equals(p) || !fighter(o) || ally(p, o)) continue;
            if (o.getLocation().distanceSquared(a.loc) <= radius * radius) n++;
        }
        return n;
    }

    private static boolean fighter(Player o) {
        return !o.isDead() && (o.getGameMode() == GameMode.SURVIVAL || o.getGameMode() == GameMode.ADVENTURE);
    }

    private static boolean ally(Player a, Player b) {
        try {
            org.bukkit.scoreboard.Team ta = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(a.getName());
            return ta != null && ta.equals(Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(b.getName()));
        } catch (Throwable t) { return false; }
    }

    /** @return true when the click was consumed by the capture mechanic (started, running or refused). */
    public boolean intercept(Player p, ChestManager.Active a) {
        Layered d = a.tier.layered("duel", plugin.settings());
        if (!d.bool("enabled", false)) return false;
        Cap c = caps.get(a);
        if (c != null) {
            plugin.lang().send(p, c.capturer.equals(p.getUniqueId()) ? "duel.keep" : "duel.busy", "player", c.name);
            return true;
        }
        boolean always = d.str("trigger", "WHEN_CONTESTED").equalsIgnoreCase("ALWAYS");
        int detect = d.integer("detect-radius", 16, 2, 200);
        if (!always && others(p, a, detect) == 0) return false;     // nobody to fight: just open it
        c = new Cap(a, p);
        glow(p, d, false);
        try {
            c.bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
            c.bar.setProgress(0);
        } catch (Throwable t) { c.bar = null; }
        caps.put(a, c);
        for (Player o : a.loc.getWorld().getPlayers()) {
            if (o.getLocation().distanceSquared(a.loc) <= (double) detect * detect) {
                plugin.lang().send(o, "duel.started", "player", p.getName(), "tier", a.tier.name(plugin.lang().code(o)),
                        "seconds", String.valueOf(d.integer("capture-seconds", 10, 1, 600)));
                plugin.effects().soundTo(o, "ENTITY_ENDER_DRAGON_GROWL", 0.6f, 1.4f);
            }
        }
        return true;
    }

    /** Once a second. */
    public void tick() {
        if (caps.isEmpty()) return;
        for (Cap c : new ArrayList<Cap>(caps.values())) {
            ChestManager.Active a = c.chest;
            Layered d = a.tier.layered("duel", plugin.settings());
            Player cp = Bukkit.getPlayer(c.capturer);
            double zone = d.integer("zone-radius", 5, 1, 50);
            int need = d.integer("capture-seconds", 10, 1, 600), detect = d.integer("detect-radius", 16, 2, 200);
            if (!plugin.chests().isActive(a.loc.getBlock())) { end(c, null); continue; }
            if (cp == null || !cp.isOnline() || cp.isDead() || cp.getWorld() != a.loc.getWorld()
                    || cp.getLocation().distanceSquared(a.loc) > zone * zone) { end(c, "duel.failed"); continue; }
            int enemies = others(cp, a, zone);
            boolean contested = enemies > 0;
            glow(cp, d, contested);
            if (contested) c.progress = Math.max(0, c.progress - d.decimal("decay-per-second", 2.0, 0, 100));
            else c.progress += 1;
            double frac = Math.min(1, c.progress / need);
            if (c.bar != null) {
                try {
                    c.bar.setTitle(plugin.lang().get(contested ? "duel.bar-contested" : "duel.bar", "player", c.name, "percent", String.valueOf((int) (frac * 100))));
                    c.bar.setColor(contested ? BarColor.RED : BarColor.GREEN);
                    c.bar.setProgress(frac);
                    for (Player o : a.loc.getWorld().getPlayers()) {
                        boolean near = o.getLocation().distanceSquared(a.loc) <= (double) detect * detect, has = c.bar.getPlayers().contains(o);
                        if (near && !has) c.bar.addPlayer(o); else if (!near && has) c.bar.removePlayer(o);
                    }
                } catch (Throwable ignored) {}
            }
            if (c.progress >= need) {
                caps.remove(a);
                dispose(c);
                plugin.stats().add(cp, "captures");
                for (Player o : a.loc.getWorld().getPlayers()) {
                    if (o.getLocation().distanceSquared(a.loc) <= (double) detect * detect) plugin.lang().send(o, "duel.won", "player", c.name);
                }
                plugin.open().openWorldChest(cp, a);
            }
        }
    }

    /** Coloured outline of the capturer (duel.glow / glow-color / glow-contested-color). */
    private void glow(Player p, Layered d, boolean contested) {
        if (!d.bool("glow", true)) return;
        ru.mysticchest.effects.Glow.on(p, contested ? d.str("glow-contested-color", "RED") : d.str("glow-color", "GOLD"));
    }

    private void dispose(Cap c) {
        Player p = Bukkit.getPlayer(c.capturer);
        if (p != null) ru.mysticchest.effects.Glow.off(p); else ru.mysticchest.effects.Glow.off(c.capturer);
        if (c.bar != null) { try { c.bar.removeAll(); } catch (Throwable ignored) {} c.bar = null; }
    }

    private void end(Cap c, String reasonKey) {
        caps.remove(c.chest);
        dispose(c);
        if (reasonKey == null) return;
        for (Player o : c.chest.loc.getWorld().getPlayers()) {
            if (o.getLocation().distanceSquared(c.chest.loc) <= 40 * 40) plugin.lang().send(o, reasonKey, "player", c.name);
        }
    }

    public void cancelFor(ChestManager.Active a) {
        Cap c = caps.remove(a);
        if (c != null) dispose(c);
    }

    public void shutdown() {
        for (Cap c : new ArrayList<Cap>(caps.values())) dispose(c);
        caps.clear();
    }
}
