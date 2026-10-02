package ru.mysticchest;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.chest.Rewards;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.chest.TierRegistry;
import ru.mysticchest.command.MysticCommand;
import ru.mysticchest.config.ConfigManager;
import ru.mysticchest.config.Settings;
import ru.mysticchest.cooldown.CooldownManager;
import ru.mysticchest.core.Animator;
import ru.mysticchest.core.AsyncIO;
import ru.mysticchest.core.Scheduler;
import ru.mysticchest.economy.Economies;
import ru.mysticchest.effects.Announcer;
import ru.mysticchest.effects.Effects;
import ru.mysticchest.gui.ChatPrompt;
import ru.mysticchest.gui.GuiCache;
import ru.mysticchest.gui.GuiListener;
import ru.mysticchest.lang.Lang;
import ru.mysticchest.loot.LootStore;
import ru.mysticchest.open.OpenService;
import ru.mysticchest.spawn.Locators;
import ru.mysticchest.spawn.SpawnService;
import ru.mysticchest.util.Text;

import java.util.UUID;

public final class MysticChestPlugin extends JavaPlugin {
    private YamlConfiguration cfg;
    private Settings settings;
    private ConfigManager configs;
    private Scheduler scheduler;
    private final ru.mysticchest.core.Diag diag = new ru.mysticchest.core.Diag();
    private Animator animator;
    private AsyncIO io;
    private Lang lang;
    private TierRegistry tiers;
    private LootStore loot;
    private CooldownManager cooldowns;
    private ChestManager chests;
    private Rewards rewards;
    private Effects effects;
    private Announcer announcer;
    private OpenService open;
    private Locators locators;
    private SpawnService spawner;
    private GuiCache guiCache;
    private ChatPrompt prompts;
    private ru.mysticchest.effects.Fireworks fireworks;
    private ru.mysticchest.chest.Compass compass;
    private ru.mysticchest.effects.BossBars bossBars;
    private ru.mysticchest.guard.GuardService guards;
    private ru.mysticchest.atmosphere.Atmosphere atmosphere;
    private ru.mysticchest.duel.Captures captures;
    private ru.mysticchest.event.BeaconEvent beacon;
    private ru.mysticchest.event.DeathZone deathZone;
    private ru.mysticchest.stats.StatsService stats;
    private ru.mysticchest.stats.BoardService boards;
    private ru.mysticchest.stats.Prefs prefs;
    private ru.mysticchest.effects.Tracker tracker;
    private ru.mysticchest.core.Scheduler.Handle periodTimer;
    private ru.mysticchest.structure.StructureService structures;
    private Economies economies;

