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

    public void open(Player p, Tier t) {
        long start = System.nanoTime();
        Settings s = plugin.settings();
        int cd = t.cooldownOpen(s);
        if (cd > 0 && !p.hasPermission("mysticchest.bypass.cooldown")) plugin.cooldowns().start(owner(p), key(t), cd);
        plugin.cooldowns().addOpen(p.getUniqueId());

        OpenType type = t.openMode(s);
        if (type != OpenType.INSTANT && plugin.animator().size() >= s.maxAnimations) type = OpenType.FULL_CHEST;
        List<Reward> rewards = plugin.rewards().roll(p, t, type == OpenType.PICK ? s.pickCards : t.rolls());
        plugin.effects().playTier(s.fxOpen, p, t);
        plugin.announcer().send(s.onOpen, p.getLocation(), "announce.opened", t, "player", p.getName());

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
            default:
                new RouletteSession(plugin, p, t, rewards).start();
        }
        Metrics.open(start);
    }
}
