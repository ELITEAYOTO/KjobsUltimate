package me.krunsh.kjobultimate.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modèle RAM d'un joueur connecté.
 * Toutes les opérations XP/level passent par cette classe en mémoire.
 * Les données sont persistées en SQLite via DatabaseManager.
 *
 * Les cooldowns (block position, pvp target) sont volontairement en RAM uniquement —
 * ils se réinitialisent au redémarrage du serveur, comportement intentionnel.
 */
public final class PlayerData {

    // ─── Identité ───────────────────────────────────────────
    private final UUID uuid;
    private long firstJoin;
    private long lastSeen;

    // ─── Jobs : XP et niveaux ────────────────────────────────
    /** XP actuel par jobId */
    private final Map<String, Integer> jobXP     = new HashMap<>();
    /** Niveau actuel par jobId */
    private final Map<String, Integer> jobLevels = new HashMap<>();
    /** XP gagné aujourd'hui par jobId (reset quotidien) */
    private final Map<String, Integer> dailyXP   = new HashMap<>();
    /** Timestamp du dernier reset quotidien par jobId */
    private final Map<String, Long> dailyXpResetTime = new HashMap<>();

    // ─── Slots de jobs ──────────────────────────────────────
    private int unlockedSlots = 1;
    /** Slot (1–5) → jobId actif dans ce slot */
    private final Map<Integer, String> slotJobs = new HashMap<>();

    // ─── HUD ────────────────────────────────────────────────
    /** Job dont la bossbar est actuellement affichée (dernier job ayant eu XP) */
    private String displayJob;
    /** Timestamp du dernier gain XP (pour le timer de disparition bossbar) */
    private long lastXpTimestamp;
    private boolean hudEnabled = true;
    private boolean bossBarHudEnabled = true;
    private boolean actionBarHudEnabled = true;

    /** Timestamp du dernier leave/changement de job soumis au cooldown. */
    private long lastJobChangeAt;

    // ─── Cooldowns RAM (non persistés) ──────────────────────
    /** "world:x:y:z" → timestamp de fin de cooldown */
    private final Map<String, Long> blockCooldowns    = new HashMap<>();
    /** UUID cible → timestamp de fin de cooldown PvP */
    private final Map<UUID, Long>   pvpTargetCooldowns = new HashMap<>();

    // ─── Quêtes ──────────────────────────────────────────────
    /** questId → progression en RAM (chargé depuis quest_progress en DB) */
    private final Map<String, QuestData> questProgress = new HashMap<>();

    // ─── Bonus Multipliers ───────────────────────────────────────
    /** Cache RAM des multiplicateurs bonus (jobId ou "all" → valeur). Chargé depuis DB au join. */
    private final Map<String, Double> bonusMultipliers = new HashMap<>();

    // ─── Dirty flag ─────────────────────────────────────────
    /** true = données modifiées depuis la dernière sauvegarde */
    private volatile boolean dirty = false;

    // ────────────────────────────────────────────────────────

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    // ─── XP + Level ─────────────────────────────────────────

    public int getXP(String jobId) {
        return jobXP.getOrDefault(jobId, 0);
    }

    public void setXP(String jobId, int xp) {
        jobXP.put(jobId, xp);
        dirty = true;
    }

    public int getLevel(String jobId) {
        return jobLevels.getOrDefault(jobId, 0);
    }

    public void setLevel(String jobId, int level) {
        jobLevels.put(jobId, level);
        dirty = true;
    }

    public boolean hasJob(String jobId) {
        return jobLevels.containsKey(jobId);
    }

    // ─── Daily XP ───────────────────────────────────────────

    public int getDailyXP(String jobId) {
        return dailyXP.getOrDefault(jobId, 0);
    }

    public void addDailyXP(String jobId, int amount) {
        dailyXP.merge(jobId, amount, Integer::sum);
        dirty = true;
    }

    public void resetDailyXP(String jobId) {
        dailyXP.put(jobId, 0);
        dailyXpResetTime.put(jobId, System.currentTimeMillis());
        dirty = true;
    }

    // ─── Slots ──────────────────────────────────────────────

    public int getUnlockedSlots() {
        return unlockedSlots;
    }

    public void setUnlockedSlots(int count) {
        this.unlockedSlots = count;
        dirty = true;
    }

