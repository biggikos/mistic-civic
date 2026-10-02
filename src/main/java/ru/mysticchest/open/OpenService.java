package ru.mysticchest.open;

import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Reward;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.config.Settings;
import ru.mysticchest.cooldown.CooldownManager;
import ru.mysticchest.core.Metrics;

import java.util.List;
import java.util.UUID;

/** The single entry point for "a player opens a chest of this tier" (item click or world chest). */
public final class OpenService {
    private final MysticChestPlugin plugin;

    public OpenService(MysticChestPlugin plugin) { this.plugin = plugin; }

    private UUID owner(Player p) {
        return plugin.settings().cdScope == Settings.Scope.GLOBAL ? CooldownManager.GLOBAL : p.getUniqueId();
    }

    private String key(Tier t) {
        return plugin.settings().cdScope == Settings.Scope.PLAYER ? "open" : "open_" + t.id;
    }

    /** Seconds until this player may open this tier again (0 = now). */
    public long cooldownLeft(Player p, Tier t) {
        return t.cooldownOpen(plugin.settings()) > 0 ? plugin.cooldowns().remaining(owner(p), key(t)) : 0;
    }

    /** @return null when allowed, otherwise {langKey, placeholder pairs...}. */
    public String[] check(Player p, Tier t) {
        Settings s = plugin.settings();
        if (!p.hasPermission("mysticchest.bypass.cooldown") && t.cooldownOpen(s) > 0) {
            long left = plugin.cooldowns().remaining(owner(p), key(t));
            if (left > 0) return new String[]{"deny.cooldown", "time", plugin.lang().time(p, left)};
        }
        if (!p.hasPermission("mysticchest.bypass.limits")) {
            int max = t.maxOpensPerDay(s);
            if (max > 0 && plugin.cooldowns().opens(p.getUniqueId()) >= max) {
                return new String[]{"deny.limit-opens", "max", String.valueOf(max)};
            }
        }
        if (t.pool.isEmpty()) return new String[]{"deny.no-loot"};
        return null;
    }

    /** The concrete way this tier opens now: RANDOM is rolled from random-mode.weights. */
    public OpenType pickMode(Tier t) {
        OpenType type = t.openMode(plugin.settings());
        if (type != OpenType.RANDOM) return type;
        long total = 0;
        for (Integer w : plugin.settings().randomModes.values()) total += w;
        if (total <= 0) return OpenType.ROULETTE;
        long r = (long) (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * total);
        for (java.util.Map.Entry<OpenType, Integer> e : plugin.settings().randomModes.entrySet()) {
            r -= e.getValue();
            if (r < 0) return e.getKey();
        }
        return OpenType.ROULETTE;
    }

    public void open(Player p, Tier t) { open(p, t, null, 0); }

    public void open(Player p, Tier t, OpenType fixed) { open(p, t, fixed, 0); }

    /** @param fixed mode decided earlier (a world chest announced as "cards"); null = decide now. */
    /**
     * A player opens a chest that stood in the world. Hunt: whoever is fast enough (hunt.window-seconds)
     * gets bonus rolls and commands, and a "hunts" point for the leaderboard.
     */
    private final java.util.Map<ru.mysticchest.chest.ChestManager.Active, Pinata> pinatas = new java.util.HashMap<ru.mysticchest.chest.ChestManager.Active, Pinata>();

    /** Pinata state: the rewards are rolled at the first hit and fall out one hit at a time. */
    private static final class Pinata {
        java.util.List<Reward> rewards;
        int hitsLeft;
        long lastHit;
        int extra;
    }

