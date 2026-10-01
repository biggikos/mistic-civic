package ru.mysticchest.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.core.AsyncIO;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;

/** Finds spawn locations without ever blocking on chunk generation when the server can load async. */
public final class Locators {
    public interface Callback { void done(Location loc); }

    private static final int ATTEMPTS = 8;
    private final MysticChestPlugin plugin;
    private final Map<String, Location> points = new LinkedHashMap<String, Location>();

    public Locators(MysticChestPlugin plugin) { this.plugin = plugin; }

    // ---- fixed points --------------------------------------------------

    private File pointsFile() { return new File(plugin.getDataFolder(), "data/points.yml"); }

    public void loadPoints() {
        points.clear();
        if (!pointsFile().exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(pointsFile());
        for (String s : y.getStringList("points")) {
            String[] p = s.split(";");
            if (p.length != 5) continue;
            World w = Bukkit.getWorld(p[1]);
            if (w == null) continue;
            points.put(p[0], new Location(w, Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])));
        }
    }

    public Map<String, Location> points() { return points; }

    public void addPoint(String name, Location l) { points.put(name, l.getBlock().getLocation()); savePoints(); }
    public boolean removePoint(String name) { boolean r = points.remove(name) != null; if (r) savePoints(); return r; }

    private void savePoints() {
        plugin.io().request(pointsFile(), new AsyncIO.Source() {
            public String content() {
                List<String> out = new ArrayList<String>();
                for (Map.Entry<String, Location> e : points.entrySet()) {
                    Location l = e.getValue();
                    out.add(e.getKey() + ";" + l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ());
                }
                YamlConfiguration y = new YamlConfiguration();
                y.set("points", out);
                return "## Fixed spawn points (/mystic point add|remove|list). Managed by the plugin.\n" + y.saveToString();
            }
        });
    }

    // ---- strategies ----------------------------------------------------

    public void locate(SpawnProfile p, SpawnProfile.Mode mode, Callback cb) {
        switch (mode) {
            case NEAR_PLAYER: nearPlayer(p, cb); break;
            case FIXED_POINTS: fixedPoint(p, cb); break;
            default: randomWorld(p, cb);
        }
    }

    private World pickWorld(SpawnProfile p) {
        List<World> ws = new ArrayList<World>();
        for (String n : p.worlds) {
            World w = Bukkit.getWorld(n);
            if (w != null) ws.add(w);
        }
        return ws.isEmpty() ? null : ws.get(ThreadLocalRandom.current().nextInt(ws.size()));
    }

    private void randomWorld(final SpawnProfile p, final Callback cb) {
        final World w = pickWorld(p);
        if (w == null) { cb.done(null); return; }
        attempt(w, p, cb, 0, new Pick() {
            public int[] next() {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                int x, z, tries = 0;
                do {
                    x = w.getSpawnLocation().getBlockX() + r.nextInt(-p.radius, p.radius + 1);
                    z = w.getSpawnLocation().getBlockZ() + r.nextInt(-p.radius, p.radius + 1);
                } while (++tries < 10 && dist2(w, x, z) < (long) p.minDistanceFromSpawn * p.minDistanceFromSpawn);
                return new int[]{x, z};
            }
        });
    }

    private static long dist2(World w, int x, int z) {
        long dx = x - w.getSpawnLocation().getBlockX(), dz = z - w.getSpawnLocation().getBlockZ();
        return dx * dx + dz * dz;
    }

    private void nearPlayer(final SpawnProfile p, final Callback cb) {
        List<Player> candidates = new ArrayList<Player>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (p.worlds.contains(pl.getWorld().getName()) && pl.getGameMode() != org.bukkit.GameMode.SPECTATOR) candidates.add(pl);
        }
        if (candidates.isEmpty()) { cb.done(null); return; }
        final Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        final World w = target.getWorld();
        final Location base = target.getLocation();
        attempt(w, p, new Callback() {
            public void done(Location loc) {
                if (loc != null && p.notifyPlayer && target.isOnline()) plugin.lang().send(target, "spawn.near-you");
                cb.done(loc);
            }
        }, 0, new Pick() {
            public int[] next() {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double ang = r.nextDouble() * Math.PI * 2, dist = p.nearMin + r.nextDouble() * (p.nearMax - p.nearMin);
                return new int[]{(int) (base.getX() + Math.cos(ang) * dist), (int) (base.getZ() + Math.sin(ang) * dist)};
            }
        });
    }

    private void fixedPoint(SpawnProfile p, Callback cb) {
        List<Location> free = new ArrayList<Location>();
        for (Map.Entry<String, Location> e : points.entrySet()) {
            if (!p.pointNames.isEmpty() && !p.pointNames.contains(e.getKey())) continue;
            if (!p.worlds.isEmpty() && !p.worlds.contains(e.getValue().getWorld().getName())) continue;
            if (!plugin.chests().isActive(e.getValue().getBlock())) free.add(e.getValue());
        }
        cb.done(free.isEmpty() ? null : free.get(ThreadLocalRandom.current().nextInt(free.size())).clone());
    }

    // ---- shared machinery ----------------------------------------------

    private interface Pick { int[] next(); }

    private void attempt(final World w, final SpawnProfile p, final Callback cb, final int n, final Pick pick) {
        if (n >= ATTEMPTS) { cb.done(null); return; }
        final int[] xz = pick.next();
        final int cx = xz[0] >> 4, cz = xz[1] >> 4;
        if (!w.getWorldBorder().isInside(new Location(w, xz[0], 64, xz[1]))) { attempt(w, p, cb, n + 1, pick); return; }
        withChunk(w, cx, cz, new Runnable() {
            public void run() {
                Location l = surface(w, xz[0], xz[1], p);
                if (l != null) cb.done(l); else attempt(w, p, cb, n + 1, pick);
            }
        });
    }

    /** Runs the task once the chunk is loaded: async on Paper 1.13+, a single sync load elsewhere. */
    private void withChunk(final World w, int cx, int cz, final Runnable then) {
        if (w.isChunkLoaded(cx, cz)) { then.run(); return; }
        try {
            Object fut = World.class.getMethod("getChunkAtAsync", int.class, int.class).invoke(w, cx, cz);
            ((CompletableFuture<?>) fut).whenComplete(new java.util.function.BiConsumer<Object, Throwable>() {
                public void accept(Object c, Throwable t) {
                    Bukkit.getScheduler().runTask(plugin, then);
                }
            });
        } catch (Throwable t) {
            w.getChunkAt(cx, cz);
            then.run();
        }
    }

    private static boolean air(Block b) { return b.getType().name().endsWith("AIR"); }

    private Location surface(World w, int x, int z, SpawnProfile p) {
        int min = minHeight(w);
        int y = w.getHighestBlockYAt(x, z);
        while (y < w.getMaxHeight() - 2 && !air(w.getBlockAt(x, y, z))) y++;
        while (y > min + 1 && air(w.getBlockAt(x, y - 1, z))) y--;
        if (y <= min + 1) return null;
        Block ground = w.getBlockAt(x, y - 1, z);
        String g = ground.getType().name();
        if (ground.isLiquid()) return null;
        for (String bad : p.avoidGround) if (g.contains(bad.toUpperCase())) return null;
        return new Location(w, x, y, z);
    }

    /** World#getMinHeight exists only on 1.16.5+ (negative build limits arrived in 1.18). */
    private static int minHeight(World w) {
        try { return (Integer) World.class.getMethod("getMinHeight").invoke(w); } catch (Exception e) { return 0; }
    }
}
