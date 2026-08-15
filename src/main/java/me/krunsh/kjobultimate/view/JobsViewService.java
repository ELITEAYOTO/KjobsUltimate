package me.krunsh.kjobultimate.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.LevelUtil;

/**
 * Source unique des snapshots Jobs V3.
 *
 * V3.9 :
 * - cache RAM par joueur ;
 * - invalidation immédiate via PlayerData.viewRevision ;
 * - TTL de sécurité pour prendre en compte les reloads de configuration qui
 *   ne modifient pas PlayerData ;
 * - aucune lecture SQL.
 */
public final class JobsViewService {

    private static final long CACHE_TTL_MS = 1000L;

    private final KjobUltimate plugin;

    private final ConcurrentMap<UUID, CacheEntry> cache =
        new ConcurrentHashMap<UUID, CacheEntry>();

    public JobsViewService(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                "KjobUltimate ne peut pas être null."
            );
        }

        this.plugin = plugin;
    }

    public PlayerJobsView getPlayer(UUID playerId) {
        if (playerId == null
                || plugin.getPlayerDataManager() == null) {

            return null;
        }

        PlayerData data =
            plugin.getPlayerDataManager().get(playerId);

        if (data == null) {
            cache.remove(playerId);
            return null;
        }

        long revision = data.getViewRevision();
        long now = System.currentTimeMillis();

        CacheEntry cached = cache.get(playerId);

        if (cached != null
                && cached.revision == revision
                && now - cached.createdAt <= CACHE_TTL_MS) {

            return cached.view;
        }

        PlayerJobsView rebuilt =
            buildPlayerView(data);

        cache.put(
            playerId,
            new CacheEntry(
                revision,
                now,
                rebuilt
            )
        );

        return rebuilt;
    }

    public PlayerJobsView getPlayer(Player player) {
        return player == null
            ? null
            : getPlayer(player.getUniqueId());
    }

    public JobView getJob(
            UUID playerId,
            String rawJobId) {

        PlayerJobsView playerView =
            getPlayer(playerId);

        return playerView == null
            ? null
            : playerView.getJob(rawJobId);
    }

    public JobView getJob(
            Player player,
            String rawJobId) {

        return player == null
            ? null
            : getJob(
                player.getUniqueId(),
                rawJobId
            );
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    public void clearCache() {
        cache.clear();
    }

    public int getCachedPlayerCount() {
        return cache.size();
    }

    private PlayerJobsView buildPlayerView(
            PlayerData data) {

        List<String> activeJobs =
            plugin.getSlotManager()
                .getActiveJobs(data);

        Set<String> activeJobIds =
            new HashSet<String>();

        for (String jobId : activeJobs) {
            if (jobId != null) {
                activeJobIds.add(
                    normalizeJobId(jobId)
                );
            }
        }

        String displayJobId =
            normalizeJobId(
                data.getDisplayJob()
            );

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
                    displayJobId
                )
            );
        }

        int maxSlots =
            Math.max(
                0,
                plugin.getConfigManager()
                    .getMaxSlots()
            );

        int unlockedSlots =
            Math.min(
                maxSlots,
                Math.max(
                    0,
                    data.getUnlockedSlots()
                )
            );

        int usedSlots =
            Math.min(
                unlockedSlots,
                activeJobIds.size()
            );

        int freeSlots =
            Math.max(
                0,
                unlockedSlots - usedSlots
            );

        int globalLevel =
            Math.max(
                0,
                plugin.getSlotManager()
                    .getGlobalLevel(data)
            );

        return new PlayerJobsView(
            data.getUuid(),
            globalLevel,
            unlockedSlots,
            usedSlots,
            freeSlots,
            maxSlots,
            displayJobId,
            displayJobName,
            jobs
        );
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
                    data.getLevel(jobId)
                )
            );

        boolean maxLevelReached =
            level >= job.getMaxLevel();

        int xp =
            maxLevelReached
                ? 0
                : LevelUtil.getCurrentLevelXp(
                    data,
                    job
                );

        int xpRequired =
            LevelUtil.getRequiredXpForNextLevel(
                data,
                job
            );

        int xpRemaining =
            maxLevelReached
                ? 0
                : Math.max(
                    0,
                    xpRequired - xp
                );

        int xpPercent =
            LevelUtil.getProgressPercentage(
                data,
                job
            );

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
                data.getDailyXP(jobId)
            );

        int dailyXpCap =
            Math.max(
                0,
                job.getDailyXpCap()
            );

        boolean dailyXpCapEnabled =
            plugin.getConfigManager()
                .isDailyCapEnabled()
                && dailyXpCap > 0;

        int dailyXpRemaining =
            dailyXpCapEnabled
                ? Math.max(
                    0,
                    dailyXpCap - dailyXp
                )
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
            cit
        );
    }

    private static String normalizeJobId(
            String value) {

        return value == null
            ? ""
            : value.trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String safe(
            String value) {

        return value == null
            ? ""
            : value;
    }

    private static final class CacheEntry {

        private final long revision;
        private final long createdAt;
        private final PlayerJobsView view;

        private CacheEntry(
                long revision,
                long createdAt,
                PlayerJobsView view) {

            this.revision = revision;
            this.createdAt = createdAt;
            this.view = view;
        }
    }
}
