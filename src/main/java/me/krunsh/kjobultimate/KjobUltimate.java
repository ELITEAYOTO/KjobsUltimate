package me.krunsh.kjobultimate;

import java.util.UUID;

import org.bukkit.plugin.java.JavaPlugin;

import me.krunsh.kjobultimate.config.ConfigManager;
import me.krunsh.kjobultimate.data.DatabaseManager;
import me.krunsh.kjobultimate.data.PlayerDataManager;
import me.krunsh.kjobultimate.hooks.HookManager;
import me.krunsh.kjobultimate.hud.HudManager;
import me.krunsh.kjobultimate.jobs.JobRegistry;
import me.krunsh.kjobultimate.jobs.XpManager;
import me.krunsh.kjobultimate.listeners.PlayerConnectionListener;
import me.krunsh.kjobultimate.quests.QuestManager;
import me.krunsh.kjobultimate.slots.SlotManager;
import me.krunsh.kjobultimate.util.KjobLogger;
import me.krunsh.kjobultimate.validation.ConfigValidator;
import me.krunsh.kjobultimate.view.JobsViewService;
import me.krunsh.kjobultimate.view.QuestViewService;

/**
 * Point d'entrée de KjobsUltimate.
 *
 * V3.11 :
 * - Kgui V2 est l'unique moteur GUI ;
 * - le TAB a été retiré de KjobsUltimate et sera géré par Ktab ;
 * - HUD reste temporairement dans KjobsUltimate ;
 * - JobsViewService / QuestViewService restent les snapshots officiels
 *   consommés par Kgui et PlaceholderAPI.
 */
public final class KjobUltimate extends JavaPlugin {

    private static KjobUltimate instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;

    private JobRegistry jobRegistry;
    private QuestManager questManager;

    private XpManager xpManager;
    private SlotManager slotManager;

    private JobsViewService jobsViewService;
    private QuestViewService questViewService;

    private HookManager hookManager;
    private HudManager hudManager;

    @Override
    public void onEnable() {

        long start = System.currentTimeMillis();
        instance = this;

        KjobLogger.init(this);
        KjobLogger.printStartupBanner(
            getDescription().getVersion()
        );

        // ---------------------------------------------------------------------
        // 1. CONFIGURATION / CATALOGUES
        // ---------------------------------------------------------------------

        try {

            configManager =
                new ConfigManager(this);

            configManager.loadAll();

            jobRegistry =
                new JobRegistry(this);

            jobRegistry.loadAll();

            questManager =
                new QuestManager(this);

            questManager.loadAll();

            new ConfigValidator(this)
                .validateOrThrow();

            xpManager =
                new XpManager(this);

            slotManager =
                new SlotManager(this);

            KjobLogger.success(
                "Configs chargées — "
                    + jobRegistry.getJobCount()
                    + " jobs"
            );

        } catch (Exception failure) {

            KjobLogger.error(
                "Échec du chargement des configs",
                failure
            );

            disableSelf();
            return;
        }

        // ---------------------------------------------------------------------
        // 2. STORAGE + 3. VIEWS
        // ---------------------------------------------------------------------

        try {

            databaseManager =
                new DatabaseManager(this);

            databaseManager.initialize();

            playerDataManager =
                new PlayerDataManager(
                    this,
                    databaseManager
                );

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

        } catch (Exception failure) {

            KjobLogger.error(
                "Impossible d'initialiser le storage",
                failure
            );

            disableSelf();
            return;
        }

        // ---------------------------------------------------------------------
        // 4. INTÉGRATIONS
        // ---------------------------------------------------------------------

        try {

            hookManager =
                new HookManager(this);

            hookManager.setupAll();

        } catch (RuntimeException failure) {

            KjobLogger.error(
                "Impossible d'initialiser les intégrations obligatoires",
                failure
            );

            disableSelf();
            return;
        }

        // ---------------------------------------------------------------------
        // 5. LISTENERS / COMMANDES
        // ---------------------------------------------------------------------

        registerListeners();
        registerCommands();

        // ---------------------------------------------------------------------
        // 6. HUD
        // ---------------------------------------------------------------------

        hudManager =
            new HudManager(this);

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

        clearViewCaches();

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

    private void disableSelf() {
        getServer()
            .getPluginManager()
            .disablePlugin(this);
    }

    private void registerListeners() {

        getServer()
            .getPluginManager()
            .registerEvents(
                new PlayerConnectionListener(this),
                this
            );

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

        me.krunsh.kjobultimate.commands.KjobCommand jobCommand =
            new me.krunsh.kjobultimate.commands.KjobCommand(this);

        getCommand("jobs")
            .setExecutor(jobCommand);

        getCommand("jobs")
            .setTabCompleter(jobCommand);

        me.krunsh.kjobultimate.commands.KjobAdminCommand adminCommand =
            new me.krunsh.kjobultimate.commands.KjobAdminCommand(this);

        getCommand("kjobs")
            .setExecutor(adminCommand);

        getCommand("kjobs")
            .setTabCompleter(adminCommand);
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

    public QuestManager getQuestManager() {
        return questManager;
    }

    public XpManager getXpManager() {
        return xpManager;
    }

    public SlotManager getSlotManager() {
        return slotManager;
    }

    public JobsViewService getJobsViewService() {
        return jobsViewService;
    }

    public QuestViewService getQuestViewService() {
        return questViewService;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public HudManager getHudManager() {
        return hudManager;
    }

    public void notifyJobsUiChanged(
            UUID playerId,
            String reason,
            String... menuIds) {

        invalidateViewCaches(playerId);

        if (hookManager != null) {
            hookManager.invalidateKgui(
                playerId,
                reason,
                menuIds
            );
        }
    }

    public void invalidateViewCaches(
            UUID playerId) {

        if (playerId == null) {
            return;
        }

        if (jobsViewService != null) {
            jobsViewService.invalidate(playerId);
        }

        if (questViewService != null) {
            questViewService.invalidate(playerId);
        }
    }

    public void clearViewCaches() {

        if (jobsViewService != null) {
            jobsViewService.clearCache();
        }

        if (questViewService != null) {
            questViewService.clearCache();
        }
    }
}
