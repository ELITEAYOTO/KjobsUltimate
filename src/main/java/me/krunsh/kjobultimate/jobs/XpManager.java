package me.krunsh.kjobultimate.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.config.ConfigManager;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service central de progression XP des métiers.
 *
 * Responsabilités :
 * - calculer l'XP réellement attribuée après multiplicateurs ;
 * - appliquer les plafonds quotidiens globaux et propres au métier ;
 * - gérer les passages de niveau, y compris plusieurs niveaux d'un coup ;
 * - exécuter les récompenses de chaque niveau atteint ;
 * - garantir que l'XP stockée correspond toujours au niveau courant ;
 * - empêcher tout stockage d'XP au-delà du niveau maximum.
 *
 * Toutes les méthodes qui modifient un joueur doivent être appelées depuis
 * le thread principal Bukkit.
 */
public final class XpManager {

    private static final long DAILY_WINDOW_MS = 86_400_000L;

    private final KjobUltimate plugin;

    public XpManager(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin ne peut pas être null.");
        }
        this.plugin = plugin;
    }

    /**
     * Ajoute de l'XP normale à un joueur.
     *
     * L'XP finale tient compte :
     * - du meilleur multiplicateur de permission ;
     * - du multiplicateur d'événement global ;
     * - du bonus persistant du joueur ;
     * - des plafonds quotidiens ;
     * - de l'XP restante avant le niveau maximum.
     *
     * @param player joueur connecté
     * @param data données RAM du joueur
     * @param jobId identifiant du métier
     * @param baseXp XP brute avant multiplicateurs
     * @return résultat détaillé de la progression
     */
    public LevelUpResult addXP(
            Player player,
            PlayerData data,
            String jobId,
            int baseXp) {

        requirePrimaryThread("addXP");

        if (player == null || data == null) {
            return LevelUpResult.noLevelUp(0, 0, 0);
        }

        String normalizedJobId = normalizeJobId(jobId);
        JobDefinition job = plugin.getJobRegistry().getJob(normalizedJobId);

        if (job == null) {
            KjobLogger.error("[XP] Métier inconnu lors d'un gain XP : "
                + normalizedJobId);
            return LevelUpResult.noLevelUp(0, 0, 0);
        }

        PlayerState state = sanitizePlayerState(data, job);

        if (state.level >= job.getMaxLevel()) {
            ensureMaxLevelState(data, job, state);
            return LevelUpResult.maxLevel(job.getMaxLevel());
        }

        if (baseXp <= 0) {
            return LevelUpResult.noLevelUp(
                state.level, state.xp, 0);
        }

        int calculatedXp = calculateAwardedXp(
            player, data, normalizedJobId, baseXp);

        if (calculatedXp <= 0) {
            return LevelUpResult.noLevelUp(
                state.level, state.xp, 0);
        }

        resetExpiredDailyCounters(data);
        int dailyAllowance = getDailyAllowance(data, job);

        if (dailyAllowance <= 0) {
            return LevelUpResult.noLevelUp(
                state.level, state.xp, 0);
        }

        long xpUntilMax = calculateXpUntilMax(
            job, state.level, state.xp);

        if (xpUntilMax <= 0L) {
            KjobLogger.error("[XP] Courbe invalide ou progression incohérente pour "
                + player.getName() + "/" + normalizedJobId
                + " au niveau " + state.level + ".");
            return LevelUpResult.noLevelUp(
                state.level, state.xp, 0);
        }

        int actualXp = minPositiveInt(
            calculatedXp,
            dailyAllowance,
            xpUntilMax);

        if (actualXp <= 0) {
            return LevelUpResult.noLevelUp(
                state.level, state.xp, 0);
        }

        data.addDailyXP(normalizedJobId, actualXp);

        LevelUpResult result = applyPositiveXp(
            player,
            data,
            job,
            state.level,
            state.xp,
            actualXp,
            true);

        if (plugin.getConfigManager().isDebugXp()) {
            double permissionMultiplier =
                getPermissionMultiplier(player);
            double eventMultiplier =
                getEventMultiplier();
            double bonusMultiplier =
                sanitizeMultiplier(
                    data.getBonusMultiplier(normalizedJobId),
                    1D);

            KjobLogger.info("[XP] " + player.getName()
                + " +" + actualXp + " XP " + normalizedJobId
                + " (base=" + baseXp
                + ", permission=x" + permissionMultiplier
                + ", event=x" + eventMultiplier
                + ", bonus=x" + bonusMultiplier
                + ", niveau=" + result.getNewLevel()
                + ", restant=" + result.getRemainingXP() + ")");
        }

        return result;
    }

    /**
     * Applique une quantité d'XP positive à l'état du joueur.
     *
     * Cette méthode :
     * - consomme l'XP palier par palier ;
     * - utilise getXpRequiredForNextLevel(currentLevel) ;
     * - exécute chaque récompense de niveau exactement une fois dans cette
     *   progression RAM ;
     * - supprime tout surplus une fois le niveau maximum atteint.
     */
    private LevelUpResult applyPositiveXp(
            Player player,
            PlayerData data,
            JobDefinition job,
            int startingLevel,
            int startingXp,
            int xpToAdd,
            boolean executeRewards) {

        long currentXp = (long) startingXp + xpToAdd;
        int currentLevel = startingLevel;
        int levelsGained = 0;

        while (currentLevel < job.getMaxLevel()) {
            int required =
                job.getXpRequiredForNextLevel(currentLevel);

            if (required <= 0) {
                KjobLogger.error("[XP] Palier invalide pour "
                    + job.getId() + " : niveau actuel="
                    + currentLevel + ", XP requise=" + required + ".");
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
                    player, job, currentLevel);
            }
        }

        if (currentLevel >= job.getMaxLevel()) {
            currentLevel = job.getMaxLevel();
            currentXp = 0L;
        }

        int safeRemainingXp =
            currentXp >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) Math.max(0L, currentXp);

        data.setLevel(job.getId(), currentLevel);
        data.setXP(job.getId(), safeRemainingXp);
        data.setDisplayJob(job.getId());
        plugin.notifyJobsUiChanged(player.getUniqueId(), "kjobs:xp");

        if (levelsGained > 0) {
            return LevelUpResult.leveled(
                levelsGained,
                currentLevel,
                safeRemainingXp,
                xpToAdd);
        }

        return LevelUpResult.noLevelUp(
            currentLevel,
            safeRemainingXp,
            xpToAdd);
    }

    /**
     * Ajoute ou retire de l'XP via une commande administrative.
     *
     * Une valeur positive :
     * - ignore les multiplicateurs ;
     * - ignore les plafonds quotidiens ;
     * - déclenche les récompenses des niveaux réellement atteints.
     *
     * Une valeur négative :
     * - retire uniquement l'XP du niveau courant ;
     * - ne fait jamais redescendre de niveau ;
     * - ne révoque jamais une récompense déjà distribuée.
     */
    public LevelUpResult adminAddXp(
            Player player,
            PlayerData data,
            String jobId,
            int amount) {

        requirePrimaryThread("adminAddXp");

        if (player == null || data == null) {
            return LevelUpResult.noLevelUp(0, 0, 0);
        }

        String normalizedJobId = normalizeJobId(jobId);
        JobDefinition job = plugin.getJobRegistry().getJob(
            normalizedJobId);

        if (job == null) {
            KjobLogger.error("[XP-ADMIN] Métier inconnu : "
                + normalizedJobId);
            return LevelUpResult.noLevelUp(0, 0, 0);
        }

        PlayerState state = sanitizePlayerState(data, job);

        if (amount == 0) {
            return LevelUpResult.noLevelUp(
                state.level, state.xp, 0);
        }

        if (amount < 0) {
            long reduced =
                (long) state.xp + (long) amount;
            int newXp = (int) Math.max(0L, reduced);

            data.setXP(normalizedJobId, newXp);
            plugin.notifyJobsUiChanged(player.getUniqueId(), "kjobs:admin-xp");

            return LevelUpResult.noLevelUp(
                state.level, newXp, 0);
        }

        if (state.level >= job.getMaxLevel()) {
            ensureMaxLevelState(data, job, state);
            return LevelUpResult.maxLevel(
                job.getMaxLevel());
        }

        long xpUntilMax = calculateXpUntilMax(
            job, state.level, state.xp);

        if (xpUntilMax <= 0L) {
            KjobLogger.error("[XP-ADMIN] Progression impossible pour "
                + player.getName() + "/" + normalizedJobId
                + " : courbe invalide.");
            return LevelUpResult.noLevelUp(
                state.level, state.xp, 0);
        }

        int actualXp = minPositiveInt(
            amount,
            Integer.MAX_VALUE,
            xpUntilMax);

        return applyPositiveXp(
            player,
            data,
            job,
            state.level,
            state.xp,
            actualXp,
            true);
    }

    /**
     * Exécute toutes les commandes configurées pour un niveau atteint.
     *
     * Les commandes sans préfixe sont exécutées par la console.
     * Préfixes acceptés :
     * - [console]
     * - [command]
     * - [player]
     * - [joueur]
     */
    private void applyLevelRewards(
            Player player,
            JobDefinition job,
            int reachedLevel) {

        for (String configuredCommand :
                job.getLevelRewardCommands(reachedLevel)) {

            if (configuredCommand == null
                    || configuredCommand.trim().isEmpty()) {
                KjobLogger.error("[XP-REWARD] Commande vide pour "
                    + job.getId() + " niveau " + reachedLevel + ".");
                continue;
            }

            String resolved = configuredCommand
                .replace("{player}", player.getName())
                .replace("{uuid}",
                    player.getUniqueId().toString())
                .replace("{level}",
                    String.valueOf(reachedLevel))
                .replace("{job}", job.getId())
                .trim();

            try {
                executeRewardCommand(player, resolved);
            } catch (RuntimeException failure) {
                KjobLogger.error(
                    "[XP-REWARD] Échec de la commande du métier "
                    + job.getId() + " niveau " + reachedLevel
                    + " : " + configuredCommand,
                    failure);
            }
        }
    }

    private void executeRewardCommand(
            Player player,
            String command) {

        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Commande de récompense vide.");
        }

        String trimmed = command.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.startsWith("[player]")) {
            dispatchAsPlayer(
                player,
                trimmed.substring("[player]".length()).trim());
            return;
        }

        if (lower.startsWith("[joueur]")) {
            dispatchAsPlayer(
                player,
                trimmed.substring("[joueur]".length()).trim());
            return;
        }

        if (lower.startsWith("[command]")) {
            dispatchAsConsole(
                trimmed.substring("[command]".length()).trim());
            return;
        }

        if (lower.startsWith("[console]")) {
            dispatchAsConsole(
                trimmed.substring("[console]".length()).trim());
            return;
        }

        dispatchAsConsole(trimmed);
    }

    private void dispatchAsConsole(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Commande console vide.");
        }

        boolean accepted = plugin.getServer().dispatchCommand(
            plugin.getServer().getConsoleSender(),
            command.trim());

        if (!accepted) {
            throw new IllegalStateException(
                "Commande console refusée : " + command);
        }
    }

    private void dispatchAsPlayer(
            Player player,
            String command) {

        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Commande joueur vide.");
        }

        boolean accepted =
            player.performCommand(command.trim());

        if (!accepted) {
            throw new IllegalStateException(
                "Commande joueur refusée : " + command);
        }
    }

    /**
     * Effets visuels et fonctionnels après un passage de niveau.
     *
     * Les récompenses ont déjà été exécutées dans applyPositiveXp().
     */
    public void handleLevelUp(
            Player player,
            PlayerData data,
            String jobId,
            LevelUpResult result) {

        requirePrimaryThread("handleLevelUp");

        if (player == null || data == null
                || result == null
                || !result.isLeveledUp()) {
            return;
        }

        String normalizedJobId = normalizeJobId(jobId);
        JobDefinition job = plugin.getJobRegistry().getJob(
            normalizedJobId);

        if (job == null) {
            KjobLogger.error("[XP] handleLevelUp appelé pour un métier inconnu : "
                + normalizedJobId);
            return;
        }

        plugin.getSlotManager().checkAndUnlockSlots(
            player,
            data,
            normalizedJobId,
            result.getNewLevel());

        String message = plugin.getConfigManager()
            .getMessage("levelup.message")
            .replace("{prefix}",
                plugin.getConfigManager().getPrefix())
            .replace("{job}", job.getDisplayName())
            .replace("{job_id}", normalizedJobId)
            .replace("{level}",
                String.valueOf(result.getNewLevel()))
            .replace("{levels_gained}",
                String.valueOf(result.getLevelsGained()))
            .replace("{player}", player.getName());

        if (!message.isEmpty()) {
            player.sendMessage(message);
        }

        playSoundForKey(player, "level_up");

        if (plugin.getHudManager() != null) {
            plugin.getHudManager().onLevelUp(
                player,
                data,
                normalizedJobId,
                result.getNewLevel());
        }
    }

    /**
     * Retourne le multiplicateur de permission applicable.
     *
     * Règles :
     * - sans permission correspondante : x1 ;
     * - plusieurs permissions positives : la plus élevée gagne ;
     * - une permission correspondante configurée à 0 ou moins bloque l'XP.
     */
    public double getPermissionMultiplier(Player player) {
        if (player == null) {
            return 1D;
        }

        ConfigManager config = plugin.getConfigManager();
        ConfigurationSection section =
            config.getMainConfig()
                .getConfigurationSection(
                    "xp_multipliers.permissions");

        if (section == null) {
            return 1D;
        }

        double best = 1D;

        for (String permission : section.getKeys(false)) {
            if (!player.hasPermission(permission)) {
                continue;
            }

            double configured =
                section.getDouble(permission, 1D);

            if (!Double.isFinite(configured)) {
                KjobLogger.warn("[XP] Multiplicateur invalide pour "
                    + permission + " : " + configured
                    + ". Valeur ignorée.");
                continue;
            }

            if (configured <= 0D) {
                return 0D;
            }

            if (configured > best) {
                best = configured;
            }
        }

        return best;
    }

    public double getEventMultiplier() {
        double configured = plugin.getConfigManager()
            .getMainConfig()
            .getDouble(
                "xp_multipliers.event_multiplier", 1D);

        return sanitizeMultiplier(configured, 1D);
    }

    private int calculateAwardedXp(
            Player player,
            PlayerData data,
            String jobId,
            int baseXp) {

        double permissionMultiplier =
            getPermissionMultiplier(player);
        double eventMultiplier =
            getEventMultiplier();
        double bonusMultiplier =
            sanitizeMultiplier(
                data.getBonusMultiplier(jobId),
                1D);

        double combinedMultiplier =
            permissionMultiplier
            * eventMultiplier
            * bonusMultiplier;

        if (!Double.isFinite(combinedMultiplier)
                || combinedMultiplier <= 0D) {
            return 0;
        }

        double calculated =
            baseXp * combinedMultiplier;

        if (!Double.isFinite(calculated)
                || calculated <= 0D) {
            return 0;
        }

        if (calculated >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        /*
         * Une action positive reste au minimum à 1 XP.
         * Cela évite qu'un bloc à 1 XP ne donne systématiquement 0 XP
         * pendant un événement x0.5 faute de stockage décimal.
         */
        return Math.max(
            1,
            (int) Math.floor(calculated));
    }

    /**
     * Réinitialise les compteurs quotidiens expirés de tous les métiers connus.
     *
     * Cela permet au plafond global d'ignorer les compteurs datant de plus
     * de 24 heures, même si le joueur change de métier.
     */
    private void resetExpiredDailyCounters(
            PlayerData data) {

        Set<String> jobIds = new HashSet<String>();
        jobIds.addAll(
            plugin.getJobRegistry().getExpectedJobIds());
        jobIds.addAll(
            data.getDailyXPMap().keySet());
        jobIds.addAll(
            data.getDailyXpResetTimeMap().keySet());

        for (String jobId : jobIds) {
            checkDailyReset(data, jobId);
        }
    }

    /**
     * Compatibilité avec les listeners actuels.
     *
     * Le compteur utilise une fenêtre de 24 heures par métier.
     */
    public void checkDailyReset(
            PlayerData data,
            String jobId) {

        if (data == null) {
            return;
        }

        String normalizedJobId =
            normalizeJobId(jobId);

        long now = System.currentTimeMillis();
        long lastReset =
            data.getDailyXpResetTimeMap()
                .getOrDefault(normalizedJobId, 0L);

        if (lastReset <= 0L
                || now < lastReset
                || now - lastReset >= DAILY_WINDOW_MS) {
            data.resetDailyXP(normalizedJobId);
        }
    }

    /**
     * Retourne true si le plafond global ou celui du métier est atteint.
     */
    public boolean isDailyCapReached(
            PlayerData data,
            String jobId) {

        if (data == null) {
            return false;
        }

        String normalizedJobId =
            normalizeJobId(jobId);
        JobDefinition job =
            plugin.getJobRegistry().getJob(normalizedJobId);

        if (job == null) {
            return false;
        }

        resetExpiredDailyCounters(data);
        return getDailyAllowance(data, job) <= 0;
    }

    /**
     * Calcule la quantité d'XP encore attribuable aujourd'hui.
     *
     * Le plus petit plafond restant est appliqué :
     * - daily_xp_cap du fichier du métier ;
     * - anti_abuse.daily_xp_cap.amount de config.yml.
     */
    private int getDailyAllowance(
            PlayerData data,
            JobDefinition job) {

        long allowance = Integer.MAX_VALUE;

        int jobCap = job.getDailyXpCap();
        if (jobCap > 0) {
            long remaining =
                (long) jobCap
                - data.getDailyXP(job.getId());
            allowance = Math.min(
                allowance,
                Math.max(0L, remaining));
        }

        ConfigManager config = plugin.getConfigManager();
        if (config.isDailyCapEnabled()) {
            int globalCap = config.getMainConfig()
                .getInt(
                    "anti_abuse.daily_xp_cap.amount", 0);

            if (globalCap > 0) {
                long remaining =
                    (long) globalCap
                    - getGlobalDailyXp(data);
                allowance = Math.min(
                    allowance,
                    Math.max(0L, remaining));
            }
        }

        return allowance >= Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int) allowance;
    }

    private long getGlobalDailyXp(PlayerData data) {
        long total = 0L;

        for (Integer value :
                data.getDailyXPMap().values()) {

            if (value == null || value <= 0) {
                continue;
            }

            total += value.intValue();

            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }

        return total;
    }

    /**
     * Calcule l'XP exacte restant avant le niveau maximum.
     */
    private long calculateXpUntilMax(
            JobDefinition job,
            int currentLevel,
            int currentXp) {

        if (currentLevel >= job.getMaxLevel()) {
            return 0L;
        }

        long total = 0L;

        for (int level = currentLevel;
                level < job.getMaxLevel();
                level++) {

            int required =
                job.getXpRequiredForNextLevel(level);

            if (required <= 0) {
                return 0L;
            }

            if (level == currentLevel) {
                total += Math.max(
                    0L,
                    (long) required - currentXp);
            } else {
                total += required;
            }

            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }

        return total;
    }

    /**
     * Corrige en RAM un état provenant d'une ancienne configuration invalide.
     */
    private PlayerState sanitizePlayerState(
            PlayerData data,
            JobDefinition job) {

        int storedLevel =
            data.getLevel(job.getId());
        int storedXp =
            data.getXP(job.getId());

        int safeLevel = Math.max(
            0,
            Math.min(job.getMaxLevel(), storedLevel));
        int safeXp = Math.max(0, storedXp);

        if (safeLevel >= job.getMaxLevel()) {
            safeXp = 0;
        } else {
            int required =
                job.getXpRequiredForNextLevel(safeLevel);

            if (required > 0 && safeXp >= required) {
                /*
                 * Une ancienne sauvegarde peut contenir assez d'XP pour
                 * plusieurs niveaux. On ne la normalise pas silencieusement
                 * ici : le prochain gain ou une commande admin utilisera
                 * la boucle multi-niveau et conservera la valeur.
                 */
                safeXp = storedXp;
            }
        }

        if (safeLevel != storedLevel) {
            KjobLogger.warn("[XP] Niveau corrigé en RAM pour "
                + data.getUuid() + "/" + job.getId()
                + " : " + storedLevel + " -> " + safeLevel);
            data.setLevel(job.getId(), safeLevel);
        }

        if (safeXp != storedXp) {
            KjobLogger.warn("[XP] XP corrigée en RAM pour "
                + data.getUuid() + "/" + job.getId()
                + " : " + storedXp + " -> " + safeXp);
            data.setXP(job.getId(), safeXp);
        }

        return new PlayerState(safeLevel, safeXp);
    }

    private void ensureMaxLevelState(
            PlayerData data,
            JobDefinition job,
            PlayerState state) {

        if (state.level != job.getMaxLevel()) {
            data.setLevel(
                job.getId(),
                job.getMaxLevel());
        }

        if (state.xp != 0) {
            data.setXP(job.getId(), 0);
        }
    }

    private double sanitizeMultiplier(
            double value,
            double fallback) {

        if (!Double.isFinite(value)) {
            return fallback;
        }

        return Math.max(0D, value);
    }

    private int minPositiveInt(
            int first,
            int second,
            long third) {

        long result = Math.min(
            Math.min(
                Math.max(0L, first),
                Math.max(0L, second)),
            Math.max(0L, third));

        return result >= Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int) result;
    }

    private String normalizeJobId(String jobId) {
        return jobId == null
            ? ""
            : jobId.trim().toLowerCase(Locale.ROOT);
    }

    private void requirePrimaryThread(
            String operation) {

        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "XpManager." + operation
                + " doit être appelé depuis le thread principal Bukkit.");
        }
    }

    private void playSoundForKey(
            Player player,
            String soundKey) {

        try {
            boolean enabled = plugin.getConfigManager()
                .getSoundsConfig()
                .getBoolean(
                    soundKey + ".enabled", true);

            if (!enabled) {
                return;
            }

            String soundName = plugin.getConfigManager()
                .getSoundsConfig()
                .getString(
                    soundKey + ".sound", "LEVEL_UP");

            float volume = (float) plugin.getConfigManager()
                .getSoundsConfig()
                .getDouble(
                    soundKey + ".volume", 1D);

            float pitch = (float) plugin.getConfigManager()
                .getSoundsConfig()
                .getDouble(
                    soundKey + ".pitch", 1D);

            player.playSound(
                player.getLocation(),
                org.bukkit.Sound.valueOf(
                    soundName.trim()
                        .toUpperCase(Locale.ROOT)),
                Math.max(0F, volume),
                Math.max(0F, pitch));

        } catch (RuntimeException failure) {
            KjobLogger.warn("[XP] Son invalide pour "
                + soundKey + " : " + failure.getMessage());
        }
    }

    private static final class PlayerState {

        private final int level;
        private final int xp;

        private PlayerState(int level, int xp) {
            this.level = level;
            this.xp = xp;
        }
    }
}
