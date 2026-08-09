package me.krunsh.kjobultimate.data;

/**
 * Modèle RAM de la progression d'une quête pour un joueur.
 * Les QuestData sont chargées dans PlayerData.questProgress (Map<questId, QuestData>).
 * Persistées dans la table quest_progress via DatabaseManager.
 */
public final class QuestData {

    private final String questId;
    private int     progress;
    private boolean completed;
    private boolean claimed;
    private long    completedAt;

    public QuestData(String questId) {
        this.questId     = questId;
        this.progress    = 0;
        this.completed   = false;
        this.claimed     = false;
        this.completedAt = 0;
    }

    public QuestData(String questId, int progress, boolean completed,
                     boolean claimed, long completedAt) {
        this.questId     = questId;
        this.progress    = progress;
        this.completed   = completed;
        this.claimed     = claimed;
        this.completedAt = completedAt;
    }

    // ─── Progression ────────────────────────────────────────

    /**
     * Ajoute N unités de progression.
     * @param amount Quantité à ajouter
     * @param objective Total requis pour compléter la quête
     * @return true si la quête vient d'être complétée avec cette progression
     */
    public boolean addProgress(int amount, int objective) {
        if (completed) return false;
        progress = Math.min(progress + amount, objective);
        if (progress >= objective) {
            completed    = true;
            completedAt  = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public void markClaimed() {
        this.claimed = true;
    }

    public void reset() {
        this.progress    = 0;
        this.completed   = false;
        this.claimed     = false;
        this.completedAt = 0;
    }

    // ─── Accesseurs ─────────────────────────────────────────

    public String  getQuestId()    { return questId; }
    public int     getProgress()   { return progress; }
    public boolean isCompleted()   { return completed; }
    public boolean isClaimed()     { return claimed; }
    public long    getCompletedAt(){ return completedAt; }

    public void setProgress(int progress)    { this.progress    = progress; }
    public void setCompleted(boolean v)      { this.completed   = v; }
    public void setClaimed(boolean v)        { this.claimed     = v; }
    public void setCompletedAt(long ts)      { this.completedAt = ts; }
}
