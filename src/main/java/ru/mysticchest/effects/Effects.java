package ru.mysticchest.effects;

import com.cryptomorin.xseries.XSound;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.config.Settings;
import ru.mysticchest.util.Template;
import ru.mysticchest.util.Text;

import java.util.HashMap;
import java.util.Map;

/** Sounds, particles, titles. Names are resolved once and cached; particles only reach nearby players. */
public final class Effects {
    private static final String[][] PARTICLE_ALIASES = {
            {"HAPPY_VILLAGER", "VILLAGER_HAPPY"}, {"TOTEM_OF_UNDYING", "TOTEM"},
            {"ANGRY_VILLAGER", "VILLAGER_ANGRY"}, {"CRIT", "CRIT"}, {"FIREWORK", "FIREWORKS_SPARK"},
            {"ENCHANT", "ENCHANTMENT_TABLE"}, {"SMOKE", "SMOKE_NORMAL"}, {"FLAME", "FLAME"}
    };

    private final MysticChestPlugin plugin;
    private final Map<String, Sound> sounds = new HashMap<String, Sound>();
    private final Map<String, Particle> particles = new HashMap<String, Particle>();
    private final Map<String, Template> templates = new HashMap<String, Template>();

    public Effects(MysticChestPlugin plugin) { this.plugin = plugin; }

    public void clear() { sounds.clear(); particles.clear(); templates.clear(); }

    /** Resolves every configured sound/particle once so the first opener doesn't pay for XSeries class loading. */
    public void warmUp(Settings s) {
        for (Settings.Effect fx : new Settings.Effect[]{s.fxOpen, s.fxTick, s.fxWin, s.fxRare, s.fxSpawn}) {
            if (!fx.sound.isEmpty()) sound(fx.sound);
            if (!fx.particle.isEmpty()) particle(fx.particle);
        }
    }

    private Sound sound(String name) {
        if (sounds.containsKey(name)) return sounds.get(name);
        Sound s = null;
        try {
            java.util.Optional<XSound> x = XSound.matchXSound(name);
            if (x.isPresent()) s = x.get().parseSound();
        } catch (Throwable ignored) {}
        sounds.put(name, s);
        return s;
    }

    private Particle particle(String name) {
        if (particles.containsKey(name)) return particles.get(name);
        Particle p = tryParticle(name);
        if (p == null) {
            for (String[] a : PARTICLE_ALIASES) {
                if (a[0].equalsIgnoreCase(name)) p = tryParticle(a[1]);
                else if (a[1].equalsIgnoreCase(name)) p = tryParticle(a[0]);
                if (p != null) break;
            }
        }
        particles.put(name, p);
        return p;
    }

    private static Particle tryParticle(String n) {
        try { return Particle.valueOf(n.toUpperCase()); } catch (Throwable t) { return null; }
    }

    private String fmt(String raw, String[] kv) {
        Template t = templates.get(raw);
        if (t == null) { t = Template.parse(Text.color(raw)); templates.put(raw, t); }
        return t.apply(kv);
    }

    /** Plays for a single player (sound + particles around them + title/actionbar). */
    public void play(Settings.Effect fx, Player p, String... kv) {
        if (fx == null || p == null || !p.isOnline()) return;
        sound(fx, p, p.getLocation());
        particles(fx, p.getLocation().add(0, 1, 0));
        if (!fx.title.isEmpty() || !fx.subtitle.isEmpty()) {
            try { p.sendTitle(fmt(fx.title, kv), fmt(fx.subtitle, kv), 5, 40, 10); } catch (Throwable ignored) {}
        }
        if (!fx.actionbar.isEmpty()) actionBar(p, fmt(fx.actionbar, kv));
    }

    /** Plays at a location for everyone close enough (spawn effects). */
    public void playAt(Settings.Effect fx, Location loc) {
        if (fx == null || loc.getWorld() == null) return;
        double max = plugin.settings().viewDistance;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= max * max) sound(fx, p, loc);
        }
        particles(fx, loc.clone().add(0.5, 1, 0.5));
    }

    private void sound(Settings.Effect fx, Player p, Location at) {
        if (fx.sound.isEmpty()) return;
        Sound s = sound(fx.sound);
        if (s != null) p.playSound(at, s, fx.volume, fx.pitch);
    }

    private void particles(Settings.Effect fx, Location loc) {
        if (fx.count <= 0 || fx.particle.isEmpty() || loc.getWorld() == null) return;
        Particle pt = particle(fx.particle);
        if (pt == null) return;
        double max = plugin.settings().viewDistance;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= max * max) {
                try { p.spawnParticle(pt, loc, fx.count, 0.4, 0.5, 0.4, 0.05); } catch (Throwable ignored) {}
            }
        }
    }

    public static void actionBar(Player p, String msg) {
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
        } catch (Throwable t) {
            p.sendMessage(msg);
        }
    }
}
