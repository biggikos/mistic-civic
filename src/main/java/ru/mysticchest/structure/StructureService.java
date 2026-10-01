package ru.mysticchest.structure;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.config.Cfg;
import ru.mysticchest.config.Settings;
import ru.mysticchest.core.Animator;
import ru.mysticchest.core.AsyncIO;
import ru.mysticchest.core.Scheduler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds the artistic structures around chests. Blocks are placed layer by layer by the shared Animator,
 * every replaced block is remembered (and written to data/structures so a crash can be undone), and the
 * terrain is restored when the chest is gone.
 */
public final class StructureService {
    public interface Callback { void done(Structure built); }

    public static final class Spec {
        public final StructureCatalog.Entry entry;
        public final Theme theme;   // built-in shapes only
        public final double decay;
        public final boolean rotate;
        Spec(StructureCatalog.Entry e, Theme t, double d, boolean r) { entry = e; theme = t; decay = d; rotate = r; }
    }

    private static final String[] PLAYER_MADE = {"CHEST", "BARREL", "FURNACE", "HOPPER", "DISPENSER", "DROPPER", "SHULKER", "_BED", "BED_BLOCK",
            "SIGN", "DOOR", "RAIL", "TORCH", "LANTERN", "BEACON", "SPAWNER", "PORTAL", "BREWING", "ENCHANT", "ANVIL", "CRAFTING", "JUKEBOX", "NOTE_BLOCK"};

    private final MysticChestPlugin plugin;
    private final List<Structure> active = new ArrayList<Structure>();
    private final StructureCatalog catalog;

    public StructureService(MysticChestPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new StructureCatalog(plugin);
    }

    public StructureCatalog catalog() { return catalog; }

    private Wand wand;
    public Wand wand() { if (wand == null) wand = new Wand(plugin); return wand; }

    /** Builds a structure at the player's feet without a chest and takes it down again after 40 seconds. */
    public void preview(final Player p, String name, String theme) {
        final Location at = p.getLocation().getBlock().getLocation();
        Spec spec = resolve(null, null, name, theme, at);
        if (spec == null) { plugin.lang().send(p, "structure.unknown", "name", name); return; }
        plugin.lang().send(p, "structure.previewing", "name", spec.entry.id);
        build(spec, at, new Callback() {
            public void done(Structure st) {
                if (st == null) { plugin.lang().send(p, "structure.cannot-build"); return; }
                scheduleRestore(st, 40);
            }
        });
    }

    private File dir() { return new File(plugin.getDataFolder(), "data/structures"); }

    // ---- which structure, if any ---------------------------------------

    /**
     * @return null when this spawn gets no structure. forceName/forceTheme are for /mystic spawn and previews.
     * Filters: a profile or tier may restrict the choice with shapes: [..] / shape: name.
     */
    public Spec resolve(Cfg profile, Cfg tier, String forceName, String forceTheme, Location at) {
        Settings s = plugin.settings();
        boolean enabled = s.structEnabled;
        int chance = s.structChance;
        List<String> filter = null;
        String theme = s.structTheme;
        double decay = s.structDecay;
        for (Cfg lvl : new Cfg[]{profile, tier}) {
            if (lvl == null || !lvl.exists()) continue;
            enabled = lvl.bool("enabled", enabled);
            chance = lvl.integer("chance", chance, 0, 100);
            if (lvl.has("shapes")) filter = lvl.strings("shapes");
            if (lvl.has("shape")) { filter = new ArrayList<String>(); filter.add(lvl.str("shape", "")); }
            theme = lvl.str("theme", theme);
            decay = lvl.decimal("decay", decay, 0, 1);
        }
        if (forceName != null || forceTheme != null) {
            enabled = true;
            chance = 100;
            if (forceName != null) { filter = new ArrayList<String>(); filter.add(forceName); }
            if (forceTheme != null) theme = forceTheme;
        }
        if (!enabled || s.lowResource && forceName == null) return null;
        if (chance < 100 && ThreadLocalRandom.current().nextInt(100) >= chance) return null;
        StructureCatalog.Entry e;
        if (forceName != null) {
            e = catalog.get(forceName);                       // forcing ignores enabled/weight
        } else {
            e = catalog.pick(filter, ThreadLocalRandom.current());
        }
        if (e == null) return null;
        if (forceTheme == null && e.theme != null && (tier == null || !tier.has("theme"))) theme = e.theme;
        Theme t = Theme.parse(theme);
        Theme[] all = Theme.values();
        if (theme != null && theme.equalsIgnoreCase("RANDOM")) {
            t = all[ThreadLocalRandom.current().nextInt(all.length)];
        } else if (t == null) {
            Block b = at.getBlock();
            t = Theme.forBiome(String.valueOf(b.getBiome()), String.valueOf(at.getWorld().getEnvironment()));
            // AUTO follows the biome most of the time, but every so often it is something unexpected
            if (s.structSurprise > 0 && ThreadLocalRandom.current().nextInt(100) < s.structSurprise) t = all[ThreadLocalRandom.current().nextInt(all.length)];
        }
        return new Spec(e, t, decay, s.structRotate);
    }

