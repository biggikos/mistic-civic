package ru.mysticchest.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.config.Cfg;
import ru.mysticchest.config.Settings;
import ru.mysticchest.cooldown.CooldownManager;
import ru.mysticchest.core.Animator;
import ru.mysticchest.core.Scheduler;
import ru.mysticchest.structure.Structure;
import ru.mysticchest.structure.StructureService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Runs the spawn profiles on the shared Scheduler: zero tasks while nothing is due. */
public final class SpawnService {
    private final MysticChestPlugin plugin;
    private final Locators locators;
    private final Map<String, SpawnProfile> profiles = new LinkedHashMap<String, SpawnProfile>();
    private final List<Scheduler.Handle> handles = new ArrayList<Scheduler.Handle>();
    private final Map<String, Long> lastFire = new java.util.HashMap<String, Long>();

    public SpawnService(MysticChestPlugin plugin, Locators locators) {
        this.plugin = plugin;
        this.locators = locators;
    }

    public Map<String, SpawnProfile> profiles() { return profiles; }
    public Locators locators() { return locators; }

    public void reload() {
        stop();
        profiles.clear();
        Cfg root = new Cfg(plugin.getConfig(), "config.yml", plugin.getLogger()).sub("spawn").sub("profiles");
        for (String name : root.keys()) {
            SpawnProfile p = new SpawnProfile(name, root.sub(name));
            profiles.put(name, p);
            if (p.enabled) schedule(p);
        }
    }

    public void stop() {
        for (Scheduler.Handle h : handles) h.cancel();
        handles.clear();
    }

    private void schedule(final SpawnProfile p) {
        long delay;
        if (p.trigger == SpawnProfile.Trigger.ONLINE_THRESHOLD) {
            // poll every 30 s (one queue entry); fire when enough players are online and the cooldown passed
            handles.add(plugin.scheduler().later(30000L, new Runnable() {
                public void run() {
                    try {
                        long now = System.currentTimeMillis();
                        Long last = lastFire.get(p.name);
                        if (Bukkit.getOnlinePlayers().size() >= p.thresholdPlayers
                                && (last == null || now - last >= p.thresholdCooldownMinutes * 60000L)) {
                            lastFire.put(p.name, now);
                            SpawnService.this.run(p, null, null, false);
                        }
                    } finally { schedule(p); }
                }
            }));
            return;
        }
        if (p.trigger == SpawnProfile.Trigger.INTERVAL) {
            // jitter makes the gaps irregular: 45 min +- 30% is anything from 31 to 58 minutes
            double f = 1 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * p.jitterPercent / 100.0;
            delay = Math.max(60000L, (long) (p.intervalMinutes * 60000L * f));
        } else {
            if (p.times.isEmpty()) {
                plugin.getLogger().warning("[spawn.profiles." + p.name + "] trigger TIMES has no valid times (use \"HH:mm\"); profile disabled.");
                return;
            }
            delay = Long.MAX_VALUE;
            LocalDateTime now = LocalDateTime.now();
            for (LocalTime t : p.times) {
                LocalDateTime at = now.toLocalDate().atTime(t);
                if (!at.isAfter(now)) at = at.plusDays(1);
                delay = Math.min(delay, java.time.Duration.between(now, at).toMillis());
            }
        }
        handles.add(plugin.scheduler().later(delay, new Runnable() {
            public void run() {
                try { SpawnService.this.run(p, null, null, false); } finally { schedule(p); }
            }
        }));
    }

    private Tier pickTier(SpawnProfile p) {
        long total = 0;
        for (Map.Entry<String, Integer> e : p.tierWeights.entrySet()) if (plugin.tiers().get(e.getKey()) != null) total += e.getValue();
        if (total <= 0) return null;
        long r = (long) (ThreadLocalRandom.current().nextDouble() * total);
        for (Map.Entry<String, Integer> e : p.tierWeights.entrySet()) {
            Tier t = plugin.tiers().get(e.getKey());
            if (t == null) continue;
            r -= e.getValue();
            if (r < 0) return t;
        }
        return null;
    }

