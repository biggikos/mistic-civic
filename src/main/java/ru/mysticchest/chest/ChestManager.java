package ru.mysticchest.chest;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.core.AsyncIO;
import ru.mysticchest.core.Scheduler;
import ru.mysticchest.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chest items (tier <-> ItemStack) and chests standing in the world. */
public final class ChestManager {
    /** Plain visible lore line. Hidden colour-code markers get rewritten by Paper's Adventure lore round-trip. */
    private static final String MARKER = "ID: ";
    /** 1.14+ stores the tier in the item's PersistentDataContainer (fast, no lore parsing); lore is the fallback. */
    private static final boolean PDC = classExists("org.bukkit.persistence.PersistentDataType");

    private static boolean classExists(String n) {
        try { Class.forName(n); return true; } catch (Throwable t) { return false; }
    }

    public static final class Active {
        public final long key;
        public final Location loc;
        public final Tier tier;
        public final String profile;
        public final UUID owner;
        public final long claimUntil;
        public final Material block;
        ArmorStand holo;
        Scheduler.Handle ttl;

        Active(long key, Location loc, Tier tier, String profile, UUID owner, long claimUntil, Material block) {
            this.key = key; this.loc = loc; this.tier = tier; this.profile = profile;
            this.owner = owner; this.claimUntil = claimUntil; this.block = block;
        }
    }

    private final MysticChestPlugin plugin;
    private final Map<World, Map<Long, Active>> byWorld = new HashMap<World, Map<Long, Active>>();
    private int total;

    public ChestManager(MysticChestPlugin plugin) {
        this.plugin = plugin;
        if (PDC) Pdc.init(plugin);
    }

    // ---- keeping the chest's chunk loaded -------------------------------
    private static final java.lang.reflect.Method ADD_TICKET = ticket("addPluginChunkTicket");
    private static final java.lang.reflect.Method REMOVE_TICKET = ticket("removePluginChunkTicket");
    private final Map<String, Integer> held = new HashMap<String, Integer>();

    private static java.lang.reflect.Method ticket(String name) {
        try { return World.class.getMethod(name, int.class, int.class, org.bukkit.plugin.Plugin.class); }
        catch (Throwable t) { return null; }
    }

    public static boolean ticketsSupported() { return ADD_TICKET != null && REMOVE_TICKET != null; }

    /** Counted plugin chunk tickets: several chests in one chunk share one ticket. */
    public void hold(World w, int cx, int cz) {
        if (!ticketsSupported()) return;
        String k = w.getName() + ":" + cx + ":" + cz;
        Integer n = held.get(k);
        held.put(k, n == null ? 1 : n + 1);
        if (n == null) { try { ADD_TICKET.invoke(w, cx, cz, plugin); } catch (Throwable ignored) {} }
    }

    public void release(World w, int cx, int cz) {
        if (!ticketsSupported()) return;
        String k = w.getName() + ":" + cx + ":" + cz;
        Integer n = held.get(k);
        if (n == null) return;
        if (n <= 1) { held.remove(k); try { REMOVE_TICKET.invoke(w, cx, cz, plugin); } catch (Throwable ignored) {} }
        else held.put(k, n - 1);
    }

    /** true when a world chest stands in that chunk (used to veto unloads on servers without tickets). */
    public boolean hasChestIn(Chunk c) {
        Map<Long, Active> m = byWorld.get(c.getWorld());
        if (m == null) return false;
        for (Active a : m.values()) {
            if ((a.loc.getBlockX() >> 4) == c.getX() && (a.loc.getBlockZ() >> 4) == c.getZ()) return true;
        }
        return false;
    }

