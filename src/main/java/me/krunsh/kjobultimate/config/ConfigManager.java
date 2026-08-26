package me.krunsh.kjobultimate.config;

import java.io.File;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Charge et expose les YAML de KjobsUltimate.
 *
 * V3.16.3 : les options hot-path restent snapshotées et le QuestWriteBuffer
 * recharge également ses réglages de persistance lors de /kjobs reload.
 */
public final class ConfigManager {

    private final KjobUltimate plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration soundsConfig;
    private FileConfiguration hudConfig;

    private volatile RuntimeSettings runtime = RuntimeSettings.defaults();

    public ConfigManager(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin ne peut pas être null.");
        }
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        mainConfig = plugin.getConfig();
        runtime = RuntimeSettings.load(mainConfig);

        messagesConfig = loadOrCreate("messages.yml");
        soundsConfig = loadOrCreate("sounds.yml");
        hudConfig = loadOrCreate("hud.yml");

        /* Les services n'existent pas encore au premier load. */
        if (plugin.getXpManager() != null) {
            plugin.getXpManager().reloadRuntimeConfig();
        }

        if (plugin.getBlockCooldownService() != null) {
            plugin.getBlockCooldownService().reloadSettings();
        }

        if (plugin.getUiInvalidationQueue() != null) {
            plugin.getUiInvalidationQueue().reloadSettings();
        }

        if (plugin.getQuestWriteBuffer() != null) {
            plugin.getQuestWriteBuffer().reloadSettings();
        }

        plugin.clearViewCaches();

