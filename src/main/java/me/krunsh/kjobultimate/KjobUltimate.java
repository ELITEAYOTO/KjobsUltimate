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
import me.krunsh.kjobultimate.view.JobsViewService;
import me.krunsh.kjobultimate.view.QuestViewService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Point d'entrée du plugin KjobUltimate.
 *
 * Ordre de démarrage strict :
 *   1. Logger
 *   2. Config + jobs + quêtes
 *   3. Storage SQLite/MySQL + couches View
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

    private JobsViewService   jobsViewService;
    private QuestViewService  questViewService;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;

        // 1. Logger
        KjobLogger.init(this);
        KjobLogger.printStartupBanner(
            getDescription().getVersion()
        );

        // 2. Configs + jobs + quêtes
        try {
            configManager = new ConfigManager(this);
            configManager.loadAll();

            jobRegistry = new JobRegistry(this);
            jobRegistry.loadAll();

            questManager = new QuestManager(this);
            questManager.loadAll();

            new ConfigValidator(this)
                .validateOrThrow();

            xpManager = new XpManager(this);
            slotManager = new SlotManager(this);

            /*
             * GUI interne historique.
             * Il sera supprimé lorsque la migration Kgui-only sera complète.
             */
            guiManager = new GuiManager(this);
            guiManager.loadAll();

            KjobLogger.success(
                "Configs chargées — "
                    + jobRegistry.getJobCount()
                    + " jobs"
            );
        } catch (Exception e) {
            KjobLogger.error(
                "Échec du chargement des configs",
                e
            );

            getServer()
                .getPluginManager()
                .disablePlugin(this);

            return;
        }

        // 3. Storage SQLite/MySQL + couches View V3
        try {
            databaseManager =
                new DatabaseManager(this);

            databaseManager.initialize();

            playerDataManager =
                new PlayerDataManager(
                    this,
                    databaseManager
                );

            /*
             * Sources de vérité communes pour les couches de présentation.
             *
             * Elles ne font aucune lecture SQL pendant le rendu : elles
             * construisent des snapshots immuables depuis PlayerData déjà
             * chargé en RAM.
             */
            jobsViewService =
                new JobsViewService(this);

            questViewService =
                new QuestViewService(this);

            KjobLogger.success(
                "Storage "
                    + databaseManager.getStorageTypeName()
                    + " connecte - "
                    + databaseManager.getDbPath()
            );
        } catch (Exception e) {
            KjobLogger.error(
                "Impossible d'initialiser le storage",
                e
            );

            getServer()
                .getPluginManager()
                .disablePlugin(this);

            return;
        }

        // 4. Hooks externes
        hookManager = new HookManager(this);
        hookManager.setupAll();

        // 5. Listeners + commandes
        registerListeners();
        registerCommands();

        // 6. HUD + TAB historique
        hudManager = new HudManager(this);
        tabManager = new TabManager(this);

        long elapsed =
                System.currentTimeMillis()
                    - start;

        KjobLogger.printLoadSummary(
            jobRegistry.getJobCount(),
            hookManager.getRegisteredProviders(),
            elapsed
        );
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

        KjobLogger.info(
            "Plugin désactivé — données sauvegardées."
        );
    }

    private void registerListeners() {

        getServer()
            .getPluginManager()
            .registerEvents(
                new PlayerConnectionListener(this),
                this
            );

        if (guiManager != null) {
            getServer()
                .getPluginManager()
                .registerEvents(
                    guiManager,
                    this
                );
        }

        getServer()
            .getPluginManager()
            .registerEvents(
                new me.krunsh.kjobultimate.listeners.jobs.MinerListener(this),
                this
            );

        getServer()
            .getPluginManager()
            .registerEvents(
                new me.krunsh.kjobultimate.listeners.jobs.FarmerListener(this),
                this
            );

        getServer()
            .getPluginManager()
            .registerEvents(
                new me.krunsh.kjobultimate.listeners.jobs.HunterListener(this),
                this
            );

        getServer()
            .getPluginManager()
            .registerEvents(
                new me.krunsh.kjobultimate.listeners.jobs.PretorienListener(this),
                this
            );

        getServer()
            .getPluginManager()
            .registerEvents(
                new me.krunsh.kjobultimate.listeners.jobs.ArtisanListener(this),
                this
            );

        getServer()
            .getPluginManager()
            .registerEvents(
                new me.krunsh.kjobultimate.listeners.jobs.PilleurListener(this),
                this
            );

        getServer()
            .getPluginManager()
            .registerEvents(
                new me.krunsh.kjobultimate.listeners.quests.QuestActionListener(this),
                this
            );
    }

    private void registerCommands() {

        me.krunsh.kjobultimate.commands.KjobCommand jobCmd =
            new me.krunsh.kjobultimate.commands.KjobCommand(this);

        getCommand("jobs")
            .setExecutor(jobCmd);

        getCommand("jobs")
            .setTabCompleter(jobCmd);

        me.krunsh.kjobultimate.commands.KjobAdminCommand adminCmd =
            new me.krunsh.kjobultimate.commands.KjobAdminCommand(this);

        getCommand("kjobs")
            .setExecutor(adminCmd);

        getCommand("kjobs")
            .setTabCompleter(adminCmd);
    }

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

    public JobsViewService getJobsViewService() {
        return jobsViewService;
    }

    public QuestViewService getQuestViewService() {
        return questViewService;
    }

    /**
     * Point unique d'invalidation optionnelle, sans importer Kgui dans le métier.
     */
    public void notifyJobsUiChanged(
            UUID playerId,
            String reason,
            String... menuIds) {

        if (hookManager != null) {
            hookManager.invalidateKgui(
                playerId,
                reason,
                menuIds
            );
        }
    }
}