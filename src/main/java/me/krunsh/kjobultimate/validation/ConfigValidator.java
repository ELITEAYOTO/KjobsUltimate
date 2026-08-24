package me.krunsh.kjobultimate.validation;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validation centralisÃ©e des configurations critiques de KjobsUltimate.
 *
 * Les erreurs empÃªchent le dÃ©marrage ou le reload afin d'Ã©viter de charger un
 * Ã©tat incohÃ©rent. Les avertissements signalent une configuration valide mais
 * probablement involontaire ou risquÃ©e.
 *
 * Cette classe valide notamment :
 * - le stockage ;
 * - les slots de mÃ©tiers ;
 * - les multiplicateurs et protections anti-abus ;
 * - chaque transition de niveau ;
 * - les actions MATERIAL, MATERIAL:data, EntityType et actions spÃ©ciales ;
 * - les rÃ©compenses de niveau ;
 * - les quÃªtes et leurs targets.
 */
public final class ConfigValidator {

    private static final int MAX_SUPPORTED_SLOTS = 6;
    private static final int MAX_BLOCK_DATA = 255;

    private static final Set<String> SPECIAL_ACTIONS =
        immutableSet(
            "PVP_KILL",
            "WITHER_SKELETON",
            "TNT_EXPLODE",
            "DYNAMITE_EXPLODE",
            "TNT_CRAFT",
            "DYNAMITE_CRAFT");

    private static final Set<String> QUEST_TYPES =
        immutableSet(
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
            "TAME");

    private static final Set<String> SPECIAL_QUEST_TARGETS =
        immutableSet(
            "*",
            "ALL",
            "DYNAMITE");

    private final KjobUltimate plugin;

    private int errors;
    private int warnings;

    public ConfigValidator(KjobUltimate plugin) {
        this.plugin = Objects.requireNonNull(
            plugin,
            "KjobUltimate ne peut pas Ãªtre null.");
    }

    /**
     * ExÃ©cute toutes les validations.
     *
     * @throws IllegalStateException lorsqu'au moins une erreur bloquante existe
     */
    public void validateOrThrow() {
        errors = 0;
        warnings = 0;

        validateStorage();
        validateJobSlots();
        validateXpMultipliers();
        validateAntiAbuse();
        validateJobs();
        validatePilleur();
        validateQuests();

        if (errors > 0) {
            throw new IllegalStateException(
                "Validation KjobsUltimate Ã©chouÃ©e : "
                    + errors
                    + " erreur(s), "
                    + warnings
                    + " avertissement(s).");
        }

        if (warnings > 0) {
            KjobLogger.warn(
                "Validation terminÃ©e avec "
                    + warnings
                    + " avertissement(s).");
        } else {
            KjobLogger.success(
                "Validation configs/jobs OK.");
        }
    }

    private void validateStorage() {
        ConfigurationSection main =
            plugin.getConfigManager().getMainConfig();

        String type =
            normalizeUpper(
                main.getString(
                    "storage.type",
                    "SQLITE"));

        if (!"SQLITE".equals(type)
                && !"MYSQL".equals(type)) {

            error(
                "storage.type invalide : "
                    + type
                    + " (attendu SQLITE ou MYSQL).");
            return;
        }

        int autosave =
            main.getInt(
                "storage.autosave_interval",
                10);

        if (autosave <= 0) {
            error(
                "storage.autosave_interval doit Ãªtre > 0 minute.");
        } else if (autosave < 5) {
            warn(
                "storage.autosave_interval < 5 peut provoquer "
                    + "trop d'Ã©critures.");
        }

        if ("SQLITE".equals(type)) {
            validateSqlite(main);
        } else {
            validateMysql(main);
        }
    }

