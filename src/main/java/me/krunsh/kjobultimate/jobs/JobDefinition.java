package me.krunsh.kjobultimate.jobs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Modèle immutable d'un job chargé depuis jobs/<jobId>.yml.
 * Expose les XP requis par niveau, les actions (blocs/mobs/craft) et les récompenses de niveau.
 */
public final class JobDefinition {

    private final String id;
    private final String displayName;
    private final int maxLevel;
    private final JobIcon icon;

    /** Niveau → XP requis pour passer AU niveau suivant */
    private final Map<Integer, Integer> xpTable;

    /** Material (uppercase) → ActionReward (xp, money, silktouch) */
    private final Map<String, ActionReward> actions;

    /** Niveau → liste de commandes console à exécuter */
    private final Map<Integer, List<String>> levelRewards;

    // ─── Courbe de fallback ─────────────────────────────────
    private final String fallbackType;
    private final int    fallbackBase;
    private final double fallbackMultiplier;

    private JobDefinition(String id, String displayName, int maxLevel, JobIcon icon,
                          Map<Integer, Integer> xpTable, Map<String, ActionReward> actions,
                          Map<Integer, List<String>> levelRewards,
                          String fallbackType, int fallbackBase, double fallbackMultiplier) {
        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.icon = icon;
        this.xpTable = xpTable;
        this.actions = actions;
        this.levelRewards = levelRewards;
        this.fallbackType = fallbackType;
        this.fallbackBase = fallbackBase;
        this.fallbackMultiplier = fallbackMultiplier;
    }

    /**
     * Crée une JobDefinition depuis un fichier YML de job.
     */
    public static JobDefinition fromConfig(String jobId, FileConfiguration cfg) {
        String displayName = cfg.getString("display_name", jobId);
        int maxLevel       = cfg.getInt("max_level", 50);
        JobIcon icon = JobIcon.fromConfig(cfg.getConfigurationSection("icon"));

        // Courbe XP
        String fallbackType       = cfg.getString("xp_curve.fallback_type", "linear");
        int    fallbackBase       = cfg.getInt("xp_curve.fallback_base", 1000);
        double fallbackMultiplier = cfg.getDouble("xp_curve.fallback_multiplier", 1.3);

        Map<Integer, Integer> xpTable = new LinkedHashMap<>();
        ConfigurationSection customLevels = cfg.getConfigurationSection("xp_curve.custom_levels");
        if (customLevels != null) {
            for (String key : customLevels.getKeys(false)) {
                try {
                    int lvl = Integer.parseInt(key);
                    int xp  = customLevels.getInt(key);
                    xpTable.put(lvl, xp);
                } catch (NumberFormatException ignored) {}
            }
        }

        // Actions (blocs/mobs)
        Map<String, ActionReward> actions = new LinkedHashMap<>();
        ConfigurationSection actSection = cfg.getConfigurationSection("actions");
        if (actSection != null) {
            for (String material : actSection.getKeys(false)) {
                int    xp          = actSection.getInt(material + ".xp", 0);
                double money       = actSection.getDouble(material + ".money", 0);
                boolean silktouch  = actSection.getBoolean(material + ".silktouch", false);
                actions.put(material.toUpperCase(Locale.ROOT), new ActionReward(xp, money, silktouch));
            }
        }

        // Récompenses de niveau
        Map<Integer, List<String>> levelRewards = new LinkedHashMap<>();
        ConfigurationSection rewardSection = cfg.getConfigurationSection("level_rewards");
        if (rewardSection != null) {
            for (String key : rewardSection.getKeys(false)) {
                try {
                    int lvl = Integer.parseInt(key);
                    List<String> cmds = rewardSection.getStringList(key);
                    if (!cmds.isEmpty()) levelRewards.put(lvl, cmds);
                } catch (NumberFormatException ignored) {}
            }
        }

        return new JobDefinition(jobId, displayName, maxLevel, icon, xpTable, actions, levelRewards,
            fallbackType, fallbackBase, fallbackMultiplier);
    }

    /**
     * Retourne le total d'XP requis pour passer du niveau {@code level} au niveau {@code level + 1}.
     * Utilise la table custom si disponible, sinon la courbe de fallback.
     */
    public int getXpForLevel(int level) {
        if (level <= 0) level = 1;
        if (level > maxLevel) return 0;
        Integer custom = xpTable.get(level);
        if (custom != null) return custom;
        // Fallback linéaire ou exponentiel
        if ("exponential".equalsIgnoreCase(fallbackType)) {
            return (int) (fallbackBase * Math.pow(fallbackMultiplier, level - 1));
        }
        // linear par défaut
        return (int) (fallbackBase + (level - 1) * fallbackBase * (fallbackMultiplier - 1));
    }

    /**
     * Retourne l'ActionReward pour un material Bukkit (insensible à la casse).
     * Retourne null si ce material ne donne pas de XP pour ce job.
     */
    public ActionReward getAction(String material) {
        return actions.get(material.toUpperCase(Locale.ROOT));
    }

    /**
     * Retourne les commandes à exécuter lors du passage au niveau donné, ou liste vide.
     */
    public List<String> getLevelRewardCommands(int level) {
        return levelRewards.getOrDefault(level, Collections.emptyList());
    }

    // ─── Accesseurs ─────────────────────────────────────────

    public String getId()         { return id; }
    public String getDisplayName(){ return displayName; }
    public int    getMaxLevel()   { return maxLevel; }
    public JobIcon getIcon()      { return icon; }

    public Map<String, ActionReward> getActions()    { return Collections.unmodifiableMap(actions); }
    public Map<Integer, List<String>> getLevelRewards() { return Collections.unmodifiableMap(levelRewards); }

    /** Apparence centrale d'un metier, reutilisee dans toutes les interfaces. */
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
            return new JobIcon(material == null ? "" : material.trim(),
                (short) section.getInt("data", 0), cit == null ? "" : cit);
        }

        public String getMaterial() { return material; }
        public short getData() { return data; }
        public String getCit() { return cit; }
        public boolean isConfigured() {
            return !material.isEmpty() || !cit.trim().isEmpty();
        }
    }

    // ─── ActionReward interne ────────────────────────────────

    public static final class ActionReward {
        private final int    xp;
        private final double money;
        private final boolean silkTouchBlocked;

        public ActionReward(int xp, double money, boolean silkTouchBlocked) {
            this.xp = xp;
            this.money = money;
            this.silkTouchBlocked = silkTouchBlocked;
        }

        public int    getXp()               { return xp; }
        public double getMoney()            { return money; }
        public boolean isSilkTouchBlocked() { return silkTouchBlocked; }
    }
}