    // ---- building ---------------------------------------------------------

    public void build(final Spec spec, final Location center, final Callback cb) {
        if (plugin.animator().size() >= plugin.settings().maxAnimations) { cb.done(null); return; }
        final World w = center.getWorld();
        final int cx = center.getBlockX(), cz = center.getBlockZ();
        final Blueprint bp;
        if (spec.entry.custom()) {
            bp = Blueprint.of(spec.entry.template, spec.rotate ? ThreadLocalRandom.current().nextInt(4) : 0);
        } else {
            Canvas cv = new Canvas(new Random(System.nanoTime() ^ (cx * 31L + cz * 17L)), spec.decay);
            spec.entry.shape.draw(cv);
            bp = Blueprint.of(cv, spec.theme);
        }
        bp.name = spec.entry.id;
        final int minX = cx + bp.minX - 1, maxX = cx + bp.maxX + 1, minZ = cz + bp.minZ - 1, maxZ = cz + bp.maxZ + 1;
        final List<int[]> chunks = new ArrayList<int[]>();
        for (int x = minX >> 4; x <= maxX >> 4; x++) for (int z = minZ >> 4; z <= maxZ >> 4; z++) chunks.add(new int[]{x, z});
        ensure(w, chunks, 0, new Runnable() {
            public void run() {
                try {
                    start(bp, w, cx, cz, minX, maxX, minZ, maxZ, chunks, cb);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Structure failed: " + t);
                    for (int[] c : chunks) plugin.chests().release(w, c[0], c[1]);
                    cb.done(null);
                }
            }
        });
    }

    private void ensure(final World w, final List<int[]> list, final int i, final Runnable done) {
        if (i >= list.size()) { done.run(); return; }
        int[] c = list.get(i);
        plugin.chests().hold(w, c[0], c[1]);
        plugin.locators().withChunk(w, c[0], c[1], new Runnable() {
            public void run() { ensure(w, list, i + 1, done); }
        });
    }

    private static boolean soft(String n) { return n.contains("LEAVES") || n.contains("LOG") || n.contains("WOOD") || n.contains("GRASS") || n.contains("FLOWER") || n.contains("SNOW") && !n.contains("BLOCK"); }

    private int groundY(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        for (int guard = 0; guard < 300 && y < w.getMaxHeight() - 2 && !w.getBlockAt(x, y, z).getType().name().endsWith("AIR"); guard++) y++;
        int min = ru.mysticchest.spawn.Locators.minHeight(w);
        while (y > min + 1 && (w.getBlockAt(x, y - 1, z).getType().name().endsWith("AIR") || soft(w.getBlockAt(x, y - 1, z).getType().name()))) y--;
        return y;
    }