    private void validateSqlite(ConfigurationSection main) {
        String sqliteFile =
            main.getString(
                "storage.sqlite_file",
                "");

        if (isBlank(sqliteFile)) {
            error(
                "storage.sqlite_file ne peut pas Ãªtre vide en SQLITE.");
        } else if (sqliteFile.contains("\0")) {
            error(
                "storage.sqlite_file contient un caractÃ¨re interdit.");
        }

        int busyTimeout =
            main.getInt(
                "storage.sqlite.busy_timeout_ms",
                5000);

        int checkpointPages =
            main.getInt(
                "storage.sqlite.wal_autocheckpoint_pages",
                1000);

        int cacheSize =
            main.getInt(
                "storage.sqlite.cache_size_kib",
                8000);

        long journalLimit =
            main.getLong(
                "storage.sqlite.journal_size_limit_bytes",
                67108864L);

        if (busyTimeout < 100
                || busyTimeout > 120000) {

            error(
                "storage.sqlite.busy_timeout_ms doit Ãªtre "
                    + "compris entre 100 et 120000.");
        }

        if (checkpointPages < 100
                || checkpointPages > 100000) {

            error(
                "storage.sqlite.wal_autocheckpoint_pages doit Ãªtre "
                    + "compris entre 100 et 100000.");
        }

        if (cacheSize < 100
                || cacheSize > 1000000) {

            error(
                "storage.sqlite.cache_size_kib doit Ãªtre "
                    + "compris entre 100 et 1000000.");
        }

        if (journalLimit < 1048576L
                || journalLimit > 1073741824L) {

            error(
                "storage.sqlite.journal_size_limit_bytes doit Ãªtre "
                    + "compris entre 1 Mio et 1 Gio.");
        }
    }

    private void validateMysql(ConfigurationSection main) {
        requireNonEmpty(
            main,
            "storage.mysql.host");

        requireNonEmpty(
            main,
            "storage.mysql.database");

        requireNonEmpty(
            main,
            "storage.mysql.username");

        int port =
            main.getInt(
                "storage.mysql.port",
                3306);

        if (port <= 0 || port > 65535) {
            error(
                "storage.mysql.port hors limites : "
                    + port
                    + ".");
        }

        int connectTimeout =
            main.getInt(
                "storage.mysql.connection_timeout_ms",
                10000);

        int socketTimeout =
            main.getInt(
                "storage.mysql.socket_timeout_ms",
                30000);

        if (connectTimeout <= 0) {
            error(
                "storage.mysql.connection_timeout_ms doit Ãªtre > 0.");
        }

        if (socketTimeout <= 0) {
            error(
                "storage.mysql.socket_timeout_ms doit Ãªtre > 0.");
        }

        int maxPool =
            main.getInt(
                "storage.mysql.pool.maximum_pool_size",
                10);

        int minIdle =
            main.getInt(
                "storage.mysql.pool.minimum_idle",
                2);

        long idleTimeout =
            main.getLong(
                "storage.mysql.pool.idle_timeout_ms",
                600000L);

        long maxLifetime =
            main.getLong(
                "storage.mysql.pool.max_lifetime_ms",
                1800000L);

        long leakDetection =
            main.getLong(
                "storage.mysql.pool.leak_detection_ms",
                0L);

        if (maxPool < 1) {
            error(
                "storage.mysql.pool.maximum_pool_size doit Ãªtre >= 1.");
        } else if (maxPool > 50) {
            warn(
                "storage.mysql.pool.maximum_pool_size > 50 est "
                    + "rarement utile et peut saturer MySQL.");
        }

        if (minIdle < 0) {
            error(
                "storage.mysql.pool.minimum_idle ne peut pas Ãªtre nÃ©gatif.");
        }

        if (minIdle > maxPool) {
            error(
                "storage.mysql.pool.minimum_idle ne peut pas dÃ©passer "
                    + "maximum_pool_size.");
        }

        if (idleTimeout < 10000L) {
            warn(
                "storage.mysql.pool.idle_timeout_ms < 10000 peut "
                    + "recycler les connexions trop frÃ©quemment.");
        }

        if (maxLifetime < 30000L) {
            error(
                "storage.mysql.pool.max_lifetime_ms doit Ãªtre >= 30000.");
        }

        if (leakDetection > 0L
                && leakDetection < 2000L) {

            error(
                "storage.mysql.pool.leak_detection_ms doit Ãªtre "
                    + "Ã©gal Ã  0 ou >= 2000.");
        }
    }

