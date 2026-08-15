package me.krunsh.kjobultimate.view;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.LevelUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Source unique de construction des vues Jobs V3.
 *
 * IMPORTANT :
 *
 * - aucune lecture SQL ;
 * - aucune écriture PlayerData ;
 * - aucun cache dans cette première version ;
 * - aucune logique d'affichage spécifique à Kgui/PAPI/HUD ;
 * - toutes les données sont construites depuis l'état RAM déjà chargé.
 *
 * Les futures couches de présentation consommeront ce service au lieu de
 * recalculer les mêmes informations indépendamment.
 */
public final class JobsViewService {

    private final KjobUltimate plugin;

    public JobsViewService(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "KjobUltimate ne peut pas être null.");
        }

        this.plugin = plugin;
    }

    /**
     * Construit le snapshot d'un joueur depuis son UUID.
     *
     * @return snapshot immutable, ou null si les données joueur ne sont pas
     * encore chargées en RAM.
     */
    public PlayerJobsView getPlayer(UUID playerId) {
        if (playerId == null
                || plugin.getPlayerDataManager() == null) {
            return null;
        }

        PlayerData data =
                plugin.getPlayerDataManager().get(playerId);

        return data == null ? null : buildPlayerView(data);
    }

    /**
     * Variante pratique pour Bukkit Player.
     */
    public PlayerJobsView getPlayer(Player player) {
        return player == null
                ? null
                : getPlayer(player.getUniqueId());
    }

    /**
     * Retourne uniquement la vue d'un métier.
     *
     * @return vue du métier ou null si joueur/job indisponible.
     */
    public JobView getJob(UUID playerId, String rawJobId) {
        PlayerJobsView playerView = getPlayer(playerId);

        return playerView == null
                ? null
                : playerView.getJob(rawJobId);
    }

    public JobView getJob(Player player, String rawJobId) {
        return player == null
                ? null
                : getJob(player.getUniqueId(), rawJobId);
    }

    /**
     * Construit le snapshot global à partir de PlayerData déjà chargé.
     */
    private PlayerJobsView buildPlayerView(PlayerData data) {

        List<String> activeJobs =
                plugin.getSlotManager().getActiveJobs(data);

        Set<String> activeJobIds =
                new HashSet<String>();

        for (String jobId : activeJobs) {
            if (jobId != null) {
                activeJobIds.add(
                        normalizeJobId(jobId));
            }
        }

        String displayJobId =
                normalizeJobId(data.getDisplayJob());

        JobDefinition displayJob =
                displayJobId.isEmpty()
                        ? null
                        : plugin.getJobRegistry()
                            .getJob(displayJobId);

        String displayJobName =
                displayJob == null
                        ? ""
                        : displayJob.getDisplayName();

        List<JobView> jobs =
                new ArrayList<JobView>();

        for (JobDefinition definition
                : plugin.getJobRegistry().getAllJobs()) {

            if (definition == null) {
                continue;
            }

            jobs.add(
                    buildJobView(
                            data,
                            definition,
                            activeJobIds,
                            displayJobId));
        }

        int maxSlots =
                Math.max(
                        0,
                        plugin.getConfigManager()
                            .getMaxSlots());

        int unlockedSlots =
                Math.min(
                        maxSlots,
                        Math.max(
                                0,
                                data.getUnlockedSlots()));

        int usedSlots =
                Math.min(
                        unlockedSlots,
                        activeJobIds.size());

        int freeSlots =
                Math.max(
                        0,
                        unlockedSlots - usedSlots);

        int globalLevel =
                Math.max(
                        0,
                        plugin.getSlotManager()
                            .getGlobalLevel(data));

        return new PlayerJobsView(
                data.getUuid(),
                globalLevel,
                unlockedSlots,
                usedSlots,
                freeSlots,
                maxSlots,
                displayJobId,
                displayJobName,
                jobs);
    }

    private JobView buildJobView(
            PlayerData data,
            JobDefinition job,
            Set<String> activeJobIds,
            String displayJobId) {

        String jobId =
                normalizeJobId(job.getId());

        int level =
                Math.max(
                        0,
                        Math.min(
                                job.getMaxLevel(),
                                data.getLevel(jobId)));

        boolean maxLevelReached =
                level >= job.getMaxLevel();

        int xp =
                maxLevelReached
                        ? 0
                        : LevelUtil.getCurrentLevelXp(
                            data,
                            job);

        int xpRequired =
                LevelUtil.getRequiredXpForNextLevel(
                        data,
                        job);

        int xpRemaining =
                maxLevelReached
                        ? 0
                        : Math.max(
                            0,
                            xpRequired - xp);

        int xpPercent =
                LevelUtil.getProgressPercentage(
                        data,
                        job);

        boolean active =
                activeJobIds.contains(jobId);

        boolean favorite =
                !displayJobId.isEmpty()
                        && displayJobId.equals(jobId);

        int slot =
                active
                        ? data.getSlotOfJob(jobId)
                        : -1;

        int dailyXp =
                Math.max(
                        0,
                        data.getDailyXP(jobId));

        int dailyXpCap =
                Math.max(
                        0,
                        job.getDailyXpCap());

        boolean dailyXpCapEnabled =
                plugin.getConfigManager()
                    .isDailyCapEnabled()
                    && dailyXpCap > 0;

        int dailyXpRemaining =
                dailyXpCapEnabled
                        ? Math.max(
                            0,
                            dailyXpCap - dailyXp)
                        : 0;

        JobDefinition.JobIcon icon =
                job.getIcon();

        String iconMaterial =
                icon == null
                        ? ""
                        : safe(icon.getMaterial());

        short iconData =
                icon == null
                        ? (short) 0
                        : icon.getData();

        String cit =
                icon == null
                        ? ""
                        : safe(icon.getCit());

        return new JobView(
                jobId,
                job.getDisplayName(),
                level,
                job.getMaxLevel(),
                xp,
                xpRequired,
                xpRemaining,
                xpPercent,
                maxLevelReached,
                active,
                favorite,
                slot,
                dailyXp,
                dailyXpCap,
                dailyXpRemaining,
                dailyXpCapEnabled,
                iconMaterial,
                iconData,
                cit);
    }

    private static String normalizeJobId(String value) {
        return value == null
                ? ""
                : value.trim()
                    .toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}