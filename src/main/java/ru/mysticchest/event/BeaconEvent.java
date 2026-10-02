package ru.mysticchest.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.config.Cfg;
import ru.mysticchest.core.Scheduler;
import ru.mysticchest.economy.Economies;
import ru.mysticchest.spawn.SpawnProfile;
import ru.mysticchest.structure.Structure;
import ru.mysticchest.structure.StructureService;
import ru.mysticchest.util.Potions;
import ru.mysticchest.util.Text;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Beacon killer": announced minutes ahead with a countdown, it appears at a random spot as a platform with four
 * elite chests round a beacon. Inside the zone the beacon poisons players with harsher effects at every stage, and
 * pays coins every second - more the longer you stay - but a death inside loses what you carry. Chests can be looted
 * the usual way (capture, hunt ...). When the time is up everything collapses and the terrain returns.
 */
public final class BeaconEvent implements Listener {
    private static final class Acc {
        double pending;
        int seconds;
        boolean inside;
        long total;
    }

    private static final class State {
        Structure st;
        Location beacon;
        List<ChestManager.Active> chests = new ArrayList<ChestManager.Active>();
        long startedAt, endAt;
        int duration;
        final Map<UUID, Acc> acc = new HashMap<UUID, Acc>();
        BossBar bar;
        Scheduler.Handle tick;
        List<Map<?, ?>> stages;
        double zone;
        int ring;
    }

    private final MysticChestPlugin plugin;
    private Scheduler.Handle handle;
    private BossBar warnBar;
    private State state;
    private boolean counting;

    public BeaconEvent(MysticChestPlugin plugin) { this.plugin = plugin; }

    private Cfg cfg() { return plugin.settings().root.sub("beacon-event"); }

    public boolean running() { return state != null; }

    // ---- scheduling ----------------------------------------------------------

    public void reload() {
        cancelPending();
        if (state == null && cfg().bool("enabled", false)) scheduleNext();
    }

    private void cancelPending() {
        if (handle != null) handle.cancel();
        handle = null;
        counting = false;
        if (warnBar != null) { try { warnBar.removeAll(); } catch (Throwable ignored) {} warnBar = null; }
    }

    private void scheduleNext() {
        SpawnProfile p = new SpawnProfile("beacon", cfg());
        long delay;
        if (p.trigger == SpawnProfile.Trigger.TIMES && !p.times.isEmpty()) {
            delay = Long.MAX_VALUE;
            LocalDateTime now = LocalDateTime.now();
            for (LocalTime t : p.times) {
                LocalDateTime at = now.toLocalDate().atTime(t);
                if (!at.isAfter(now)) at = at.plusDays(1);
                delay = Math.min(delay, Duration.between(now, at).toMillis());
            }
        } else {
            double f = 1 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * p.jitterPercent / 100.0;
            delay = Math.max(60000L, (long) (p.intervalMinutes * 60000L * f));
        }
        final long target = System.currentTimeMillis() + delay;
        long lead = maxWarn() * 1000L;
        if (delay <= lead) { beginCountdown(target); return; }
        handle = plugin.scheduler().later(delay - lead, new Runnable() { public void run() { beginCountdown(target); } });
    }

    private int maxWarn() {
        int m = 0;
        for (String s : cfg().strings("warn-seconds")) { try { m = Math.max(m, Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} }
        return m;
    }

    /** Boss bar + chat warnings until the target moment, then the beacon appears. */
    private void beginCountdown(final long target) {
        counting = true;
        final long total = Math.max(1000L, target - System.currentTimeMillis());
        if (cfg().bool("warn-bossbar", true)) {
            try { warnBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_20); } catch (Throwable ignored) { warnBar = null; }
        }
        final java.util.Set<Integer> warned = new java.util.HashSet<Integer>();
        final Runnable[] loop = new Runnable[1];
        loop[0] = new Runnable() {
            public void run() {
                long left = target - System.currentTimeMillis();
                int secs = (int) Math.max(0, (left + 999) / 1000);
                if (warnBar != null) {
                    try {
                        warnBar.setTitle(plugin.lang().get("beacon.warn-bar", "time", plugin.lang().time(Bukkit.getConsoleSender(), Math.max(1, secs))));
                        warnBar.setProgress(Math.max(0, Math.min(1, left / (double) total)));
                        for (Player p : Bukkit.getOnlinePlayers()) if (!warnBar.getPlayers().contains(p)) warnBar.addPlayer(p);
                    } catch (Throwable ignored) {}
                }
                for (String s : cfg().strings("warn-seconds")) {
                    int w;
                    try { w = Integer.parseInt(s.trim()); } catch (NumberFormatException e) { continue; }
                    if (secs <= w && warned.add(w)) {
                        plugin.announcer().send(plugin.settings().onBeacon, null, "beacon.warn", null, "ttlsec", String.valueOf(Math.max(1, secs)));
                    }
                }
                if (left <= 0) { cancelPending(); trigger(); return; }
                handle = plugin.scheduler().later(1000L, loop[0]);
            }
        };
        loop[0].run();
    }