    private void validateJobSlots() {
        ConfigurationSection slots =
            plugin.getConfigManager()
                .getMainConfig()
                .getConfigurationSection("job_slots");

        if (slots == null) {
            error(
                "Section job_slots manquante dans config.yml.");
            return;
        }

        int defaultSlots =
            slots.getInt(
                "default_slots",
                2);

        int maxSlots =
            slots.getInt(
                "max_slots",
                MAX_SUPPORTED_SLOTS);

        if (defaultSlots < 1) {
            error(
                "job_slots.default_slots doit Ãªtre >= 1.");
        }

        if (maxSlots < 1
                || maxSlots > MAX_SUPPORTED_SLOTS) {

            error(
                "job_slots.max_slots doit Ãªtre compris entre 1 et "
                    + MAX_SUPPORTED_SLOTS
                    + ".");
        }

        if (defaultSlots > maxSlots) {
            error(
                "job_slots.default_slots ne peut pas dÃ©passer "
                    + "job_slots.max_slots.");
        }

        String condition =
            normalizeUpper(
                slots.getString(
                    "unlock_condition",
                    "TOTAL_LEVEL"));

        if (!"TOTAL_LEVEL".equals(condition)
                && !"HIGHEST_JOB_LEVEL".equals(condition)
                && !"MAIN_JOB_LEVEL".equals(condition)) {

            error(
                "job_slots.unlock_condition invalide : "
                    + condition
                    + ".");
        }

        ConfigurationSection thresholds =
            slots.getConfigurationSection(
                "unlock_thresholds");

        if (maxSlots > defaultSlots
                && thresholds == null) {

            error(
                "job_slots.unlock_thresholds est manquant alors que "
                    + "max_slots > default_slots.");
            return;
        }

        if (thresholds != null) {
            int previous = 0;

            for (int slot = defaultSlots + 1;
                    slot <= maxSlots;
                    slot++) {

                String key =
                    String.valueOf(slot);

                if (!thresholds.contains(key)) {
                    error(
                        "job_slots.unlock_thresholds."
                            + slot
                            + " est manquant.");
                    continue;
                }

                int required =
                    thresholds.getInt(key);

                if (required <= 0) {
                    error(
                        "job_slots.unlock_thresholds."
                            + slot
                            + " doit Ãªtre > 0.");
                }

                if (previous > 0
                        && required <= previous) {

                    warn(
                        "Le seuil du slot "
                            + slot
                            + " n'est pas strictement supÃ©rieur "
                            + "au prÃ©cÃ©dent.");
                }

                previous = required;
            }

            for (String rawSlot
                    : thresholds.getKeys(false)) {

                Integer parsed =
                    parseInteger(rawSlot);

                if (parsed == null) {
                    error(
                        "ClÃ© non numÃ©rique dans "
                            + "job_slots.unlock_thresholds : "
                            + rawSlot
                            + ".");
                    continue;
                }

                if (parsed.intValue() <= defaultSlots
                        || parsed.intValue() > maxSlots) {

                    warn(
                        "Seuil de slot inutilisÃ© : "
                            + rawSlot
                            + " (plage attendue "
                            + (defaultSlots + 1)
                            + "-"
                            + maxSlots
                            + ").");
                }
            }
        }

        long cooldown =
            slots.getLong(
                "change_cooldown",
                28800L);

        if (cooldown < 0L) {
            error(
                "job_slots.change_cooldown ne peut pas Ãªtre nÃ©gatif.");
        }
    }

    private void validateXpMultipliers() {
        ConfigurationSection multipliers =
            plugin.getConfigManager()
                .getMainConfig()
                .getConfigurationSection("xp_multipliers");

        if (multipliers == null) {
            warn(
                "Section xp_multipliers manquante : multiplicateurs "
                    + "par dÃ©faut utilisÃ©s.");
            return;
        }

        double eventMultiplier =
            multipliers.getDouble(
                "event_multiplier",
                1.0D);

        validateNonNegativeFiniteDouble(
            "xp_multipliers.event_multiplier",
            eventMultiplier);

        ConfigurationSection permissions =
            multipliers.getConfigurationSection(
                "permissions");

        if (permissions == null) {
            return;
        }

        for (String permission
                : permissions.getKeys(false)) {

            if (isBlank(permission)) {
                error(
                    "Permission XP vide dans "
                        + "xp_multipliers.permissions.");
                continue;
            }

            double multiplier =
                permissions.getDouble(permission);

            validateNonNegativeFiniteDouble(
                "xp_multipliers.permissions."
                    + permission,
                multiplier);

            if (multiplier > 100.0D) {
                warn(
                    "Multiplicateur trÃ¨s Ã©levÃ© pour "
                        + permission
                        + " : "
                        + multiplier
                        + ".");
            }
        }
    }

