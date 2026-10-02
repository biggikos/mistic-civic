package ru.mysticchest.guard;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.config.Cfg;
import ru.mysticchest.config.Layered;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Guards around world chests: levels (patrol / veteran / boss ...) are defined in config.yml, a tier chooses
 * its own chance and level weights. While guards live, the chest can stay locked. Bosses get a boss bar and
 * can run commands for whoever kills them.
 */
public final class GuardService implements Listener {
    private static final String TAG = "mc-guard";

    private static final class Group {
        final Set<UUID> alive = new HashSet<UUID>();
        UUID boss;
        BossBar bar;
        String levelName = "";
        ConfigurationSection bossCfg;
        Location center;
        int total;
        double leash;
    }

    private final MysticChestPlugin plugin;
    private final Map<ChestManager.Active, Group> groups = new HashMap<ChestManager.Active, Group>();
    private final Map<UUID, ChestManager.Active> byEntity = new HashMap<UUID, ChestManager.Active>();
    private long sweepUntil;
    private int tick;

    public GuardService(MysticChestPlugin plugin) { this.plugin = plugin; }

    public boolean locked(ChestManager.Active a) {
        Group g = groups.get(a);
        return g != null && !g.alive.isEmpty() && a.tier.layered("guards", plugin.settings()).bool("lock-chest", true);
    }

    /** {count, level} for the spawn announcement, or null when the chest has no guards. */
    public String[] summary(ChestManager.Active a) {
        Group g = groups.get(a);
        if (g == null || g.alive.isEmpty()) return null;
        return new String[]{String.valueOf(g.total), Text.color(g.levelName)};
    }

    public int left(ChestManager.Active a) {
        Group g = groups.get(a);
        return g == null ? 0 : g.alive.size();
    }

    // ---- spawning -------------------------------------------------------

    public void spawnFor(ChestManager.Active a) {
        Tier t = a.tier;
        Layered g = t.layered("guards", plugin.settings());
        if (!g.bool("enabled", true)) return;
        int chance = g.integer("chance", 20, 0, 100);
        if (chance <= 0 || ThreadLocalRandom.current().nextInt(100) >= chance) return;
        ConfigurationSection levels = plugin.settings().root.sub("guards").sub("levels").raw();
        if (levels == null) return;
        // tier weights (guards.levels: {boss: 60, patrol: 40}) win over the weights written on the levels themselves
        Cfg tierLevels = t.cfg.sub("guards").sub("levels");
        Map<String, Integer> weights = new HashMap<String, Integer>();
        long total = 0;
        for (String name : levels.getKeys(false)) {
            ConfigurationSection l = levels.getConfigurationSection(name);
            if (l == null) continue;
            int w = tierLevels.exists() && !tierLevels.keys().isEmpty() ? tierLevels.integer(name, 0, 0, 100000) : l.getInt("weight", 10);
            if (w > 0) { weights.put(name, w); total += w; }
        }
        if (total <= 0) return;
        long r = (long) (ThreadLocalRandom.current().nextDouble() * total);
        String picked = null;
        for (Map.Entry<String, Integer> e : weights.entrySet()) { r -= e.getValue(); if (r < 0) { picked = e.getKey(); break; } }
        if (picked == null) return;
        ConfigurationSection level = levels.getConfigurationSection(picked);
        final Group grp = new Group();
        grp.levelName = level.getString("name", picked);
        grp.center = a.loc.clone().add(0.5, 0, 0.5);
        grp.leash = g.integer("leash", 20, 5, 200);
        int radius = g.integer("radius", 7, 2, 40);
        // a saved structure can mark where guards and the boss stand with [guard] / [boss] signs
        boolean main = a.structure != null && a.structure.chest != null && a.structure.chest.getBlock().equals(a.loc.getBlock());
        java.util.List<Location> points = main ? a.structure.guardPoints : new ArrayList<Location>();
        int pi = 0;
        for (Map<?, ?> m : level.getMapList("mobs")) {
            ConfigurationSection spec = section(m);
            int count = Math.max(1, Math.min(30, spec.getInt("count", 1)));
            for (int i = 0; i < count; i++) {
                LivingEntity e = spawnMob(spec, grp.center, radius, a, points.isEmpty() ? null : points.get(pi++ % points.size()));
                if (e != null) { grp.alive.add(e.getUniqueId()); byEntity.put(e.getUniqueId(), a); glow(e, g, level, spec, false); }
            }
        }
        ConfigurationSection boss = level.getConfigurationSection("boss");
        if (boss != null) {
            LivingEntity e = spawnMob(boss, grp.center, Math.min(radius, 4), a, main && !a.structure.bossPoints.isEmpty() ? a.structure.bossPoints.get(0) : null);
            if (e != null) {
                grp.boss = e.getUniqueId();
                grp.bossCfg = boss;
                grp.alive.add(e.getUniqueId());
                byEntity.put(e.getUniqueId(), a);
                glow(e, g, level, boss, true);
                ConfigurationSection bb = boss.getConfigurationSection("bossbar");
                if (boss.getBoolean("bossbar", true) || bb != null) {
                    BarColor col = BarColor.RED;
                    BarStyle sty = BarStyle.SEGMENTED_10;
                    try { if (bb != null) col = BarColor.valueOf(bb.getString("color", "RED").toUpperCase()); } catch (IllegalArgumentException ignored) {}
                    try { if (bb != null) sty = BarStyle.valueOf(bb.getString("style", "SEGMENTED_10").toUpperCase()); } catch (IllegalArgumentException ignored) {}
                    try {
                        grp.bar = Bukkit.createBossBar(Text.color(boss.getString("name", "Boss")), col, sty);
                        grp.bar.setProgress(1.0);
                    } catch (Throwable ignored) { grp.bar = null; }
                }
            }
        }
        grp.total = grp.alive.size();
        if (grp.alive.isEmpty()) return;
        if (w0(a).getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            plugin.getLogger().warning("[guards] difficulty is PEACEFUL in " + w0(a).getName() + ": hostile guards cannot exist there. Raise the difficulty or lower guards.chance.");
        }
        groups.put(a, grp);
        if (g.bool("announce", true)) {
            plugin.announcer().send(plugin.settings().onGuards, a.loc, "guards.appeared", t, "level", Text.color(grp.levelName),
                    "count", String.valueOf(grp.total), "bossname", Text.color(boss == null ? "" : boss.getString("name", "")));
        }
    }

