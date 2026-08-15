package me.krunsh.kjobultimate.view;

/**
 * Snapshot immutable d'une quête pour un joueur.
 *
 * Cette classe ne contient aucune logique Bukkit, SQL ou de progression.
 * Elle expose uniquement un état déjà calculé afin d'être consommé par :
 *
 * - PlaceholderAPI
 * - Kgui
 * - HUD / notifications
 * - future API publique Kjobs
 * - outils de debug
 */
public final class QuestView {

    private final String id;
    private final String displayName;
    private final String jobId;

    private final String type;
    private final String target;

    private final int progress;
    private final int amount;
    private final int remaining;
    private final int percent;

    private final int minLevel;
    private final int rewardXp;

    private final String chainId;
    private final int stage;
    private final int stageTotal;

    private final String state;
    private final String stateName;
    private final String stateColor;

    private final boolean completed;
    private final boolean claimed;
    private final boolean claimable;
    private final boolean active;
    private final boolean locked;
    private final boolean jobActive;

    private final long completedAt;

    QuestView(
            String id,
            String displayName,
            String jobId,
            String type,
            String target,
            int progress,
            int amount,
            int remaining,
            int percent,
            int minLevel,
            int rewardXp,
            String chainId,
            int stage,
            int stageTotal,
            String state,
            String stateName,
            String stateColor,
            boolean completed,
            boolean claimed,
            boolean claimable,
            boolean active,
            boolean locked,
            boolean jobActive,
            long completedAt) {

        this.id = safe(id);
        this.displayName = safe(displayName);
        this.jobId = safe(jobId);

        this.type = safe(type);
        this.target = safe(target);

        this.progress = Math.max(0, progress);
        this.amount = Math.max(1, amount);
        this.remaining = Math.max(0, remaining);
        this.percent = clampPercent(percent);

        this.minLevel = Math.max(0, minLevel);
        this.rewardXp = Math.max(0, rewardXp);

        this.chainId = safe(chainId);
        this.stage = Math.max(1, stage);
        this.stageTotal = Math.max(1, stageTotal);

        this.state = safe(state);
        this.stateName = safe(stateName);
        this.stateColor = safe(stateColor);

        this.completed = completed;
        this.claimed = claimed;
        this.claimable = claimable;
        this.active = active;
        this.locked = locked;
        this.jobActive = jobActive;

        this.completedAt = Math.max(0L, completedAt);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getJobId() {
        return jobId;
    }

    public String getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public int getProgress() {
        return progress;
    }

    public int getAmount() {
        return amount;
    }

    public int getRemaining() {
        return remaining;
    }

    public int getPercent() {
        return percent;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getRewardXp() {
        return rewardXp;
    }

    public String getChainId() {
        return chainId;
    }

    public int getStage() {
        return stage;
    }

    public int getStageTotal() {
        return stageTotal;
    }

    public String getState() {
        return state;
    }

    public String getStateName() {
        return stateName;
    }

    public String getStateColor() {
        return stateColor;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public boolean isClaimable() {
        return claimable;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isJobActive() {
        return jobActive;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    @Override
    public String toString() {
        return "QuestView{"
                + "id='" + id + '\''
                + ", jobId='" + jobId + '\''
                + ", progress=" + progress
                + ", amount=" + amount
                + ", percent=" + percent
                + ", chainId='" + chainId + '\''
                + ", stage=" + stage
                + ", stageTotal=" + stageTotal
                + ", state='" + state + '\''
                + '}';
    }
}