    private void validateAntiAbuse() {
        ConfigurationSection antiAbuse =
            plugin.getConfigManager()
                .getMainConfig()
                .getConfigurationSection("anti_abuse");

        if (antiAbuse == null) {
            error(
                "Section anti_abuse manquante dans config.yml.");
            return;
        }

        if (antiAbuse.getInt(
                "block_position_cooldown",
                300) < 0) {

            error(
                "anti_abuse.block_position_cooldown "
                    + "ne peut pas Ãªtre nÃ©gatif.");
        }

        if (antiAbuse.getInt(
                "pvp_target_cooldown",
                1200) < 0) {

            error(
                "anti_abuse.pvp_target_cooldown "
                    + "ne peut pas Ãªtre nÃ©gatif.");
        }

        validateGlobalDailyXpCap(antiAbuse);
        validatePvpAntiAbuse(antiAbuse);
    }

    private void validateGlobalDailyXpCap(
            ConfigurationSection antiAbuse) {

        ConfigurationSection cap =
            antiAbuse.getConfigurationSection(
                "daily_xp_cap");

        if (cap == null) {
            warn(
                "anti_abuse.daily_xp_cap manquant : "
                    + "plafond global dÃ©sactivÃ© par dÃ©faut.");
            return;
        }

        if (cap.getBoolean("enabled", false)
                && cap.getInt("amount", 0) <= 0) {

            error(
                "anti_abuse.daily_xp_cap.amount doit Ãªtre > 0 "
                    + "lorsque le plafond est activÃ©.");
        }
    }

    private void validatePvpAntiAbuse(
            ConfigurationSection antiAbuse) {

        ConfigurationSection pvp =
            antiAbuse.getConfigurationSection("pvp");

        if (pvp == null) {
            warn(
                "anti_abuse.pvp manquant : protections PrÃ©torien "
                    + "avancÃ©es en valeurs par dÃ©faut.");
            return;
        }

        if (pvp.getInt(
                "last_hit_seconds",
                20) < 0) {

            error(
                "anti_abuse.pvp.last_hit_seconds "
                    + "ne peut pas Ãªtre nÃ©gatif.");
        }

        if (pvp.getLong(
                "victim_cooldown_seconds",
                1200L) < 0L) {

            error(
                "anti_abuse.pvp.victim_cooldown_seconds "
                    + "ne peut pas Ãªtre nÃ©gatif.");
        }

        ConfigurationSection cap =
            pvp.getConfigurationSection(
                "daily_kill_cap");

        if (cap == null) {
            warn(
                "anti_abuse.pvp.daily_kill_cap manquant : "
                    + "cap PrÃ©torien par dÃ©faut utilisÃ©.");
            return;
        }

        if (!cap.getBoolean("enabled", true)) {
            return;
        }

        if (cap.getInt("amount", 80) <= 0) {
            error(
                "anti_abuse.pvp.daily_kill_cap.amount "
                    + "doit Ãªtre > 0.");
        }

        if (cap.getLong(
                "window_seconds",
                86400L) <= 0L) {

            error(
                "anti_abuse.pvp.daily_kill_cap.window_seconds "
                    + "doit Ãªtre > 0.");
        }
    }

    private void validateJobs() {
        java.util.List<String> expectedIds =
            plugin.getJobRegistry()
                .getExpectedJobIds();

        for (String expected : expectedIds) {
            JobDefinition job =
                plugin.getJobRegistry()
                    .getJob(expected);

            if (job == null) {
                error(
                    "MÃ©tier attendu non chargÃ© : "
                        + expected
                        + ". VÃ©rifie jobs/"
                        + expected
                        + ".yml.");
                continue;
            }

            validateJob(job);
        }

        int loaded =
            plugin.getJobRegistry()
                .getJobCount();

        if (loaded != expectedIds.size()) {
            error(
                "Nombre de mÃ©tiers chargÃ©s incohÃ©rent : "
                    + loaded
                    + "/"
                    + expectedIds.size()
                    + ".");
        }
    }

