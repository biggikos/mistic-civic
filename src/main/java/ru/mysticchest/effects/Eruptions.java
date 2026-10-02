package ru.mysticchest.effects;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.config.Layered;
import ru.mysticchest.core.Animator;
import ru.mysticchest.structure.Structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Volcano eruptions. Once a second the chest ticker calls {@link #update}: a structure with a crater (the volcano)
 * that has a standing chest erupts every few minutes, but only while somebody is close enough to see it. An
 * eruption is one shared-animator job: warning rumble and smoke, then lava bombs on parabolic paths. Nothing is
 * ever placed or destroyed, bombs are particles plus a small damage check where they land.
 */
public final class Eruptions {
    private final MysticChestPlugin plugin;

    public Eruptions(MysticChestPlugin plugin) { this.plugin = plugin; }

    /** Once a second. */
    public void update(List<ChestManager.Active> chests) {
        long now = System.currentTimeMillis();
        Set<Structure> seen = new HashSet<Structure>();
        for (ChestManager.Active a : chests) {
            Structure st = a.structure;
            if (st == null || st.crater == null || !st.alive() || !seen.add(st)) continue;
            Layered c = a.tier.layered("eruption", plugin.settings());
            if (!c.bool("enabled", true) || st.erupting) continue;
            if (st.nextEruption == 0) { st.nextEruption = now + nextGap(c); continue; }
            if (now < st.nextEruption) continue;
            if (!watched(st.crater, c.integer("view-range", 90, 10, 500))) { st.nextEruption = now + 15000L; continue; }
            st.erupting = true;
            st.nextEruption = now + nextGap(c);
            plugin.animator().add(new Job(st, c));
        }
    }

    private long nextGap(Layered c) {
        long base = c.integer("interval-seconds", 75, 5, 86400) * 1000L;
        int jitter = c.integer("jitter-percent", 40, 0, 95);
        double f = 1 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitter / 100.0;
        return Math.max(5000L, (long) (base * f));
    }

    private static boolean watched(Location at, int range) {
        World w = at.getWorld();
        if (w == null) return false;
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(at) <= (double) range * range) return true;
        return false;
    }

    private static final class Bomb {
        final double sx, sy, sz, tx, ty, tz, arc;
        final int flight;
        int age;
        Bomb(double sx, double sy, double sz, double tx, double ty, double tz, double arc, int flight) {
            this.sx = sx; this.sy = sy; this.sz = sz; this.tx = tx; this.ty = ty; this.tz = tz; this.arc = arc; this.flight = flight;
        }
        double x() { return sx + (tx - sx) * age / flight; }
        double z() { return sz + (tz - sz) * age / flight; }
        double y() { double f = (double) age / flight; return sy + (ty - sy) * f + 4 * arc * f * (1 - f); }
    }

    private final class Job implements Animator.Animation {
        final Structure st;
        final int warn, total, perTick100, radius;
        final double hit, damage;
        final int fire;
        final String sound, warnSound;
        final boolean announce;
        final List<Bomb> bombs = new ArrayList<Bomb>();
        int t;

        Job(Structure st, Layered c) {
            this.st = st;
            warn = c.integer("warning-seconds", 4, 0, 60) * 20;
            total = warn + c.integer("duration-seconds", 8, 1, 120) * 20;
            perTick100 = (int) Math.round(c.decimal("bombs-per-second", 3, 0, 50) * 100 / 20.0);
            radius = c.integer("bomb-radius", 14, 4, 40);
            hit = c.decimal("hit-radius", 3.0, 0.5, 10);
            damage = c.decimal("damage", 5.0, 0, 200);
            fire = c.integer("fire-seconds", 4, 0, 60);
            sound = c.str("sound", "ENTITY_GENERIC_EXPLODE");
            warnSound = c.str("warning-sound", "ENTITY_WITHER_SPAWN");
            announce = c.bool("announce", true);
        }

        public boolean tick() {
            if (!st.alive() || st.crater.getWorld() == null) { st.erupting = false; return false; }
            Location cr = st.crater;
            t++;
            if (t == 1) {
                plugin.effects().soundAt(warnSound, cr, 1.5f, 0.6f);
                if (announce) for (Player p : cr.getWorld().getPlayers())
                    if (p.getLocation().distanceSquared(cr) <= 60 * 60) Effects.actionBar(p, plugin.lang().get(p, "eruption.warning"));
            }
            if (t <= total) {
                if (t % 2 == 0) {
                    plugin.effects().burst("SMOKE_LARGE", cr, 6, 1.4, 0.5, 1.4, 0.04);
                    plugin.effects().burst("FLAME", cr, 4, 1.0, 0.3, 1.0, 0.03);
                }
                if (t % 15 == 0) plugin.effects().soundAt("ENTITY_GENERIC_EXPLODE", cr, 0.5f, 0.5f);
                if (t > warn) launch(cr);
            }
            for (int i = bombs.size() - 1; i >= 0; i--) {
                Bomb b = bombs.get(i);
                b.age++;
                Location at = new Location(cr.getWorld(), b.x(), b.y(), b.z());
                plugin.effects().burst("FLAME", at, 3, 0.1, 0.1, 0.1, 0.01);
                if (t % 2 == 0) plugin.effects().burst("SMOKE_NORMAL", at, 2, 0.1, 0.1, 0.1, 0.01);
                if (b.age % 3 == 0) plugin.effects().burst("LAVA", at, 1, 0.1, 0.1, 0.1, 0);
                if (b.age >= b.flight) { land(new Location(cr.getWorld(), b.tx, b.ty, b.tz)); bombs.remove(i); }
            }
            if (t > total && bombs.isEmpty()) { st.erupting = false; return false; }
            return true;
        }

        private void launch(Location cr) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            int n = perTick100 / 100 + (r.nextInt(100) < perTick100 % 100 ? 1 : 0);
            World w = cr.getWorld();
            for (int i = 0; i < n; i++) {
                double ang = r.nextDouble() * Math.PI * 2, dist = 4 + r.nextDouble() * (radius - 4);
                int tx = (int) Math.floor(cr.getX() + Math.cos(ang) * dist), tz = (int) Math.floor(cr.getZ() + Math.sin(ang) * dist);
                if (!w.isChunkLoaded(tx >> 4, tz >> 4)) continue;
                double ty = w.getHighestBlockYAt(tx, tz) + 1;
                bombs.add(new Bomb(cr.getX(), cr.getY(), cr.getZ(), tx + 0.5, ty, tz + 0.5, 6 + r.nextDouble() * 8, 22 + r.nextInt(18)));
            }
        }

        private void land(Location at) {
            plugin.effects().burst("EXPLOSION_LARGE", at, 1, 0, 0, 0, 0);
            plugin.effects().burst("LAVA", at, 14, 0.8, 0.3, 0.8, 0);
            plugin.effects().burst("FLAME", at, 12, 0.7, 0.2, 0.7, 0.05);
            plugin.effects().soundAt(sound, at, 0.9f, 0.9f);
            for (Player p : at.getWorld().getPlayers()) {
                GameMode g = p.getGameMode();
                if (p.isDead() || g == GameMode.CREATIVE || g == GameMode.SPECTATOR) continue;
                Location pl = p.getLocation();
                double dx = pl.getX() - at.getX(), dz = pl.getZ() - at.getZ();
                if (dx * dx + dz * dz > hit * hit || Math.abs(pl.getY() - at.getY()) > 3) continue;
                if (damage > 0) p.damage(damage);
                if (fire > 0) p.setFireTicks(fire * 20);
            }
        }

        public void abort() { st.erupting = false; }
    }
}