    /** Retourne le jobId dans ce slot, ou null si slot vide. */
    public String getJobInSlot(int slot) {
        return slotJobs.get(slot);
    }

    public void setJobInSlot(int slot, String jobId) {
        if (jobId == null) {
            slotJobs.remove(slot);
        } else {
            slotJobs.put(slot, jobId);
        }
        dirty = true;
    }

    /** Retourne le numéro de slot du job donné, ou -1 s'il n'est pas actif. */
    public int getSlotOfJob(String jobId) {
        for (Map.Entry<Integer, String> entry : slotJobs.entrySet()) {
            if (jobId.equals(entry.getValue())) return entry.getKey();
        }
        return -1;
    }

    public Map<Integer, String> getSlotJobs() {
        return slotJobs;
    }

    // ─── HUD ────────────────────────────────────────────────

    public String getDisplayJob() {
        return displayJob;
    }

    public void setDisplayJob(String jobId) {
        this.displayJob = jobId;
        this.lastXpTimestamp = System.currentTimeMillis();
        dirty = true;
    }

    public long getLastXpTimestamp() {
        return lastXpTimestamp;
    }

    public long getLastJobChangeAt() {
        return lastJobChangeAt;
    }

    public void setLastJobChangeAt(long lastJobChangeAt) {
        this.lastJobChangeAt = lastJobChangeAt;
        dirty = true;
    }

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    public void setHudEnabled(boolean enabled) {
        this.hudEnabled = enabled;
        dirty = true;
    }

    public boolean isBossBarHudEnabled() {
        return bossBarHudEnabled;
    }

    public void setBossBarHudEnabled(boolean enabled) {
        this.bossBarHudEnabled = enabled;
        dirty = true;
    }

    public boolean isActionBarHudEnabled() {
        return actionBarHudEnabled;
    }

    public void setActionBarHudEnabled(boolean enabled) {
        this.actionBarHudEnabled = enabled;
        dirty = true;
    }

    // ─── Cooldowns RAM ──────────────────────────────────────

    public boolean isBlockOnCooldown(String key) {
        Long end = blockCooldowns.get(key);
        return end != null && System.currentTimeMillis() < end;
    }

    public void setBlockCooldown(String key, long durationMs) {
        blockCooldowns.put(key, System.currentTimeMillis() + durationMs);
    }

    public boolean isPvpTargetOnCooldown(UUID target) {
        Long end = pvpTargetCooldowns.get(target);
        return end != null && System.currentTimeMillis() < end;
    }

    public void setPvpTargetCooldown(UUID target, long durationMs) {
        pvpTargetCooldowns.put(target, System.currentTimeMillis() + durationMs);
    }

    // ─── Identité + persistance ──────────────────────────────

    public UUID getUuid()             { return uuid; }
    public long getFirstJoin()        { return firstJoin; }
    public void setFirstJoin(long t)  { this.firstJoin = t; dirty = true; }
    public long getLastSeen()         { return lastSeen; }
    public void setLastSeen(long t)   { this.lastSeen = t; dirty = true; }

    public Map<String, Integer> getJobXP()     { return jobXP; }
    public Map<String, Integer> getJobLevels() { return jobLevels; }
    public Map<String, Integer> getDailyXPMap(){ return dailyXP; }
    public Map<String, Long> getDailyXpResetTimeMap() { return dailyXpResetTime; }
    public Map<String, QuestData> getQuestProgress() { return questProgress; }

    // ─── Bonus Multipliers ───────────────────────────────────────────────────
    /** Retourne le multiplicateur de bonus pour ce job (prend le max entre jobId et "all"). */
    public double getBonusMultiplier(String jobId) {
        double specific = bonusMultipliers.getOrDefault(jobId, 1.0);
        double global   = bonusMultipliers.getOrDefault("all", 1.0);
        return Math.max(specific, global);
    }

    public void setBonusMultiplier(String jobId, double multiplier) {
        bonusMultipliers.put(jobId, multiplier);
        dirty = true;
    }

    public Map<String, Double> getBonusMultipliers() { return bonusMultipliers; }

    public boolean isDirty()          { return dirty; }
    public void markClean()           { dirty = false; }
    public void markDirty()           { dirty = true; }
}
