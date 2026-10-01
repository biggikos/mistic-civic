package ru.mysticchest.chest;

import ru.mysticchest.config.Cfg;
import ru.mysticchest.config.Settings;
import ru.mysticchest.open.OpenType;
import ru.mysticchest.util.Text;

import java.util.HashMap;
import java.util.Map;

/** One chest tier. Optional per-tier overrides are resolved against Settings through the accessors. */
public final class Tier {
    public final String id;
    public final long price;
    public final String currency, icon;
    public final boolean purchasable;
    public final int rollsMin, rollsMax;
    public final int pityAfter;
    private final OpenType openMode;
    private final int cdOpen, cdBuy, cdClaim, maxOpens, maxPurchases, ttl;
    public final String holoText;
    private final Map<String, String> names = new HashMap<String, String>();
    private final String anyName;
    public LootPool pool = new LootPool(new java.util.ArrayList<LootEntry>(), 0);

    public Tier(String id, Cfg c) {
        this.id = id;
        Object nm = c.raw() == null ? null : c.raw().get("name");
        String any = id;
        if (nm instanceof org.bukkit.configuration.ConfigurationSection) {
            org.bukkit.configuration.ConfigurationSection ns = (org.bukkit.configuration.ConfigurationSection) nm;
            for (String k : ns.getKeys(false)) {
                String v = Text.color(String.valueOf(ns.get(k)));
                names.put(k.toLowerCase(), v);
                any = names.containsKey("en") ? names.get("en") : (any.equals(id) ? v : any);
            }
        } else if (nm != null) {
            any = Text.color(String.valueOf(nm));
        } else {
            any = Text.color("&f" + id);
        }
        this.anyName = any;
        this.price = Math.max(0, c.raw() == null ? 0 : c.raw().getLong("price", 0));
        this.currency = c.str("currency", "");
        this.icon = c.str("icon", "CHEST");
        this.purchasable = c.bool("purchasable", true) && price > 0;
        Object r = c.raw() == null ? null : c.raw().get("rolls");
        if (r instanceof org.bukkit.configuration.ConfigurationSection) {
            Cfg rc = c.sub("rolls");
            int mn = rc.integer("min", 1, 1, 1000);
            rollsMin = mn;
            rollsMax = Math.max(mn, rc.integer("max", mn, 1, 1000));
        } else {
            int n = c.integer("rolls", 3, 1, 1000);
            rollsMin = n;
            rollsMax = n;
        }
        this.openMode = c.has("open-mode") ? c.enumOf("open-mode", OpenType.class, null) : null;
        Cfg cd = c.sub("cooldowns");
        cdOpen = cd.has("open-seconds") ? cd.integer("open-seconds", 0, 0, 31536000) : -1;
        cdBuy = cd.has("buy-seconds") ? cd.integer("buy-seconds", 0, 0, 31536000) : -1;
        cdClaim = cd.has("claim-seconds") ? cd.integer("claim-seconds", 0, 0, 86400) : -1;
        Cfg lim = c.sub("limits");
        maxOpens = lim.has("max-opens-per-day") ? lim.integer("max-opens-per-day", 0, 0, 1000000) : -1;
        maxPurchases = lim.has("max-purchases-per-day") ? lim.integer("max-purchases-per-day", 0, 0, 1000000) : -1;
        ttl = c.has("ttl-seconds") ? c.integer("ttl-seconds", 300, 5, 31536000) : -1;
        pityAfter = c.sub("pity").integer("after", 0, 0, 100000);
        holoText = c.has("hologram") ? c.str("hologram", null) : null;
    }

    public String name(String lang) {
        String n = names.get(lang);
        return n != null ? n : anyName;
    }

    public OpenType openMode(Settings s) { return openMode != null ? openMode : s.defaultOpenMode; }
    public int cooldownOpen(Settings s) { return cdOpen >= 0 ? cdOpen : s.cdOpen; }
    public int cooldownBuy(Settings s) { return cdBuy >= 0 ? cdBuy : s.cdBuy; }
    public int cooldownClaim(Settings s) { return cdClaim >= 0 ? cdClaim : s.cdClaim; }
    public int maxOpensPerDay(Settings s) { return maxOpens >= 0 ? maxOpens : s.maxOpensPerDay; }
    public int maxPurchasesPerDay(Settings s) { return maxPurchases >= 0 ? maxPurchases : s.maxPurchasesPerDay; }
    public int ttlSeconds(Settings s) { return ttl >= 0 ? ttl : s.ttlSeconds; }
    public int rolls() {
        return rollsMin >= rollsMax ? rollsMin : java.util.concurrent.ThreadLocalRandom.current().nextInt(rollsMin, rollsMax + 1);
    }
}