    @Override
    public void onEnable() {
        startMetrics();
        configs = new ConfigManager(this);
        scheduler = new Scheduler(this);
        animator = new Animator(this);
        io = new AsyncIO(this, scheduler);
        guiCache = new GuiCache();
        lang = new Lang(this);
        tiers = new TierRegistry(this);
        loot = new LootStore(this);
        cooldowns = new CooldownManager(this);
        chests = new ChestManager(this);
        rewards = new Rewards(this);
        effects = new Effects(this);
        announcer = new Announcer(this);
        open = new OpenService(this);
        locators = new Locators(this);
        spawner = new SpawnService(this, locators);
        prompts = new ChatPrompt(this);
        economies = new Economies(this);
        fireworks = new ru.mysticchest.effects.Fireworks(this);
        compass = new ru.mysticchest.chest.Compass(this);
        bossBars = new ru.mysticchest.effects.BossBars(this);
        guards = new ru.mysticchest.guard.GuardService(this);
        atmosphere = new ru.mysticchest.atmosphere.Atmosphere(this);
        captures = new ru.mysticchest.duel.Captures(this);
        beacon = new ru.mysticchest.event.BeaconEvent(this);
        deathZone = new ru.mysticchest.event.DeathZone(this);
        stats = new ru.mysticchest.stats.StatsService(this);
        boards = new ru.mysticchest.stats.BoardService(this);
        prefs = new ru.mysticchest.stats.Prefs(this);
        tracker = new ru.mysticchest.effects.Tracker(this);
        structures = new ru.mysticchest.structure.StructureService(this);

        cfg = configs.load("config.yml", true);
        settings = new Settings(cfg, getLogger());
        io.configure(settings.asyncIo, settings.saveInterval);
        chests.cleanupLeftovers();
        structures.cleanupLeftovers();
        cooldowns.load();
        reloadAll();

        MysticCommand cmd = new MysticCommand(this);
        getCommand("mystic").setExecutor(cmd);
        getCommand("mystic").setTabCompleter(cmd);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                if (new ru.mysticchest.hook.PapiHook(this).register()) getLogger().info("PlaceholderAPI expansion registered (%mysticchest_...%)");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI hook failed: " + t);
            }
        }
        getServer().getPluginManager().registerEvents(fireworks, this);
        getServer().getPluginManager().registerEvents(guards, this);
        getServer().getPluginManager().registerEvents(beacon, this);
        getServer().getPluginManager().registerEvents(deathZone, this);
        getServer().getPluginManager().registerEvents(structures.wand(), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.mysticchest.chest.WorldListener(this), this);
    }

    @Override
    public void onDisable() {
        if (spawner != null) spawner.stop();
        if (animator != null) animator.shutdown();
        if (prompts != null) prompts.clear();
        if (guards != null) guards.shutdown();
        if (atmosphere != null) atmosphere.clearAll();
        if (captures != null) captures.shutdown();
        ru.mysticchest.effects.Glow.clearAll();
        if (beacon != null) beacon.shutdown();
        if (chests != null) chests.removeAll();
        if (structures != null) structures.shutdown();
        if (boards != null) boards.shutdown();
        if (scheduler != null) scheduler.shutdown();
        if (io != null) io.shutdown();
    }

    /** Re-reads every file. Running chests and cooldowns are kept. */
    public void reloadAll() {
        cfg = configs.load("config.yml", true);
        settings = new Settings(cfg, getLogger());
        io.configure(settings.asyncIo, settings.saveInterval);
        guiCache.enabled(settings.guiCache);
        effects.clear();
        effects.warmUp(settings);
        lang.load(settings.language);
        economies.reload(cfg.getConfigurationSection("economy"));
        tiers.load();
        locators.loadPoints();
        structures.catalog().load();
        stats.load();
        prefs.load();
        boards.load();
        schedulePeriodCheck();
        spawner.reload();
        beacon.reload();
        guiCache.clear();
        getLogger().info("Loaded " + tiers.all().size() + " tiers, language=" + settings.language
                + ", economy=" + economies.defaultId());
    }

    private void schedulePeriodCheck() {
        if (periodTimer != null) periodTimer.cancel();
        periodTimer = scheduler.later(3600000L, new Runnable() {
            public void run() { stats.checkPeriod(); schedulePeriodCheck(); }
        });
    }

    @Override
    public FileConfiguration getConfig() { return cfg; }

    @Override
    public void reloadConfig() { cfg = configs.load("config.yml", true); }

    /** Shop purchase: checks cooldown and daily limit, charges, hands out the chest item. */
    public void buy(Player p, Tier t) {
        if (!t.purchasable) return;
        Economies.Resolved eco = economies.resolve(t.currency);
        if (eco == null) { lang.send(p, "shop.economy-disabled"); return; }
        UUID id = p.getUniqueId();
        String key = "buy_" + t.id;
        if (!p.hasPermission("mysticchest.bypass.cooldown") && t.cooldownBuy(settings) > 0) {
            long left = cooldowns.remaining(id, key);
            if (left > 0) { lang.send(p, "deny.buy-cooldown", "time", lang.time(p, left)); return; }
        }
        int max = t.maxPurchasesPerDay(settings);
        if (max > 0 && !p.hasPermission("mysticchest.bypass.limits") && cooldowns.buys(id) >= max) {
            lang.send(p, "deny.limit-buys", "max", String.valueOf(max));
            return;
        }
        if (!eco.provider.withdraw(p, eco.currency, t.price)) {
            lang.send(p, "shop.not-enough", "price", Text.format(t.price), "currency", eco.display);
            return;
        }
        rewards.give(p, chests.chestItem(t, p, 1));
        cooldowns.start(id, key, t.cooldownBuy(settings));
        cooldowns.addBuy(id);
        lang.send(p, "shop.bought", "tier", t.name(lang.code(p)));
    }

    /** Anonymous usage statistics (bStats). Servers can opt out in plugins/bStats/config.yml. */
    private void startMetrics() {
        try {
            org.bstats.bukkit.Metrics m = new org.bstats.bukkit.Metrics(this, 34450);
            m.addCustomChart(new org.bstats.charts.SimplePie("language", new java.util.concurrent.Callable<String>() {
                public String call() { return settings == null ? "unknown" : String.valueOf(getConfig().getString("language", "auto")); }
            }));
            m.addCustomChart(new org.bstats.charts.SimplePie("structures", new java.util.concurrent.Callable<String>() {
                public String call() { return settings != null && settings.structEnabled ? "enabled" : "disabled"; }
            }));
        } catch (Throwable t) {
            getLogger().fine("bStats unavailable: " + t);
        }
    }

    public void invalidateGuis() { guiCache.clear(); }

    public Settings settings() { return settings; }
    public ConfigManager configs() { return configs; }
    public Scheduler scheduler() { return scheduler; }
    public ru.mysticchest.core.Diag diag() { return diag; }
    public Animator animator() { return animator; }
    public AsyncIO io() { return io; }
    public Lang lang() { return lang; }
    public TierRegistry tiers() { return tiers; }
    public LootStore loot() { return loot; }
    public CooldownManager cooldowns() { return cooldowns; }
    public ChestManager chests() { return chests; }
    public Rewards rewards() { return rewards; }
    public Effects effects() { return effects; }
    public Announcer announcer() { return announcer; }
    public OpenService open() { return open; }
    public Locators locators() { return locators; }
    public SpawnService spawner() { return spawner; }
    public GuiCache guiCache() { return guiCache; }
    public ChatPrompt prompts() { return prompts; }
    public ru.mysticchest.effects.Fireworks fireworks() { return fireworks; }
    public ru.mysticchest.chest.Compass compass() { return compass; }
    public ru.mysticchest.effects.BossBars bossBars() { return bossBars; }
    public ru.mysticchest.guard.GuardService guards() { return guards; }
    public ru.mysticchest.atmosphere.Atmosphere atmosphere() { return atmosphere; }
    public ru.mysticchest.duel.Captures captures() { return captures; }
    public ru.mysticchest.event.BeaconEvent beacon() { return beacon; }
    public ru.mysticchest.event.DeathZone deathZone() { return deathZone; }
    public ru.mysticchest.stats.StatsService stats() { return stats; }
    public ru.mysticchest.stats.BoardService boards() { return boards; }
    public ru.mysticchest.stats.Prefs prefs() { return prefs; }
    public ru.mysticchest.effects.Tracker tracker() { return tracker; }
    public ru.mysticchest.structure.StructureService structures() { return structures; }
    public Economies economies() { return economies; }
}
