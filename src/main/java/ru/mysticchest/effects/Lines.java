package ru.mysticchest.effects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.config.Settings;
import ru.mysticchest.util.Colors;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Four particle lines (a cross: north, east, south, west) leading to a standing chest. A light pulse runs along
 * each line towards the chest. Walk through a line and the particles around you change colour for a couple of
 * seconds - for you only, every player sees their own copy. The points follow the ground and start outside the structure.
 */
final class Lines {
    private final int n;
    private final double[] x, z;
    private final double[] y;
    private final int[] arm, index;
    private long yRefresh;
    private final Map<UUID, long[]> until = new HashMap<UUID, long[]>();
    private final Map<UUID, int[]> flash = new HashMap<UUID, int[]>();
    private final World world;

    Lines(ChestManager.Active a, Settings s) {
        world = a.loc.getWorld();
        double cx = a.loc.getBlockX() + 0.5, cz = a.loc.getBlockZ() + 0.5;
        double start = 2.5;
        if (a.structure != null) {
            double ext = Math.max(Math.max(Math.abs(a.structure.minX - a.loc.getBlockX()), Math.abs(a.structure.maxX - a.loc.getBlockX())),
                    Math.max(Math.abs(a.structure.minZ - a.loc.getBlockZ()), Math.abs(a.structure.maxZ - a.loc.getBlockZ())));
            start = ext + 1.5;
        }
        int per = (int) Math.max(1, Math.floor(s.linesLength / s.linesStep));
        n = per * 4;
        x = new double[n]; z = new double[n]; y = new double[n]; arm = new int[n]; index = new int[n];
        int[][] dir = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int d = 0; d < 4; d++) {
            for (int k = 0; k < per; k++) {
                int i = d * per + k;
                double dist = start + k * s.linesStep;
                x[i] = cx + dir[d][0] * dist;
                z[i] = cz + dir[d][1] * dist;
                arm[i] = d;
                index[i] = k;
                y[i] = a.loc.getBlockY() + 0.35;
            }
        }
    }

    private void refreshY(ChestManager.Active a) {
        long now = System.currentTimeMillis();
        if (now - yRefresh < 30000) return;
        yRefresh = now;
        for (int i = 0; i < n; i++) {
            int bx = (int) Math.floor(x[i]), bz = (int) Math.floor(z[i]);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) { y[i] = a.loc.getBlockY() + 0.35; continue; }
            int h = world.getHighestBlockYAt(bx, bz);
            for (int g = 0; g < 40 && h < world.getMaxHeight() - 2 && !world.getBlockAt(bx, h, bz).getType().name().endsWith("AIR"); g++) h++;
            // trees and leaves are not "ground": stay near the chest height instead of floating on canopies
            y[i] = Math.abs(h - a.loc.getBlockY()) > 8 ? a.loc.getBlockY() + 0.35 : h + 0.35;
        }
    }

    void draw(ChestManager.Active a, Player p, Settings s, Particle pt, Color tier, int phase, boolean dust, Aura.Spawner out) {
        refreshY(a);
        long now = System.currentTimeMillis();
        long[] u = until.get(p.getUniqueId());
        int[] c = flash.get(p.getUniqueId());
        Location pl = p.getLocation();
        // did the player walk through a line? then colour the points around them
        for (int i = 0; i < n; i++) {
            double dx = pl.getX() - x[i], dz = pl.getZ() - z[i];
            if (dx * dx + dz * dz > 1.69 || Math.abs(pl.getY() - y[i]) > 3) continue;
            if (u == null) { u = new long[n]; c = new int[n]; until.put(p.getUniqueId(), u); flash.put(p.getUniqueId(), c); }
            Color f = Colors.random();
            for (int j = Math.max(0, i - 2); j <= Math.min(n - 1, i + 2); j++) {
                if (arm[j] != arm[i] || u[j] > now) continue;     // a point already flashing keeps its colour until it fades
                u[j] = now + s.linesFlashSeconds * 1000L;
                c[j] = f.asRGB();
            }
        }
        boolean any = false;
        for (int i = 0; i < n; i++) {
            Color col;
            if (u != null && u[i] > now) { col = Color.fromRGB(c[i]); any = true; }
            else {
                double wave = 0.5 + 0.5 * Math.sin(phase * 0.55 + index[i] * 0.9);   // moves towards the chest
                Color base;
                if (s.linesColorMode == Settings.ColorMode.RANDOM) base = Colors.random();
                else if (s.linesColorMode == Settings.ColorMode.RAINBOW) base = Colors.hsv(phase * 0.015 + index[i] * 0.06, 1, 1);
                else base = tier;
                col = Colors.mix(base, Color.WHITE, wave * 0.65);
            }
            out.spawn(p, pt, x[i], y[i], z[i], col);
        }
        if (u != null && !any && now > maxUntil(u)) { until.remove(p.getUniqueId()); flash.remove(p.getUniqueId()); }
    }

    private static long maxUntil(long[] u) {
        long m = 0;
        for (long v : u) if (v > m) m = v;
        return m;
    }

    void forget(UUID id) { until.remove(id); flash.remove(id); }
}
