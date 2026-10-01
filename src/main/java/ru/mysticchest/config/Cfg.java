package ru.mysticchest.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/** Validating reader: bad values produce a readable warning with the key path and fall back to the default. */
public final class Cfg {
    private final ConfigurationSection sec;
    private final String where;
    private final Logger log;

    public Cfg(ConfigurationSection sec, String where, Logger log) {
        this.sec = sec;
        this.where = where;
        this.log = log;
    }

    public boolean exists() { return sec != null; }
    public boolean has(String path) { return sec != null && sec.contains(path); }
    public ConfigurationSection raw() { return sec; }

    public Cfg sub(String path) {
        ConfigurationSection s = sec == null ? null : sec.getConfigurationSection(path);
        return new Cfg(s, where + "." + path, log);
    }

    public java.util.Set<String> keys() {
        return sec == null ? Collections.<String>emptySet() : sec.getKeys(false);
    }

    private void warn(String path, String expected, Object got, Object def) {
        log.warning("[" + where + "] " + path + ": expected " + expected + ", got '" + got + "' -> using " + def);
    }

    public int integer(String path, int def, int min, int max) {
        if (sec == null || !sec.contains(path)) return def;
        Object o = sec.get(path);
        if (!(o instanceof Number)) { warn(path, "a number", o, def); return def; }
        int v = ((Number) o).intValue();
        if (v < min || v > max) { warn(path, "a number between " + min + " and " + max, v, def); return def; }
        return v;
    }

    public double decimal(String path, double def, double min, double max) {
        if (sec == null || !sec.contains(path)) return def;
        Object o = sec.get(path);
        if (!(o instanceof Number)) { warn(path, "a number", o, def); return def; }
        double v = ((Number) o).doubleValue();
        if (v < min || v > max) { warn(path, "a number between " + min + " and " + max, v, def); return def; }
        return v;
    }

    public boolean bool(String path, boolean def) {
        if (sec == null || !sec.contains(path)) return def;
        Object o = sec.get(path);
        if (o instanceof Boolean) return (Boolean) o;
        warn(path, "true/false", o, def);
        return def;
    }

    public String str(String path, String def) {
        if (sec == null || !sec.contains(path)) return def;
        Object o = sec.get(path);
        return o == null ? def : String.valueOf(o);
    }

    public <E extends Enum<E>> E enumOf(String path, Class<E> type, E def) {
        if (sec == null || !sec.contains(path)) return def;
        String s = String.valueOf(sec.get(path)).trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return Enum.valueOf(type, s);
        } catch (IllegalArgumentException e) {
            StringBuilder opts = new StringBuilder();
            for (E c : type.getEnumConstants()) opts.append(opts.length() == 0 ? "" : " | ").append(c.name());
            warn(path, opts.toString(), sec.get(path), def);
            return def;
        }
    }

    public List<String> strings(String path) {
        if (sec == null || !sec.contains(path)) return new ArrayList<String>();
        return new ArrayList<String>(sec.getStringList(path));
    }
}