    public void openWorldChest(Player p, ru.mysticchest.chest.ChestManager.Active a) {
        if (a.mode == OpenType.PINATA) { hitPinata(p, a); return; }
        if (!plugin.chests().remove(a, true)) return;     // first opener wins
        Settings s = plugin.settings();
        ru.mysticchest.config.Layered h = a.tier.layered("hunt", s);
        long age = Math.max(0, (System.currentTimeMillis() - a.wokeAt()) / 1000);
        boolean fast = h.bool("enabled", true) && age <= h.integer("window-seconds", 120, 0, 86400);
        open(p, a.tier, a.mode, fast ? h.integer("bonus-rolls", 1, 0, 50) : 0, a.loc);
        if (!fast) return;
        plugin.stats().add(p, "hunts");
        for (String c : h.strings("commands")) {
            String cmd = c.replace("{player}", p.getName());
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
        if (h.bool("announce", true)) plugin.announcer().send(s.onHunt, p.getLocation(), "hunt.fast", a.tier, "player", p.getName(), "ttlsec", String.valueOf(age));
    }

    public void open(Player p, Tier t, OpenType fixed, int extraRolls) { open(p, t, fixed, extraRolls, null); }

    /** @param at where eruptions happen (the chest); null = in front of the player. */
    public void open(Player p, Tier t, OpenType fixed, int extraRolls, org.bukkit.Location at) {
        long start = System.nanoTime();
        Settings s = plugin.settings();
        int cd = t.cooldownOpen(s);
        if (cd > 0 && !p.hasPermission("mysticchest.bypass.cooldown")) plugin.cooldowns().start(owner(p), key(t), cd);
        plugin.cooldowns().addOpen(p.getUniqueId());
        plugin.stats().add(p, "opens");

        OpenType type = fixed != null && fixed != OpenType.RANDOM ? fixed : pickMode(t);
        if (type == OpenType.PINATA && at == null) type = OpenType.VOLCANO;    // a pinata needs a standing chest
        if (type != OpenType.INSTANT && plugin.animator().size() >= s.maxAnimations) type = OpenType.FULL_CHEST;
        List<Reward> rewards = plugin.rewards().roll(p, t, (type == OpenType.PICK ? s.pickCards : t.rolls()) + extraRolls);
        plugin.effects().playTier(s.fxOpen, p, t);
        plugin.announcer().send(s.onOpen, p.getLocation(), "announce.opened", t, "player", p.getName(), "modeid", type.name());

        switch (type) {
            case INSTANT:
                for (Reward r : rewards) plugin.rewards().apply(p, t, r, true, false);
                if (!rewards.isEmpty()) plugin.effects().playTier(s.fxWin, p, t, "player", p.getName(), "item", "");
                break;
            case FULL_CHEST:
                new FullChestSession(plugin, p, t, rewards).start();
                break;
            case PICK:
                new PickSession(plugin, p, t, rewards).start();
                break;
            case VOLCANO:
                new VolcanoSession(plugin, p, t, rewards, at != null ? at.clone().add(0.5, 0.6, 0.5)
                        : p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2.5)).add(0, 0.5, 0)).start();
                break;
            default:
                new RouletteSession(plugin, p, t, rewards).start();
        }
        Metrics.open(start);
    }

    // ---- pinata ------------------------------------------------------------

    private void hitPinata(Player p, ru.mysticchest.chest.ChestManager.Active a) {
        Settings s = plugin.settings();
        ru.mysticchest.config.Layered cfg = a.tier.layered("pinata", s);
        Pinata st = pinatas.get(a);
        long now = System.currentTimeMillis();
        if (st != null && now - st.lastHit < cfg.integer("hit-cooldown-ms", 250, 0, 5000)) return;
        if (st == null) {
            // the first hit opens the chest for real: cooldowns, stats, hunt bonus
            String[] deny = check(p, a.tier);
            if (deny != null) return;
            st = new Pinata();
            ru.mysticchest.config.Layered h = a.tier.layered("hunt", s);
            long age = Math.max(0, (now - a.wokeAt()) / 1000);
            boolean fast = h.bool("enabled", true) && age <= h.integer("window-seconds", 120, 0, 86400);
            st.extra = fast ? h.integer("bonus-rolls", 1, 0, 50) : 0;
            int cd = a.tier.cooldownOpen(s);
            if (cd > 0 && !p.hasPermission("mysticchest.bypass.cooldown")) plugin.cooldowns().start(owner(p), key(a.tier), cd);
            plugin.cooldowns().addOpen(p.getUniqueId());
            plugin.stats().add(p, "opens");
            if (fast) plugin.stats().add(p, "hunts");
            st.rewards = plugin.rewards().roll(p, a.tier, a.tier.rolls() + st.extra);
            st.hitsLeft = Math.max(1, Math.min(cfg.integer("hits", 6, 1, 100), Math.max(1, st.rewards.size())));
            pinatas.put(a, st);
            plugin.announcer().send(s.onOpen, a.loc, "announce.opened", a.tier, "player", p.getName(), "modeid", "PINATA");
        }
        st.lastHit = now;
        int pop = (int) Math.ceil(st.rewards.size() / (double) st.hitsLeft);
        org.bukkit.Location at = a.loc.clone().add(0.5, 1.1, 0.5);
        plugin.effects().playSound(p, at, cfg.str("hit-sound", "ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR"), 0.9f, 0.8f + 0.12f * (1 - st.hitsLeft / (float) Math.max(1, st.hitsLeft + 1)));
        plugin.effects().burst("CRIT", at, 14, 0.35, 0.3, 0.35, 0.15);
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < pop && !st.rewards.isEmpty(); i++) {
            Reward rw = st.rewards.remove(0);
            plugin.rewards().apply(p, a.tier, rw, false, false);
            if (rw.entry.giveItem) {
                org.bukkit.entity.Item it = at.getWorld().dropItem(at, rw.stack.clone());
                it.setVelocity(new org.bukkit.util.Vector((r.nextDouble() - 0.5) * 0.5, 0.35 + r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 0.5));
                it.setPickupDelay(15);
            }
        }
        st.hitsLeft--;
        if (st.hitsLeft <= 0 || st.rewards.isEmpty()) {
            pinatas.remove(a);
            plugin.chests().remove(a, true);
            plugin.effects().burst("EXPLOSION", at, 3, 0.4, 0.4, 0.4, 0);
            plugin.effects().playSound(p, at, "ENTITY_GENERIC_EXPLODE", 0.8f, 1.2f);
            plugin.effects().playTier(s.fxWin, p, a.tier, "player", p.getName(), "item", "");
        } else {
            plugin.lang().send(p, "pinata.hit", "left", String.valueOf(st.hitsLeft));
        }
    }

    public boolean pinataActive(ru.mysticchest.chest.ChestManager.Active a) { return pinatas.containsKey(a); }

    public void forgetPinata(ru.mysticchest.chest.ChestManager.Active a) { pinatas.remove(a); }
}
