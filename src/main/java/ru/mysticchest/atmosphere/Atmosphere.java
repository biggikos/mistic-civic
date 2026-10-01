package ru.mysticchest.atmosphere;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.config.Layered;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A mood around a standing chest, felt ONLY by players near it: their own time of day and weather
 * (a midnight storm over an elite chest) and harmless lightning flashes. Nothing changes for the world
 * or for players farther away; leaving the radius restores their normal sky at once.
 */
public final class Atmosphere {
    private final MysticChestPlugin plugin;
    private final Set<UUID> affected = new HashSet<UUID>();
    private final Map<UUID, String> applied = new HashMap<UUID, String>();
    private final Map<ChestManager.Active, Long> nextBolt = new HashMap<ChestManager.Active, Long>();
    private final Map<ChestManager.Active, Long> nextAmbient = new HashMap<ChestManager.Active, Long>();

    public Atmosphere(MysticChestPlugin plugin) { this.plugin = plugin; }

    private static long ticks(String s) {
        String v = s.trim().toUpperCase();
        if (v.equals("DAWN") || v.equals("SUNRISE")) return 23000;
        if (v.equals("NOON") || v.equals("DAY")) return 6000;
        if (v.equals("DUSK") || v.equals("SUNSET")) return 12500;
        if (v.equals("NIGHT")) return 14000;
        if (v.equals("MIDNIGHT")) return 18000;
        try { return Math.max(0, Math.min(24000, Long.parseLong(v))); } catch (NumberFormatException e) { return 18000; }
    }

    /** Once a second. */
    public void update(List<ChestManager.Active> chests) {
        long now = System.currentTimeMillis();
        Map<UUID, ChestManager.Active> best = new HashMap<UUID, ChestManager.Active>();
        Map<UUID, Double> bestDist = new HashMap<UUID, Double>();
        for (ChestManager.Active a : chests) {
            Layered l = a.tier.layered("atmosphere", plugin.settings());
            if (!l.bool("enabled", false)) continue;
            double r = l.integer("radius", 40, 5, 300);
            World w = a.loc.getWorld();
            if (w == null) continue;
            boolean anyone = false;
            for (Player p : w.getPlayers()) {
                double d = p.getLocation().distanceSquared(a.loc);
                if (d > r * r) continue;
                anyone = true;
                Double old = bestDist.get(p.getUniqueId());
                if (old == null || d < old) { best.put(p.getUniqueId(), a); bestDist.put(p.getUniqueId(), d); }
            }
            if (!anyone) continue;
            int bolt = l.integer("lightning-interval-seconds", 9, 0, 3600);
            if (bolt > 0 && now >= (nextBolt.containsKey(a) ? nextBolt.get(a) : 0L)) {
                nextBolt.put(a, now + (long) (bolt * 1000L * (0.6 + ThreadLocalRandom.current().nextDouble() * 0.8)));
                int lr = l.integer("lightning-radius", 10, 2, 60);
                double ang = ThreadLocalRandom.current().nextDouble() * Math.PI * 2, dist = 3 + ThreadLocalRandom.current().nextDouble() * Math.max(1, lr - 3);
                Location at = a.loc.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
                at.setY(w.getHighestBlockYAt(at));
                try { w.strikeLightningEffect(at); } catch (Throwable ignored) {}   // effect only: no fire, no damage
            }
            String amb = l.str("ambient-sound", "");
            if (!amb.isEmpty() && now >= (nextAmbient.containsKey(a) ? nextAmbient.get(a) : 0L)) {
                nextAmbient.put(a, now + l.integer("ambient-interval-seconds", 12, 1, 3600) * 1000L);
                for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(a.loc) <= r * r) plugin.effects().playSound(p, a.loc, amb, 0.7f, 1f);
            }
        }
        for (Map.Entry<UUID, ChestManager.Active> e : best.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            Layered l = e.getValue().tier.layered("atmosphere", plugin.settings());
            String time = l.str("time", "MIDNIGHT"), weather = l.str("weather", "STORM").toUpperCase();
            String key = time + "|" + weather;
            if (!key.equals(applied.get(p.getUniqueId()))) {
                try {
                    if (!time.equalsIgnoreCase("NONE")) p.setPlayerTime(ticks(time), false);
                    if (weather.equals("CLEAR")) p.setPlayerWeather(WeatherType.CLEAR);
                    else if (!weather.equals("NONE")) p.setPlayerWeather(WeatherType.DOWNFALL);
                } catch (Throwable ignored) {}
                applied.put(p.getUniqueId(), key);
            }
            affected.add(p.getUniqueId());
        }
        for (UUID id : new HashSet<UUID>(affected)) {
            if (best.containsKey(id)) continue;
            reset(id);
        }
        nextBolt.keySet().retainAll(chests);
        nextAmbient.keySet().retainAll(chests);
    }

    private void reset(UUID id) {
        affected.remove(id);
        applied.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        try { p.resetPlayerTime(); p.resetPlayerWeather(); } catch (Throwable ignored) {}
    }

    /** No chests left (or shutdown): everybody gets their normal sky back. */
    public void clearAll() {
        for (UUID id : new HashSet<UUID>(affected)) reset(id);
        nextBolt.clear();
        nextAmbient.clear();
    }
}