    private void purgeHolograms(Location loc) {
        World w = loc.getWorld();
        if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) w.getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        for (Entity e : w.getNearbyEntities(loc.clone().add(0.5, 0.5, 0.5), 1, 1, 1)) {
            if (e instanceof ArmorStand) e.remove();
        }
    }

    // ---- item <-> tier -------------------------------------------------

    public ItemStack chestItem(Tier t, org.bukkit.command.CommandSender forWho, int amount) {
        ItemStack it = icon(t);
        it.setAmount(Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(t.name(plugin.lang().code(forWho)));
        List<String> lore = new ArrayList<String>(plugin.lang().list(forWho, "item.lore"));
        lore.add(Text.color("&8" + MARKER + "&7" + t.id));
        m.setLore(lore);
        if (PDC) Pdc.set(m, t.id);
        it.setItemMeta(m);
        return it;
    }

    public ItemStack icon(Tier t) {
        java.util.Optional<XMaterial> xm = XMaterial.matchXMaterial(t.icon);
        ItemStack it = xm.isPresent() ? xm.get().parseItem() : null;
        return it != null ? it : new ItemStack(Material.CHEST);
    }

    public Tier tierOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        ItemMeta m = it.getItemMeta();
        if (PDC) {
            String id = Pdc.get(m);
            if (id != null) return plugin.tiers().get(id);
        }
        if (!m.hasLore()) return null;
        for (String l : m.getLore()) {
            String plain = org.bukkit.ChatColor.stripColor(l);
            if (plain != null && plain.startsWith(MARKER)) return plugin.tiers().get(plain.substring(MARKER.length()).trim());
        }
        return null;
    }

    // ---- world chests --------------------------------------------------

    /** x/y/z packed into one long (26+26+12 bits): lookups allocate nothing. */
    public static long key(Block b) { return key(b.getX(), b.getY(), b.getZ()); }

    public static long key(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) (y + 2048) & 0xFFFL);
    }

    public Active at(Block b) {
        if (total == 0) return null;
        Map<Long, Active> m = byWorld.get(b.getWorld());
        return m == null ? null : m.get(key(b));
    }

    public boolean isActive(Block b) { return at(b) != null; }
    public int count() { return total; }

    private List<Active> all() {
        List<Active> out = new ArrayList<Active>(total);
        for (Map<Long, Active> m : byWorld.values()) out.addAll(m.values());
        return out;
    }

    public int countProfile(String profile) {
        int n = 0;
        for (Map<Long, Active> m : byWorld.values()) {
            for (Active a : m.values()) if (a.profile.equals(profile)) n++;
        }
        return n;
    }

    public boolean place(Location loc, final Tier t, String profile, UUID owner) {
        if (total >= plugin.settings().maxActiveWorldChests) return false;
        Block b = loc.getBlock();
        if (!b.getType().name().endsWith("AIR")) return false;
        Material mat = blockMaterial(t);
        b.setType(mat);
        int claim = owner == null ? 0 : t.cooldownClaim(plugin.settings());
        final Active a = new Active(key(b), b.getLocation(), t, profile, owner,
                claim > 0 ? System.currentTimeMillis() + claim * 1000L : 0L, mat);
        if (plugin.settings().holoEnabled && !plugin.settings().lowResource) {
            String txt = t.holoText != null ? t.holoText : plugin.settings().holoText;
            ArmorStand h = loc.getWorld().spawn(loc.clone().add(0.5, 0.2, 0.5), ArmorStand.class);
            h.setVisible(false);
            h.setGravity(false);
            h.setMarker(true);
            h.setCustomName(Text.color(txt.replace("{tier}", t.name(plugin.lang().code(Bukkit.getConsoleSender())))));
            h.setCustomNameVisible(true);
            a.holo = h;
        }
        a.ttl = plugin.scheduler().later(t.ttlSeconds(plugin.settings()) * 1000L, new Runnable() {
            public void run() {
                if (remove(a, true)) {
                    plugin.announcer().send(plugin.settings().onSpawn, a.loc, "announce.expired", a.tier);
                }
            }
        });
        Map<Long, Active> m = byWorld.get(b.getWorld());
        if (m == null) { m = new HashMap<Long, Active>(); byWorld.put(b.getWorld(), m); }
        m.put(a.key, a);
        total++;
        hold(b.getWorld(), b.getX() >> 4, b.getZ() >> 4);
        save();
        return true;
    }

    private Material blockMaterial(Tier t) {
        ItemStack ic = icon(t);
        Material m = ic.getType();
        return m.isBlock() ? m : Material.CHEST;
    }

    public boolean remove(Active a, boolean clearBlock) {
        Map<Long, Active> m = byWorld.get(a.loc.getWorld());
        if (m == null || m.remove(a.key) == null) return false;
        total--;
        if (m.isEmpty()) byWorld.remove(a.loc.getWorld());
        if (a.ttl != null) a.ttl.cancel();
        if (a.holo != null) {
            if (a.holo.isValid()) a.holo.remove(); else purgeHolograms(a.loc);   // stale reference = chunk was unloaded
        }
        if (clearBlock && a.loc.getBlock().getType() == a.block) a.loc.getBlock().setType(Material.AIR);
        release(a.loc.getWorld(), a.loc.getBlockX() >> 4, a.loc.getBlockZ() >> 4);
        save();
        return true;
    }

    public void removeAll() {
        for (Active a : all()) remove(a, true);
    }

    // ---- crash safety --------------------------------------------------

    private File dataFile() { return new File(plugin.getDataFolder(), "data/active.yml"); }

    private void save() {
        plugin.io().request(dataFile(), new AsyncIO.Source() {
            public String content() {
                YamlConfiguration y = new YamlConfiguration();
                List<String> out = new ArrayList<String>();
                for (Active a : all()) {
                    Location l = a.loc;
                    out.add(l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ() + ";" + a.block.name());
                }
                y.set("chests", out);
                return "## Live world chests. Used to clean up after a crash. Managed by the plugin.\n" + y.saveToString();
            }
        });
    }

    /** Called once on startup: removes blocks/holograms that survived a crash. */
    public void cleanupLeftovers() {
        File f = dataFile();
        if (!f.exists()) return;
        for (String s : YamlConfiguration.loadConfiguration(f).getStringList("chests")) {
            String[] p = s.split(";");
            World w = p.length >= 4 ? Bukkit.getWorld(p[0]) : null;
            if (w == null) continue;
            int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
            Location l = new Location(w, x, y, z);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) w.getChunkAt(x >> 4, z >> 4);
            Material saved = p.length > 4 ? Material.matchMaterial(p[4]) : Material.CHEST;
            if (l.getBlock().getType() == saved) l.getBlock().setType(Material.AIR);
            for (Entity e : w.getNearbyEntities(l.clone().add(0.5, 0.5, 0.5), 1, 1, 1)) {
                if (e instanceof ArmorStand) e.remove();
            }
        }
        f.delete();
    }
}
