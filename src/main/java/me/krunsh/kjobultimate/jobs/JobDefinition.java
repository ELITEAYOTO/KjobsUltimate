package me.krunsh.kjobultimate.jobs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Définition immuable d'un métier chargé depuis jobs/<jobId>.yml.
 *
 * V3.13 :
 * - une action de craft peut choisir son mode de comptage ;
 * - les crafts Kcraft peuvent être ciblés exactement via "KCRAFT:<craftId>" ;
 * - les crafts forcés sont refusés par défaut.
 */
public final class JobDefinition {

    private final String id;
    private final String displayName;
    private final int maxLevel;
    private final JobIcon icon;
    private final int dailyXpCap;

    private final Map<Integer, Integer> xpTable;

    /**
     * Actions standards (Material, MATERIAL:data, actions spéciales).
     * Cette map reste celle exposée à ConfigValidator pour préserver son
     * contrat actuel.
     */
    private final Map<String, ActionReward> actions;

    /**
     * Actions Kcraft exactes, volontairement séparées des actions Bukkit.
     * Elles sont validées pendant le parsing de JobDefinition.
     */
    private final Map<String, ActionReward> kcraftActions;

    private final Map<Integer, List<String>> levelRewards;

    private final String fallbackType;
    private final int fallbackBase;
    private final double fallbackMultiplier;

    private JobDefinition(
            String id,
            String displayName,
            int maxLevel,
            JobIcon icon,
            int dailyXpCap,
            Map<Integer, Integer> xpTable,
            Map<String, ActionReward> actions,
            Map<String, ActionReward> kcraftActions,
            Map<Integer, List<String>> levelRewards,
            String fallbackType,
            int fallbackBase,
            double fallbackMultiplier) {

        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.icon = icon;
        this.dailyXpCap = dailyXpCap;

        this.xpTable = Collections.unmodifiableMap(
            new LinkedHashMap<Integer, Integer>(xpTable));

        this.actions = Collections.unmodifiableMap(
            new LinkedHashMap<String, ActionReward>(actions));

        this.kcraftActions = Collections.unmodifiableMap(
            new LinkedHashMap<String, ActionReward>(kcraftActions));

        LinkedHashMap<Integer, List<String>> immutableRewards =
            new LinkedHashMap<Integer, List<String>>();

        for (Map.Entry<Integer, List<String>> entry : levelRewards.entrySet()) {
            immutableRewards.put(
                entry.getKey(),
                Collections.unmodifiableList(
                    new ArrayList<String>(entry.getValue())));
        }

        this.levelRewards = Collections.unmodifiableMap(immutableRewards);
        this.fallbackType = fallbackType;
        this.fallbackBase = fallbackBase;
        this.fallbackMultiplier = fallbackMultiplier;
    }

