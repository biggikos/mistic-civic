package ru.mysticchest.open;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Reward;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.config.Layered;
import ru.mysticchest.core.Animator;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Volcano": the chest erupts. Lava and flames burst out, the ground rumbles, and every reward is launched as a
 * real item in a fountain over a few seconds - whoever is quick picks them up. Nothing is lost if the opener leaves:
 * the items are in the world.
 */
final class VolcanoSession implements Animator.Animation {
    private final MysticChestPlugin plugin;
    private final Player p;
    private final Tier t;
    private final List<Reward> rewards;
    private final Location at;
    private final Layered cfg;
    private final int duration, protect;
    private int tick, released;

    VolcanoSession(MysticChestPlugin plugin, Player p, Tier t, List<Reward> rewards, Location at) {
        this.plugin = plugin;
        this.p = p;
        this.t = t;
        this.rewards = rewards;
        this.at = at;
        this.cfg = t.layered("volcano", plugin.settings());
        this.duration = Math.max(10, cfg.integer("duration-ticks", 70, 10, 600));
        this.protect = cfg.integer("owner-protect-seconds", 0, 0, 120);
    }

    void start() {
        if (rewards.isEmpty()) return;
        plugin.effects().playSound(p, at, "ENTITY_ENDER_DRAGON_GROWL", 0.9f, 0.6f);
        plugin.effects().burst("EXPLOSION", at, 2, 0.3, 0.3, 0.3, 0);
        plugin.animator().add(this);
    }

    public boolean tick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // the crater: lava drops, flames, smoke, every tick
        plugin.effects().burst("LAVA", at, 6, 0.5, 0.2, 0.5, 0);
        plugin.effects().burst("FLAME", at, 10, 0.35, 0.25, 0.35, 0.12);
        if (tick % 3 == 0) plugin.effects().burst("LARGE_SMOKE", at.clone().add(0, 0.6, 0), 4, 0.3, 0.4, 0.3, 0.05);
        if (tick % 5 == 0) plugin.effects().playSound(p, at, "ENTITY_GENERIC_EXPLODE", 0.5f, 0.5f + r.nextFloat() * 0.5f);
        if (tick % 9 == 0) plugin.effects().playSound(p, at, "BLOCK_LAVA_POP", 1f, 0.8f + r.nextFloat() * 0.4f);
        // rewards leave the crater evenly over the whole eruption
        int target = Math.min(rewards.size(), (int) Math.ceil(rewards.size() * (tick + 1) / (double) duration));
        double spread = cfg.decimal("spread", 0.5, 0.05, 3), up = cfg.decimal("launch-power", 0.85, 0.2, 2.5);
        while (released < target) {
            Reward rw = rewards.get(released++);
            plugin.rewards().apply(p, t, rw, false, false);                  // commands + announcements; the item is dropped below
            if (!rw.entry.giveItem) continue;
            Item it = at.getWorld().dropItem(at, rw.stack.clone());
            double a = r.nextDouble() * Math.PI * 2, h = 0.12 + r.nextDouble() * spread;
            it.setVelocity(new Vector(Math.cos(a) * h, up * (0.7 + r.nextDouble() * 0.5), Math.sin(a) * h));
            it.setPickupDelay(25);
            if (protect > 0 && p.isOnline()) {
                try { it.setOwner(p.getUniqueId()); } catch (Throwable ignored) {}
            }
            plugin.effects().burst("FLAME", at, 12, 0.2, 0.3, 0.2, 0.25);
        }
        tick++;
        if (tick < duration) return true;
        finish();
        return false;
    }

    private void finish() {
        plugin.effects().burst("EXPLOSION", at, 2, 0.5, 0.3, 0.5, 0);
        plugin.effects().playSound(p, at, "ENTITY_GENERIC_EXPLODE", 0.8f, 0.9f);
        if (p.isOnline()) plugin.effects().playTier(plugin.settings().fxWin, p, t, "player", p.getName(), "item", "");
        if (plugin.settings().fwOnRare) plugin.fireworks().launch(at, t.color);
    }

    /** Server shutdown / animator full: put everything on the ground at once. */
    public void abort() {
        while (released < rewards.size()) {
            Reward rw = rewards.get(released++);
            plugin.rewards().apply(p, t, rw, false, false);
            if (rw.entry.giveItem) at.getWorld().dropItemNaturally(at, rw.stack.clone());
        }
    }
}