    private void validateJob(JobDefinition job) {
        String jobPath =
            "jobs/" + job.getId() + ".yml";

        if (isBlank(job.getId())) {
            error(
                "Identifiant de mÃ©tier vide dans "
                    + jobPath
                    + ".");
        }

        if (isBlank(job.getDisplayName())) {
            error(
                "display_name vide pour le mÃ©tier "
                    + job.getId()
                    + ".");
        }

        if (job.getMaxLevel() <= 0) {
            error(
                "max_level doit Ãªtre > 0 pour le mÃ©tier "
                    + job.getId()
                    + ".");
            return;
        }

        if (job.getMaxLevel() > 10000) {
            warn(
                "max_level trÃ¨s Ã©levÃ© pour le mÃ©tier "
                    + job.getId()
                    + " : "
                    + job.getMaxLevel()
                    + ".");
        }

        if (job.getDailyXpCap() < 0) {
            error(
                "daily_xp_cap ne peut pas Ãªtre nÃ©gatif pour le mÃ©tier "
                    + job.getId()
                    + ".");
        }

        validateXpCurve(job);
        validateActions(job);
        validateLevelRewards(job);
    }

    /**
     * VÃ©rifie chaque transition 0->1, 1->2, ..., max-1->max.
     */
    private void validateXpCurve(JobDefinition job) {
        Map<Integer, Integer> configured =
            job.getConfiguredXpLevels();

        for (Map.Entry<Integer, Integer> entry
                : configured.entrySet()) {

            Integer targetLevel =
                entry.getKey();

            Integer requiredXp =
                entry.getValue();

            if (targetLevel == null
                    || targetLevel.intValue() < 1
                    || targetLevel.intValue()
                        > job.getMaxLevel()) {

                error(
                    "Palier XP hors limites pour le mÃ©tier "
                        + job.getId()
                        + " : "
                        + targetLevel
                        + ".");
                continue;
            }

            if (requiredXp == null
                    || requiredXp.intValue() <= 0) {

                error(
                    "XP configurÃ©e invalide pour atteindre le niveau "
                        + targetLevel
                        + " du mÃ©tier "
                        + job.getId()
                        + ".");
            }
        }

        int previousRequired = 0;
        long totalRequired = 0L;

        for (int targetLevel = 1;
                targetLevel <= job.getMaxLevel();
                targetLevel++) {

            int required =
                job.getXpRequiredToReachLevel(
                    targetLevel);

            if (required <= 0) {
                error(
                    "XP requise invalide pour atteindre le niveau "
                        + targetLevel
                        + " du mÃ©tier "
                        + job.getId()
                        + ".");
                continue;
            }

            if (previousRequired > 0
                    && required < previousRequired) {

                warn(
                    "La courbe XP du mÃ©tier "
                        + job.getId()
                        + " diminue au niveau "
                        + targetLevel
                        + " ("
                        + previousRequired
                        + " -> "
                        + required
                        + ").");
            }

            previousRequired = required;
            totalRequired += required;
        }

        if (configured.size() < job.getMaxLevel()) {
            warn(
                "Le mÃ©tier "
                    + job.getId()
                    + " configure "
                    + configured.size()
                    + "/"
                    + job.getMaxLevel()
                    + " paliers explicites ; la courbe fallback "
                    + "complÃ¨te les niveaux manquants.");
        }

        if (totalRequired <= 0L) {
            error(
                "Somme de la courbe XP invalide pour le mÃ©tier "
                    + job.getId()
                    + ".");
        }

        if (job.getXpRequiredForNextLevel(
                job.getMaxLevel()) != 0) {

            error(
                "Le mÃ©tier "
                    + job.getId()
                    + " retourne encore une XP requise au niveau maximum.");
        }
    }

