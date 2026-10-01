package ru.mysticchest.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.LootEntry;
import ru.mysticchest.chest.LootPool;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.core.AsyncIO;
import ru.mysticchest.util.ItemCodec;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** loot/<tier>.yml persistence + all mutations (command and GUI editor go through here). */
public final class LootStore {
    private static final String HEADER =
            "## Rewards of this tier. Edit in game: /mystic loot edit <tier>\n"
                    + "## This file is rewritten (comments are lost) when loot is changed in game.\n"
                    + "## keys: material|item, amount | min+max, weight, give-item, commands, broadcast, permission, limit-per-player\n";

    private final MysticChestPlugin plugin;
    private final Map<String, List<LootEntry>> data = new HashMap<String, List<LootEntry>>();

    public LootStore(MysticChestPlugin plugin) { this.plugin = plugin; }

    private File file(Tier t) { return new File(plugin.getDataFolder(), "loot/" + t.id + ".yml"); }

    public List<LootEntry> entries(Tier t) {
        List<LootEntry> l = data.get(t.id);
        if (l == null) { l = new ArrayList<LootEntry>(); data.put(t.id, l); }
        return l;
    }

    public void load(Tier t) {
        plugin.configs().ensure("loot/" + t.id + ".yml");   // only copies when the jar ships one
        List<LootEntry> list = new ArrayList<LootEntry>();
        File f = file(t);
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (Map<?, ?> m : y.getMapList("entries")) {
                try {
                    LootEntry e = fromMap(m);
                    if (e != null) list.add(e);
                } catch (Exception ex) {
                    plugin.getLogger().warning("[loot/" + t.id + ".yml] bad entry skipped: " + ex.getMessage());
                }
            }
        }
        data.put(t.id, list);
        rebuild(t);
    }

    @SuppressWarnings("unchecked")
    private LootEntry fromMap(Map<?, ?> m) {
        ConfigurationSection s = new MemoryConfiguration().createSection("e", (Map<String, Object>) m);
        ItemStack item = ItemCodec.read(s);
        if (item == null) {
            plugin.getLogger().fine("Skipping reward with unsupported item: " + m.get("material"));
            return null;
        }
        String id = s.getString("id");
        LootEntry e = new LootEntry(id != null ? id : newId(), item);
        int amount = s.getInt("amount", Math.max(1, item.getAmount()));
        e.min = Math.max(1, s.getInt("min", amount));
        e.max = Math.max(e.min, s.getInt("max", Math.max(e.min, amount)));
        e.weight = Math.max(1, s.getInt("weight", plugin.settings().defaultWeight));
        e.giveItem = s.getBoolean("give-item", true);
        e.broadcast = s.getBoolean("broadcast", false);
        e.permission = s.getString("permission", "");
        e.limitPerPlayer = Math.max(0, s.getInt("limit-per-player", 0));
        List<?> cmds = s.getList("commands");
        if (cmds != null) {
            for (Object o : cmds) {
                if (o instanceof String) {
                    e.commands.add(new LootEntry.Cmd(false, (String) o, 100));
                } else if (o instanceof Map) {
                    Map<?, ?> cm = (Map<?, ?>) o;
                    Object text = cm.get("command");
                    if (text == null) continue;
                    boolean asPlayer = "PLAYER".equalsIgnoreCase(String.valueOf(cm.get("run")));
                    Object ch = cm.get("chance");
                    e.commands.add(new LootEntry.Cmd(asPlayer, String.valueOf(text),
                            ch instanceof Number ? ((Number) ch).intValue() : 100));
                }
            }
        }
        return e;
    }

    private Map<String, Object> toMap(LootEntry e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", e.id);
        MemoryConfiguration tmp = new MemoryConfiguration();
        ItemCodec.write(tmp, e.item);
        m.putAll(tmp.getValues(false));
        if (e.min == e.max) m.put("amount", e.min);
        else { m.put("min", e.min); m.put("max", e.max); }
        m.put("weight", e.weight);
        if (!e.giveItem) m.put("give-item", false);
        if (e.broadcast) m.put("broadcast", true);
        if (e.permission != null && !e.permission.isEmpty()) m.put("permission", e.permission);
        if (e.limitPerPlayer > 0) m.put("limit-per-player", e.limitPerPlayer);
        if (!e.commands.isEmpty()) {
            List<Object> cl = new ArrayList<Object>();
            for (LootEntry.Cmd c : e.commands) {
                Map<String, Object> cm = new LinkedHashMap<String, Object>();
                cm.put("run", c.asPlayer ? "PLAYER" : "CONSOLE");
                cm.put("command", c.text);
                if (c.chance < 100) cm.put("chance", c.chance);
                cl.add(cm);
            }
            m.put("commands", cl);
        }
        return m;
    }

    private static String newId() {
        String s = Long.toString(Math.abs(ThreadLocalRandom.current().nextLong()), 36);
        return s.length() > 6 ? s.substring(0, 6) : s;
    }

    /** Rebuilds the weighted pool, queues an async save and drops cached GUIs. */
    public void commit(final Tier t) {
        rebuild(t);
        final List<LootEntry> list = entries(t);
        plugin.io().request(file(t), new AsyncIO.Source() {
            public String content() {
                YamlConfiguration y = new YamlConfiguration();
                List<Object> out = new ArrayList<Object>();
                for (LootEntry e : list) out.add(toMap(e));
                y.set("entries", out);
                return HEADER + y.saveToString();
            }
        });
        plugin.invalidateGuis();
    }

    private void rebuild(Tier t) {
        t.pool = new LootPool(entries(t), plugin.settings().rareBelow);
    }

    // ---- mutations ------------------------------------------------------

    public LootEntry addItem(Tier t, ItemStack stack, int weight, boolean merge) {
        if (merge) {
            for (LootEntry e : entries(t)) {
                if (e.commands.isEmpty() && e.giveItem && ItemCodec.similar(e.item, stack)) {
                    e.weight += weight;
                    e.min = Math.min(e.min, stack.getAmount());
                    e.max = Math.max(e.max, stack.getAmount());
                    return e;
                }
            }
        }
        LootEntry e = new LootEntry(newId(), stack.clone());
        e.min = e.max = Math.max(1, stack.getAmount());
        e.weight = Math.max(1, weight);
        entries(t).add(e);
        return e;
    }

    public LootEntry addCommand(Tier t, ItemStack icon, int weight, String command) {
        LootEntry e = new LootEntry(newId(), icon.clone());
        e.min = e.max = 1;
        e.weight = Math.max(1, weight);
        e.giveItem = false;
        e.commands.add(new LootEntry.Cmd(false, command, 100));
        entries(t).add(e);
        return e;
    }

    public LootEntry remove(Tier t, int index) {
        List<LootEntry> l = entries(t);
        return index >= 0 && index < l.size() ? l.remove(index) : null;
    }

    public void clear(Tier t) { entries(t).clear(); }
}