    private void debug(String m) { if (plugin.settings().debug) plugin.getLogger().info("[spawn] " + m); }

    /** @param force true skips player/active/cooldown conditions (admin command). */
    public void run(final SpawnProfile p, Tier forced, final CommandSender who, boolean force) {
        Settings s = plugin.settings();
        if (!force) {
            if (Bukkit.getOnlinePlayers().size() < p.minPlayers) { debug(p.name + ": not enough players"); return; }
            if (s.cdSpawn > 0 && plugin.cooldowns().remaining(CooldownManager.GLOBAL, "spawn") > 0) { debug(p.name + ": spawn cooldown"); return; }
            if (plugin.chests().countProfile(p.name) >= p.maxActive) { debug(p.name + ": max-active reached"); return; }
        }
        final Tier tier = forced != null ? forced : pickTier(p);
        if (tier == null) { debug(p.name + ": no valid tier in tier-weights"); fail(who); return; }
        SpawnProfile.Mode mode = p.mode == SpawnProfile.Mode.AIRDROP ? p.airdropLocation : p.mode;
        locators.locate(p, mode, new Locators.Callback() {
            public void done(Location loc) {
                if (loc == null) { debug(p.name + ": no suitable location"); fail(who); return; }
                if (p.mode == SpawnProfile.Mode.AIRDROP) airdrop(p, tier, loc); else land(p, tier, loc, "announce.spawned");
            }
        });
    }

    private void fail(CommandSender who) { if (who != null) plugin.lang().send(who, "spawn.failed"); }

    private void land(final SpawnProfile p, final Tier tier, final Location loc, final String key) {
        StructureService.Spec spec = plugin.structures().resolve(p.structure, tier.structure, null, null, loc);
        if (spec == null) { finishLand(p, tier, loc, null, key); return; }
        plugin.structures().build(spec, loc, new StructureService.Callback() {
            public void done(Structure st) { finishLand(p, tier, st == null ? loc : st.chest, st, key); }
        });
    }

    /** modeid / structid / ttlsec placeholders for the announcement of a chest that was just placed. */
    private String[] details(Location loc, Tier tier, Structure st) {
        ru.mysticchest.chest.ChestManager.Active a = plugin.chests().at(loc.getBlock());
        return new String[]{"modeid", a != null && a.mode != null ? a.mode.name() : tier.openMode(plugin.settings()).name(),
                "structid", st == null || st.name == null ? "" : st.name,
                "ttlsec", String.valueOf(tier.ttlSeconds(plugin.settings()))};
    }

    private void finishLand(SpawnProfile p, Tier tier, Location loc, Structure st, String key) {
        if (!plugin.chests().place(loc, tier, p.name, null, st)) {
            debug(p.name + ": cannot place at " + loc);
            if (st != null) plugin.structures().scheduleRestore(st);
            return;
        }
        if (plugin.settings().cdSpawn > 0) plugin.cooldowns().start(CooldownManager.GLOBAL, "spawn", plugin.settings().cdSpawn);
        announce(p.announce, loc, tier, key, details(loc, tier, st));
        plugin.effects().playAt(plugin.settings().fxSpawn, loc);
        if (plugin.settings().fwOnSpawn) plugin.fireworks().launch(loc.clone().add(0.5, 0, 0.5), tier.color);
        plugin.getLogger().info("Spawned " + org.bukkit.ChatColor.stripColor(tier.name("en")) + " at " + loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + " (" + p.name + ")");
    }

    /** Manual placement from /mystic spawn (exact coordinates, ignores conditions). shape/theme may be null. */
    public boolean spawnHere(final Tier tier, final Location loc, String shape, String theme) {
        StructureService.Spec spec = plugin.structures().resolve(null, tier.structure, shape, theme, loc);
        if (spec == null) return placeManual(tier, loc.getBlock().getLocation(), null);
        final Location here = loc.getBlock().getLocation();
        plugin.structures().build(spec, here, new StructureService.Callback() {
            public void done(Structure st) { placeManual(tier, st == null ? here : st.chest, st); }
        });
        return true;
    }

