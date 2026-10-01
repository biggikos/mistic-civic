package ru.mysticchest.lang;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.util.Template;
import ru.mysticchest.util.Text;
import ru.mysticchest.util.TimeFmt;

import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Message lookup. Every key is coloured and parsed into a Template once, so sending a message
 * costs one StringBuilder pass. Bundles: lang/<code>.yml with the jar copy as defaults.
 */
public final class Lang {
    private static final class Bundle {
        final String code;
        final YamlConfiguration yml;
        final Map<String, Template> cache = new HashMap<String, Template>();
        final Map<String, List<Template>> lists = new HashMap<String, List<Template>>();

        Bundle(String code, YamlConfiguration yml) { this.code = code; this.yml = yml; }
    }

    private final MysticChestPlugin plugin;
    private final Map<String, Bundle> bundles = new HashMap<String, Bundle>();
    private final Set<String> warned = new HashSet<String>();
    private Bundle fixed;
    private boolean auto;
    private Bundle english;

    public Lang(MysticChestPlugin plugin) { this.plugin = plugin; }

    public void load(String language) {
        bundles.clear();
        warned.clear();
        java.util.List<String> codes = new ArrayList<String>();
        codes.add("en");
        codes.add("ru");
        File dir = new File(plugin.getDataFolder(), "lang");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(".yml")) {
                    String c = n.substring(0, n.length() - 4).toLowerCase();
                    if (!codes.contains(c)) codes.add(c);
                }
            }
        }
        for (String code : codes) {
            plugin.configs().ensure("lang/" + code + ".yml");
            File f = new File(dir, code + ".yml");
            YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
            Reader jar = plugin.configs().reader("lang/" + code + ".yml");
            if (jar == null) jar = plugin.configs().reader("lang/en.yml");
            if (jar != null) y.setDefaults(YamlConfiguration.loadConfiguration(jar));
            bundles.put(code, new Bundle(code, y));
        }
        english = bundles.get("en");
        auto = language.equals("auto");
        fixed = auto ? english : bundles.get(language);
        if (fixed == null) {
            plugin.getLogger().warning("Language '" + language + "' not found in lang/, using en.");
            fixed = english;
        }
        plugin.getLogger().info("Language: " + (auto ? "auto (per player)" : fixed.code));
    }

    public String code(CommandSender s) { return bundle(s).code; }

    private Bundle bundle(CommandSender s) {
        if (!auto || !(s instanceof Player)) return fixed;
        String loc = locale((Player) s);
        if (loc != null && loc.length() >= 2) {
            Bundle b = bundles.get(loc.substring(0, 2).toLowerCase());
            if (b != null) return b;
        }
        return english;
    }

    private static String locale(Player p) {
        try {
            return (String) Player.class.getMethod("getLocale").invoke(p);
        } catch (Throwable t) {
            try {
                Object spigot = Player.class.getMethod("spigot").invoke(p);
                return (String) spigot.getClass().getMethod("getLocale").invoke(spigot);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private Template template(Bundle b, String key) {
        Template t = b.cache.get(key);
        if (t != null) return t;
        String raw = b.yml.getString(key);
        if (raw == null && b != english) raw = english.yml.getString(key);
        if (raw == null) {
            if (warned.add(key)) plugin.getLogger().warning("Missing language key: " + key);
            raw = "<" + key + ">";
        }
        t = Template.parse(Text.color(raw));
        b.cache.put(key, t);
        return t;
    }

    public String get(CommandSender s, String key, String... kv) { return template(bundle(s), key).apply(kv); }
    public String get(String key, String... kv) { return template(fixed, key).apply(kv); }

    public List<String> list(CommandSender s, String key, String... kv) {
        Bundle b = bundle(s);
        List<Template> ts = b.lists.get(key);
        if (ts == null) {
            List<String> raw = b.yml.getStringList(key);
            if (raw.isEmpty() && b != english) raw = english.yml.getStringList(key);
            ts = new ArrayList<Template>(raw.size());
            for (String r : raw) ts.add(Template.parse(Text.color(r)));
            b.lists.put(key, ts);
        }
        List<String> out = new ArrayList<String>(ts.size());
        for (Template t : ts) out.add(t.apply(kv));
        return out;
    }

    /** Prefixed message to a sender. */
    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(get(to, "prefix") + get(to, key, kv));
    }

    public String time(CommandSender s, long seconds) {
        return TimeFmt.format(seconds, get(s, "time.d"), get(s, "time.h"), get(s, "time.m"), get(s, "time.s"));
    }
}
