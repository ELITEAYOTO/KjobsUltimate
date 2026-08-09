package me.krunsh.kjobultimate;

import me.krunsh.kjobultimate.config.ConfigManager;
import me.krunsh.kjobultimate.data.DatabaseManager;
import me.krunsh.kjobultimate.data.PlayerDataManager;
import me.krunsh.kjobultimate.gui.GuiManager;
import me.krunsh.kjobultimate.hooks.HookManager;
import me.krunsh.kjobultimate.hud.HudManager;
import me.krunsh.kjobultimate.jobs.JobRegistry;
import me.krunsh.kjobultimate.jobs.XpManager;
import me.krunsh.kjobultimate.listeners.PlayerConnectionListener;
import me.krunsh.kjobultimate.quests.QuestManager;
import me.krunsh.kjobultimate.slots.SlotManager;
import me.krunsh.kjobultimate.tab.TabManager;
import me.krunsh.kjobultimate.util.KjobLogger;
import me.krunsh.kjobultimate.validation.ConfigValidator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Point d'entrée du plugin KjobUltimate.
 * Ordre de démarrage strict :
 *   1. Logger
 *   2. Config + jobs
 *   3. Storage SQLite/MySQL
 *   4. Hooks externes (Vault, PAPI, Kgui, Kcraft, Kstacker)
 *   5. Listeners + commandes
 */
public final class KjobUltimate extends JavaPlugin {

    private static KjobUltimate instance;

    private ConfigManager     configManager;
    private DatabaseManager   databaseManager;
    private PlayerDataManager playerDataManager;
    private JobRegistry       jobRegistry;
    private XpManager         xpManager;
    private SlotManager       slotManager;
    private HookManager       hookManager;
    private HudManager        hudManager;
    private TabManager        tabManager;
    private GuiManager        guiManager;
    private QuestManager      questManager;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;

        // 1. Logger
        KjobLogger.init(this);
        KjobLogger.printStartupBanner(getDescription().getVersion());

        // 2. Configs + jobs
        try {
            configManager = new ConfigManager(this);
            configManager.loadAll();
            jobRegistry = new JobRegistry(this);
            jobRegistry.loadAll();
            questManager = new QuestManager(this);
            questManager.loadAll();
            new ConfigValidator(this).validateOrThrow();
            xpManager  = new XpManager(this);
            slotManager = new SlotManager(this);
            guiManager = new GuiManager(this);
            guiManager.loadAll();
            KjobLogger.success("Configs chargées — " + jobRegistry.getJobCount() + " jobs");
        } catch (Exception e) {
            KjobLogger.error("Échec du chargement des configs", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Storage SQLite/MySQL
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            playerDataManager = new PlayerDataManager(this, databaseManager);
            KjobLogger.success("Storage " + databaseManager.getStorageTypeName() + " connecte - " + databaseManager.getDbPath());
        } catch (Exception e) {
            KjobLogger.error("Impossible d'initialiser le storage", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Hooks externes
        hookManager = new HookManager(this);
        hookManager.setupAll();

        // 5. Listeners + commandes
        registerListeners();
        registerCommands();

        // 6. HUD (actionbar + bossbar + level-up popup)
        hudManager = new HudManager(this);
        tabManager = new TabManager(this);

        long elapsed = System.currentTimeMillis() - start;
        KjobLogger.printLoadSummary(jobRegistry.getJobCount(), hookManager.getRegisteredProviders(), elapsed);
    }

    @Override
    public void onDisable() {
        if (hookManager != null) {
            hookManager.close();
        }
        if (hudManager != null) {
            hudManager.shutdown();
        }
        if (tabManager != null) {
            tabManager.shutdown();
        }
        if (playerDataManager != null) {
            playerDataManager.cancelAutosave();
            playerDataManager.saveAll();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        KjobLogger.info("Plugin désactivé — données sauvegardées.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        if (guiManager != null) getServer().getPluginManager().registerEvents(guiManager, this);
        // Phase 3+4 : listeners de jobs métier
        getServer().getPluginManager().registerEvents(
            new me.krunsh.kjobultimate.listeners.jobs.MinerListener(this), this);
        getServer().getPluginManager().registerEvents(
            new me.krunsh.kjobultimate.listeners.jobs.FarmerListener(this), this);
        getServer().getPluginManager().registerEvents(
            new me.krunsh.kjobultimate.listeners.jobs.HunterListener(this), this);
        getServer().getPluginManager().registerEvents(
            new me.krunsh.kjobultimate.listeners.jobs.PretorienListener(this), this);
        getServer().getPluginManager().registerEvents(
            new me.krunsh.kjobultimate.listeners.jobs.ArtisanListener(this), this);
        getServer().getPluginManager().registerEvents(
            new me.krunsh.kjobultimate.listeners.jobs.PilleurListener(this), this);
        getServer().getPluginManager().registerEvents(
            new me.krunsh.kjobultimate.listeners.quests.QuestActionListener(this), this);
        // TODO Phase 7 : ContentProviders Kgui
    }

    private void registerCommands() {
        me.krunsh.kjobultimate.commands.KjobCommand jobCmd = new me.krunsh.kjobultimate.commands.KjobCommand(this);
        getCommand("jobs").setExecutor(jobCmd);
        getCommand("jobs").setTabCompleter(jobCmd);

        me.krunsh.kjobultimate.commands.KjobAdminCommand adminCmd = new me.krunsh.kjobultimate.commands.KjobAdminCommand(this);
        getCommand("kjobs").setExecutor(adminCmd);
        getCommand("kjobs").setTabCompleter(adminCmd);
    }

    // ─── Accesseurs statiques ───────────────────────────────

    public static KjobUltimate getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public JobRegistry getJobRegistry() {
        return jobRegistry;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public XpManager getXpManager() {
        return xpManager;
    }

    public SlotManager getSlotManager() {
        return slotManager;
    }

    public HudManager getHudManager() {
        return hudManager;
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    /** Point unique d'invalidation optionnelle, sans importer Kgui dans le métier. */
    public void notifyJobsUiChanged(UUID playerId, String reason, String... menuIds) {
        if (hookManager != null) hookManager.invalidateKgui(playerId, reason, menuIds);
    }
}
