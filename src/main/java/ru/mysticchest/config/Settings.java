package ru.mysticchest.config;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.mysticchest.open.OpenType;

import java.util.logging.Logger;

/** Everything from config.yml that hot paths need, read once per reload into final fields. */
public final class Settings {
    public enum ClickAir { OPEN, NONE }
    public enum ClickBlock { PLACE, OPEN, NONE }
    public enum Scope { PLAYER, PLAYER_TIER, GLOBAL }
    public enum Style { SINGLE, SCROLL, RANDOM }
    public enum Viewers { ALL, WORLD, RADIUS }

    public enum Channel { CHAT, TITLE, ACTIONBAR }
    public enum TitleAnim { SHIMMER, FADE, NONE }

    /** One announcement event (on-spawn, on-open, ...): where, to whom, how it looks and which buttons it has. */
    public static final class Announce {
        public final boolean enabled;
        public final java.util.Set<Channel> channels = java.util.EnumSet.noneOf(Channel.class);
        public final int radius;
        public final boolean worldOnly, mutable, animated;
        public final java.util.List<String> tiers, buttons;
        public final String permission, sound, tickSound;
        public final float volume, pitch, tickVolume;
        public final int lineDelay;
        public final TitleAnim titleAnim;

        Announce(Cfg c) {
            // old configs had a single "type: CHAT|TITLE|ACTIONBAR|NONE"
            boolean on = true;
            java.util.List<String> ch = c.strings("channels");
            if (ch.isEmpty()) {
                String t = c.str("type", "CHAT").toUpperCase();
                if (t.equals("NONE")) on = false; else ch.add(t);
            }
            for (String s : ch) {
                try { channels.add(Channel.valueOf(s.trim().toUpperCase())); } catch (IllegalArgumentException e) { /* reported below */ }
            }
            if (channels.isEmpty()) on = false;
            enabled = on && c.bool("enabled", true);
            radius = c.integer("radius", -1, -1, 100000);
            worldOnly = c.bool("world-only", false);
            mutable = c.bool("mutable", true);
            animated = c.bool("animated", true);
            lineDelay = c.integer("line-delay-ticks", 5, 1, 40);
            tickSound = c.str("line-sound", "BLOCK_NOTE_BLOCK_HAT");
            tickVolume = (float) c.decimal("line-sound-volume", 0.5, 0, 10);
            tiers = new java.util.ArrayList<String>();
            for (String t : c.strings("tiers")) tiers.add(t.toLowerCase());
            buttons = new java.util.ArrayList<String>();
            for (String b : c.strings("buttons")) buttons.add(b.toUpperCase());
            permission = c.str("permission", "");
            sound = c.str("sound", "");
            volume = (float) c.decimal("volume", 1.0, 0, 10);
            pitch = (float) c.decimal("pitch", 1.0, 0, 2);
            titleAnim = c.enumOf("title-animation", TitleAnim.class, TitleAnim.SHIMMER);
        }
    }

