package ru.mysticchest.config;

/** A setting that a tier may override: looks in the tier's section first, then in the global one. */
public final class Layered {
    private final Cfg tier, global;

    public Layered(Cfg tier, Cfg global) { this.tier = tier; this.global = global; }

    public boolean has(String path) { return tier.has(path) || global.has(path); }
    public int integer(String path, int def, int min, int max) { return tier.has(path) ? tier.integer(path, def, min, max) : global.integer(path, def, min, max); }
    public double decimal(String path, double def, double min, double max) { return tier.has(path) ? tier.decimal(path, def, min, max) : global.decimal(path, def, min, max); }
    public boolean bool(String path, boolean def) { return tier.has(path) ? tier.bool(path, def) : global.bool(path, def); }
    public String str(String path, String def) { return tier.has(path) ? tier.str(path, def) : global.str(path, def); }
    public java.util.List<String> strings(String path) { return tier.has(path) ? tier.strings(path) : global.strings(path); }
    public Cfg tier() { return tier; }
    public Cfg global() { return global; }
}