    private void validateActions(JobDefinition job) {
        if (job.getActions().isEmpty()) {
            warn(
                "Aucune action configurÃ©e pour le mÃ©tier "
                    + job.getId()
                    + ".");
            return;
        }

        for (Map.Entry<String, JobDefinition.ActionReward> entry
                : job.getActions().entrySet()) {

            String actionKey =
                entry.getKey();

            JobDefinition.ActionReward reward =
                entry.getValue();

            String actionPath =
                job.getId() + "." + actionKey;

            if (isBlank(actionKey)) {
                error(
                    "ClÃ© d'action vide pour le mÃ©tier "
                        + job.getId()
                        + ".");
                continue;
            }

            if (reward == null) {
                error(
                    "RÃ©compense absente pour l'action "
                        + actionPath
                        + ".");
                continue;
            }

            if (reward.getXp() <= 0) {
                error(
                    "Action "
                        + actionPath
                        + " : xp doit Ãªtre > 0.");
            }

            if (Double.isNaN(reward.getMoney())
                    || Double.isInfinite(
                        reward.getMoney())) {

                error(
                    "Action "
                        + actionPath
                        + " : money n'est pas un nombre valide.");
            } else if (reward.getMoney() != 0.0D) {
                warn(
                    "Action "
                        + actionPath
                        + " : money="
                        + reward.getMoney()
                        + " alors que l'Ã©conomie actuelle vise "
                        + "0 monnaie par action.");
            }

            if (!isKnownActionKey(actionKey)) {
                warn(
                    "Action "
                        + actionPath
                        + " inconnue en Minecraft 1.8 : "
                        + "attendu MATERIAL, MATERIAL:data, "
                        + "EntityType ou action spÃ©ciale.");
            }
        }
    }

    private void validateLevelRewards(JobDefinition job) {
        for (Map.Entry<Integer, java.util.List<String>> entry
                : job.getLevelRewards().entrySet()) {

            Integer level =
                entry.getKey();

            if (level == null
                    || level.intValue() <= 0
                    || level.intValue()
                        > job.getMaxLevel()) {

                error(
                    "Niveau de rÃ©compense invalide pour le mÃ©tier "
                        + job.getId()
                        + " : "
                        + level
                        + ".");
                continue;
            }

            java.util.List<String> commands =
                entry.getValue();

            if (commands == null
                    || commands.isEmpty()) {

                error(
                    "Aucune commande pour la rÃ©compense du mÃ©tier "
                        + job.getId()
                        + " niveau "
                        + level
                        + ".");
                continue;
            }

            for (int index = 0;
                    index < commands.size();
                    index++) {

                String command =
                    commands.get(index);

                if (isBlank(command)) {
                    error(
                        "Commande de rÃ©compense vide pour le mÃ©tier "
                            + job.getId()
                            + " niveau "
                            + level
                            + " index "
                            + index
                            + ".");
                    continue;
                }

                validateRewardCommandPrefix(
                    job,
                    level.intValue(),
                    command.trim());
            }
        }
    }

    private void validateRewardCommandPrefix(
            JobDefinition job,
            int level,
            String command) {

        if (!command.startsWith("[")) {
            return;
        }

        int closingBracket =
            command.indexOf(']');

        if (closingBracket <= 1) {
            warn(
                "PrÃ©fixe de commande mal formÃ© pour le mÃ©tier "
                    + job.getId()
                    + " niveau "
                    + level
                    + " : "
                    + command
                    + ".");
            return;
        }

        String prefix =
            command.substring(
                1,
                closingBracket)
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!"console".equals(prefix)
                && !"command".equals(prefix)
                && !"player".equals(prefix)
                && !"joueur".equals(prefix)) {

            warn(
                "PrÃ©fixe de commande inconnu ["
                    + prefix
                    + "] pour le mÃ©tier "
                    + job.getId()
                    + " niveau "
                    + level
                    + ".");
        }