    public static JobDefinition fromConfig(String jobId, FileConfiguration cfg) {
        if (jobId == null || jobId.trim().isEmpty()) {
            throw new IllegalArgumentException("jobId ne peut pas être vide.");
        }
        if (cfg == null) {
            throw new IllegalArgumentException(
                "La configuration du métier " + jobId + " est absente.");
        }

        String normalizedJobId = jobId.trim().toLowerCase(Locale.ROOT);
        String displayName = cfg.getString("display_name", normalizedJobId);
        int maxLevel = cfg.getInt("max_level", 50);
        int dailyXpCap = cfg.getInt("daily_xp_cap", 0);
        JobIcon icon = JobIcon.fromConfig(cfg.getConfigurationSection("icon"));

        String fallbackType = normalizeFallbackType(
            cfg.getString("xp_curve.fallback_type", "linear"));
        int fallbackBase = cfg.getInt("xp_curve.fallback_base", 1000);
        double fallbackMultiplier =
            cfg.getDouble("xp_curve.fallback_multiplier", 1.3D);

        Map<Integer, Integer> xpTable =
            new LinkedHashMap<Integer, Integer>();

        ConfigurationSection customLevels =
            cfg.getConfigurationSection("xp_curve.custom_levels");

        if (customLevels != null) {
            for (String rawLevel : customLevels.getKeys(false)) {
                try {
                    int targetLevel = Integer.parseInt(rawLevel);
                    int requiredXp = customLevels.getInt(rawLevel);
                    xpTable.put(targetLevel, requiredXp);
                } catch (NumberFormatException ignored) {
                    // ConfigValidator signalera la clé invalide.
                }
            }
        }

        Map<String, ActionReward> actions =
            new LinkedHashMap<String, ActionReward>();

        Map<String, ActionReward> kcraftActions =
            new LinkedHashMap<String, ActionReward>();

        ConfigurationSection actionSection =
            cfg.getConfigurationSection("actions");

        if (actionSection != null) {
            for (String rawActionKey : actionSection.getKeys(false)) {
                String actionKey = normalizeActionKey(rawActionKey);
                if (actionKey.isEmpty()) {
                    continue;
                }

                int xp =
                    actionSection.getInt(
                        rawActionKey + ".xp",
                        0
                    );

                double money =
                    actionSection.getDouble(
                        rawActionKey + ".money",
                        0D
                    );

                boolean silkTouchBlocked =
                    actionSection.getBoolean(
                        rawActionKey + ".silktouch",
                        false
                    );

                CountMode countMode =
                    CountMode.fromConfig(
                        actionSection.getString(
                            rawActionKey + ".count_mode",
                            "CRAFTS"
                        )
                    );

                boolean allowForced =
                    actionSection.getBoolean(
                        rawActionKey + ".allow_forced",
                        false
                    );

                ActionReward reward =
                    new ActionReward(
                        xp,
                        money,
                        silkTouchBlocked,
                        countMode,
                        allowForced
                    );

                if (isKcraftActionKey(actionKey)) {
                    validateKcraftAction(
                        normalizedJobId,
                        actionKey,
                        reward
                    );
                    kcraftActions.put(
                        actionKey,
                        reward
                    );
                } else {
                    actions.put(
                        actionKey,
                        reward
                    );
                }
            }
        }

        Map<Integer, List<String>> levelRewards =
            new LinkedHashMap<Integer, List<String>>();

        ConfigurationSection rewardSection =
            cfg.getConfigurationSection("level_rewards");

        if (rewardSection != null) {
            for (String rawLevel : rewardSection.getKeys(false)) {
                try {
                    int reachedLevel = Integer.parseInt(rawLevel);
                    List<String> commands =
                        rewardSection.getStringList(rawLevel);

                    if (commands != null && !commands.isEmpty()) {
                        levelRewards.put(
                            reachedLevel,
                            new ArrayList<String>(commands));
                    }
                } catch (NumberFormatException ignored) {
                    // ConfigValidator signalera la clé invalide.
                }
            }
        }

        return new JobDefinition(
            normalizedJobId,
            displayName == null ? normalizedJobId : displayName,
            maxLevel,
            icon,
            dailyXpCap,
            xpTable,
            actions,
            kcraftActions,
            levelRewards,
            fallbackType,
            fallbackBase,
            fallbackMultiplier);
    }

    private static void validateKcraftAction(
            String jobId,
            String actionKey,
            ActionReward reward) {

        if ("KCRAFT:".equals(actionKey)
                || actionKey.length() <= "KCRAFT:".length()) {

            throw new IllegalArgumentException(
                "Action Kcraft invalide dans "
                    + jobId
                    + " : un craftId est obligatoire après KCRAFT:."
            );
        }

        if (reward.getXp() <= 0) {
            throw new IllegalArgumentException(
                "Action "
                    + actionKey
                    + " dans "
                    + jobId
                    + " : xp doit être > 0."
            );
        }

        if (Double.isNaN(reward.getMoney())
                || Double.isInfinite(reward.getMoney())
                || reward.getMoney() < 0D) {

            throw new IllegalArgumentException(
                "Action "
                    + actionKey
                    + " dans "
                    + jobId
                    + " : money doit être un nombre fini >= 0."
            );
        }
    }

    public int getXpRequiredToReachLevel(int targetLevel) {
        if (targetLevel < 1 || targetLevel > maxLevel) {
            return 0;
        }

        Integer customXp = xpTable.get(targetLevel);
        if (customXp != null) {
            return customXp.intValue();
        }

        return calculateFallbackXp(targetLevel);
    }