        KjobLogger.info(
            "Configs rechargees (main + messages + sounds + hud + runtime V3.16.3)");
    }

    private FileConfiguration loadOrCreate(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    public boolean isDebug() {
        return runtime.debug;
    }

    public boolean isDebugXp() {
        return runtime.debugXp;
    }

    public boolean isDebugQuest() {
        return runtime.debugQuest;
    }

    public boolean isDebugHud() {
        return runtime.debugHud;
    }

    public boolean isDebugSlots() {
        return runtime.debugSlots;
    }

    public String getSqliteFile() {
        return mainConfig.getString(
            "storage.sqlite_file",
            "data/KjobsUltimate.db");
    }

    public int getAutosaveInterval() {
        return mainConfig.getInt("storage.autosave_interval", 10);
    }

    public boolean isBlockXpCreative() {
        return runtime.blockCreative;
    }

    public boolean isBlockXpSpectator() {
        return runtime.blockSpectator;
    }

    public boolean isSilkTouchBlocked() {
        return runtime.silkTouchBlocked;
    }

    public boolean isCropsMatureOnly() {
        return runtime.cropsMatureOnly;
    }

    public int getBlockCooldown() {
        return runtime.blockCooldownSeconds;
    }

    public int getHarvestCascadeGuardTicks() {
        return runtime.harvestCascadeGuardTicks;
    }

    public boolean isVerticalHarvestEnabled(String materialName) {
        String material = normalizeMaterial(materialName);
        if ("SUGAR_CANE_BLOCK".equals(material)) {
            return runtime.sugarCaneEnabled;
        }
        if ("CACTUS".equals(material)) {
            return runtime.cactusEnabled;
        }
        return false;
    }

    public int getVerticalHarvestMaxUnits(String materialName) {
        String material = normalizeMaterial(materialName);
        if ("SUGAR_CANE_BLOCK".equals(material)) {
            return runtime.sugarCaneMaxUnits;
        }
        if ("CACTUS".equals(material)) {
            return runtime.cactusMaxUnits;
        }
        return 1;
    }

    public int getVerticalHarvestMaxScan(String materialName) {
        String material = normalizeMaterial(materialName);
        if ("SUGAR_CANE_BLOCK".equals(material)) {
            return runtime.sugarCaneMaxScan;
        }
        if ("CACTUS".equals(material)) {
            return runtime.cactusMaxScan;
        }
        return 1;
    }

    public int getPvpTargetCooldown() {
        return runtime.pvpVictimCooldownSeconds;
    }

    public boolean isDailyCapEnabled() {
        return runtime.dailyCapEnabled;
    }

    public int getDefaultSlots() {
        return runtime.defaultSlots;
    }

    public int getMaxSlots() {
        return runtime.maxSlots;
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

    public String getMessage(String key, String def) {
        String raw = messagesConfig.getString(key, def);
        return color(raw).replace("{prefix}", getPrefixRaw());
    }

    public String getMessage(String key) {
        return getMessage(key, "");
    }

    public String getPrefix() {
        return getPrefixRaw();
    }

    private String getPrefixRaw() {
        return color(
            messagesConfig.getString(
                "prefix",
                "\u00A78[\u00A76Jobs\u00A78] \u00A7r"));
    }

    private static String color(String raw) {
        return raw == null ? "" : raw.replace("&", "\u00A7");
    }

    private static String normalizeMaterial(String materialName) {
        if (materialName == null) {
            return "";
        }
        return materialName.trim().toUpperCase(Locale.ROOT);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Snapshot des valeurs lues fréquemment. */
    private static final class RuntimeSettings {

        private final boolean debug;
        private final boolean debugXp;
        private final boolean debugQuest;
        private final boolean debugHud;
        private final boolean debugSlots;

        private final boolean blockCreative;
        private final boolean blockSpectator;
        private final boolean silkTouchBlocked;
        private final boolean cropsMatureOnly;
        private final int blockCooldownSeconds;
        private final boolean dailyCapEnabled;
        private final int pvpVictimCooldownSeconds;

        private final int harvestCascadeGuardTicks;
        private final boolean sugarCaneEnabled;
        private final int sugarCaneMaxUnits;
        private final int sugarCaneMaxScan;
        private final boolean cactusEnabled;
        private final int cactusMaxUnits;
        private final int cactusMaxScan;

        private final int defaultSlots;
        private final int maxSlots;

        private RuntimeSettings(
                boolean debug,
                boolean debugXp,
                boolean debugQuest,
                boolean debugHud,
                boolean debugSlots,
                boolean blockCreative,
                boolean blockSpectator,
                boolean silkTouchBlocked,
                boolean cropsMatureOnly,
                int blockCooldownSeconds,
                boolean dailyCapEnabled,
                int pvpVictimCooldownSeconds,
                int harvestCascadeGuardTicks,
                boolean sugarCaneEnabled,
                int sugarCaneMaxUnits,
                int sugarCaneMaxScan,
                boolean cactusEnabled,
                int cactusMaxUnits,
                int cactusMaxScan,
                int defaultSlots,
                int maxSlots) {

            this.debug = debug;
            this.debugXp = debugXp;
            this.debugQuest = debugQuest;
            this.debugHud = debugHud;
            this.debugSlots = debugSlots;
            this.blockCreative = blockCreative;
            this.blockSpectator = blockSpectator;
            this.silkTouchBlocked = silkTouchBlocked;
            this.cropsMatureOnly = cropsMatureOnly;
            this.blockCooldownSeconds = blockCooldownSeconds;
            this.dailyCapEnabled = dailyCapEnabled;
            this.pvpVictimCooldownSeconds = pvpVictimCooldownSeconds;
            this.harvestCascadeGuardTicks = harvestCascadeGuardTicks;
            this.sugarCaneEnabled = sugarCaneEnabled;
            this.sugarCaneMaxUnits = sugarCaneMaxUnits;
            this.sugarCaneMaxScan = sugarCaneMaxScan;
            this.cactusEnabled = cactusEnabled;
            this.cactusMaxUnits = cactusMaxUnits;
            this.cactusMaxScan = cactusMaxScan;
            this.defaultSlots = defaultSlots;
            this.maxSlots = maxSlots;
        }

        private static RuntimeSettings load(FileConfiguration config) {
            if (config == null) {
                return defaults();
            }

            int sugarUnits = clamp(
                config.getInt(
                    "anti_abuse.harvest.vertical_crops.SUGAR_CANE_BLOCK.max_units_per_break",
                    16),
                1,
                64);
            int cactusUnits = clamp(
                config.getInt(
                    "anti_abuse.harvest.vertical_crops.CACTUS.max_units_per_break",
                    16),
                1,
                64);

            int sugarScan = Math.max(
                sugarUnits,
                clamp(
                    config.getInt(
                        "anti_abuse.harvest.vertical_crops.SUGAR_CANE_BLOCK.max_scan",
                        16),
                    1,
                    64));
            int cactusScan = Math.max(
                cactusUnits,
                clamp(
                    config.getInt(
                        "anti_abuse.harvest.vertical_crops.CACTUS.max_scan",
                        16),
                    1,
                    64));

            int maxSlots = clamp(config.getInt("job_slots.max_slots", 6), 1, 6);
            int defaultSlots = clamp(
                config.getInt("job_slots.default_slots", 2),
                1,
                maxSlots);

            return new RuntimeSettings(
                config.getBoolean("debug", false),
                config.getBoolean("debug_xp", false),
                config.getBoolean("debug_quest", false),
                config.getBoolean("debug_hud", false),
                config.getBoolean("debug_slots", false),
                config.getBoolean("anti_abuse.block_creative", true),
                config.getBoolean("anti_abuse.block_spectator", true),
                config.getBoolean("anti_abuse.silktouch_enabled", true),
                config.getBoolean("anti_abuse.crops_mature_only", true),
                Math.max(0, config.getInt("anti_abuse.block_position_cooldown", 300)),
                config.getBoolean("anti_abuse.daily_xp_cap.enabled", false),
                Math.max(0, config.getInt("anti_abuse.pvp.victim_cooldown_seconds", 1200)),
                clamp(config.getInt("anti_abuse.harvest.cascade_guard_ticks", 3), 0, 20),
                config.getBoolean(
                    "anti_abuse.harvest.vertical_crops.SUGAR_CANE_BLOCK.enabled",
                    true),
                sugarUnits,
                sugarScan,
                config.getBoolean(
                    "anti_abuse.harvest.vertical_crops.CACTUS.enabled",
                    true),
                cactusUnits,
                cactusScan,
                defaultSlots,
                maxSlots);
        }

        private static RuntimeSettings defaults() {
            return new RuntimeSettings(
                false, false, false, false, false,
                true, true, true, true,
                300, false, 1200,
                3,
                true, 16, 16,
                true, 16, 16,
                2, 6);
        }
    }
}
