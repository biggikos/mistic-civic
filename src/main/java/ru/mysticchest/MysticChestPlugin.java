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
import ru.mysticchest.economy.EconomyProvider;
import ru.mysticchest.economy.ExcellentEconomyProvider;
import ru.mysticchest.economy.NoneProvider;
import ru.mysticchest.economy.VaultProvider;
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
    private EconomyProvider economy = new NoneProvider();

    @Override
    public void onEnable() {
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

        cfg = configs.load("config.yml", true);
        settings = new Settings(cfg, getLogger());
        io.configure(settings.asyncIo, settings.saveInterval);
        chests.cleanupLeftovers();
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
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.mysticchest.chest.WorldListener(this), this);
    }

    @Override
    public void onDisable() {
        if (spawner != null) spawner.stop();
        if (animator != null) animator.shutdown();
        if (prompts != null) prompts.clear();
        if (chests != null) chests.removeAll();
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
        setupEconomy();
        tiers.load();
        locators.loadPoints();
        spawner.reload();
        guiCache.clear();
        getLogger().info("Loaded " + tiers.all().size() + " tiers, language=" + settings.language
                + ", economy=" + economy.name());
    }

    @Override
    public FileConfiguration getConfig() { return cfg; }

    @Override
    public void reloadConfig() { cfg = configs.load("config.yml", true); }

    private void setupEconomy() {
        String p = cfg.getString("economy.provider", "VAULT").toUpperCase();
        EconomyProvider found = null;
        if (p.equals("VAULT")) found = VaultProvider.create();
        else if (p.equals("EXCELLENT_ECONOMY")) {
            found = ExcellentEconomyProvider.create(cfg.getConfigurationSection("economy.excellent-economy"));
        }
        if (found == null && !p.equals("NONE")) {
            getLogger().warning("Economy provider " + p + " is not available - shop purchases are disabled.");
        }
        economy = found != null ? found : new NoneProvider();
    }

    /** Shop purchase: checks cooldown and daily limit, charges, hands out the chest item. */
    public void buy(Player p, Tier t) {
        if (!t.purchasable) return;
        if (economy instanceof NoneProvider) { lang.send(p, "shop.economy-disabled"); return; }
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
        if (!economy.has(p, t.currency, t.price) || !economy.withdraw(p, t.currency, t.price)) {
            lang.send(p, "shop.not-enough", "price", Text.format(t.price), "currency", t.currency);
            return;
        }
        rewards.give(p, chests.chestItem(t, p, 1));
        cooldowns.start(id, key, t.cooldownBuy(settings));
        cooldowns.addBuy(id);
        lang.send(p, "shop.bought", "tier", t.name(lang.code(p)));
    }

    public void invalidateGuis() { guiCache.clear(); }

    public Settings settings() { return settings; }
    public ConfigManager configs() { return configs; }
    public Scheduler scheduler() { return scheduler; }
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
    public EconomyProvider economy() { return economy; }
}