    public int getXpRequiredForNextLevel(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= maxLevel) {
            return 0;
        }
        return getXpRequiredToReachLevel(currentLevel + 1);
    }

    private int calculateFallbackXp(int targetLevel) {
        double calculated;

        if ("exponential".equals(fallbackType)) {
            calculated = fallbackBase
                * Math.pow(fallbackMultiplier, targetLevel - 1);
        } else {
            calculated = fallbackBase
                + (targetLevel - 1D)
                * fallbackBase
                * (fallbackMultiplier - 1D);
        }

        if (Double.isNaN(calculated)
                || Double.isInfinite(calculated)
                || calculated <= 0D) {
            return 0;
        }

        if (calculated >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) Math.floor(calculated);
    }

    /**
     * Cherche une action standard ou une action Kcraft exacte.
     */
    public ActionReward getAction(String actionKey) {
        String normalized = normalizeActionKey(actionKey);
        if (normalized.isEmpty()) {
            return null;
        }

        if (isKcraftActionKey(normalized)) {
            return kcraftActions.get(normalized);
        }

        return actions.get(normalized);
    }

    public boolean hasAction(String actionKey) {
        return getAction(actionKey) != null;
    }

    public List<String> getLevelRewardCommands(int level) {
        List<String> commands = levelRewards.get(level);
        return commands == null
            ? Collections.<String>emptyList()
            : commands;
    }

    public boolean isMaxLevel(int level) {
        return level >= maxLevel;
    }

    private static boolean isKcraftActionKey(
            String actionKey) {

        return actionKey != null
            && actionKey.startsWith("KCRAFT:");
    }

    private static String normalizeActionKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
    }

    private static String normalizeFallbackType(String value) {
        if (value == null) {
            return "linear";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "exponential".equals(normalized)
            ? "exponential"
            : "linear";
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public JobIcon getIcon() {
        return icon;
    }

    public int getDailyXpCap() {
        return dailyXpCap;
    }

    public Map<Integer, Integer> getConfiguredXpLevels() {
        return xpTable;
    }

    /**
     * Actions standard uniquement.
     * ConfigValidator continue volontairement de valider cette map.
     */
    public Map<String, ActionReward> getActions() {
        return actions;
    }

    public Map<String, ActionReward> getKcraftActions() {
        return kcraftActions;
    }

    public Map<Integer, List<String>> getLevelRewards() {
        return levelRewards;
    }

    public static final class JobIcon {

        private final String material;
        private final short data;
        private final String cit;

        private JobIcon(String material, short data, String cit) {
            this.material = material;
            this.data = data;
            this.cit = cit;
        }

        private static JobIcon fromConfig(ConfigurationSection section) {
            if (section == null) {
                return new JobIcon("", (short) 0, "");
            }

            String material = section.getString("material", "");
            String cit = section.contains("nbt_cit")
                ? section.getString("nbt_cit", "")
                : section.getString("cit", "");

            return new JobIcon(
                material == null ? "" : material.trim(),
                (short) section.getInt("data", 0),
                cit == null ? "" : cit.trim());
        }

        public String getMaterial() {
            return material;
        }

        public short getData() {
            return data;
        }

        public String getCit() {
            return cit;
        }

        public boolean isConfigured() {
            return !material.isEmpty() || !cit.isEmpty();
        }
    }

    public enum CountMode {

        /**
         * 1 unité = 1 exécution de la recette.
         * Mode par défaut, rétrocompatible avec l'XP Artisan historique.
         */
        CRAFTS,

        /**
         * 1 unité = 1 item réellement produit.
         * Ex.: une recette donnant 4 flèches vaut 4 unités.
         */
        RESULT_ITEMS;

        public static CountMode fromConfig(
                String raw) {

            if (raw == null
                    || raw.trim().isEmpty()) {

                return CRAFTS;
            }

            String normalized =
                raw.trim()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');

            if ("CRAFTS".equals(normalized)
                    || "CRAFT".equals(normalized)) {

                return CRAFTS;
            }

            if ("RESULT_ITEMS".equals(normalized)
                    || "ITEMS".equals(normalized)
                    || "OUTPUT_ITEMS".equals(normalized)) {

                return RESULT_ITEMS;
            }

            throw new IllegalArgumentException(
                "count_mode inconnu : "
                    + raw
                    + " (CRAFTS ou RESULT_ITEMS attendu)."
            );
        }
    }

    public static final class ActionReward {

        private final int xp;
        private final double money;
        private final boolean silkTouchBlocked;
        private final CountMode countMode;
        private final boolean allowForced;

        /**
         * Constructeur historique conservé pour compatibilité.
         */
        public ActionReward(
                int xp,
                double money,
                boolean silkTouchBlocked) {

            this(
                xp,
                money,
                silkTouchBlocked,
                CountMode.CRAFTS,
                false
            );
        }

        public ActionReward(
                int xp,
                double money,
                boolean silkTouchBlocked,
                CountMode countMode,
                boolean allowForced) {

            this.xp = xp;
            this.money = money;
            this.silkTouchBlocked = silkTouchBlocked;
            this.countMode =
                countMode == null
                    ? CountMode.CRAFTS
                    : countMode;
            this.allowForced = allowForced;
        }

        public int getXp() {
            return xp;
        }

        public double getMoney() {
            return money;
        }

        public boolean isSilkTouchBlocked() {
            return silkTouchBlocked;
        }

        public CountMode getCountMode() {
            return countMode;
        }

        public boolean isAllowForced() {
            return allowForced;
        }
    }
}
