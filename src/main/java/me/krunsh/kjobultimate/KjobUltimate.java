package me.krunsh.kjobultimate;

import java.util.UUID;

import org.bukkit.plugin.java.JavaPlugin;

import me.krunsh.kjobultimate.action.JobActionService;
import me.krunsh.kjobultimate.commands.KjobAdminCommand;
import me.krunsh.kjobultimate.commands.KjobAdminRouter;
import me.krunsh.kjobultimate.config.ConfigManager;
import me.krunsh.kjobultimate.data.DatabaseManager;
import me.krunsh.kjobultimate.data.PlayerDataManager;
import me.krunsh.kjobultimate.hooks.HookManager;
import me.krunsh.kjobultimate.hud.HudManager;
import me.krunsh.kjobultimate.jobs.JobRegistry;
import me.krunsh.kjobultimate.jobs.XpManager;
import me.krunsh.kjobultimate.listeners.PlayerConnectionListener;
import me.krunsh.kjobultimate.performance.BlockCooldownService;
import me.krunsh.kjobultimate.performance.UiInvalidationQueue;
import me.krunsh.kjobultimate.persistence.QuestWriteBuffer;
import me.krunsh.kjobultimate.quests.QuestManager;
import me.krunsh.kjobultimate.slots.SlotManager;
import me.krunsh.kjobultimate.util.KjobLogger;
import me.krunsh.kjobultimate.validation.ConfigValidator;
import me.krunsh.kjobultimate.view.JobsViewService;
import me.krunsh.kjobultimate.view.QuestViewService;

/**
 * Point d'entrée de KjobsUltimate.
 *
 * V3.16 :
 * - accounting central V3.13 ;
 * - hot paths V3.14 ;
 * - persistence buffer V3.15 ;
 * - HUD NMS cache + active-only scheduler + /kjobs perf.
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
    private JobActionService jobActionService;
    private BlockCooldownService blockCooldownService;
    private UiInvalidationQueue uiInvalidationQueue;
    private QuestWriteBuffer questWriteBuffer;

    private JobsViewService jobsViewService;
    private QuestViewService questViewService;

    private HookManager hookManager;
    private HudManager hudManager;

    @Override
    public void onEnable() {

        long start =
            System.currentTimeMillis();

        instance = this;

        KjobLogger.init(this);
        KjobLogger.printStartupBanner(
            getDescription().getVersion()
        );

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

            jobActionService =
                new JobActionService(this);

            blockCooldownService =
                new BlockCooldownService(this);

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

        try {
            databaseManager =
                new DatabaseManager(this);

            databaseManager.initialize();

            questWriteBuffer =
                new QuestWriteBuffer(this);

            questWriteBuffer.start();

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

        try {
            hookManager =
                new HookManager(this);

            hookManager.setupAll();

            uiInvalidationQueue =
                new UiInvalidationQueue(this);

            uiInvalidationQueue.start();

        } catch (RuntimeException failure) {
            KjobLogger.error(
                "Impossible d'initialiser les intégrations obligatoires",
                failure
            );

            disableSelf();
            return;
        }

        /*
         * HUD avant les commandes : /kjobs perf est ainsi initialise avec
         * tous les services disponibles.
         */
        hudManager =
            new HudManager(this);

        registerListeners();
        registerCommands();

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

        if (uiInvalidationQueue != null) {
            uiInvalidationQueue.shutdown();
        }

        if (hookManager != null) {
            hookManager.close();
        }

        if (hudManager != null) {
            hudManager.shutdown();
        }

        if (blockCooldownService != null) {
            blockCooldownService.clear();
        }

        clearViewCaches();

        if (playerDataManager != null) {
            playerDataManager.cancelAutosave();
        }

        if (questWriteBuffer != null) {
            questWriteBuffer.shutdownAndFlush();
        }

        if (playerDataManager != null) {
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

        KjobAdminCommand adminCommand =
            new KjobAdminCommand(this);

        KjobAdminRouter adminRouter =
            new KjobAdminRouter(
                this,
                adminCommand
            );

        getCommand("kjobs")
            .setExecutor(adminRouter);

        getCommand("kjobs")
            .setTabCompleter(adminRouter);
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

    public JobActionService getJobActionService() {
        return jobActionService;
    }

    public BlockCooldownService getBlockCooldownService() {
        return blockCooldownService;
    }

    public UiInvalidationQueue getUiInvalidationQueue() {
        return uiInvalidationQueue;
    }

    public QuestWriteBuffer getQuestWriteBuffer() {
        return questWriteBuffer;
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

        if (playerId == null) {
            return;
        }

        UiInvalidationQueue queue =
            uiInvalidationQueue;

        if (queue != null
                && queue.isEnabled()) {

            queue.mark(
                playerId,
                reason,
                menuIds
            );
            return;
        }

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