    // ---- start -------------------------------------------------------------

    /** Starts the event right now (command) or when the countdown ends. */
    public boolean trigger() { return trigger(false); }

    /** {@code force} (admin command) ignores the minimum player count. */
    public boolean trigger(boolean force) {
        if (state != null) return false;
        Cfg c = cfg();
        if (!force && Bukkit.getOnlinePlayers().size() < c.integer("min-players", 1, 0, 1000)) {
            plugin.diag().add("beacon", "not enough players online (min-players " + c.integer("min-players", 1, 0, 1000) + "), postponed");
            plugin.getLogger().info("[beacon] not enough players online, postponed.");
            scheduleNext();
            return false;
        }
        final SpawnProfile profile = new SpawnProfile("beacon", c);
        plugin.locators().locate(profile, SpawnProfile.Mode.RANDOM_WORLD, new ru.mysticchest.spawn.Locators.Callback() {
            public void done(Location loc) {
                if (loc == null) { plugin.diag().add("beacon", "no location found, postponed"); plugin.getLogger().warning("[beacon] no location found, postponed."); scheduleNext(); return; }
                build(loc);
            }
        });
        return true;
    }

    private void build(Location loc) {
        StructureService.Spec spec = plugin.structures().resolve(null, null, "beacon", cfg().str("theme", "AUTO"), loc);
        if (spec == null) { scheduleNext(); return; }
        plugin.structures().build(spec, loc, new StructureService.Callback() {
            public void done(Structure st) {
                if (st == null) { plugin.diag().add("beacon", "could not build here, postponed"); plugin.getLogger().warning("[beacon] could not build here, postponed."); scheduleNext(); return; }
                begin(st);
            }
        });
    }

