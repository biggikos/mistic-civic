package ru.mysticchest.config;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.util.YamlBlocks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps user files intact: templates are copied from the jar once; on plugin updates only
 * missing top-level blocks are appended as text (comments survive), and the fresh template is
 * written next to the user file as "<name>.new" for manual diffing.
 */
public final class ConfigManager {
    private static final Pattern VERSION = Pattern.compile("(?m)^config-version:\\s*(\\d+)");
    private final MysticChestPlugin plugin;

    public ConfigManager(MysticChestPlugin plugin) { this.plugin = plugin; }

    public String template(String res) {
        InputStream in = plugin.getResource(res);
        if (in == null) return null;
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    /** Copies the template when missing; returns the user file. */
    public File ensure(String res) {
        File f = new File(plugin.getDataFolder(), res);
        if (!f.exists()) {
            String t = template(res);
            if (t != null) {
                try {
                    f.getParentFile().mkdirs();
                    Files.write(f.toPath(), t.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    plugin.getLogger().warning("Cannot create " + res + ": " + e.getMessage());
                }
            }
        }
        return f;
    }

    /** Loads a yml with the jar template as defaults, upgrading the user file text-wise if it is outdated. */
    public YamlConfiguration load(String res, boolean versioned) {
        File f = ensure(res);
        String tpl = template(res);
        if (versioned && tpl != null && f.exists()) upgrade(f, res, tpl);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (tpl != null) {
            YamlConfiguration def = new YamlConfiguration();
            try {
                def.loadFromString(tpl);
                y.setDefaults(def);
            } catch (Exception e) {
                plugin.getLogger().warning("Bad template " + res + ": " + e.getMessage());
            }
        }
        return y;
    }

    private void upgrade(File f, String res, String tpl) {
        try {
            String user = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            int have = version(user), want = version(tpl);
            if (have >= want) return;
            Set<String> added = new LinkedHashSet<String>();
            String merged = YamlBlocks.appendMissing(user, tpl, added);
            if (VERSION.matcher(merged).find()) {
                merged = VERSION.matcher(merged).replaceFirst(Matcher.quoteReplacement("config-version: " + want));
            } else {
                merged = merged + "\nconfig-version: " + want + "\n";
            }
            Files.write(f.toPath(), merged.getBytes(StandardCharsets.UTF_8));
            Files.write(new File(f.getPath() + ".new").toPath(), tpl.getBytes(StandardCharsets.UTF_8));
            plugin.getLogger().info(res + " upgraded v" + have + " -> v" + want
                    + (added.isEmpty() ? "" : ", added sections: " + added)
                    + ". Compare with " + res + ".new for new options inside existing sections.");
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot upgrade " + res + ": " + e.getMessage());
        }
    }

    private static int version(String text) {
        Matcher m = VERSION.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    public java.io.Reader reader(String res) {
        InputStream in = plugin.getResource(res);
        return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
    }
}
