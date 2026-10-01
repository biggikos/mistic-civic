package ru.mysticchest.effects;

import com.cryptomorin.xseries.XSound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.config.Settings;
import ru.mysticchest.core.Animator;

import java.util.List;

/**
 * Particle aura (ring / pillar / spiral) and a soft chime around every standing world chest.
 * One shared animation that exists only while chests exist; chests without a player nearby cost a distance check.
 */
public final class Aura implements Animator.Animation {
    private static final boolean DUST = classExists("org.bukkit.Particle$DustOptions");

    private final MysticChestPlugin plugin;
    private int tick, phase;
    private long nextSound;

    public Aura(MysticChestPlugin plugin) { this.plugin = plugin; }

    private static boolean classExists(String n) {
        try { Class.forName(n); return true; } catch (Throwable t) { return false; }
    }

    public boolean tick() {
        Settings s = plugin.settings();
        List<ChestManager.Active> chests = plugin.chests().snapshot();
        if (chests.isEmpty() || !s.auraEnabled) { plugin.chests().auraStopped(); return false; }
        if (++tick % s.auraInterval != 0) return true;
        phase++;
        boolean chime = !s.auraSound.isEmpty() && System.currentTimeMillis() >= nextSound;
        if (chime) nextSound = System.currentTimeMillis() + s.auraSoundInterval * 1000L;
        double view2 = (double) s.viewDistance * s.viewDistance;
        for (ChestManager.Active a : chests) {
            World w = a.loc.getWorld();
            if (w == null) continue;
            Location base = a.loc.clone().add(0.5, 0, 0.5);
            Settings.AuraStyle style = style(a, s);
            Particle pt = particle(a, s);
            if (pt == null || style == Settings.AuraStyle.NONE) continue;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(base) > view2) continue;
                draw(p, pt, base, style, a.tier.color, s);
                if (chime) {
                    Sound snd = sound(s.auraSound);
                    if (snd != null) p.playSound(base, snd, s.auraVolume, s.auraPitch);
                }
            }
        }
        return true;
    }

    private Settings.AuraStyle style(ChestManager.Active a, Settings s) {
        if (a.tier.auraStyle == null) return s.auraStyle;
        try { return Settings.AuraStyle.valueOf(a.tier.auraStyle.toUpperCase()); } catch (IllegalArgumentException e) { return s.auraStyle; }
    }

    private Particle particle(ChestManager.Active a, Settings s) {
        return plugin.effects().particle(a.tier.auraParticle != null ? a.tier.auraParticle : s.auraParticle);
    }

    private void draw(Player p, Particle pt, Location base, Settings.AuraStyle style, Color color, Settings s) {
        double r = s.auraRadius, h = s.auraHeight;
        switch (style) {
            case RING:
                for (int i = 0; i < 8; i++) {
                    double ang = phase * 0.35 + i * Math.PI / 4;
                    put(p, pt, base.getX() + Math.cos(ang) * r, base.getY() + 1.0, base.getZ() + Math.sin(ang) * r, color);
                }
                break;
            case PILLAR:
                for (double y = 0.6; y < h + 0.6; y += 0.45) put(p, pt, base.getX(), base.getY() + y, base.getZ(), color);
                break;
            default:   // SPIRAL: two helix arms rising together
                for (int arm = 0; arm < 2; arm++) {
                    for (int i = 0; i < 6; i++) {
                        double t = (phase * 0.3 + i * 0.55) % (h * 2.2);
                        double ang = t * 2.4 + arm * Math.PI;
                        put(p, pt, base.getX() + Math.cos(ang) * r, base.getY() + 0.3 + (t / (h * 2.2)) * h, base.getZ() + Math.sin(ang) * r, color);
                    }
                }
        }
    }

    private void put(Player p, Particle pt, double x, double y, double z, Color color) {
        Location l = new Location(p.getWorld(), x, y, z);
        try {
            if (DUST && (pt.name().equals("REDSTONE") || pt.name().equals("DUST"))) Dust.spawn(p, pt, l, 1, color);
            else p.spawnParticle(pt, l, 1, 0, 0, 0, 0);
        } catch (Throwable ignored) {}
    }

    private Sound sound(String name) {
        try {
            java.util.Optional<XSound> x = XSound.matchXSound(name);
            return x.isPresent() ? x.get().parseSound() : null;
        } catch (Throwable t) { return null; }
    }

    public void abort() { plugin.chests().auraStopped(); }
}
