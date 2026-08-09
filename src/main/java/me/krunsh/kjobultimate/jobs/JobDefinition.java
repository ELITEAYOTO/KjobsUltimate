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
 * Convention de niveaux :
 * - le joueur commence au niveau 0 ;
 * - custom_levels.1 représente l'XP nécessaire pour atteindre le niveau 1 ;
 * - custom_levels.2 représente l'XP nécessaire pour passer du niveau 1 au niveau 2 ;
 * - custom_levels.N représente l'XP nécessaire pour atteindre le niveau N.
 *
 * Cette convention est exposée par :
 * - getXpRequiredToReachLevel(targetLevel) ;
 * - getXpRequiredForNextLevel(currentLevel).
 */
public final class JobDefinition {

    private final String id;
    private final String displayName;
    private final int maxLevel;
    private final JobIcon icon;
    private final int dailyXpCap;

    /**
     * Niveau cible -> XP nécessaire pour atteindre ce niveau.
     *
     * Exemple :
     * 1 -> XP nécessaire pour passer de 0 à 1
     * 2 -> XP nécessaire pour passer de 1 à 2
     */
    private final Map<Integer, Integer> xpTable;

    /** Clé d'action normalisée -> récompense associée. */
    private final Map<String, ActionReward> actions;

    /** Niveau atteint -> commandes de récompense à exécuter. */
    private final Map<Integer, List<String>> levelRewards;

    // Courbe utilisée lorsqu'un niveau n'est pas présent dans custom_levels.
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

    /**
     * Construit une définition de métier depuis un fichier YAML.
     */
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
                    // ConfigValidator signalera la clé invalide lors de la validation.
                }
            }
        }

        Map<String, ActionReward> actions =
            new LinkedHashMap<String, ActionReward>();

        ConfigurationSection actionSection =
            cfg.getConfigurationSection("actions");

        if (actionSection != null) {
            for (String rawActionKey : actionSection.getKeys(false)) {
                String actionKey = normalizeActionKey(rawActionKey);
                if (actionKey.isEmpty()) {
                    continue;
                }

                int xp = actionSection.getInt(rawActionKey + ".xp", 0);
                double money =
                    actionSection.getDouble(rawActionKey + ".money", 0D);
                boolean silkTouchBlocked =
                    actionSection.getBoolean(
                        rawActionKey + ".silktouch", false);

                actions.put(
                    actionKey,
                    new ActionReward(xp, money, silkTouchBlocked));
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
            levelRewards,
            fallbackType,
            fallbackBase,
            fallbackMultiplier);
    }

    /**
     * Retourne l'XP nécessaire pour atteindre un niveau cible précis.
     *
     * @param targetLevel niveau à atteindre, compris entre 1 et maxLevel
     * @return XP nécessaire, ou 0 si le niveau demandé est hors limites
     */
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

    /**
     * Retourne l'XP nécessaire pour passer du niveau actuel au suivant.
     *
     * Exemples :
     * currentLevel=0 -> lit custom_levels.1
     * currentLevel=1 -> lit custom_levels.2
     * currentLevel=49 -> lit custom_levels.50
     * currentLevel=50 -> retourne 0 si maxLevel=50
     */
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
     * Retourne la récompense associée à une action.
     *
     * Les clés Bukkit et les clés MATERIAL:data sont acceptées :
     * STONE, STONE:3, PRISMARINE:2, PVP_KILL, etc.
     */
    public ActionReward getAction(String actionKey) {
        String normalized = normalizeActionKey(actionKey);
        return normalized.isEmpty() ? null : actions.get(normalized);
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

    // Accesseurs

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

    /**
     * 0 signifie qu'aucun plafond propre au métier n'est activé.
     */
    public int getDailyXpCap() {
        return dailyXpCap;
    }

    public Map<Integer, Integer> getConfiguredXpLevels() {
        return xpTable;
    }

    public Map<String, ActionReward> getActions() {
        return actions;
    }

    public Map<Integer, List<String>> getLevelRewards() {
        return levelRewards;
    }

    /**
     * Apparence centrale d'un métier, réutilisée dans les interfaces.
     */
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

    /**
     * Récompense immuable d'une action de métier.
     */
    public static final class ActionReward {

        private final int xp;
        private final double money;
        private final boolean silkTouchBlocked;

        public ActionReward(
                int xp,
                double money,
                boolean silkTouchBlocked) {

            this.xp = xp;
            this.money = money;
            this.silkTouchBlocked = silkTouchBlocked;
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
    }
}