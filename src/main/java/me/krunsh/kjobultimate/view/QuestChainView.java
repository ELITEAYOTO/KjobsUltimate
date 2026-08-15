package me.krunsh.kjobultimate.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Snapshot immutable d'une chaîne de quêtes pour un joueur.
 *
 * Il regroupe les étapes déjà construites sous forme de QuestView et fournit
 * les agrégats nécessaires aux GUI et placeholders sans recalcul côté rendu.
 */
public final class QuestChainView {

    private final String id;
    private final String displayName;
    private final String jobId;

    private final List<QuestView> stages;
    private final QuestView activeQuest;

    private final int stageTotal;
    private final int currentStage;

    private final int completedStages;
    private final int claimedStages;
    private final int claimableStages;
    private final int remainingStages;
    private final int unclaimedStages;

    private final long progress;
    private final long amount;
    private final int percent;

    private final boolean complete;
    private final boolean fullyClaimed;
    private final boolean jobActive;

    private final String state;
    private final String stateName;
    private final String stateColor;

    QuestChainView(
            String id,
            String displayName,
            String jobId,
            List<QuestView> stages,
            QuestView activeQuest,
            int completedStages,
            int claimedStages,
            int claimableStages,
            long progress,
            long amount,
            boolean jobActive,
            String state,
            String stateName,
            String stateColor) {

        this.id = safe(id);
        this.displayName = safe(displayName);
        this.jobId = safe(jobId);

        List<QuestView> safeStages = stages == null
                ? Collections.<QuestView>emptyList()
                : new ArrayList<QuestView>(stages);

        this.stages = Collections.unmodifiableList(safeStages);
        this.activeQuest = activeQuest;

        this.stageTotal = safeStages.size();

        this.completedStages = clamp(completedStages, 0, stageTotal);
        this.claimedStages = clamp(claimedStages, 0, stageTotal);
        this.claimableStages = clamp(claimableStages, 0, stageTotal);

        this.remainingStages = Math.max(0, stageTotal - this.completedStages);
        this.unclaimedStages = Math.max(0, stageTotal - this.claimedStages);

        this.progress = Math.max(0L, progress);
        this.amount = Math.max(0L, amount);
        this.percent = calculatePercent(this.progress, this.amount);

        this.complete = stageTotal > 0 && this.completedStages >= stageTotal;
        this.fullyClaimed = stageTotal > 0 && this.claimedStages >= stageTotal;
        this.jobActive = jobActive;

        if (activeQuest != null) {
            this.currentStage = activeQuest.getStage();
        } else if (complete) {
            this.currentStage = stageTotal;
        } else {
            this.currentStage = 0;
        }

        this.state = safe(state);
        this.stateName = safe(stateName);
        this.stateColor = safe(stateColor);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int calculatePercent(long current, long maximum) {
        if (maximum <= 0L) {
            return 0;
        }

        double ratio = (double) current / (double) maximum;

        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return 0;
        }

        return Math.max(
            0,
            Math.min(
                100,
                (int) Math.floor(ratio * 100.0D)
            )
        );
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

    public List<QuestView> getStages() {
        return stages;
    }

    public QuestView getActiveQuest() {
        return activeQuest;
    }

    public boolean hasActiveQuest() {
        return activeQuest != null;
    }

    public int getStageTotal() {
        return stageTotal;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public int getCompletedStages() {
        return completedStages;
    }

    public int getClaimedStages() {
        return claimedStages;
    }

    public int getClaimableStages() {
        return claimableStages;
    }

    /**
     * Nombre d'étapes qui ne sont pas encore terminées.
     */
    public int getRemainingStages() {
        return remainingStages;
    }

    /**
     * Nombre d'étapes dont la récompense n'est pas encore claim.
     */
    public int getUnclaimedStages() {
        return unclaimedStages;
    }

    public long getProgress() {
        return progress;
    }

    public long getAmount() {
        return amount;
    }

    public int getPercent() {
        return percent;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isFullyClaimed() {
        return fullyClaimed;
    }

    public boolean isJobActive() {
        return jobActive;
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

    public QuestView getStage(int stage) {
        if (stage <= 0) {
            return null;
        }

        for (QuestView quest : stages) {
            if (quest != null && quest.getStage() == stage) {
                return quest;
            }
        }

        return null;
    }

    public QuestView getQuest(String rawQuestId) {
        if (rawQuestId == null) {
            return null;
        }

        String questId = rawQuestId.trim().toLowerCase(Locale.ROOT);

        if (questId.isEmpty()) {
            return null;
        }

        for (QuestView quest : stages) {
            if (quest != null
                    && questId.equals(quest.getId().toLowerCase(Locale.ROOT))) {

                return quest;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "QuestChainView{"
                + "id='" + id + '\''
                + ", jobId='" + jobId + '\''
                + ", currentStage=" + currentStage
                + ", stageTotal=" + stageTotal
                + ", completedStages=" + completedStages
                + ", claimedStages=" + claimedStages
                + ", percent=" + percent
                + ", state='" + state + '\''
                + '}';
    }
}
