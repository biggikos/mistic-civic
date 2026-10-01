package ru.mysticchest.chest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.config.Cfg;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TierRegistry {
    private final MysticChestPlugin plugin;
    private final Map<String, Tier> tiers = new LinkedHashMap<String, Tier>();

    public TierRegistry(MysticChestPlugin plugin) { this.plugin = plugin; }

    public void load() {
        tiers.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(plugin.configs().ensure("tiers.yml"));
        ConfigurationSection root = y.getConfigurationSection("tiers");
        if (root == null) {
            plugin.getLogger().warning("tiers.yml has no 'tiers:' section.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            String key = id.toLowerCase();
            Tier t = new Tier(key, new Cfg(s, "tiers.yml:" + id, plugin.getLogger()));
            tiers.put(key, t);
            plugin.loot().load(t);
        }
    }

    public Tier get(String id) { return id == null ? null : tiers.get(id.toLowerCase()); }
    public Collection<Tier> all() { return tiers.values(); }
}
