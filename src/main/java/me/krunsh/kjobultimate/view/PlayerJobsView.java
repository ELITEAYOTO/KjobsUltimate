package me.krunsh.kjobultimate.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Snapshot immutable de l'état global des métiers d'un joueur.
 *
 * Ce snapshot regroupe les informations communes nécessaires aux interfaces
 * sans exposer directement PlayerData.
 */
public final class PlayerJobsView {

    private final UUID playerId;

    private final int globalLevel;

    private final int unlockedSlots;
    private final int usedSlots;
    private final int freeSlots;
    private final int maxSlots;

    private final String displayJobId;
    private final String displayJobName;

    private final List<JobView> jobs;

    PlayerJobsView(
            UUID playerId,
            int globalLevel,
            int unlockedSlots,
            int usedSlots,
            int freeSlots,
            int maxSlots,
            String displayJobId,
            String displayJobName,
            List<JobView> jobs) {

        if (playerId == null) {
            throw new IllegalArgumentException("playerId ne peut pas être null.");
        }

        this.playerId = playerId;

        this.globalLevel = Math.max(0, globalLevel);

        this.maxSlots = Math.max(0, maxSlots);
        this.unlockedSlots = clamp(unlockedSlots, 0, this.maxSlots);
        this.usedSlots = clamp(usedSlots, 0, this.unlockedSlots);
        this.freeSlots = clamp(freeSlots, 0, this.unlockedSlots);

        this.displayJobId = safe(displayJobId);
        this.displayJobName = safe(displayJobName);

        List<JobView> safeJobs = jobs == null
                ? Collections.<JobView>emptyList()
                : new ArrayList<JobView>(jobs);

        this.jobs = Collections.unmodifiableList(safeJobs);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getGlobalLevel() {
        return globalLevel;
    }

    public int getUnlockedSlots() {
        return unlockedSlots;
    }

    public int getUsedSlots() {
        return usedSlots;
    }

    public int getFreeSlots() {
        return freeSlots;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public String getDisplayJobId() {
        return displayJobId;
    }

    public String getDisplayJobName() {
        return displayJobName;
    }

    public boolean hasDisplayJob() {
        return !displayJobId.isEmpty();
    }

    public List<JobView> getJobs() {
        return jobs;
    }

    public int getJobCount() {
        return jobs.size();
    }

    public int getActiveJobCount() {
        int count = 0;

        for (JobView job : jobs) {
            if (job != null && job.isActive()) {
                count++;
            }
        }

        return count;
    }

    /**
     * Recherche un métier dans le snapshot.
     *
     * Il n'y a actuellement que quelques métiers Volkaria, donc une recherche
     * linéaire évite de maintenir une seconde collection inutile.
     *
     * @return JobView correspondant, ou null.
     */
    public JobView getJob(String rawJobId) {
        if (rawJobId == null) {
            return null;
        }

        String jobId = rawJobId.trim().toLowerCase(Locale.ROOT);

        if (jobId.isEmpty()) {
            return null;
        }

        for (JobView job : jobs) {
            if (job != null && jobId.equals(job.getId())) {
                return job;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "PlayerJobsView{"
                + "playerId=" + playerId
                + ", globalLevel=" + globalLevel
                + ", unlockedSlots=" + unlockedSlots
                + ", usedSlots=" + usedSlots
                + ", freeSlots=" + freeSlots
                + ", maxSlots=" + maxSlots
                + ", displayJobId='" + displayJobId + '\''
                + ", jobs=" + jobs.size()
                + '}';
    }
}