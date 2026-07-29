package me.krunsh.kjobultimate.validation;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Validation boot/reload des configs critiques.
 *
 * Les erreurs bloquent le demarrage, les warnings restent informatifs. Le but
 * est d'eviter un serveur prod qui demarre avec un job manquant, un storage
 * invalide ou un modele slots incoherent.
 */
public final class ConfigValidator {

    private static final Set<String> SPECIAL_ACTIONS = new HashSet<String>(Arrays.asList(
        "PVP_KILL",
        "WITHER_SKELETON",
        "TNT_EXPLODE",
        "DYNAMITE_EXPLODE",
        "TNT_CRAFT",
        "DYNAMITE_CRAFT"
    ));

    private static final Set<String> QUEST_TYPES = new HashSet<String>(Arrays.asList(
        "MINE",
        "HARVEST",
        "KILL",
        "CRAFT",
        "PVP_KILL",
        "TNT_EXPLODE",
        "DYNAMITE_EXPLODE",
        "TNT_CRAFT",
        "DYNAMITE_CRAFT",
        "EAT",
        "CONSUME",
        "SMELT",
        "FISH",
        "FISH_ENTITY",
        "ENCHANT",
        "ENCHANT_LEVELS",
        "PLACE",
        "TAME"
    ));

    private final KjobUltimate plugin;
    private int errors;
    private int warnings;

