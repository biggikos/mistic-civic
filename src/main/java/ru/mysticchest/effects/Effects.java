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
            {"ENCHANT", "ENCHANTMENT_TABLE"}, {"DUST", "REDSTONE"}, {"LARGE_SMOKE", "SMOKE_LARGE"}, {"POOF", "EXPLOSION_NORMAL"}, {"EXPLOSION", "EXPLOSION_LARGE"}, {"ELECTRIC_SPARK", "CRIT_MAGIC"}, {"SMOKE", "SMOKE_NORMAL"}, {"FLAME", "FLAME"}
    };

    private final MysticChestPlugin plugin;
    private final Map<String, Particle> particles = new HashMap<String, Particle>();
    private final Map<String, Template> templates = new HashMap<String, Template>();

    public Effects(MysticChestPlugin plugin) { this.plugin = plugin; }

    /** A sound only this player hears (announcements, boss bar pings). */
    public void soundTo(Player p, String name, float vol, float pitch) {
        playSound(p, p.getLocation(), name, vol, pitch);
    }

    /** A burst of particles for everyone near (volcano eruptions, pinata hits). Honors low-resource and the particle cap. */
    public void burst(String name, Location loc, int count, double dx, double dy, double dz, double speed) {
        Settings s = plugin.settings();
        if (s.lowResource || loc.getWorld() == null || s.particleLimit <= 0) return;
        Particle pt = particle(name);
        if (pt == null) return;
        int n = Math.min(count, s.particleLimit);
        double max = s.viewDistance;
        Location pl = new Location(null, 0, 0, 0);
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation(pl).distanceSquared(loc) <= max * max) {
                try { p.spawnParticle(pt, loc, n, dx, dy, dz, speed); } catch (Throwable ignored) {}
            }
        }
    }

    /** One-off sound at a location for nearby players (structure building). */
    public void soundAt(String name, Location loc, float vol, float pitch) {
        if (name == null || name.isEmpty() || loc.getWorld() == null) return;
        double max = plugin.settings().viewDistance;
        for (Player p : loc.getWorld().getPlayers()) if (p.getLocation().distanceSquared(loc) <= max * max) playSound(p, loc, name, vol, pitch);
    }

    public void clear() { snds.clear(); particles.clear(); templates.clear(); }

    /** Resolves every configured sound/particle once so the first opener doesn't pay for XSeries class loading. */
    public void warmUp(Settings s) {
        for (Settings.Effect fx : new Settings.Effect[]{s.fxOpen, s.fxTick, s.fxWin, s.fxRare, s.fxSpawn, s.fxExpire}) {
            if (!fx.sound.isEmpty()) snd(fx.sound);
            if (!fx.particle.isEmpty()) particle(fx.particle);
        }
    }

    /** A resolved sound: the Sound object when we can get one, else a namespaced key ("block.chest.open"). */
    private static final class Snd {
        final Sound sound;
        final String key;
        Snd(Sound s, String k) { sound = s; key = k; }
    }

    private final Map<String, Snd> snds = new HashMap<String, Snd>();

    /**
     * XSeries cannot build Sound objects on 1.21.3+ (Sound stopped being an enum), so there are three tries:
     * XSeries, Sound.valueOf by reflection, and finally the raw key. Names like "minecraft:block.chest.open" always work.
     */
    private Snd snd(String name) {
        Snd r = snds.get(name);
        if (r != null) return r;
        Sound s = null;
        try {
            java.util.Optional<XSound> x = XSound.matchXSound(name);
            if (x.isPresent()) s = x.get().parseSound();
        } catch (Throwable ignored) {}
        if (s == null) {
            try { s = (Sound) Sound.class.getMethod("valueOf", String.class).invoke(null, name.toUpperCase()); } catch (Throwable ignored) {}
        }
        String key = null;
        if (s == null && (name.contains(".") || name.contains(":"))) key = name.toLowerCase();
        r = new Snd(s, key);
        snds.put(name, r);
        if (s == null && key == null) plugin.getLogger().warning("Sound '" + name + "' is not available on this server version (check effects/announce settings).");
        return r;
    }

    @SuppressWarnings("deprecation")
    public void playSound(Player p, Location at, String name, float vol, float pitch) {
        if (name == null || name.isEmpty()) return;
        Snd r = snd(name);
        try {
            if (r.sound != null) p.playSound(at, r.sound, vol, pitch);
            else if (r.key != null) p.playSound(at, r.key, vol, pitch);
        } catch (Throwable ignored) {}
    }

    public Particle particle(String name) {
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
    public void play(Settings.Effect fx, Player p, String... kv) { play(fx, p, org.bukkit.Color.WHITE, kv); }

    public void playTier(Settings.Effect fx, Player p, ru.mysticchest.chest.Tier t, String... kv) { play(fx, p, t.color, kv); }

    private void play(Settings.Effect fx, Player p, org.bukkit.Color color, String... kv) {
        if (fx == null || p == null || !p.isOnline()) return;
        sound(fx, p, p.getLocation());
        particles(fx, p.getLocation().add(0, 1, 0));
        extras(fx, p, color);
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

    /** Optional potion effects and a harmless lightning bolt for the player. */
    private void extras(Settings.Effect fx, Player p, org.bukkit.Color color) {
        for (String spec : fx.potions) {
            org.bukkit.potion.PotionEffect pe = ru.mysticchest.util.Potions.parse(spec, 5);
            if (pe != null) p.addPotionEffect(pe);
        }
        if (fx.lightning) p.getWorld().strikeLightningEffect(p.getLocation());
        if (fx.firework) plugin.fireworks().launch(p.getLocation(), color);
    }

    private void sound(Settings.Effect fx, Player p, Location at) {
        if (fx.sound.isEmpty()) return;
        playSound(p, at, fx.sound, fx.volume, fx.pitch);
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