    private boolean placeManual(Tier tier, Location at, Structure st) {
        if (!plugin.chests().place(at, tier, "manual", null, st)) { if (st != null) plugin.structures().scheduleRestore(st); return false; }
        announce(SpawnProfile.Announce.EXACT, at, tier, "announce.spawned", details(at, tier, st));
        plugin.effects().playAt(plugin.settings().fxSpawn, at);
        if (plugin.settings().fwOnSpawn) plugin.fireworks().launch(at.clone().add(0.5, 0, 0.5), tier.color);
        return true;
    }

    private static final String[] DIRS = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};

    /** key + ".exact" / ".region" / ".hint" are language keys. */
    private void announce(SpawnProfile.Announce mode, Location loc, Tier tier, String key, String[] extra) {
        Settings.Announce a = plugin.settings().onSpawn;
        if (mode == SpawnProfile.Announce.NONE || a.type == Settings.AnnounceType.NONE) return;
        String w = loc.getWorld().getName();
        if (mode == SpawnProfile.Announce.EXACT) {
            plugin.announcer().send(a, loc, key + ".exact", tier, concat(extra, "x", String.valueOf(loc.getBlockX()),
                    "y", String.valueOf(loc.getBlockY()), "z", String.valueOf(loc.getBlockZ()), "world", w));
        } else if (mode == SpawnProfile.Announce.REGION) {
            int x = Math.round(loc.getBlockX() / 100f) * 100, z = Math.round(loc.getBlockZ() / 100f) * 100;
            plugin.announcer().send(a, loc, key + ".region", tier, concat(extra, "x", String.valueOf(x),
                    "z", String.valueOf(z), "world", w));
        } else {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!plugin.announcer().inRange(a, loc, p) || p.getWorld() != loc.getWorld()) continue;
                double dx = loc.getX() - p.getLocation().getX(), dz = loc.getZ() - p.getLocation().getZ();
                int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 50.0) * 50;
                int dir = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0);
                String d = plugin.lang().get(p, "dir." + DIRS[((dir % 8) + 8) % 8]);
                String[] kv = concat(extra, "distance", String.valueOf(dist), "dir", d, "world", w,
                        "tier", tier.name(plugin.lang().code(p)));
                plugin.announcer().deliver(a, p, plugin.lang().get(p, key + ".hint", kv));
            }
        }
    }

    private static String[] concat(String[] a, String... b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    // ---- airdrop -------------------------------------------------------

    private void airdrop(final SpawnProfile p, final Tier tier, final Location land) {
        if (plugin.animator().size() >= plugin.settings().maxAnimations) { land(p, tier, land, "announce.spawned"); return; }
        announce(p.announce, land, tier, "announce.airdrop", new String[]{"seconds", String.valueOf(p.fallSeconds)});
        plugin.effects().playAt(plugin.settings().fxSpawn, land);
        final int cx = land.getBlockX() >> 4, cz = land.getBlockZ() >> 4;
        plugin.chests().hold(land.getWorld(), cx, cz);          // the chunk must stay loaded while the crate falls
        final Location start = land.clone().add(0.5, p.airdropHeight, 0.5);
        final ArmorStand stand = land.getWorld().spawn(start, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        ItemStack icon = plugin.chests().icon(tier);
        stand.setHelmet(icon);
        final double step = (double) p.airdropHeight / (p.fallSeconds * 20.0);
        plugin.animator().add(new Animator.Animation() {
            double y = start.getY();
            public boolean tick() {
                y -= step;
                if (y > land.getY() + 0.2 && stand.isValid()) {
                    stand.teleport(new Location(land.getWorld(), start.getX(), y, start.getZ()));
                    return true;
                }
                stand.remove();
                land(p, tier, land, "announce.landed");
                plugin.chests().release(land.getWorld(), cx, cz);
                return false;
            }
            public void abort() { stand.remove(); plugin.chests().release(land.getWorld(), cx, cz); }
        });
    }
}