    public ConfigValidator(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void validateOrThrow() {
        errors = 0;
        warnings = 0;

        validateStorage();
        validateJobSlots();
        validateAntiAbuse();
        validateJobs();
        validatePilleur();
        validateQuests();

        if (errors > 0) {
            throw new IllegalStateException("Validation KjobsUltimate echouee: " + errors + " erreur(s), " + warnings + " warning(s)");
        }
        if (warnings > 0) {
            KjobLogger.warn("Validation terminee avec " + warnings + " warning(s).");
        } else {
            KjobLogger.success("Validation configs/jobs OK.");
        }
    }

    private void validateStorage() {
        String type = plugin.getConfigManager().getMainConfig().getString("storage.type", "SQLITE").toUpperCase(Locale.ROOT);
        if (!"SQLITE".equals(type) && !"MYSQL".equals(type)) {
            error("storage.type invalide: " + type + " (attendu SQLITE ou MYSQL)");
            return;
        }

        int autosave = plugin.getConfigManager().getMainConfig().getInt("storage.autosave_interval", 10);
        if (autosave <= 0) error("storage.autosave_interval doit etre > 0 minute.");
        if (autosave < 5) warn("storage.autosave_interval < 5 peut creer trop d'ecritures en prod.");

        if ("SQLITE".equals(type)) {
            String sqliteFile = plugin.getConfigManager().getMainConfig().getString("storage.sqlite_file", "");
            if (sqliteFile.trim().isEmpty()) error("storage.sqlite_file ne peut pas etre vide en SQLITE.");
            int busyTimeout = plugin.getConfigManager().getMainConfig().getInt(
                "storage.sqlite.busy_timeout_ms", 5000);
            int checkpointPages = plugin.getConfigManager().getMainConfig().getInt(
                "storage.sqlite.wal_autocheckpoint_pages", 1000);
            int cacheSize = plugin.getConfigManager().getMainConfig().getInt(
                "storage.sqlite.cache_size_kib", 8000);
            long journalLimit = plugin.getConfigManager().getMainConfig().getLong(
                "storage.sqlite.journal_size_limit_bytes", 67108864L);
            if (busyTimeout < 100 || busyTimeout > 120000) {
                error("storage.sqlite.busy_timeout_ms doit etre entre 100 et 120000.");
            }
            if (checkpointPages < 100 || checkpointPages > 100000) {
                error("storage.sqlite.wal_autocheckpoint_pages doit etre entre 100 et 100000.");
            }
            if (cacheSize < 100 || cacheSize > 1000000) {
                error("storage.sqlite.cache_size_kib doit etre entre 100 et 1000000.");
            }
            if (journalLimit < 1048576L || journalLimit > 1073741824L) {
                error("storage.sqlite.journal_size_limit_bytes doit etre entre 1 Mio et 1 Gio.");
            }
            return;
        }

        requireNonEmpty("storage.mysql.host");
        requireNonEmpty("storage.mysql.database");
        requireNonEmpty("storage.mysql.username");
        int port = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.port", 3306);
        if (port <= 0 || port > 65535) error("storage.mysql.port hors limites: " + port);
        int connectTimeout = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.connection_timeout_ms", 10000);
        int socketTimeout = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.socket_timeout_ms", 30000);
        if (connectTimeout <= 0) error("storage.mysql.connection_timeout_ms doit etre > 0.");
        if (socketTimeout <= 0) error("storage.mysql.socket_timeout_ms doit etre > 0.");

        int maxPool = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.pool.maximum_pool_size", 10);
        int minIdle = plugin.getConfigManager().getMainConfig().getInt("storage.mysql.pool.minimum_idle", 2);
        long idleTimeout = plugin.getConfigManager().getMainConfig().getLong("storage.mysql.pool.idle_timeout_ms", 600000L);
        long maxLifetime = plugin.getConfigManager().getMainConfig().getLong("storage.mysql.pool.max_lifetime_ms", 1800000L);
        long leakDetection = plugin.getConfigManager().getMainConfig().getLong("storage.mysql.pool.leak_detection_ms", 0L);
        if (maxPool < 1) error("storage.mysql.pool.maximum_pool_size doit etre >= 1.");
        if (maxPool > 50) warn("storage.mysql.pool.maximum_pool_size > 50 est rarement utile et peut saturer MySQL.");
        if (minIdle < 0) error("storage.mysql.pool.minimum_idle ne peut pas etre negatif.");
        if (minIdle > maxPool) error("storage.mysql.pool.minimum_idle ne peut pas depasser maximum_pool_size.");
        if (idleTimeout < 10000L) warn("storage.mysql.pool.idle_timeout_ms < 10000 peut recycler les connexions trop souvent.");
        if (maxLifetime < 30000L) error("storage.mysql.pool.max_lifetime_ms doit etre >= 30000.");
        if (leakDetection > 0L && leakDetection < 2000L) error("storage.mysql.pool.leak_detection_ms doit etre 0 ou >= 2000.");
    }

    private void validateJobSlots() {
        ConfigurationSection slots = plugin.getConfigManager().getMainConfig().getConfigurationSection("job_slots");
        if (slots == null) {
            error("Section job_slots manquante dans config.yml.");
            return;
        }

        int defaultSlots = slots.getInt("default_slots", 2);
        int maxSlots = slots.getInt("max_slots", 6);
        if (defaultSlots < 1) error("job_slots.default_slots doit etre >= 1.");
        if (maxSlots < 1 || maxSlots > 6) error("job_slots.max_slots doit etre entre 1 et 6.");
        if (defaultSlots > maxSlots) error("job_slots.default_slots ne peut pas depasser max_slots.");

        String condition = slots.getString("unlock_condition", "TOTAL_LEVEL");
        if (!"TOTAL_LEVEL".equalsIgnoreCase(condition)
            && !"HIGHEST_JOB_LEVEL".equalsIgnoreCase(condition)
            && !"MAIN_JOB_LEVEL".equalsIgnoreCase(condition)) {
            error("job_slots.unlock_condition invalide: " + condition);
        }
        if (!"TOTAL_LEVEL".equalsIgnoreCase(condition)) {
            warn("unlock_condition recommande pour la V1: TOTAL_LEVEL (actuel: " + condition + ").");
        }

        ConfigurationSection thresholds = slots.getConfigurationSection("unlock_thresholds");
        if (maxSlots > defaultSlots && thresholds == null) {
            error("job_slots.unlock_thresholds manquant alors que max_slots > default_slots.");
            return;
        }
        if (thresholds != null) {
            int previous = 0;
            for (int slot = defaultSlots + 1; slot <= maxSlots; slot++) {
                if (!thresholds.contains(String.valueOf(slot))) {
                    error("job_slots.unlock_thresholds." + slot + " manquant.");
                    continue;
                }
                int required = thresholds.getInt(String.valueOf(slot));
                if (required <= 0) error("job_slots.unlock_thresholds." + slot + " doit etre > 0.");
                if (required <= previous) warn("Seuil slot " + slot + " non strictement superieur au precedent.");
                previous = required;
            }
        }

        long cooldown = slots.getLong("change_cooldown", 28800L);
        if (cooldown < 0L) error("job_slots.change_cooldown ne peut pas etre negatif.");
    }

    private void validateAntiAbuse() {
        ConfigurationSection antiAbuse = plugin.getConfigManager().getMainConfig().getConfigurationSection("anti_abuse");
        if (antiAbuse == null) {
            error("Section anti_abuse manquante dans config.yml.");
            return;
        }

        if (antiAbuse.getInt("block_position_cooldown", 300) < 0) {
            error("anti_abuse.block_position_cooldown ne peut pas etre negatif.");
        }
        if (antiAbuse.getInt("pvp_target_cooldown", 1200) < 0) {
            error("anti_abuse.pvp_target_cooldown ne peut pas etre negatif.");
        }

        ConfigurationSection pvp = antiAbuse.getConfigurationSection("pvp");
        if (pvp == null) {
            warn("anti_abuse.pvp manquant: protections Pretorien avancees en valeurs par defaut.");
            return;
        }
        if (pvp.getInt("last_hit_seconds", 20) < 0) error("anti_abuse.pvp.last_hit_seconds ne peut pas etre negatif.");
        if (pvp.getLong("victim_cooldown_seconds", 1200L) < 0L) error("anti_abuse.pvp.victim_cooldown_seconds ne peut pas etre negatif.");

        ConfigurationSection cap = pvp.getConfigurationSection("daily_kill_cap");
        if (cap == null) {
            warn("anti_abuse.pvp.daily_kill_cap manquant: cap Pretorien par defaut 80/24h.");
            return;
        }
        if (cap.getBoolean("enabled", true)) {
            if (cap.getInt("amount", 80) <= 0) error("anti_abuse.pvp.daily_kill_cap.amount doit etre > 0.");
            if (cap.getLong("window_seconds", 86400L) <= 0L) error("anti_abuse.pvp.daily_kill_cap.window_seconds doit etre > 0.");
        }
    }

    private void validateJobs() {
        for (String expected : plugin.getJobRegistry().getExpectedJobIds()) {
            JobDefinition job = plugin.getJobRegistry().getJob(expected);
            if (job == null) {
                error("Job attendu non charge: " + expected + ". Verifie jobs/" + expected + ".yml");
                continue;
            }
            validateJob(job);
        }

        if (plugin.getJobRegistry().getJobCount() != plugin.getJobRegistry().getExpectedJobIds().size()) {
            error("Nombre de jobs charges incoherent: " + plugin.getJobRegistry().getJobCount()
                + "/" + plugin.getJobRegistry().getExpectedJobIds().size());
        }
    }

    private void validateJob(JobDefinition job) {
        if (job.getDisplayName() == null || job.getDisplayName().trim().isEmpty()) {
            error("display_name vide pour job " + job.getId());
        }
        if (job.getMaxLevel() <= 0) {
            error("max_level doit etre > 0 pour job " + job.getId());
        }
        if (job.getXpForLevel(1) <= 0) {
            error("XP requis niveau 1 invalide pour job " + job.getId());
        }
        if (job.getActions().isEmpty()) {
            warn("Aucune action configuree pour job " + job.getId());
        }

        for (String actionKey : job.getActions().keySet()) {
            JobDefinition.ActionReward reward = job.getActions().get(actionKey);
            if (reward.getXp() <= 0) {
                error("Action " + job.getId() + "." + actionKey + " a xp <= 0.");
            }
            if (reward.getMoney() != 0D) {
                warn("Action " + job.getId() + "." + actionKey + " a money=" + reward.getMoney()
                    + " alors que la V1 vise 0 money par action.");
            }
            if (!isKnownActionKey(actionKey)) {
                warn("Action " + job.getId() + "." + actionKey
                    + " ne correspond pas a un Material/EntityType 1.8 connu ni a une action speciale.");
            }
        }

        for (Integer level : job.getLevelRewards().keySet()) {
            if (level == null || level <= 0 || level > job.getMaxLevel()) {
                error("Reward niveau invalide pour job " + job.getId() + ": " + level);
                continue;
            }
            for (String command : job.getLevelRewardCommands(level)) {
                if (command == null || command.trim().isEmpty()) {
                    error("Commande reward vide pour job " + job.getId() + " niveau " + level);}
            }
        }
    }

    private void validatePilleur() {
        JobDefinition pilleur = plugin.getJobRegistry().getJob("pilleur");
        if (pilleur == null) return;
        requireAction(pilleur, "TNT_EXPLODE");
        requireAction(pilleur, "DYNAMITE_EXPLODE");
        requireAction(pilleur, "TNT_CRAFT");
        requireAction(pilleur, "DYNAMITE_CRAFT");

        ConfigurationSection limit = plugin.getConfigManager().getMainConfig().getConfigurationSection("pilleur.tnt_xp_limit");
        if (limit == null) {
            error("pilleur.tnt_xp_limit manquant dans config.yml.");
            return;
        }
        if (limit.getBoolean("enabled", true)) {
            if (limit.getInt("amount", 128) <= 0) error("pilleur.tnt_xp_limit.amount doit etre > 0.");
            if (limit.getInt("window_seconds", 28800) <= 0) error("pilleur.tnt_xp_limit.window_seconds doit etre > 0.");
        }

        ConfigurationSection dynamiteItem = plugin.getConfigManager().getMainConfig().getConfigurationSection("pilleur.dynamite_item");
        if (dynamiteItem == null) {
            warn("pilleur.dynamite_item manquant. Compat ancien pilleur.dynamite_nbt_example conservee, mais la nouvelle config est recommandee.");
            return;
        }

        String materialName = dynamiteItem.getString("material", "TNT");
        if (materialName != null && !materialName.trim().isEmpty() && !"*".equals(materialName.trim())
                && Material.matchMaterial(materialName.trim()) == null) {
            error("pilleur.dynamite_item.material inconnu: " + materialName);
        }
        if (dynamiteItem.contains("data") && dynamiteItem.getInt("data", -1) < -1) {
            error("pilleur.dynamite_item.data doit etre -1 ou une data value >= 0.");
        }
        ConfigurationSection nbt = dynamiteItem.getConfigurationSection("nbt");
        if (nbt == null || nbt.getKeys(false).isEmpty()) {
            warn("pilleur.dynamite_item.nbt vide: seul le material differenciera la dynamite.");
        }
    }

    private void validateQuests() {
        if (plugin.getQuestManager() == null || !plugin.getQuestManager().isEnabled()) return;

        for (QuestDefinition quest : plugin.getQuestManager().getQuests()) {
            if (!QUEST_TYPES.contains(quest.getType())) {
                if (plugin.getQuestManager().isCustomQuestTypeDeclared(
                        quest.getType())) {
                    KjobLogger.info("[Validation] Quete " + quest.getId()
                        + ": type custom declare " + quest.getType() + ".");
                } else {
                    warn("Quete " + quest.getId() + " utilise un type inconnu: " + quest.getType()
                        + ". Declare-le dans custom_types si un hook l'envoie volontairement.");
                }
            }
            if (!isKnownQuestTarget(quest.getTarget())) {
                warn("Quete " + quest.getId() + " utilise un target non reconnu 1.8: " + quest.getTarget()
                    + ". OK si c'est un target custom envoye par un hook, sinon verifie l'orthographe.");
            }
        }
    }

    private void requireAction(JobDefinition job, String action) {
        if (!job.getActions().containsKey(action)) {
            error("Job " + job.getId() + " doit contenir l'action " + action + ".");
        }
    }

    private boolean isKnownActionKey(String actionKey) {
        if (SPECIAL_ACTIONS.contains(actionKey)) return true;
        if (Material.matchMaterial(actionKey) != null) return true;
        try {
            EntityType.valueOf(actionKey);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isKnownQuestTarget(String target) {
        if (target == null) return false;
        String key = target.toUpperCase(Locale.ROOT);
        if ("*".equals(key) || "ALL".equals(key) || "DYNAMITE".equals(key)) return true;
        if (Material.matchMaterial(key) != null) return true;
        int dataSeparator = key.lastIndexOf(':');
        if (dataSeparator > 0 && dataSeparator < key.length() - 1
                && Material.matchMaterial(key.substring(0, dataSeparator)) != null) {
            try {
                int data = Integer.parseInt(key.substring(dataSeparator + 1));
                return data >= 0 && data <= 255;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        try {
            EntityType.valueOf(key);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void requireNonEmpty(String path) {
        String value = plugin.getConfigManager().getMainConfig().getString(path, "");
        if (value == null || value.trim().isEmpty()) error(path + " ne peut pas etre vide.");
    }

    private void error(String message) {
        errors++;
        KjobLogger.error("[Validation] " + message);
    }

    private void warn(String message) {
        warnings++;
        KjobLogger.warn("[Validation] " + message);
    }
}