        if (isBlank(
                command.substring(
                    closingBracket + 1))) {

            error(
                "Commande vide aprÃ¨s le prÃ©fixe pour le mÃ©tier "
                    + job.getId()
                    + " niveau "
                    + level
                    + ".");
        }
    }

    private void validatePilleur() {
        JobDefinition pilleur =
            plugin.getJobRegistry()
                .getJob("pilleur");

        if (pilleur == null) {
            return;
        }

        requireAction(
            pilleur,
            "TNT_EXPLODE");

        requireAction(
            pilleur,
            "DYNAMITE_EXPLODE");

        requireAction(
            pilleur,
            "TNT_CRAFT");

        requireAction(
            pilleur,
            "DYNAMITE_CRAFT");

        ConfigurationSection main =
            plugin.getConfigManager()
                .getMainConfig();

        ConfigurationSection limit =
            main.getConfigurationSection(
                "pilleur.tnt_xp_limit");

        if (limit == null) {
            error(
                "pilleur.tnt_xp_limit manquant dans config.yml.");
        } else if (limit.getBoolean("enabled", true)) {
            if (limit.getInt("amount", 128) <= 0) {
                error(
                    "pilleur.tnt_xp_limit.amount doit Ãªtre > 0.");
            }

            if (limit.getInt(
                    "window_seconds",
                    28800) <= 0) {

                error(
                    "pilleur.tnt_xp_limit.window_seconds "
                        + "doit Ãªtre > 0.");
            }
        }

        validateDynamiteItem(main);
    }

    private void validateDynamiteItem(
            ConfigurationSection main) {

        ConfigurationSection dynamiteItem =
            main.getConfigurationSection(
                "pilleur.dynamite_item");

        if (dynamiteItem == null) {
            warn(
                "pilleur.dynamite_item manquant : ancienne "
                    + "configuration de dynamite utilisÃ©e.");
            return;
        }

        String materialName =
            dynamiteItem.getString(
                "material",
                "TNT");

        if (!isBlank(materialName)
                && !"*".equals(
                    materialName.trim())
                && Material.matchMaterial(
                    normalizeUpper(materialName))
                    == null) {

            error(
                "pilleur.dynamite_item.material inconnu : "
                    + materialName
                    + ".");
        }

        if (dynamiteItem.contains("data")) {
            int data =
                dynamiteItem.getInt(
                    "data",
                    -1);

            if (data < -1
                    || data > MAX_BLOCK_DATA) {

                error(
                    "pilleur.dynamite_item.data doit Ãªtre -1 "
                        + "ou compris entre 0 et "
                        + MAX_BLOCK_DATA
                        + ".");
            }
        }

        ConfigurationSection nbt =
            dynamiteItem.getConfigurationSection(
                "nbt");

        if (nbt == null
                || nbt.getKeys(false).isEmpty()) {

            warn(
                "pilleur.dynamite_item.nbt est vide : "
                    + "seul le material diffÃ©renciera la dynamite.");
        }
    }

    private void validateQuests() {
        if (plugin.getQuestManager() == null
                || !plugin.getQuestManager()
                    .isEnabled()) {
            return;
        }

        for (QuestDefinition quest
                : plugin.getQuestManager()
                    .getQuests()) {

            if (quest == null) {
                error(
                    "Une quÃªte null a Ã©tÃ© chargÃ©e.");
                continue;
            }

            if (isBlank(quest.getId())) {
                error(
                    "Une quÃªte possÃ¨de un identifiant vide.");
            }

            String type =
                normalizeUpper(
                    quest.getType());

            if (!QUEST_TYPES.contains(type)) {
                if (plugin.getQuestManager()
                        .isCustomQuestTypeDeclared(type)) {

                    KjobLogger.info(
                        "[Validation] QuÃªte "
                            + quest.getId()
                            + " : type custom dÃ©clarÃ© "
                            + type
                            + ".");
                } else {
                    warn(
                        "QuÃªte "
                            + quest.getId()
                            + " : type inconnu "
                            + type
                            + ". DÃ©clare-le dans custom_types "
                            + "si un hook l'envoie volontairement.");
                }
            }

            if (quest.getAmount() <= 0) {
                error(
                    "QuÃªte "
                        + quest.getId()
                        + " : amount doit Ãªtre > 0.");
            }

            if (!isKnownQuestTarget(
                    quest.getTarget())) {

                warn(
                    "QuÃªte "
                        + quest.getId()
                        + " : target non reconnu en 1.8 : "
                        + quest.getTarget()
                        + ". VÃ©rifie l'orthographe ou le hook custom.");
            }
        }
    }

    private void requireAction(
            JobDefinition job,
            String action) {

        if (!job.hasAction(action)) {
            error(
                "Le mÃ©tier "
                    + job.getId()
                    + " doit contenir l'action "
                    + action
                    + ".");
        }
    }

    /**
     * Accepte :
     * - une action spÃ©ciale ;
     * - une identité KMINERAI:<oreId> ou KMINERAI_DROP:<dropId> ;
     * - un Material ;
     * - un Material avec data value, par exemple STONE:3 ;
     * - un EntityType.
     */
    private boolean isKnownActionKey(
            String actionKey) {

        if (actionKey == null) {
            return false;
        }

        String key =
            normalizeUpper(actionKey);

        if (key.isEmpty()) {
            return false;
        }

        if (SPECIAL_ACTIONS.contains(key)) {
            return true;
        }

        if (isKmineraiActionKey(key)) {
            return true;
        }

        if (Material.matchMaterial(key) != null) {
            return true;
        }

        MaterialDataKey materialData =
            parseMaterialDataKey(key);

        if (materialData != null) {
            return true;
        }

        try {
            EntityType.valueOf(key);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isKmineraiActionKey(String key) {
        String identity;

        if (key.startsWith("KMINERAI:")) {
            identity = key.substring("KMINERAI:".length());
        } else if (key.startsWith("KMINERAI_DROP:")) {
            identity = key.substring("KMINERAI_DROP:".length());
        } else {
            return false;
        }

        return !identity.isEmpty()
            && identity.matches("[A-Z0-9_./]+");
    }

    private boolean isKnownQuestTarget(
            String target) {

        if (target == null) {
            return false;
        }

        String key =
            normalizeUpper(target);

        if (key.isEmpty()) {
            return false;
        }

        if (SPECIAL_QUEST_TARGETS.contains(key)) {
            return true;
        }

        if (Material.matchMaterial(key) != null) {
            return true;
        }

        if (parseMaterialDataKey(key) != null) {
            return true;
        }

        try {
            EntityType.valueOf(key);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * Analyse strictement une clÃ© MATERIAL:data.
     */
    private MaterialDataKey parseMaterialDataKey(
            String rawKey) {

        if (rawKey == null) {
            return null;
        }

        String key =
            normalizeUpper(rawKey);

        int separator =
            key.lastIndexOf(':');

        if (separator <= 0
                || separator >= key.length() - 1) {

            return null;
        }

        if (key.indexOf(':') != separator) {
            return null;
        }

        String materialName =
            key.substring(0, separator);

        String rawData =
            key.substring(separator + 1);

        Material material =
            Material.matchMaterial(materialName);

        if (material == null) {
            return null;
        }

        Integer data =
            parseInteger(rawData);

        if (data == null
                || data.intValue() < 0
                || data.intValue()
                    > MAX_BLOCK_DATA) {

            return null;
        }

        return new MaterialDataKey(
            material,
            data.intValue());
    }

    private void requireNonEmpty(
            ConfigurationSection section,
            String path) {

        String value =
            section.getString(path, "");

        if (isBlank(value)) {
            error(
                path
                    + " ne peut pas Ãªtre vide.");
        }
    }

    private void validateNonNegativeFiniteDouble(
            String path,
            double value) {

        if (Double.isNaN(value)
                || Double.isInfinite(value)) {

            error(
                path
                    + " doit Ãªtre un nombre fini.");
            return;
        }

        if (value < 0.0D) {
            error(
                path
                    + " ne peut pas Ãªtre nÃ©gatif.");
        }
    }

    private void error(String message) {
        errors++;
        KjobLogger.error(
            "[Validation] " + message);
    }

    private void warn(String message) {
        warnings++;
        KjobLogger.warn(
            "[Validation] " + message);
    }

    private static String normalizeUpper(
            String value) {

        return value == null
            ? ""
            : value.trim()
                .toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(
            String value) {

        return value == null
            || value.trim().isEmpty();
    }

    private static Integer parseInteger(
            String value) {

        if (isBlank(value)) {
            return null;
        }

        try {
            return Integer.valueOf(
                value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Set<String> immutableSet(
            String... values) {

        return Collections.unmodifiableSet(
            new HashSet<String>(
                Arrays.asList(values)));
    }

    private static final class MaterialDataKey {

        private final Material material;
        private final int data;

        private MaterialDataKey(
                Material material,
                int data) {

            this.material = material;
            this.data = data;
        }

        @SuppressWarnings("unused")
        private Material getMaterial() {
            return material;
        }

        @SuppressWarnings("unused")
        private int getData() {
            return data;
        }
    }
}
