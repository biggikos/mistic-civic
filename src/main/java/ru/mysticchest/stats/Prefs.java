package ru.mysticchest.stats;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.core.AsyncIO;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Per-player switches (currently: muted announcements), saved in data/prefs.yml. */
public final class Prefs {
    private final MysticChestPlugin plugin;
    private final Set<UUID> muted = new HashSet<UUID>();

    public Prefs(MysticChestPlugin plugin) { this.plugin = plugin; }

    private File file() { return new File(plugin.getDataFolder(), "data/prefs.yml"); }

    public void load() {
        muted.clear();
        if (!file().exists()) return;
        for (String s : YamlConfiguration.loadConfiguration(file()).getStringList("muted")) {
            try { muted.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    public boolean muted(UUID id) { return muted.contains(id); }

    /** @return the new state (true = muted). */
    public boolean toggleMute(UUID id) {
        boolean now = muted.add(id);
        if (!now) muted.remove(id);
        plugin.io().request(file(), new AsyncIO.Source() {
            public String content() {
                YamlConfiguration y = new YamlConfiguration();
                List<String> l = new ArrayList<String>();
                for (UUID u : muted) l.add(u.toString());
                y.set("muted", l);
                return "## Players who muted chest announcements (/mystic mute). Managed by the plugin.\n" + y.saveToString();
            }
        });
        return now;
    }
}