    /**
     * Outline of a guard: guards.glow (true/false) and glow-color / boss-glow-color, overridable on a level and on a mob
     * (a mob wins over its level, a level over guards:, a tier's guards: over the global one).
     */
    private void glow(LivingEntity e, Layered g, ConfigurationSection level, ConfigurationSection mob, boolean boss) {
        boolean on = g.bool("glow", true);
        String color = boss ? g.str("boss-glow-color", "RED") : g.str("glow-color", "GRAY");
        for (ConfigurationSection s : new ConfigurationSection[]{level, mob}) {
            if (s == null) continue;
            if (s.isSet("glow")) on = s.getBoolean("glow");
            if (boss && s.isSet("boss-glow-color")) color = s.getString("boss-glow-color");
            if (s.isSet("glow-color") && (!boss || s == mob)) color = s.getString("glow-color");
        }
        if (on) ru.mysticchest.effects.Glow.on(e, color);
    }

    private static World w0(ChestManager.Active a) { return a.loc.getWorld(); }

    @SuppressWarnings("unchecked")
    private ConfigurationSection section(Map<?, ?> m) {
        return new MemoryConfiguration().createSection("s", (Map<String, Object>) m);
    }

    private int surfaceY(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        for (int guard = 0; guard < 300 && y < w.getMaxHeight() - 2 && !w.getBlockAt(x, y, z).getType().name().endsWith("AIR"); guard++) y++;
        return y;
    }

