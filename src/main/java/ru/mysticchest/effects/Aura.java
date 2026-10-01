package ru.mysticchest.effects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.config.Settings;
import ru.mysticchest.core.Animator;
import ru.mysticchest.util.Colors;

import java.util.List;

/**
 * Particle aura (ring / pillar / spiral) and a soft chime around every standing world chest.
 * One shared animation that exists only while chests exist; chests without a player nearby cost a distance check.
 */
public final class Aura implements Animator.Animation {
    /** Draws one coloured particle for one player (shared with Lines). */
    interface Spawner { void spawn(Player p, Particle pt, double x, double y, double z, Color c); }

    private static final boolean DUST = classExists("org.bukkit.Particle$DustOptions");

    private final MysticChestPlugin plugin;
    private int tick, phase;
    private final java.util.Map<ChestManager.Active, Lines> lines = new java.util.HashMap<ChestManager.Active, Lines>();
    private long nextSound;

    public Aura(MysticChestPlugin plugin) { this.plugin = plugin; }

    private static boolean classExists(String n) {
        try { Class.forName(n); return true; } catch (Throwable t) { return false; }
    }

    public boolean tick() {
        Settings s = plugin.settings();
        List<ChestManager.Active> chests = plugin.chests().snapshot();
        if (chests.isEmpty()) { plugin.atmosphere().clearAll(); plugin.chests().auraStopped(); return false; }
        tick++;
        if (tick % 20 == 0) {
            if (s.holoEnabled && s.holoCountdown) plugin.chests().updateTimers();
            if (s.bossEnabled) plugin.bossBars().update(chests);
            plugin.guards().tick();
            plugin.atmosphere().update(chests);
            plugin.captures().tick();
        }
        if (s.linesEnabled && tick % s.linesInterval == 0) drawLines(chests, s);
        lines.keySet().retainAll(chests);
        if (!s.auraEnabled || s.auraStyle == Settings.AuraStyle.NONE || tick % s.auraInterval != 0) return true;
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
                if (chime) plugin.effects().playSound(p, base, s.auraSound, s.auraVolume, s.auraPitch);
            }
        }
        return true;
    }

    private final Spawner spawner = new Spawner() {
        public void spawn(Player p, Particle pt, double x, double y, double z, Color c) { put(p, pt, x, y, z, c); }
    };

    private void drawLines(List<ChestManager.Active> chests, Settings s) {
        Particle pt = plugin.effects().particle(s.linesParticle);
        if (pt == null) return;
        boolean dust = pt.name().equals("REDSTONE") || pt.name().equals("DUST");
        double reach = s.viewDistance + 6;
        for (ChestManager.Active a : chests) {
            World w = a.loc.getWorld();
            if (w == null) continue;
            Lines l = null;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(a.loc) > (reach + s.linesLength) * (reach + s.linesLength) / 2.2) continue;
                if (l == null) { l = lines.get(a); if (l == null) { l = new Lines(a, s); lines.put(a, l); } }
                l.draw(a, p, s, pt, a.tier.color, phase + tick, dust, spawner);
            }
        }
    }

    private Color auraColor(Settings.ColorMode mode, Color tier, int i) {
        if (mode == Settings.ColorMode.RANDOM) return Colors.random();
        if (mode == Settings.ColorMode.RAINBOW) return Colors.hsv(phase * 0.02 + i * 0.09, 1, 1);
        return tier;
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
                    put(p, pt, base.getX() + Math.cos(ang) * r, base.getY() + 1.0, base.getZ() + Math.sin(ang) * r, auraColor(mode(s), color, i));
                }
                break;
            case PILLAR:
                { int k = 0; for (double y = 0.6; y < h + 0.6; y += 0.45) put(p, pt, base.getX(), base.getY() + y, base.getZ(), auraColor(mode(s), color, k++)); }
                break;
            default:   // SPIRAL: two helix arms rising together
                for (int arm = 0; arm < 2; arm++) {
                    for (int i = 0; i < 6; i++) {
                        double t = (phase * 0.3 + i * 0.55) % (h * 2.2);
                        double ang = t * 2.4 + arm * Math.PI;
                        put(p, pt, base.getX() + Math.cos(ang) * r, base.getY() + 0.3 + (t / (h * 2.2)) * h, base.getZ() + Math.sin(ang) * r, auraColor(mode(s), color, i + arm * 6));
                    }
                }
        }
    }

    private Settings.ColorMode mode(Settings s) { return s.auraColorMode; }

    @SuppressWarnings("deprecation")
    private void put(Player p, Particle pt, double x, double y, double z, Color color) {
        Location l = new Location(p.getWorld(), x, y, z);
        try {
            boolean dust = pt.name().equals("REDSTONE") || pt.name().equals("DUST");
            if (dust && DUST) Dust.spawn(p, pt, l, 1, color);
            else if (dust) p.spawnParticle(pt, l, 0, Math.max(0.001, color.getRed() / 255.0), color.getGreen() / 255.0, color.getBlue() / 255.0, 1.0);   // 1.12: colour through the offsets
            else p.spawnParticle(pt, l, 1, 0, 0, 0, 0);
        } catch (Throwable ignored) {}
    }

    public void abort() { plugin.atmosphere().clearAll(); plugin.chests().auraStopped(); }
}
