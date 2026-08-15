package me.krunsh.kjobultimate.config;

import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Charge et expose les fichiers de configuration YAML de KjobsUltimate.
 *
 * Les fichiers jobs/ sont chargés via JobRegistry.
 *
 * V3.10 :
 * - après un reload de configuration, les caches de View existants sont
 *   invalidés globalement ;
 * - au premier démarrage les services View ne sont pas encore créés, donc
 *   clearViewCaches() est volontairement sans effet.
 */
public final class ConfigManager {

    private final KjobUltimate plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration soundsConfig;
    private FileConfiguration hudConfig;
    private FileConfiguration tabConfig;

    public ConfigManager(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    /**
     * Charge ou recharge tous les fichiers de configuration.
     */
    public void loadAll() {

        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        mainConfig =
            plugin.getConfig();

        messagesConfig =
            loadOrCreate("messages.yml");

        soundsConfig =
            loadOrCreate("sounds.yml");

        hudConfig =
            loadOrCreate("hud.yml");

        /*
         * Temporaire : tab.yml restera chargé tant que le module TAB historique
         * n'aura pas été extrait dans Ktab.
         */
        tabConfig =
            loadOrCreate("tab.yml");

        /*
         * Une définition de job, quête, slot ou cap peut avoir changé sans
         * mutation de PlayerData : on invalide donc explicitement les snapshots.
         */
        plugin.clearViewCaches();

        KjobLogger.info(
            "Configs rechargees "
                + "(main + messages + sounds + hud + tab)"
        );
    }

    private FileConfiguration loadOrCreate(
            String fileName) {

        File file =
            new File(
                plugin.getDataFolder(),
                fileName
            );

        if (!file.exists()) {
            plugin.saveResource(
                fileName,
                false
            );
        }

        return YamlConfiguration
            .loadConfiguration(file);
    }

    // -------------------------------------------------------------------------
    // config.yml
    // -------------------------------------------------------------------------

    public boolean isDebug() {
        return mainConfig.getBoolean(
            "debug",
            false
        );
    }

    public boolean isDebugXp() {
        return mainConfig.getBoolean(
            "debug_xp",
            false
        );
    }

    public boolean isDebugQuest() {
        return mainConfig.getBoolean(
            "debug_quest",
            false
        );
    }

    public boolean isDebugHud() {
        return mainConfig.getBoolean(
            "debug_hud",
            false
        );
    }

    public boolean isDebugSlots() {
        return mainConfig.getBoolean(
            "debug_slots",
            false
        );
    }

    public String getSqliteFile() {
        return mainConfig.getString(
            "storage.sqlite_file",
            "data/kjobultimate.db"
        );
    }

    public int getAutosaveInterval() {
        return mainConfig.getInt(
            "storage.autosave_interval",
            10
        );
    }

    public boolean isBlockXpCreative() {
        return mainConfig.getBoolean(
            "anti_abuse.block_creative",
            true
        );
    }

    public boolean isBlockXpSpectator() {
        return mainConfig.getBoolean(
            "anti_abuse.block_spectator",
            true
        );
    }

    public boolean isSilkTouchBlocked() {
        return mainConfig.getBoolean(
            "anti_abuse.silktouch_enabled",
            true
        );
    }

    public boolean isCropsMatureOnly() {
        return mainConfig.getBoolean(
            "anti_abuse.crops_mature_only",
            true
        );
    }

    public int getBlockCooldown() {
        return mainConfig.getInt(
            "anti_abuse.block_position_cooldown",
            300
        );
    }

    public int getPvpTargetCooldown() {

        if (mainConfig.contains(
                "anti_abuse.pvp.victim_cooldown_seconds")) {

            return mainConfig.getInt(
                "anti_abuse.pvp.victim_cooldown_seconds",
                1200
            );
        }

        return mainConfig.getInt(
            "anti_abuse.pvp_target_cooldown",
            1200
        );
    }

    public boolean isDailyCapEnabled() {
        return mainConfig.getBoolean(
            "anti_abuse.daily_xp_cap.enabled",
            false
        );
    }

    public int getDefaultSlots() {
        return mainConfig.getInt(
            "job_slots.default_slots",
            1
        );
    }

    public int getMaxSlots() {
        return mainConfig.getInt(
            "job_slots.max_slots",
            5
        );
    }

    // -------------------------------------------------------------------------
    // Accesseurs bruts
    // -------------------------------------------------------------------------

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getSoundsConfig() {
        return soundsConfig;
    }

    public FileConfiguration getHudConfig() {
        return hudConfig;
    }

    public FileConfiguration getTabConfig() {
        return tabConfig;
    }

    // -------------------------------------------------------------------------
    // Messages
    // -------------------------------------------------------------------------

    public String getMessage(
            String key,
            String def) {

        String raw =
            messagesConfig.getString(
                key,
                def
            );

        return color(raw)
            .replace(
                "{prefix}",
                getPrefixRaw()
            );
    }

    public String getMessage(
            String key) {

        return getMessage(
            key,
            ""
        );
    }

    public String getPrefix() {
        return getPrefixRaw();
    }

    private String getPrefixRaw() {

        return color(
            messagesConfig.getString(
                "prefix",
                "\u00A78[\u00A76Jobs\u00A78] \u00A7r"
            )
        );
    }

    private String color(
            String raw) {

        return raw == null
            ? ""
            : raw.replace(
                "&",
                "\u00A7"
            );
    }
}
