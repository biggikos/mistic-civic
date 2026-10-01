package ru.mysticchest.effects;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.config.Settings;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Instantly detonated, harmless fireworks in the colour of the tier. */
public final class Fireworks implements Listener {
    private final MysticChestPlugin plugin;
    private final Set<UUID> ours = new HashSet<UUID>();

    public Fireworks(MysticChestPlugin plugin) { this.plugin = plugin; }

    public void launch(Location at, Color color) {
        Settings s = plugin.settings();
        if (at.getWorld() == null || s.lowResource) return;
        FireworkEffect.Type type;
        try { type = FireworkEffect.Type.valueOf(s.fwType.toUpperCase()); } catch (IllegalArgumentException e) { type = FireworkEffect.Type.BALL_LARGE; }
        for (int i = 0; i < s.fwCount; i++) {
            try {
                Firework fw = at.getWorld().spawn(at.clone().add(0, 1.5 + i * 0.5, 0), Firework.class);
                ours.add(fw.getUniqueId());
                FireworkMeta m = fw.getFireworkMeta();
                FireworkEffect.Builder b = FireworkEffect.builder().with(type).withColor(color).withFade(Color.WHITE);
                if (s.fwFlicker) b.withFlicker();
                if (s.fwTrail) b.withTrail();
                m.addEffect(b.build());
                fw.setFireworkMeta(m);
                fw.detonate();
            } catch (Throwable ignored) {
                // very old / odd servers: a missing firework is never worth an error
            }
        }
        if (ours.size() > 200) ours.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Firework && ours.contains(d.getUniqueId())) e.setCancelled(true);
    }
}