    private void begin(Structure st) {
        Cfg c = cfg();
        Tier tier = plugin.tiers().get(c.str("tier", "elite"));
        if (tier == null) { plugin.getLogger().warning("[beacon] tier '" + c.str("tier", "elite") + "' does not exist."); plugin.structures().scheduleRestore(st, 1); scheduleNext(); return; }
        State s = new State();
        s.st = st;
        s.beacon = st.center.clone().add(0, 2, 0);
        s.duration = c.integer("duration-seconds", 480, 30, 86400);
        s.startedAt = System.currentTimeMillis();
        s.endAt = s.startedAt + s.duration * 1000L;
        s.zone = c.integer("zone-radius", 24, 4, 200);
        s.stages = c.raw() == null ? new ArrayList<Map<?, ?>>() : new ArrayList<Map<?, ?>>(c.raw().getMapList("stages"));
        List<Location> cells = new ArrayList<Location>();
        cells.add(st.chest);
        cells.addAll(st.extraChests);
        for (Location l : cells) {
            if (!plugin.chests().place(l, tier, "beacon", null, null)) continue;
            ChestManager.Active a = plugin.chests().at(l.getBlock());
            if (a != null) { plugin.chests().setTtl(a, s.duration + 20); s.chests.add(a); }
        }
        if (s.chests.isEmpty()) { plugin.structures().scheduleRestore(st, 1); scheduleNext(); return; }
        state = s;
        if (c.bool("bossbar", true)) {
            try { s.bar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SEGMENTED_10); } catch (Throwable ignored) { s.bar = null; }
        }
        Location b = s.beacon;
        plugin.announcer().send(plugin.settings().onBeacon, b, "beacon.start", tier, "x", String.valueOf(b.getBlockX()), "y", String.valueOf(b.getBlockY()),
                "z", String.valueOf(b.getBlockZ()), "world", b.getWorld().getName(), "exact", "true", "chestid", String.valueOf(s.chests.get(0).id),
                "ttlsec", String.valueOf(s.duration), "zone", String.valueOf((int) s.zone), "chests", String.valueOf(s.chests.size()));
        plugin.effects().playAt(plugin.settings().fxSpawn, b);
        if (plugin.settings().fwOnSpawn) plugin.fireworks().launch(b.clone().add(0, 3, 0), org.bukkit.Color.RED);
        plugin.getLogger().info("Beacon event started at " + b.getWorld().getName() + " " + b.getBlockX() + " " + b.getBlockY() + " " + b.getBlockZ());
        tickLoop();
    }

    // ---- running -----------------------------------------------------------

    private void tickLoop() {
        final State s = state;
        if (s == null) return;
        s.tick = plugin.scheduler().later(1000L, new Runnable() {
            public void run() {
                if (state != s) return;
                try { second(s); } catch (Throwable t) { plugin.getLogger().warning("[beacon] tick failed: " + t); }
                if (state == s) tickLoop();
            }
        });
    }

    private Map<?, ?> stageAt(State s, long elapsed) {
        Map<?, ?> cur = null;
        for (Map<?, ?> m : s.stages) {
            Object after = m.get("after-seconds");
            if (after instanceof Number && ((Number) after).longValue() <= elapsed) cur = m;
        }
        return cur;
    }

    private void second(State s) {
        long now = System.currentTimeMillis();
        long elapsed = (now - s.startedAt) / 1000;
        if (now >= s.endAt) { end(); return; }
        Map<?, ?> stage = stageAt(s, elapsed);
        Cfg coins = cfg().sub("coins");
        double start = coins.decimal("start", 5, 0, 1e9), growth = coins.decimal("growth", 1.0, 0, 1e9), max = coins.decimal("max", 80, 0, 1e9);
        String stageName = stage != null && stage.get("name") != null ? Text.color(String.valueOf(stage.get("name"))) : "";
        List<Object> effects = new ArrayList<Object>();
        if (stage != null && stage.get("effects") instanceof List) effects.addAll((List<?>) stage.get("effects"));
        org.bukkit.World w = s.beacon.getWorld();
        for (Player p : w.getPlayers()) {
            boolean in = p.getLocation().distanceSquared(s.beacon) <= s.zone * s.zone && !p.isDead()
                    && (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE);
            Acc a = s.acc.get(p.getUniqueId());
            if (in) {
                if (a == null) { a = new Acc(); s.acc.put(p.getUniqueId(), a); }
                a.inside = true;
                for (Object e : effects) {
                    PotionEffect pe = Potions.parse(String.valueOf(e), 6);
                    if (pe != null) p.addPotionEffect(pe, true);
                }
                double rate = Math.min(max, start + growth * a.seconds);
                a.pending += rate;
                a.seconds++;
                ru.mysticchest.effects.Effects.actionBar(p, plugin.lang().get(p, "beacon.bar", "stage", stageName, "rate", String.valueOf((long) rate),
                        "total", String.valueOf((long) a.pending), "time", plugin.lang().time(p, Math.max(1, (s.endAt - now) / 1000))));
            } else if (a != null && a.inside) {
                a.inside = false;
                payout(p, a, "beacon.paid");
            }
        }
        if (s.bar != null) {
            try {
                s.bar.setTitle(plugin.lang().get("beacon.running-bar", "stage", stageName, "time", plugin.lang().time(Bukkit.getConsoleSender(), Math.max(1, (s.endAt - now) / 1000))));
                s.bar.setProgress(Math.max(0, Math.min(1, (s.endAt - now) / (double) (s.duration * 1000L))));
                double view = s.zone + cfg().integer("bossbar-extra-radius", 120, 0, 2000);
                for (Player p : w.getPlayers()) {
                    boolean near = p.getLocation().distanceSquared(s.beacon) <= view * view, has = s.bar.getPlayers().contains(p);
                    if (near && !has) s.bar.addPlayer(p); else if (!near && has) s.bar.removePlayer(p);
                }
            } catch (Throwable ignored) {}
        }
        // the zone border, a ring of red dust for everybody close
        if (++s.ring % 2 == 0 && !plugin.settings().lowResource) {
            for (int i = 0; i < 40; i++) {
                double ang = i * Math.PI * 2 / 40;
                Location at = s.beacon.clone().add(Math.cos(ang) * s.zone, -1.0, Math.sin(ang) * s.zone);
                at.setY(w.getHighestBlockYAt(at) + 0.6);
                for (Player p : w.getPlayers()) {
                    if (p.getLocation().distanceSquared(at) < 48 * 48) try { p.spawnParticle(ru.mysticchest.util.Particles.dustOrFallback(plugin), at, 1, 0, 0, 0, 0); } catch (Throwable ignored) {}
                }
            }
        }
    }

    private void payout(Player p, Acc a, String langKey) {
        long amount = Math.round(a.pending);
        a.pending = 0;
        a.seconds = 0;
        if (amount <= 0) return;
        a.total += amount;
        Cfg coins = cfg().sub("coins");
        boolean done = false;
        if (!coins.str("payout", "ECONOMY").equalsIgnoreCase("COMMAND")) {
            Economies.Resolved r = plugin.economies().resolve(coins.str("currency", "money"));
            if (r != null) { r.provider.deposit(p, r.currency, amount); done = true; }
        }
        if (!done) {
            String cmd = coins.str("command", "").replace("{player}", p.getName()).replace("{amount}", String.valueOf(amount));
            if (!cmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
        plugin.lang().send(p, langKey, "amount", String.valueOf(amount), "currency", coins.str("currency", "money"));
        plugin.effects().soundTo(p, "ENTITY_EXPERIENCE_ORB_PICKUP", 1f, 1.2f);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        State s = state;
        if (s == null) return;
        Acc a = s.acc.get(e.getEntity().getUniqueId());
        if (a == null || a.pending <= 0 || !cfg().sub("coins").bool("lose-on-death", true)) return;
        long lost = Math.round(a.pending);
        a.pending = 0;
        a.seconds = 0;
        a.inside = false;
        plugin.lang().send(e.getEntity(), "beacon.lost", "amount", String.valueOf(lost));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        State s = state;
        if (s == null) return;
        Acc a = s.acc.get(e.getPlayer().getUniqueId());
        if (a != null && a.pending > 0) payout(e.getPlayer(), a, "beacon.paid");
    }

    // ---- end ---------------------------------------------------------------

    public void end() {
        final State s = state;
        if (s == null) return;
        state = null;
        if (s.tick != null) s.tick.cancel();
        List<Map.Entry<UUID, Acc>> list = new ArrayList<Map.Entry<UUID, Acc>>(s.acc.entrySet());
        for (Map.Entry<UUID, Acc> e : list) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && e.getValue().pending > 0) payout(p, e.getValue(), "beacon.paid");
        }
        java.util.Collections.sort(list, new java.util.Comparator<Map.Entry<UUID, Acc>>() {
            public int compare(Map.Entry<UUID, Acc> a, Map.Entry<UUID, Acc> b) { return Long.compare(b.getValue().total, a.getValue().total); }
        });
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < list.size() && i < 3; i++) {
            if (list.get(i).getValue().total <= 0) break;
            OfflinePlayerName n = new OfflinePlayerName(list.get(i).getKey());
            if (top.length() > 0) top.append("&7, ");
            top.append("&e#").append(i + 1).append(" &f").append(n.name).append(" &7(").append(list.get(i).getValue().total).append(")");
        }
        for (ChestManager.Active a : s.chests) plugin.chests().remove(a, true);
        plugin.structures().scheduleRestore(s.st, 5);
        if (s.bar != null) { try { s.bar.removeAll(); } catch (Throwable ignored) {} }
        plugin.announcer().send(plugin.settings().onBeacon, null, "beacon.end", null, "winners", Text.color(top.length() == 0 ? "&8-" : top.toString()));
        plugin.getLogger().info("Beacon event ended.");
        if (cfg().bool("enabled", false)) scheduleNext();
    }

    private static final class OfflinePlayerName {
        final String name;
        OfflinePlayerName(UUID id) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            name = n == null ? "?" : n;
        }
    }

    /** /mystic event beacon stop */
    public void stop() {
        cancelPending();
        if (state != null) end();
    }

    public void shutdown() {
        cancelPending();
        State s = state;
        state = null;
        if (s != null) {
            if (s.tick != null) s.tick.cancel();
            if (s.bar != null) { try { s.bar.removeAll(); } catch (Throwable ignored) {} }
        }
    }

    public String status() {
        if (state != null) return "running, " + Math.max(0, (state.endAt - System.currentTimeMillis()) / 1000) + "s left at " + state.beacon.getBlockX() + " " + state.beacon.getBlockY() + " " + state.beacon.getBlockZ();
        if (counting) return "counting down";
        return cfg().bool("enabled", false) ? "waiting for the next start" : "disabled";
    }
}