    private void start(Blueprint bp, World w, int cx, int cz, int minX, int maxX, int minZ, int maxZ,
                       List<int[]> chunks, Callback cb) {
        Settings s = plugin.settings();
        int minH = ru.mysticchest.spawn.Locators.minHeight(w);
        // site check: slope, liquid, and nothing that looks player-made inside the volume
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        long sum = 0;
        int n = 0;
        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                int g = groundY(w, x, z);
                lo = Math.min(lo, g); hi = Math.max(hi, g);
                sum += g; n++;
                if (w.getBlockAt(x, g - 1, z).isLiquid()) { abort(w, chunks, cb, "water"); return; }
            }
        }
        if (hi - lo > s.structMaxSlope) { abort(w, chunks, cb, "too steep (" + (hi - lo) + ")"); return; }
        int baseY = (int) Math.round(sum / (double) n);
        int top = baseY + bp.height;
        if (top >= w.getMaxHeight() || baseY < minH + 2) { abort(w, chunks, cb, "height limit"); return; }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = baseY; y <= top; y++) {
                    String name = w.getBlockAt(x, y, z).getType().name();
                    for (String bad : PLAYER_MADE) if (name.contains(bad)) { abort(w, chunks, cb, "player-made block " + name); return; }
                }
            }
        }
        // operations: clear, foundation, then the structure bottom-up
        final List<int[]> ops = new ArrayList<int[]>();
        final List<Placer> placers = new ArrayList<Placer>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = baseY; y <= top; y++) {
                    if (bp.occupied.contains(Canvas.key(x - cx, y - baseY, z - cz))) continue;
                    Block b = w.getBlockAt(x, y, z);
                    String nm = b.getType().name();
                    if (nm.endsWith("AIR")) continue;
                    boolean chestCell = x - cx == bp.chestX && y - baseY == bp.chestY && z - cz == bp.chestZ;
                    if (bp.clearVolume || soft(nm) || chestCell) { ops.add(new int[]{x, y, z}); placers.add(null); }
                }
            }
        }
        for (java.util.Map.Entry<Long, Placer> e : bp.foundation.entrySet()) {
            int rx = Canvas.kx(e.getKey()), rz = Canvas.kz(e.getKey());
            for (int y = baseY - 1; y >= Math.max(minH + 1, baseY - 10); y--) {
                Block b = w.getBlockAt(cx + rx, y, cz + rz);
                String nm = b.getType().name();
                if (!(nm.endsWith("AIR") || b.isLiquid() || soft(nm))) break;
                ops.add(new int[]{cx + rx, y, cz + rz}); placers.add(e.getValue());
            }
        }
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < bp.xyz.size(); i++) order.add(i);
        final List<int[]> rel = bp.xyz;
        java.util.Collections.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                int[] pa = rel.get(a), pb = rel.get(b);
                if (pa[1] != pb[1]) return pa[1] < pb[1] ? -1 : 1;
                double da = Math.hypot(pa[0], pa[2]), db = Math.hypot(pb[0], pb[2]);
                return da < db ? -1 : (da > db ? 1 : 0);
            }
        });
        for (int i : order) {
            int[] p = bp.xyz.get(i);
            ops.add(new int[]{cx + p[0], baseY + p[1], cz + p[2]});
            placers.add(bp.placers.get(i));
        }
        Location chest = new Location(w, cx + bp.chestX, baseY + bp.chestY, cz + bp.chestZ);
        Structure st = new Structure(w, minX, maxX, baseY - 10, top, minZ, maxZ, chest);
        st.chunks.addAll(chunks);
        st.name = bp.name;
        active.add(st);
        plugin.animator().add(new BuildJob(st, ops, placers, cb, new Location(w, cx + 0.5, baseY + 1, cz + 0.5)));
    }

    private void abort(World w, List<int[]> chunks, Callback cb, String why) {
        if (plugin.settings().debug) plugin.getLogger().info("[structure] site rejected: " + why);
        for (int[] c : chunks) plugin.chests().release(w, c[0], c[1]);
        cb.done(null);
    }

    private final class BuildJob implements Animator.Animation {
        final Structure st;
        final List<int[]> ops;
        final List<Placer> mats;
        final Callback cb;
        final Location center;
        final Set<Long> seen = new HashSet<Long>();
        int idx, tick;

        BuildJob(Structure st, List<int[]> ops, List<Placer> mats, Callback cb, Location center) {
            this.st = st; this.ops = ops; this.mats = mats; this.cb = cb; this.center = center;
        }

        public boolean tick() {
            int per = Math.max(1, plugin.settings().structSpeed);
            for (int i = 0; i < per && idx < ops.size(); i++, idx++) {
                int[] p = ops.get(idx);
                Block b = st.world.getBlockAt(p[0], p[1], p[2]);
                if (seen.add(ru.mysticchest.chest.ChestManager.key(p[0], p[1], p[2]))) {
                    st.positions.add(p);
                    st.originals.add(Snap.of(b));
                }
                Placer m = mats.get(idx);
                if (m == null) b.setType(org.bukkit.Material.AIR, false); else m.apply(b);
            }
            if (tick++ % 3 == 0) plugin.effects().soundAt(plugin.settings().structSound, center, 0.7f, 0.7f + (float) idx / ops.size());
            if (idx < ops.size()) return true;
            finish();
            return false;
        }

        private void finish() {
            st.file = new File(dir(), UUID.randomUUID() + ".txt");
            plugin.io().request(st.file, new AsyncIO.Source() { public String content() { return encode(st); } });
            cb.done(st);
        }

        public void abort() { restoreNow(st); cb.done(null); }
    }

    // ---- persistence -------------------------------------------------------

    private static String encode(Structure st) {
        StringBuilder sb = new StringBuilder("v1\n").append(st.world.getName()).append('\n');
        for (int i = 0; i < st.positions.size(); i++) {
            int[] p = st.positions.get(i);
            sb.append(p[0]).append(';').append(p[1]).append(';').append(p[2]).append(';').append(st.originals.get(i).encode()).append('\n');
        }
        return sb.toString();
    }

    /** Startup: undo structures that a crash left behind. */
    public void cleanupLeftovers() {
        File[] files = dir().listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().endsWith(".txt")) continue;
            try {
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                World w = lines.size() > 2 ? Bukkit.getWorld(lines.get(1)) : null;
                if (w == null) continue;
                for (int i = lines.size() - 1; i >= 2; i--) {
                    String[] p = lines.get(i).split(";", 4);
                    if (p.length < 4) continue;
                    int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) w.getChunkAt(x >> 4, z >> 4);
                    Snap.decode(p[3]).apply(w.getBlockAt(x, y, z));
                }
                f.delete();
                plugin.getLogger().info("Restored terrain of a structure left by a crash (" + (lines.size() - 2) + " blocks).");
            } catch (Exception e) {
                plugin.getLogger().warning("Cannot restore " + f.getName() + ": " + e);
            }
        }
    }

    // ---- removal ---------------------------------------------------------

    /** The chest is gone: after a delay the structure collapses (top-down) and the terrain returns. */
    public void scheduleRestore(final Structure st) { scheduleRestore(st, plugin.settings().structCollapseDelay); }

    public void scheduleRestore(final Structure st, int delaySeconds) {
        if (st == null || st.restoring) return;
        st.restoring = true;
        if (plugin.settings().debug) plugin.getLogger().info("[structure] restore scheduled in " + delaySeconds + "s (" + st.blocks() + " blocks)");
        long delay = delaySeconds * 1000L;
        plugin.scheduler().later(delay, new Runnable() {
            public void run() {
                Settings s = plugin.settings();
                if (s.structCollapse && plugin.animator().size() < s.maxAnimations) plugin.animator().add(new CollapseJob(st));
                else restoreNow(st);
            }
        });
    }

    private final class CollapseJob implements Animator.Animation {
        final Structure st;
        int idx;
        int tick;

        CollapseJob(Structure st) { this.st = st; this.idx = st.positions.size() - 1; }

        public boolean tick() {
            int per = Math.max(2, plugin.settings().structSpeed * 2);
            for (int i = 0; i < per && idx >= 0; i++, idx--) {
                int[] p = st.positions.get(idx);
                st.originals.get(idx).apply(st.world.getBlockAt(p[0], p[1], p[2]));
            }
            if (tick++ % 4 == 0) plugin.effects().soundAt(plugin.settings().structSound, st.chest, 0.6f, 0.5f);
            if (idx >= 0) return true;
            close(st);
            return false;
        }

        public void abort() { restoreNow(st); }
    }

    public void restoreNow(Structure st) {
        if (st.restored) return;
        for (int i = st.positions.size() - 1; i >= 0; i--) {
            int[] p = st.positions.get(i);
            st.originals.get(i).apply(st.world.getBlockAt(p[0], p[1], p[2]));
        }
        close(st);
    }

    private void close(Structure st) {
        if (st.restored) return;
        if (plugin.settings().debug) plugin.getLogger().info("[structure] terrain restored");
        st.restored = true;
        active.remove(st);
        for (int[] c : st.chunks) plugin.chests().release(st.world, c[0], c[1]);
        if (st.file != null) st.file.delete();
    }

    public boolean protects(Block b) {
        if (active.isEmpty() || !plugin.settings().structProtect) return false;
        for (Structure st : active) if (!st.restored && st.contains(b)) return true;
        return false;
    }

    public int count() { return active.size(); }

    /** Plugin shutdown: put every piece of terrain back right now. */
    public void shutdown() {
        for (Structure st : new ArrayList<Structure>(active)) restoreNow(st);
    }
}