    public static final class Effect {
        public final String sound, particle, title, subtitle, actionbar;
        public final float volume, pitch;
        public final int count;
        public final java.util.List<String> potions;
        public final boolean lightning, firework;
        Effect(Cfg c, int particleCap) {
            potions = c.strings("potions");
            lightning = c.bool("lightning", false);
            firework = c.bool("firework", false);
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

    public final Cfg root;
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
    public final boolean holoCountdown;

    public final boolean protBreak, protExplosions, protPistons, respectRegions;

    public final Style rouletteStyle;
    public final int rouletteTicks, roulettePause;
    public final int fullChestRows;
    public final int pickCards, pickPicks, pickCloseDelay;

    public final int defaultWeight;
    public final boolean showChances, mergeIdentical;
    public final double rareBelow;

    public final Announce onSpawn, onOpen, onRare, onExpire, onHunt, onGuards, onBeacon, onActivate;
    public final Effect fxOpen, fxTick, fxWin, fxRare, fxSpawn;

    public enum AuraStyle { NONE, RING, PILLAR, SPIRAL }
    public enum ColorMode { TIER, RANDOM, RAINBOW }
    public final boolean auraEnabled;
    public final AuraStyle auraStyle;
    public final String auraParticle, auraSound;
    public final int auraInterval, auraSoundInterval;
    public final double auraHeight, auraRadius;
    public final float auraVolume, auraPitch;
    public final ColorMode auraColorMode, linesColorMode;
    public final boolean linesEnabled, fwRandomColors;
    public final int linesLength, linesInterval, linesFlashSeconds;
    public final double linesStep;
    public final String linesParticle;
    public final boolean fwOnRare, fwOnSpawn, fwFlicker, fwTrail;
    public final String fwType;
    public final int fwCount;
    public final Effect fxExpire;
    public final boolean compassEnabled;
    public final int compassCooldown;

    public final java.util.Map<OpenType, Integer> randomModes = new java.util.LinkedHashMap<OpenType, Integer>();
    public final boolean randomReveal;
    public final boolean bossEnabled, bossAutoColor;
    public final Viewers bossViewers;
    public final int bossRadius;
    public final String bossStyle, bossColor;

    public final boolean structEnabled, structCollapse, structProtect;
    public final int structChance, structSpeed, structCollapseDelay, structMaxSlope;
    public final double structDecay;
    public final String structTheme, structSound;
    public final boolean structRotate;
    public final int structSurprise, structBlendWidth, debrisRadius, debrisPieces;
    public final boolean structBlend, debrisEnabled, debrisLoadChunks, debrisKeep;

    public final boolean previewEnabled;
    public final String filler;

    public final boolean asyncIo, guiCache, lowResource;
    public final int saveInterval, maxAnimations, viewDistance, particleLimit;

    public Settings(YamlConfiguration y, Logger log) {
        Cfg c = new Cfg(y, "config.yml", log);
        root = c;
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
        holoCountdown = wc.sub("hologram").bool("countdown", true);

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
        onExpire = new Announce(an.sub("on-expire"));
        onHunt = new Announce(an.sub("on-hunt"));
        onGuards = new Announce(an.sub("on-guards"));
        onBeacon = new Announce(an.sub("on-beacon"));
        onActivate = new Announce(an.sub("on-activate"));
        Cfg fx = c.sub("effects");
        int cap = lowResource ? 0 : particleLimit;
        fxOpen = new Effect(fx.sub("open"), cap);
        fxTick = new Effect(fx.sub("tick"), cap);
        fxWin = new Effect(fx.sub("win"), cap);
        fxRare = new Effect(fx.sub("rare"), cap);
        fxSpawn = new Effect(fx.sub("spawn"), cap);

        Cfg au = fx.sub("aura");
        auraEnabled = au.bool("enabled", true) && !lowResource;
        auraStyle = au.enumOf("style", AuraStyle.class, AuraStyle.SPIRAL);
        auraParticle = au.str("particle", "END_ROD");
        auraInterval = au.integer("interval-ticks", 4, 1, 100);
        auraHeight = au.decimal("height", 3.0, 0.5, 30);
        auraRadius = au.decimal("radius", 0.9, 0.2, 5);
        auraSound = au.str("sound", "");
        auraVolume = (float) au.decimal("volume", 0.4, 0, 10);
        auraPitch = (float) au.decimal("pitch", 1.2, 0, 2);
        auraSoundInterval = au.integer("sound-interval-seconds", 6, 1, 3600);
        auraColorMode = au.enumOf("color-mode", ColorMode.class, ColorMode.RANDOM);
        Cfg ln = fx.sub("lines");
        linesEnabled = ln.bool("enabled", true) && !lowResource;
        linesLength = ln.integer("length", 22, 4, 80);
        linesStep = ln.decimal("step", 1.5, 0.5, 5);
        linesInterval = ln.integer("interval-ticks", 5, 1, 40);
        linesFlashSeconds = ln.integer("flash-seconds", 2, 1, 30);
        linesParticle = ln.str("particle", "DUST");
        linesColorMode = ln.enumOf("color-mode", ColorMode.class, ColorMode.TIER);
        Cfg fw = fx.sub("firework");
        fwRandomColors = !fw.str("colors", "RANDOM").equalsIgnoreCase("TIER");
        fwOnRare = fw.bool("on-rare", true) && !lowResource;
        fwOnSpawn = fw.bool("on-spawn", true) && !lowResource;
        fwType = fw.str("type", "BALL_LARGE");
        fwCount = fw.integer("count", 1, 1, 5);
        fwFlicker = fw.bool("flicker", true);
        fwTrail = fw.bool("trail", true);
        fxExpire = new Effect(fx.sub("expire"), cap);
        Cfg cp = c.sub("compass");
        compassEnabled = cp.bool("enabled", true);
        compassCooldown = cp.integer("cooldown-seconds", 3, 0, 3600);
        Cfg rm = c.sub("random-mode");
        randomReveal = rm.bool("reveal-at-spawn", true);
        Cfg rw = rm.sub("weights");
        for (String k : rw.keys()) {
            OpenType t = null;
            try { t = OpenType.valueOf(k.trim().toUpperCase()); } catch (IllegalArgumentException e) {
                log.warning("[config.yml.random-mode.weights] unknown mode '" + k + "' (use ROULETTE, FULL_CHEST, PICK, INSTANT)");
            }
            if (t != null && t != OpenType.RANDOM) randomModes.put(t, rw.integer(k, 1, 0, 100000));
        }
        if (randomModes.isEmpty()) { randomModes.put(OpenType.ROULETTE, 40); randomModes.put(OpenType.FULL_CHEST, 25); randomModes.put(OpenType.PICK, 25); randomModes.put(OpenType.INSTANT, 10); }
        Cfg bb = c.sub("bossbar");
        bossEnabled = bb.bool("enabled", true) && !lowResource;
        bossViewers = bb.enumOf("viewers", Viewers.class, Viewers.WORLD);
        bossRadius = bb.integer("radius", 500, 1, 100000);
        bossStyle = bb.str("style", "SOLID");
        bossColor = bb.str("color", "AUTO");
        bossAutoColor = bossColor.equalsIgnoreCase("AUTO");
        Cfg sc = c.sub("structures");
        structEnabled = sc.bool("enabled", true);
        structChance = sc.integer("chance", 70, 0, 100);
        structRotate = sc.bool("rotate", true);
        structSurprise = sc.integer("theme-surprise-percent", 35, 0, 100);
        structBlend = sc.sub("blend").bool("enabled", true);
        structBlendWidth = sc.sub("blend").integer("width", 3, 1, 8);
        Cfg db = sc.sub("debris");
        debrisEnabled = db.bool("enabled", true);
        debrisRadius = db.integer("radius", 30, 4, 80);
        debrisPieces = db.integer("pieces", 45, 0, 400);
        debrisLoadChunks = db.bool("load-chunks", true);
        debrisKeep = db.bool("keep-after-collapse", false);
        structTheme = sc.str("theme", "AUTO");
        structDecay = sc.decimal("decay", 0.08, 0, 1);
        structSpeed = sc.integer("build-speed", 40, 1, 2000);
        structCollapse = sc.bool("collapse", true);
        structCollapseDelay = sc.integer("collapse-delay-seconds", 20, 0, 3600);
        structProtect = sc.bool("protect", true);
        structMaxSlope = sc.integer("max-slope", 4, 0, 30);
        structSound = sc.str("sound", "BLOCK_STONE_PLACE");
        previewEnabled = c.sub("gui").bool("preview-enabled", true);
        filler = c.sub("gui").str("filler", "GRAY_STAINED_GLASS_PANE");
    }
}
