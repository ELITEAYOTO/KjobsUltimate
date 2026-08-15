package me.krunsh.kjobultimate.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.config.ConfigManager;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.performance.HotPathSettings;
import me.krunsh.kjobultimate.performance.PermissionMultiplierCache;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Service central de progression XP.
 *
 * V3.14 retire du hot path :
 * - la lecture répétée des ConfigurationSection de permissions ;
 * - le HashSet créé à chaque reset quotidien ;
 * - la boucle niveau courant -> niveau max à chaque gain ;
 * - la résolution répétée des multiplicateurs de permission.
 */
public final class XpManager {

    private static final long DAILY_WINDOW_MS =
        86_400_000L;

    private final KjobUltimate plugin;
    private final PermissionMultiplierCache permissionCache =
        new PermissionMultiplierCache();

    private volatile PermissionRule[] permissionRules =
        new PermissionRule[0];

    private volatile HotPathSettings hotPathSettings;

    private volatile boolean globalDailyCapEnabled;
    private volatile int globalDailyCap;

    private volatile double cachedEventMultiplier = 1D;
    private volatile long nextEventMultiplierRefreshAt;

    public XpManager(
            KjobUltimate plugin) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                "plugin ne peut pas être null."
            );
        }

        this.plugin = plugin;
        reloadRuntimeConfig();
    }

    /**
     * Appelé au constructeur et automatiquement après ConfigManager.loadAll().
     */
    public void reloadRuntimeConfig() {

        HotPathSettings settings =
            HotPathSettings.load(
                plugin
            );

        hotPathSettings = settings;

        permissionRules =
            loadPermissionRules();

        permissionCache.configure(
            settings.getPermissionCacheTtlMs(),
            settings.getPermissionCacheMaxPlayers()
        );
        permissionCache.clear();

        ConfigManager config =
            plugin.getConfigManager();

        globalDailyCapEnabled =
            config.isDailyCapEnabled();

        globalDailyCap =
            Math.max(
                0,
                config.getMainConfig()
                    .getInt(
                        "anti_abuse.daily_xp_cap.amount",
                        0
                    )
            );

        cachedEventMultiplier =
            readEventMultiplier();

        nextEventMultiplierRefreshAt =
            safeAdd(
                System.currentTimeMillis(),
                settings.getEventMultiplierRefreshMs()
            );
    }

    public void removePlayerRuntimeCache(
            UUID playerId) {

        permissionCache.remove(
            playerId
        );
    }

    public int getPermissionCacheSize() {
        return permissionCache.size();
    }

    public LevelUpResult addXP(
            Player player,
            PlayerData data,
            String jobId,
            int baseXp) {

        requirePrimaryThread("addXP");

        if (player == null
                || data == null) {

            return LevelUpResult.noLevelUp(
                0,
                0,
                0
            );
        }

        String normalizedJobId =
            normalizeJobId(
                jobId
            );

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    normalizedJobId
                );

        if (job == null) {
            KjobLogger.error(
                "[XP] Métier inconnu lors d'un gain XP : "
                    + normalizedJobId
            );
            return LevelUpResult.noLevelUp(0, 0, 0);
        }

        long packedState =
            sanitizePlayerState(
                data,
                job
            );

        int level =
            unpackLevel(
                packedState
            );
        int xp =
            unpackXp(
                packedState
            );

        if (level >= job.getMaxLevel()) {
            ensureMaxLevelState(
                data,
                job,
                level,
                xp
            );
            return LevelUpResult.maxLevel(
                job.getMaxLevel()
            );
        }

        if (baseXp <= 0) {
            return LevelUpResult.noLevelUp(
                level,
                xp,
                0
            );
        }

        long now =
            System.currentTimeMillis();

        double permissionMultiplier =
            getPermissionMultiplierAt(
                player,
                now
            );
        double eventMultiplier =
            getEventMultiplierAt(
                now
            );
        double bonusMultiplier =
            sanitizeMultiplier(
                data.getBonusMultiplier(
                    normalizedJobId
                ),
                1D
            );

        int calculatedXp =
            calculateAwardedXp(
                baseXp,
                permissionMultiplier,
                eventMultiplier,
                bonusMultiplier
            );

        if (calculatedXp <= 0) {
            return LevelUpResult.noLevelUp(
                level,
                xp,
                0
            );
        }

        ensureDailyCountersFresh(
            data,
            normalizedJobId,
            now
        );

        int dailyAllowance =
            getDailyAllowance(
                data,
                job
            );

        if (dailyAllowance <= 0) {
            return LevelUpResult.noLevelUp(
                level,
                xp,
                0
            );
        }

        long xpUntilMax =
            job.getXpUntilMax(
                level,
                xp
            );

        if (xpUntilMax <= 0L) {
            KjobLogger.error(
                "[XP] Courbe invalide ou progression incohérente pour "
                    + player.getName()
                    + "/"
                    + normalizedJobId
                    + " au niveau "
                    + level
                    + "."
            );
            return LevelUpResult.noLevelUp(
                level,
                xp,
                0
            );
        }

        int actualXp =
            minPositiveInt(
                calculatedXp,
                dailyAllowance,
                xpUntilMax
            );

        if (actualXp <= 0) {
            return LevelUpResult.noLevelUp(
                level,
                xp,
                0
            );
        }

        LevelUpResult result =
            applyPositiveXp(
                player,
                data,
                job,
                level,
                xp,
                actualXp,
                true,
                true,
                now
            );

        if (plugin.getConfigManager()
                .isDebugXp()) {

            KjobLogger.info(
                "[XP] "
                    + player.getName()
                    + " +"
                    + actualXp
                    + " XP "
                    + normalizedJobId
                    + " (base="
                    + baseXp
                    + ", permission=x"
                    + permissionMultiplier
                    + ", event=x"
                    + eventMultiplier
                    + ", bonus=x"
                    + bonusMultiplier
                    + ", niveau="
                    + result.getNewLevel()
                    + ", restant="
                    + result.getRemainingXP()
                    + ")"
            );
        }

        return result;
    }

    private LevelUpResult applyPositiveXp(
            Player player,
            PlayerData data,
            JobDefinition job,
            int startingLevel,
            int startingXp,
            int xpToAdd,
            boolean executeRewards,
            boolean countDaily,
            long now) {

        long currentXp =
            (long) startingXp
                + xpToAdd;

        int currentLevel =
            startingLevel;
        int levelsGained =
            0;

        while (currentLevel < job.getMaxLevel()) {

            int required =
                job.getXpRequiredForNextLevel(
                    currentLevel
                );

            if (required <= 0) {
                KjobLogger.error(
                    "[XP] Palier invalide pour "
                        + job.getId()
                        + " : niveau actuel="
                        + currentLevel
                        + ", XP requise="
                        + required
                        + "."
                );
                break;
            }

            if (currentXp < required) {
                break;
            }

            currentXp -= required;
            currentLevel++;
            levelsGained++;

            if (executeRewards) {
                applyLevelRewards(
                    player,
                    job,
                    currentLevel
                );
            }
        }

        if (currentLevel >= job.getMaxLevel()) {
            currentLevel =
                job.getMaxLevel();
            currentXp = 0L;
        }

        int safeRemainingXp =
            currentXp >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) Math.max(
                    0L,
                    currentXp
                );

        if (countDaily) {
            data.applyJobXpGain(
                job.getId(),
                currentLevel,
                safeRemainingXp,
                xpToAdd,
                now
            );
        } else {
            data.setLevel(
                job.getId(),
                currentLevel
            );
            data.setXP(
                job.getId(),
                safeRemainingXp
            );
            data.setDisplayJob(
                job.getId()
            );
        }

        plugin.notifyJobsUiChanged(
            player.getUniqueId(),
            "kjobs:xp"
        );

        if (levelsGained > 0) {
            return LevelUpResult.leveled(
                levelsGained,
                currentLevel,
                safeRemainingXp,
                xpToAdd
            );
        }

        return LevelUpResult.noLevelUp(
            currentLevel,
            safeRemainingXp,
            xpToAdd
        );
    }

    public LevelUpResult adminAddXp(
            Player player,
            PlayerData data,
            String jobId,
            int amount) {

        requirePrimaryThread("adminAddXp");

        if (player == null
                || data == null) {

            return LevelUpResult.noLevelUp(0, 0, 0);
        }

        String normalizedJobId =
            normalizeJobId(jobId);

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    normalizedJobId
                );

        if (job == null) {
            KjobLogger.error(
                "[XP-ADMIN] Métier inconnu : "
                    + normalizedJobId
            );
            return LevelUpResult.noLevelUp(0, 0, 0);
        }

        long packedState =
            sanitizePlayerState(
                data,
                job
            );

        int level =
            unpackLevel(
                packedState
            );
        int xp =
            unpackXp(
                packedState
            );

        if (amount == 0) {
            return LevelUpResult.noLevelUp(
                level,
                xp,
                0
            );
        }

        if (amount < 0) {
            long reduced =
                (long) xp
                    + amount;

            int newXp =
                (int) Math.max(
                    0L,
                    reduced
                );

            data.setXP(
                normalizedJobId,
                newXp
            );

            plugin.notifyJobsUiChanged(
                player.getUniqueId(),
                "kjobs:admin-xp"
            );

            return LevelUpResult.noLevelUp(
                level,
                newXp,
                0
            );
        }

        if (level >= job.getMaxLevel()) {
            ensureMaxLevelState(
                data,
                job,
                level,
                xp
            );
            return LevelUpResult.maxLevel(
                job.getMaxLevel()
            );
        }

        long xpUntilMax =
            job.getXpUntilMax(
                level,
                xp
            );

        if (xpUntilMax <= 0L) {
            KjobLogger.error(
                "[XP-ADMIN] Progression impossible pour "
                    + player.getName()
                    + "/"
                    + normalizedJobId
                    + " : courbe invalide."
            );
            return LevelUpResult.noLevelUp(
                level,
                xp,
                0
            );
        }

        int actualXp =
            minPositiveInt(
                amount,
                Integer.MAX_VALUE,
                xpUntilMax
            );

        return applyPositiveXp(
            player,
            data,
            job,
            level,
            xp,
            actualXp,
            true,
            false,
            System.currentTimeMillis()
        );
    }

    private void applyLevelRewards(
            Player player,
            JobDefinition job,
            int reachedLevel) {

        for (String configuredCommand
                : job.getLevelRewardCommands(
                    reachedLevel
                )) {

            if (configuredCommand == null
                    || configuredCommand.trim().isEmpty()) {

                KjobLogger.error(
                    "[XP-REWARD] Commande vide pour "
                        + job.getId()
                        + " niveau "
                        + reachedLevel
                        + "."
                );
                continue;
            }

            String resolved =
                configuredCommand
                    .replace(
                        "{player}",
                        player.getName()
                    )
                    .replace(
                        "{uuid}",
                        player.getUniqueId()
                            .toString()
                    )
                    .replace(
                        "{level}",
                        String.valueOf(
                            reachedLevel
                        )
                    )
                    .replace(
                        "{job}",
                        job.getId()
                    )
                    .trim();

            try {
                executeRewardCommand(
                    player,
                    resolved
                );
            } catch (RuntimeException failure) {
                KjobLogger.error(
                    "[XP-REWARD] Échec de la commande du métier "
                        + job.getId()
                        + " niveau "
                        + reachedLevel
                        + " : "
                        + configuredCommand,
                    failure
                );
            }
        }
    }

    private void executeRewardCommand(
            Player player,
            String command) {

        if (command == null
                || command.trim().isEmpty()) {

            throw new IllegalArgumentException(
                "Commande de récompense vide."
            );
        }

        String trimmed =
            command.trim();
        String lower =
            trimmed.toLowerCase(
                Locale.ROOT
            );

        if (lower.startsWith("[player]")) {
            dispatchAsPlayer(
                player,
                trimmed.substring(
                    "[player]".length()
                ).trim()
            );
            return;
        }

        if (lower.startsWith("[joueur]")) {
            dispatchAsPlayer(
                player,
                trimmed.substring(
                    "[joueur]".length()
                ).trim()
            );
            return;
        }

        if (lower.startsWith("[command]")) {
            dispatchAsConsole(
                trimmed.substring(
                    "[command]".length()
                ).trim()
            );
            return;
        }

        if (lower.startsWith("[console]")) {
            dispatchAsConsole(
                trimmed.substring(
                    "[console]".length()
                ).trim()
            );
            return;
        }

        dispatchAsConsole(
            trimmed
        );
    }

    private void dispatchAsConsole(
            String command) {

        if (command == null
                || command.trim().isEmpty()) {

            throw new IllegalArgumentException(
                "Commande console vide."
            );
        }

        boolean accepted =
            plugin.getServer()
                .dispatchCommand(
                    plugin.getServer()
                        .getConsoleSender(),
                    command.trim()
                );

        if (!accepted) {
            throw new IllegalStateException(
                "Commande console refusée : "
                    + command
            );
        }
    }

    private void dispatchAsPlayer(
            Player player,
            String command) {

        if (command == null
                || command.trim().isEmpty()) {

            throw new IllegalArgumentException(
                "Commande joueur vide."
            );
        }

        boolean accepted =
            player.performCommand(
                command.trim()
            );

        if (!accepted) {
            throw new IllegalStateException(
                "Commande joueur refusée : "
                    + command
            );
        }
    }

    public void handleLevelUp(
            Player player,
            PlayerData data,
            String jobId,
            LevelUpResult result) {

        requirePrimaryThread("handleLevelUp");

        if (player == null
                || data == null
                || result == null
                || !result.isLeveledUp()) {

            return;
        }

        String normalizedJobId =
            normalizeJobId(jobId);

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    normalizedJobId
                );

        if (job == null) {
            KjobLogger.error(
                "[XP] handleLevelUp appelé pour un métier inconnu : "
                    + normalizedJobId
            );
            return;
        }

        plugin.getSlotManager()
            .checkAndUnlockSlots(
                player,
                data,
                normalizedJobId,
                result.getNewLevel()
            );

        String message =
            plugin.getConfigManager()
                .getMessage(
                    "levelup.message"
                )
                .replace(
                    "{job}",
                    job.getDisplayName()
                )
                .replace(
                    "{job_id}",
                    normalizedJobId
                )
                .replace(
                    "{level}",
                    String.valueOf(
                        result.getNewLevel()
                    )
                )
                .replace(
                    "{levels_gained}",
                    String.valueOf(
                        result.getLevelsGained()
                    )
                )
                .replace(
                    "{player}",
                    player.getName()
                );

        if (!message.isEmpty()) {
            player.sendMessage(
                message
            );
        }

        playSoundForKey(
            player,
            "level_up"
        );

        if (plugin.getHudManager() != null) {
            plugin.getHudManager()
                .onLevelUp(
                    player,
                    data,
                    normalizedJobId,
                    result.getNewLevel()
                );
        }
    }

    public double getPermissionMultiplier(
            Player player) {

        return getPermissionMultiplierAt(
            player,
            System.currentTimeMillis()
        );
    }

    private double getPermissionMultiplierAt(
            Player player,
            long now) {

        if (player == null) {
            return 1D;
        }

        double cached =
            permissionCache.getOrNaN(
                player.getUniqueId(),
                now
            );

        if (!Double.isNaN(cached)) {
            return cached;
        }

        double best =
            1D;

        PermissionRule[] rules =
            permissionRules;

        for (PermissionRule rule
                : rules) {

            if (rule == null
                    || !player.hasPermission(
                        rule.permission
                    )) {

                continue;
            }

            if (rule.multiplier <= 0D) {
                best = 0D;
                break;
            }

            if (rule.multiplier > best) {
                best = rule.multiplier;
            }
        }

        permissionCache.put(
            player.getUniqueId(),
            best,
            now
        );

        return best;
    }

    /**
     * La commande /kjobs event modifie directement le FileConfiguration.
     * Pour conserver ce changement à chaud sans relire le YAML à chaque XP,
     * on revalide la valeur au plus une fois par intervalle configurable.
     */
    public double getEventMultiplier() {
        return getEventMultiplierAt(
            System.currentTimeMillis()
        );
    }

    private double getEventMultiplierAt(
            long now) {

        if (now >= nextEventMultiplierRefreshAt) {

            cachedEventMultiplier =
                readEventMultiplier();

            HotPathSettings settings =
                hotPathSettings;

            long refreshMs =
                settings == null
                    ? 1000L
                    : settings.getEventMultiplierRefreshMs();

            nextEventMultiplierRefreshAt =
                safeAdd(
                    now,
                    refreshMs
                );
        }

        return cachedEventMultiplier;
    }

    public void checkDailyReset(
            PlayerData data,
            String jobId) {

        if (data == null) {
            return;
        }

        checkDailyResetAt(
            data,
            normalizeJobId(jobId),
            System.currentTimeMillis()
        );
    }

    public boolean isDailyCapReached(
            PlayerData data,
            String jobId) {

        if (data == null) {
            return false;
        }

        String normalizedJobId =
            normalizeJobId(jobId);

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    normalizedJobId
                );

        if (job == null) {
            return false;
        }

        ensureDailyCountersFresh(
            data,
            normalizedJobId,
            System.currentTimeMillis()
        );

        return getDailyAllowance(
            data,
            job
        ) <= 0;
    }

    private void ensureDailyCountersFresh(
            PlayerData data,
            String currentJobId,
            long now) {

        checkDailyResetAt(
            data,
            currentJobId,
            now
        );

        if (!globalDailyCapEnabled) {
            return;
        }

        HotPathSettings settings =
            hotPathSettings;

        long interval =
            settings == null
                ? 10000L
                : settings.getGlobalDailySweepIntervalMs();

        long lastSweep =
            data.getLastGlobalDailySweepAt();

        if (lastSweep > 0L
                && now >= lastSweep
                && now - lastSweep < interval) {

            return;
        }

        List<String> jobIds =
            plugin.getJobRegistry()
                .getExpectedJobIds();

        for (int i = 0;
                i < jobIds.size();
                i++) {

            String jobId =
                jobIds.get(i);

            if (jobId != null
                    && !jobId.equals(
                        currentJobId
                    )) {

                checkDailyResetAt(
                    data,
                    jobId,
                    now
                );
            }
        }

        data.setLastGlobalDailySweepAt(
            now
        );
    }

    private void checkDailyResetAt(
            PlayerData data,
            String jobId,
            long now) {

        if (jobId == null
                || jobId.isEmpty()) {

            return;
        }

        Long stored =
            data.getDailyXpResetTimeMap()
                .get(
                    jobId
                );

        long lastReset =
            stored == null
                ? 0L
                : stored.longValue();

        if (lastReset <= 0L
                || now < lastReset
                || now - lastReset >= DAILY_WINDOW_MS) {

            data.resetDailyXP(
                jobId,
                now
            );
        }
    }

    private int getDailyAllowance(
            PlayerData data,
            JobDefinition job) {

        long allowance =
            Integer.MAX_VALUE;

        int jobCap =
            job.getDailyXpCap();

        if (jobCap > 0) {
            long remaining =
                (long) jobCap
                    - data.getDailyXP(
                        job.getId()
                    );

            allowance =
                Math.min(
                    allowance,
                    Math.max(
                        0L,
                        remaining
                    )
                );
        }

        if (globalDailyCapEnabled
                && globalDailyCap > 0) {

            long remaining =
                (long) globalDailyCap
                    - getGlobalDailyXp(
                        data
                    );

            allowance =
                Math.min(
                    allowance,
                    Math.max(
                        0L,
                        remaining
                    )
                );
        }

        return allowance >= Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int) allowance;
    }

    private long getGlobalDailyXp(
            PlayerData data) {

        long total = 0L;

        List<String> jobIds =
            plugin.getJobRegistry()
                .getExpectedJobIds();

        for (int i = 0; i < jobIds.size(); i++) {
            String jobId = jobIds.get(i);
            if (jobId == null) {
                continue;
            }

            int value = data.getDailyXP(jobId);
            if (value <= 0) {
                continue;
            }

            total += value;
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }

        return total;
    }

    private long sanitizePlayerState(
            PlayerData data,
            JobDefinition job) {

        int storedLevel =
            data.getLevel(
                job.getId()
            );
        int storedXp =
            data.getXP(
                job.getId()
            );

        int safeLevel =
            Math.max(
                0,
                Math.min(
                    job.getMaxLevel(),
                    storedLevel
                )
            );

        int safeXp =
            Math.max(
                0,
                storedXp
            );

        if (safeLevel >= job.getMaxLevel()) {
            safeXp = 0;
        }

        if (safeLevel != storedLevel) {
            KjobLogger.warn(
                "[XP] Niveau corrigé en RAM pour "
                    + data.getUuid()
                    + "/"
                    + job.getId()
                    + " : "
                    + storedLevel
                    + " -> "
                    + safeLevel
            );
            data.setLevel(
                job.getId(),
                safeLevel
            );
        }

        if (safeXp != storedXp) {
            KjobLogger.warn(
                "[XP] XP corrigée en RAM pour "
                    + data.getUuid()
                    + "/"
                    + job.getId()
                    + " : "
                    + storedXp
                    + " -> "
                    + safeXp
            );
            data.setXP(
                job.getId(),
                safeXp
            );
        }

        return packState(
            safeLevel,
            safeXp
        );
    }

    private void ensureMaxLevelState(
            PlayerData data,
            JobDefinition job,
            int level,
            int xp) {

        if (level != job.getMaxLevel()) {
            data.setLevel(
                job.getId(),
                job.getMaxLevel()
            );
        }

        if (xp != 0) {
            data.setXP(
                job.getId(),
                0
            );
        }
    }

    private int calculateAwardedXp(
            int baseXp,
            double permissionMultiplier,
            double eventMultiplier,
            double bonusMultiplier) {

        double combinedMultiplier =
            permissionMultiplier
                * eventMultiplier
                * bonusMultiplier;

        if (!Double.isFinite(combinedMultiplier)
                || combinedMultiplier <= 0D) {

            return 0;
        }

        double calculated =
            baseXp
                * combinedMultiplier;

        if (!Double.isFinite(calculated)
                || calculated <= 0D) {

            return 0;
        }

        if (calculated >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return Math.max(
            1,
            (int) Math.floor(
                calculated
            )
        );
    }

    private PermissionRule[] loadPermissionRules() {

        ConfigurationSection section =
            plugin.getConfigManager()
                .getMainConfig()
                .getConfigurationSection(
                    "xp_multipliers.permissions"
                );

        if (section == null
                || section.getKeys(false).isEmpty()) {

            return new PermissionRule[0];
        }

        List<PermissionRule> loaded =
            new ArrayList<PermissionRule>(
                section.getKeys(false)
                    .size()
            );

        for (String permission
                : section.getKeys(false)) {

            if (permission == null
                    || permission.trim().isEmpty()) {

                continue;
            }

            double configured =
                section.getDouble(
                    permission,
                    1D
                );

            if (!Double.isFinite(configured)) {
                KjobLogger.warn(
                    "[XP] Multiplicateur invalide pour "
                        + permission
                        + " : "
                        + configured
                        + ". Valeur ignorée."
                );
                continue;
            }

            loaded.add(
                new PermissionRule(
                    permission,
                    Math.max(
                        0D,
                        configured
                    )
                )
            );
        }

        return loaded.toArray(
            new PermissionRule[
                loaded.size()
            ]
        );
    }

    private double readEventMultiplier() {
        double configured =
            plugin.getConfigManager()
                .getMainConfig()
                .getDouble(
                    "xp_multipliers.event_multiplier",
                    1D
                );

        return sanitizeMultiplier(
            configured,
            1D
        );
    }

    private double sanitizeMultiplier(
            double value,
            double fallback) {

        if (!Double.isFinite(value)) {
            return fallback;
        }

        return Math.max(
            0D,
            value
        );
    }

    private int minPositiveInt(
            int first,
            int second,
            long third) {

        long result =
            Math.min(
                Math.min(
                    Math.max(
                        0L,
                        first
                    ),
                    Math.max(
                        0L,
                        second
                    )
                ),
                Math.max(
                    0L,
                    third
                )
            );

        return result >= Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int) result;
    }

    private String normalizeJobId(
            String jobId) {

        return jobId == null
            ? ""
            : jobId.trim()
                .toLowerCase(
                    Locale.ROOT
                );
    }

    private void requirePrimaryThread(
            String operation) {

        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "XpManager."
                    + operation
                    + " doit être appelé depuis le thread principal Bukkit."
            );
        }
    }

    private void playSoundForKey(
            Player player,
            String soundKey) {

        try {
            boolean enabled =
                plugin.getConfigManager()
                    .getSoundsConfig()
                    .getBoolean(
                        soundKey + ".enabled",
                        true
                    );

            if (!enabled) {
                return;
            }

            String soundName =
                plugin.getConfigManager()
                    .getSoundsConfig()
                    .getString(
                        soundKey + ".sound",
                        "LEVEL_UP"
                    );

            float volume =
                (float) plugin.getConfigManager()
                    .getSoundsConfig()
                    .getDouble(
                        soundKey + ".volume",
                        1D
                    );

            float pitch =
                (float) plugin.getConfigManager()
                    .getSoundsConfig()
                    .getDouble(
                        soundKey + ".pitch",
                        1D
                    );

            player.playSound(
                player.getLocation(),
                org.bukkit.Sound.valueOf(
                    soundName.trim()
                        .toUpperCase(
                            Locale.ROOT
                        )
                ),
                Math.max(
                    0F,
                    volume
                ),
                Math.max(
                    0F,
                    pitch
                )
            );

        } catch (RuntimeException failure) {
            KjobLogger.warn(
                "[XP] Son invalide pour "
                    + soundKey
                    + " : "
                    + failure.getMessage()
            );
        }
    }

    private static long packState(
            int level,
            int xp) {

        return ((long) level << 32)
            | ((long) xp & 0xFFFFFFFFL);
    }

    private static int unpackLevel(
            long packed) {
        return (int) (packed >>> 32);
    }

    private static int unpackXp(
            long packed) {
        return (int) packed;
    }

    private static long safeAdd(
            long first,
            long second) {

        if (second > 0L
                && first > Long.MAX_VALUE - second) {

            return Long.MAX_VALUE;
        }

        return first + second;
    }

    private static final class PermissionRule {

        private final String permission;
        private final double multiplier;

        private PermissionRule(
                String permission,
                double multiplier) {

            this.permission = permission;
            this.multiplier = multiplier;
        }
    }
}