    private LivingEntity spawnMob(ConfigurationSection spec, Location center, int radius, ChestManager.Active a, Location fixed) {
        EntityType type;
        try { type = EntityType.valueOf(spec.getString("type", "ZOMBIE").toUpperCase()); }
        catch (IllegalArgumentException e) { plugin.getLogger().warning("[guards] unknown mob type '" + spec.getString("type") + "' on this server version"); return null; }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = r.nextDouble() * Math.PI * 2, dist = Math.min(radius, 2.5) + r.nextDouble() * Math.max(0.5, radius - 2.5);
        int x = (int) Math.floor(center.getX() + Math.cos(ang) * dist), z = (int) Math.floor(center.getZ() + Math.sin(ang) * dist);
        World w = center.getWorld();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) { x = center.getBlockX(); z = center.getBlockZ(); }
        Location at = new Location(w, x + 0.5, surfaceY(w, x, z), z + 0.5);
        if (Math.abs(at.getY() - center.getY()) > 6) at.setY(center.getY() + 1);
        if (fixed != null) at = fixed.clone().add(0.5, 0, 0.5);
        Entity ent;
        try { ent = w.spawnEntity(at, type); } catch (Throwable t) { return null; }
        if (!(ent instanceof LivingEntity)) { ent.remove(); return null; }
        LivingEntity le = (LivingEntity) ent;
        String name = spec.getString("name");
        if (name != null) { le.setCustomName(Text.color(name)); le.setCustomNameVisible(true); }
        double health = spec.getDouble("health", 0);
        if (health > 0) {
            try { le.setMaxHealth(health); le.setHealth(health); } catch (Throwable ignored) {}
        }
        try { le.setRemoveWhenFarAway(false); } catch (Throwable ignored) {}
        try { Entity.class.getMethod("setPersistent", boolean.class).invoke(le, false); } catch (Throwable ignored) {}
        try { le.addScoreboardTag(TAG); } catch (Throwable ignored) {}
        EntityEquipment eq = le.getEquipment();
        if (eq != null) {
            ItemStack helm = item(spec.getString("helmet")), chest = item(spec.getString("chestplate")), legs = item(spec.getString("leggings")),
                    boots = item(spec.getString("boots")), weapon = item(spec.getString("weapon"));
            if (helm != null) eq.setHelmet(helm);
            if (chest != null) eq.setChestplate(chest);
            if (legs != null) eq.setLeggings(legs);
            if (boots != null) eq.setBoots(boots);
            if (weapon != null) eq.setItemInHand(weapon);
            // guards drop their own loot (see config), never the gear
            eq.setHelmetDropChance(0f); eq.setChestplateDropChance(0f); eq.setLeggingsDropChance(0f); eq.setBootsDropChance(0f); eq.setItemInHandDropChance(0f);
        }
        boolean undead = type.name().contains("ZOMBIE") || type.name().contains("SKELETON") || type.name().contains("DROWNED") || type.name().contains("STRAY") || type.name().contains("HUSK");
        if (undead) potion(le, "FIRE_RESISTANCE:99999:0");
        for (String e : spec.getStringList("effects")) potion(le, e);
        return le;
    }

    private static ItemStack item(String name) {
        if (name == null) return null;
        java.util.Optional<XMaterial> x = XMaterial.matchXMaterial(name);
        return x.isPresent() ? x.get().parseItem() : null;
    }

    private static void potion(LivingEntity le, String spec) {
        String[] a = spec.split(":");
        try {
            PotionEffectType t = PotionEffectType.getByName(a[0].trim().toUpperCase());
            if (t == null) return;
            int sec = a.length > 1 ? Integer.parseInt(a[1].trim()) : 30, amp = a.length > 2 ? Integer.parseInt(a[2].trim()) : 0;
            le.addPotionEffect(new PotionEffect(t, sec * 20, amp, true, false));
        } catch (Exception ignored) {}
    }

    // ---- lifecycle ------------------------------------------------------

    public void cleanup(ChestManager.Active a) {
        Group g = groups.remove(a);
        if (g == null) return;
        for (UUID id : g.alive) {
            byEntity.remove(id);
            ru.mysticchest.effects.Glow.off(id);
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        if (g.bar != null) { try { g.bar.removeAll(); } catch (Throwable ignored) {} }
    }

    public void shutdown() {
        for (ChestManager.Active a : new ArrayList<ChestManager.Active>(groups.keySet())) cleanup(a);
    }

    /** Once a second from the chest ticker: boss bar viewers and leash. */
    public void tick() {
        if (groups.isEmpty()) return;
        tick++;
        // guards that vanished without a death event (peaceful difficulty, despawn, /kill of a chunk) must not lock the chest forever
        for (ChestManager.Active a : new ArrayList<ChestManager.Active>(groups.keySet())) {
            Group g = groups.get(a);
            for (UUID id : new ArrayList<UUID>(g.alive)) {
                Entity ent = Bukkit.getEntity(id);
                if (ent == null || !ent.isValid()) { byEntity.remove(id); g.alive.remove(id); ru.mysticchest.effects.Glow.off(id); }
            }
            if (g.alive.isEmpty()) { groups.remove(a); if (g.bar != null) { try { g.bar.removeAll(); } catch (Throwable ignored) {} } }
        }
        for (Map.Entry<ChestManager.Active, Group> e : groups.entrySet()) {
            Group g = e.getValue();
            if (g.bar != null) {
                for (Player p : g.center.getWorld().getPlayers()) {
                    boolean near = p.getLocation().distanceSquared(g.center) <= 60 * 60, has = g.bar.getPlayers().contains(p);
                    if (near && !has) g.bar.addPlayer(p); else if (!near && has) g.bar.removePlayer(p);
                }
            }
            if (tick % 5 != 0) continue;
            for (UUID id : g.alive) {
                Entity ent = Bukkit.getEntity(id);
                if (ent == null || !ent.isValid()) continue;
                if (ent.getLocation().distanceSquared(g.center) > g.leash * g.leash) ent.teleport(g.center.clone().add(0, 1, 0));
            }
        }
    }

    // ---- events ---------------------------------------------------------

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        ChestManager.Active a = byEntity.get(e.getEntity().getUniqueId());
        if (a == null) return;
        final Group g = groups.get(a);
        if (g == null || g.boss == null || !g.boss.equals(e.getEntity().getUniqueId()) || g.bar == null) return;
        final LivingEntity boss = (LivingEntity) e.getEntity();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            public void run() {
                try { g.bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / boss.getMaxHealth()))); } catch (Throwable ignored) {}
            }
        });
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        ChestManager.Active a = byEntity.remove(id);
        if (a == null) return;
        Group g = groups.get(a);
        if (g == null) return;
        g.alive.remove(id);
        ru.mysticchest.effects.Glow.off(id);
        Player killer = e.getEntity().getKiller();
        boolean boss = id.equals(g.boss);
        if (!plugin.settings().root.sub("guards").bool("drop-gear", false)) { /* gear drop chances are 0 already */ }
        if (killer != null) {
            plugin.stats().add(killer, boss ? "bosses" : "guards");
            if (boss && g.bossCfg != null) {
                for (String c : g.bossCfg.getStringList("commands")) {
                    String cmd = c.replace("{player}", killer.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
                }
            }
        }
        if (boss && g.bar != null) { try { g.bar.removeAll(); } catch (Throwable ignored) {} g.bar = null; }
        if (g.alive.isEmpty()) {
            groups.remove(a);
            for (Player p : a.loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(a.loc) <= 40 * 40) {
                    plugin.lang().send(p, "guards.cleared", "tier", a.tier.name(plugin.lang().code(p)));
                    plugin.effects().soundTo(p, "ENTITY_PLAYER_LEVELUP", 1f, 1.4f);
                }
            }
        } else {
            for (Player p : a.loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(a.loc) <= 30 * 30) {
                    ru.mysticchest.effects.Effects.actionBar(p, plugin.lang().get(p, "guards.left", "count", String.valueOf(g.alive.size())));
                }
            }
        }
    }

    // ---- crash leftovers --------------------------------------------------

    public void sweepOrphansFor(int minutes) { sweepUntil = System.currentTimeMillis() + minutes * 60000L; }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (System.currentTimeMillis() > sweepUntil) return;
        for (Entity ent : e.getChunk().getEntities()) {
            if (!byEntity.containsKey(ent.getUniqueId()) && hasTag(ent)) ent.remove();
        }
    }

    private static boolean hasTag(Entity e) {
        try { return e.getScoreboardTags().contains(TAG); } catch (Throwable t) { return false; }
    }

    /** Removes tagged guards near a point (used when a crash leftover is cleaned up). */
    public void purgeNear(Location l, double r) {
        for (Entity e : l.getWorld().getNearbyEntities(l, r, r, r)) if (hasTag(e)) e.remove();
    }
}
