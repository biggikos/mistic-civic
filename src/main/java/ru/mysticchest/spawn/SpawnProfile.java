package ru.mysticchest.spawn;

import ru.mysticchest.config.Cfg;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SpawnProfile {
    public enum Mode { RANDOM_WORLD, NEAR_PLAYER, FIXED_POINTS, AIRDROP }
    public enum Announce { EXACT, REGION, HINT, NONE }
    public enum Trigger { INTERVAL, TIMES, ONLINE_THRESHOLD }

    public final String name;
    public final boolean enabled;
    public final Mode mode;
    public final Trigger trigger;
    public final int intervalMinutes;
    public final List<LocalTime> times = new ArrayList<LocalTime>();
    public final int minPlayers, maxActive, thresholdPlayers, thresholdCooldownMinutes;
    public final List<String> worlds;
    public final Map<String, Integer> tierWeights = new LinkedHashMap<String, Integer>();
    public final Announce announce;
    // random-world
    public final int radius, minDistanceFromSpawn;
    public final List<String> avoidGround;
    // near-player
    public final int nearMin, nearMax;
    public final boolean notifyPlayer;
    // fixed points
    public final List<String> pointNames;
    // airdrop
    public final Mode airdropLocation;
    public final int airdropHeight, fallSeconds;

    public SpawnProfile(String name, Cfg c) {
        this.name = name;
        enabled = c.bool("enabled", true);
        mode = c.enumOf("mode", Mode.class, Mode.RANDOM_WORLD);
        Cfg tr = c.sub("trigger");
        trigger = tr.enumOf("type", Trigger.class, Trigger.INTERVAL);
        intervalMinutes = tr.integer("minutes", 30, 1, 525600);
        for (String s : tr.strings("times")) {
            try { times.add(LocalTime.parse(s.length() == 4 ? "0" + s : s)); }
            catch (Exception e) { /* bad time is reported by the service */ }
        }
        thresholdPlayers = tr.integer("players", 10, 1, 100000);
        thresholdCooldownMinutes = tr.integer("cooldown-minutes", 60, 1, 525600);
        minPlayers = c.integer("min-players", 1, 0, 100000);
        maxActive = c.integer("max-active", 3, 1, 1000);
        worlds = c.strings("worlds");
        Cfg tw = c.sub("tier-weights");
        for (String k : tw.keys()) tierWeights.put(k.toLowerCase(), tw.integer(k, 1, 1, 1000000));
        announce = c.enumOf("announce", Announce.class, Announce.EXACT);
        Cfg rw = c.sub("random-world");
        radius = rw.integer("radius", 1500, 10, 29999984);
        minDistanceFromSpawn = rw.integer("min-distance-from-spawn", 50, 0, 29999984);
        List<String> ag = rw.strings("avoid-ground");
        avoidGround = ag.isEmpty() ? java.util.Arrays.asList("WATER", "LAVA", "LEAVES", "ICE", "CACTUS", "MAGMA") : ag;
        Cfg np = c.sub("near-player");
        nearMin = np.integer("min-distance", 40, 1, 10000);
        nearMax = Math.max(nearMin + 1, np.integer("max-distance", 150, 2, 10000));
        notifyPlayer = np.bool("notify-player", true);
        pointNames = c.sub("fixed-points").strings("names");
        Cfg ad = c.sub("airdrop");
        Mode loc = ad.enumOf("location", Mode.class, Mode.RANDOM_WORLD);
        airdropLocation = loc == Mode.NEAR_PLAYER ? Mode.NEAR_PLAYER : Mode.RANDOM_WORLD;
        airdropHeight = ad.integer("height", 60, 5, 250);
        fallSeconds = ad.integer("fall-seconds", 12, 1, 300);
    }
}
