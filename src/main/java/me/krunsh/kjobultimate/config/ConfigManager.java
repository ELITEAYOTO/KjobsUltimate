package me.krunsh.kjobultimate.config;

import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Charge et expose les fichiers de configuration YAML de KjobsUltimate.
 *
 * V3.13 :
 * - tab.yml n'appartient plus à KjobsUltimate ;
 * - les paramètres de récolte verticale disposent de valeurs sûres même si
 *   l'ancien config.yml ne contient pas encore les nouvelles clés.
 */
public final class ConfigManager {

    private final KjobUltimate plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration soundsConfig;
    private FileConfiguration hudConfig;

    public ConfigManager(KjobUltimate plugin) {
        this.plugin = plugin;
    }

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

        plugin.clearViewCaches();

        KjobLogger.info(
            "Configs rechargees "
                + "(main + messages + sounds + hud)"
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

    /**
     * Durée pendant laquelle un BlockBreakEvent secondaire produit par un
     * plugin de récolte est ignoré après qu'il a déjà été compté dans une
     * cascade de canne à sucre / cactus.
     */
    public int getHarvestCascadeGuardTicks() {
        return clamp(
            mainConfig.getInt(
                "anti_abuse.harvest.cascade_guard_ticks",
                3
            ),
            0,
            20
        );
    }

    public boolean isVerticalHarvestEnabled(
            String materialName) {

        String material =
            normalizeMaterial(materialName);

        if (material.isEmpty()) {
            return false;
        }

        return mainConfig.getBoolean(
            "anti_abuse.harvest.vertical_crops."
                + material
                + ".enabled",
            true
        );
    }

    /**
     * Nombre maximal de blocs crédités pour une seule action utilisateur.
     */
    public int getVerticalHarvestMaxUnits(
            String materialName) {

        String material =
            normalizeMaterial(materialName);

        return clamp(
            mainConfig.getInt(
                "anti_abuse.harvest.vertical_crops."
                    + material
                    + ".max_units_per_break",
                16
            ),
            1,
            64
        );
    }

    /**
     * Nombre maximal de blocs consécutifs inspectés (bloc cassé inclus).
     * Le scan peut être supérieur au nombre crédité afin de marquer les
     * événements secondaires comme déjà comptés.
     */
    public int getVerticalHarvestMaxScan(
            String materialName) {

        String material =
            normalizeMaterial(materialName);

        int maxUnits =
            getVerticalHarvestMaxUnits(material);

        int configured =
            clamp(
                mainConfig.getInt(
                    "anti_abuse.harvest.vertical_crops."
                        + material
                        + ".max_scan",
                    16
                ),
                1,
                64
            );

        return Math.max(
            maxUnits,
            configured
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

    private static String normalizeMaterial(
            String materialName) {

        if (materialName == null) {
            return "";
        }

        return materialName
            .trim()
            .toUpperCase();
    }

    private static int clamp(
            int value,
            int minimum,
            int maximum) {

        return Math.max(
            minimum,
            Math.min(
                maximum,
                value
            )
        );
    }
}
