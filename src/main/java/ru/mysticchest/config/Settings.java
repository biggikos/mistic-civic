package ru.mysticchest.config;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.mysticchest.open.OpenType;

import java.util.logging.Logger;

/** Everything from config.yml that hot paths need, read once per reload into final fields. */
public final class Settings {
    public enum ClickAir { OPEN, NONE }
    public enum ClickBlock { PLACE, OPEN, NONE }
    public enum Scope { PLAYER, PLAYER_TIER, GLOBAL }
    public enum Style { SINGLE, SCROLL }
    public enum AnnounceType { CHAT, TITLE, ACTIONBAR, NONE }

    public static final class Announce {
        public final AnnounceType type; public final int radius; public final boolean worldOnly;
        Announce(Cfg c) {
            type = c.enumOf("type", AnnounceType.class, AnnounceType.CHAT);
            radius = c.integer("radius", -1, -1, 100000);
            worldOnly = c.bool("world-only", false);
        }
    }

    public static final class Effect {
        public final String sound, particle, title, subtitle, actionbar;
        public final float volume, pitch;
        public final int count;
        Effect(Cfg c, int particleCap) {
            sound = c.str("sound", "");
            particle = c.str("particle", "");
            title = c.str("title", "");
            subtitle = c.str("subtitle", "");
            actionbar = c.str("actionbar", "");
            volume = (float) c.decimal("volume", 1.0, 0, 10);
            pitch = (float) c.decimal("pitch", 1.0, 0, 2);
            count = Math.min(particleCap, c.integer("count", 0, 0, 100000));
        }
    }

    public final String language;
    public final boolean debug;
    public final OpenType defaultOpenMode;
    public final ClickAir clickAir;
    public final ClickBlock clickBlock;

    public final Scope cdScope;
    public final int cdOpen, cdBuy, cdClaim, cdSpawn;
    public final int maxOpensPerDay, maxPurchasesPerDay, maxActiveWorldChests;

    public final int ttlSeconds;
    public final boolean holoEnabled;
    public final String holoText;

    public final boolean protBreak, protExplosions, protPistons, respectRegions;

    public final Style rouletteStyle;
    public final int rouletteTicks, roulettePause;
    public final int fullChestRows;
    public final int pickCards, pickPicks, pickCloseDelay;

    public final int defaultWeight;
    public final boolean showChances, mergeIdentical;
    public final double rareBelow;

    public final Announce onSpawn, onOpen, onRare;
    public final Effect fxOpen, fxTick, fxWin, fxRare, fxSpawn;

    public final boolean previewEnabled;
    public final String filler;

    public final boolean asyncIo, guiCache, lowResource;
    public final int saveInterval, maxAnimations, viewDistance, particleLimit;

    public Settings(YamlConfiguration y, Logger log) {
        Cfg c = new Cfg(y, "config.yml", log);
        language = c.str("language", "en").toLowerCase();
        debug = c.bool("debug", false);
        defaultOpenMode = c.enumOf("default-open-mode", OpenType.class, OpenType.ROULETTE);
        clickAir = c.sub("use").enumOf("click-air", ClickAir.class, ClickAir.OPEN);
        clickBlock = c.sub("use").enumOf("click-block", ClickBlock.class, ClickBlock.PLACE);

        Cfg cd = c.sub("cooldowns");
        cdScope = cd.enumOf("scope", Scope.class, Scope.PLAYER_TIER);
        cdOpen = cd.integer("open-seconds", 0, 0, 31536000);
        cdBuy = cd.integer("buy-seconds", 0, 0, 31536000);
        cdClaim = cd.integer("claim-seconds", 0, 0, 86400);
        cdSpawn = cd.integer("spawn-seconds", 0, 0, 31536000);
        Cfg lim = c.sub("limits");
        maxOpensPerDay = lim.integer("max-opens-per-day", 0, 0, 1000000);
        maxPurchasesPerDay = lim.integer("max-purchases-per-day", 0, 0, 1000000);
        maxActiveWorldChests = lim.integer("max-active-world-chests", 20, 1, 100000);

        Cfg wc = c.sub("world-chest");
        ttlSeconds = wc.integer("ttl-seconds", 300, 5, 31536000);
        holoEnabled = wc.sub("hologram").bool("enabled", true);
        holoText = wc.sub("hologram").str("text", "{tier}");

        Cfg pr = c.sub("protection");
        protBreak = pr.bool("prevent-break", true);
        protExplosions = pr.bool("prevent-explosions", true);
        protPistons = pr.bool("prevent-pistons", true);
        respectRegions = pr.bool("respect-regions", true);

        Cfg ro = c.sub("roulette");
        rouletteStyle = ro.enumOf("style", Style.class, Style.SCROLL);
        rouletteTicks = ro.integer("ticks", 60, 10, 600);
        roulettePause = ro.integer("pause-ticks", 14, 0, 200);
        fullChestRows = c.sub("full-chest").integer("rows", 3, 1, 6);
        Cfg pk = c.sub("pick");
        pickCards = pk.integer("cards", 9, 2, 27);
        pickPicks = Math.min(pickCards, pk.integer("picks", 1, 1, 27));
        pickCloseDelay = pk.integer("close-delay-ticks", 50, 0, 600);

        Cfg lo = c.sub("loot");
        defaultWeight = lo.integer("default-weight", 10, 1, 1000000);
        showChances = lo.bool("show-chances", true);
        rareBelow = lo.decimal("rare-below-percent", 3, 0, 100);
        mergeIdentical = lo.bool("merge-identical", true);

        Cfg pf = c.sub("performance");
        asyncIo = pf.bool("async-io", true);
        guiCache = pf.bool("gui-cache", true);
        lowResource = pf.bool("low-resource", false);
        saveInterval = pf.integer("save-interval-seconds", 5, 1, 3600);
        maxAnimations = pf.integer("max-concurrent-animations", 100, 1, 100000);
        viewDistance = pf.integer("effects-view-distance", 32, 1, 512);
        particleLimit = pf.integer("particle-limit", 60, 0, 10000);

        Cfg an = c.sub("announce");
        onSpawn = new Announce(an.sub("on-spawn"));
        onOpen = new Announce(an.sub("on-open"));
        onRare = new Announce(an.sub("on-rare"));
        Cfg fx = c.sub("effects");
        int cap = lowResource ? 0 : particleLimit;
        fxOpen = new Effect(fx.sub("open"), cap);
        fxTick = new Effect(fx.sub("tick"), cap);
        fxWin = new Effect(fx.sub("win"), cap);
        fxRare = new Effect(fx.sub("rare"), cap);
        fxSpawn = new Effect(fx.sub("spawn"), cap);

        previewEnabled = c.sub("gui").bool("preview-enabled", true);
        filler = c.sub("gui").str("filler", "GRAY_STAINED_GLASS_PANE");
    }
}
