package me.krunsh.kjobultimate.persistence;

import java.util.UUID;

/**
 * Snapshot immuable d'une progression de quête prête à être persistée.
 *
 * Créé seulement au moment d'un flush : les progressions répétées d'une même
 * quête sont fusionnées en RAM sans allouer un snapshot à chaque bloc cassé.
 */
public final class QuestWriteSnapshot {

    private final UUID playerId;
    private final String questId;
    private final int progress;
    private final boolean completed;
    private final boolean claimed;
    private final long completedAt;

    public QuestWriteSnapshot(
            UUID playerId,
            String questId,
            int progress,
            boolean completed,
            boolean claimed,
            long completedAt) {

        if (playerId == null) {
            throw new IllegalArgumentException("playerId ne peut pas être null.");
        }
        if (questId == null || questId.trim().isEmpty()) {
            throw new IllegalArgumentException("questId ne peut pas être vide.");
        }

        this.playerId = playerId;
        this.questId = questId;
        this.progress = Math.max(0, progress);
        this.completed = completed;
        this.claimed = claimed;
        this.completedAt = Math.max(0L, completedAt);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getQuestId() {
        return questId;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public long getCompletedAt() {
        return completedAt;
    }
}
