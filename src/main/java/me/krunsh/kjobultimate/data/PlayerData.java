package me.krunsh.kjobultimate.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modèle RAM d'un joueur connecté.
 *
 * V3.9 :
 * - les données métier restent la source de vérité en RAM ;
 * - viewRevision augmente lorsqu'une donnée pouvant modifier une View change ;
 * - les services de View peuvent ainsi réutiliser un snapshot tant que cette
 *   révision reste identique.
 */
public final class PlayerData {

    // ─── Identité ───────────────────────────────────────────
    private final UUID uuid;
    private long firstJoin;
    private long lastSeen;

    // ─── Jobs : XP et niveaux ───────────────────────────────
    private final Map<String, Integer> jobXP = new HashMap<String, Integer>();
    private final Map<String, Integer> jobLevels = new HashMap<String, Integer>();
    private final Map<String, Integer> dailyXP = new HashMap<String, Integer>();
    private final Map<String, Long> dailyXpResetTime = new HashMap<String, Long>();

    // ─── Slots de jobs ──────────────────────────────────────
    private int unlockedSlots = 1;
    private final Map<Integer, String> slotJobs = new HashMap<Integer, String>();

    // ─── HUD ────────────────────────────────────────────────
    private String displayJob;
    private long lastXpTimestamp;
    private boolean hudEnabled = true;
    private boolean bossBarHudEnabled = true;
    private boolean actionBarHudEnabled = true;

    private long lastJobChangeAt;

    // ─── Cooldowns RAM ──────────────────────────────────────
    private final Map<String, Long> blockCooldowns = new HashMap<String, Long>();
    private final Map<UUID, Long> pvpTargetCooldowns = new HashMap<UUID, Long>();

    // ─── Quêtes ─────────────────────────────────────────────
    private final Map<String, QuestData> questProgress = new HashMap<String, QuestData>();

    // ─── Bonus multipliers ──────────────────────────────────
    private final Map<String, Double> bonusMultipliers = new HashMap<String, Double>();

    // ─── Persistance / View revision ────────────────────────
    private volatile boolean dirty = false;

    /**
     * Révision monotone de l'état visible par JobsViewService /
     * QuestViewService.
     *
     * Elle n'est pas persistée : elle sert uniquement au cache RAM.
     */
    private volatile long viewRevision = 0L;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    // ─── XP + Level ─────────────────────────────────────────

    public int getXP(String jobId) {
        Integer value = jobXP.get(jobId);
        return value == null ? 0 : value.intValue();
    }

    public void setXP(String jobId, int xp) {
        jobXP.put(jobId, Integer.valueOf(xp));
        markViewDirty();
    }

    public int getLevel(String jobId) {
        Integer value = jobLevels.get(jobId);
        return value == null ? 0 : value.intValue();
    }

    public void setLevel(String jobId, int level) {
        jobLevels.put(jobId, Integer.valueOf(level));
        markViewDirty();
    }

    public boolean hasJob(String jobId) {
        return jobLevels.containsKey(jobId);
    }

    // ─── Daily XP ───────────────────────────────────────────

    public int getDailyXP(String jobId) {
        Integer value = dailyXP.get(jobId);
        return value == null ? 0 : value.intValue();
    }

    public void addDailyXP(String jobId, int amount) {
        Integer current = dailyXP.get(jobId);
        dailyXP.put(
            jobId,
            Integer.valueOf(
                (current == null ? 0 : current.intValue()) + amount
            )
        );
        markViewDirty();
    }

    public void resetDailyXP(String jobId) {
        dailyXP.put(jobId, Integer.valueOf(0));
        dailyXpResetTime.put(
            jobId,
            Long.valueOf(System.currentTimeMillis())
        );
        markViewDirty();
    }

    // ─── Slots ──────────────────────────────────────────────

    public int getUnlockedSlots() {
        return unlockedSlots;
    }

    public void setUnlockedSlots(int count) {
        this.unlockedSlots = count;
        markViewDirty();
    }

    public String getJobInSlot(int slot) {
        return slotJobs.get(Integer.valueOf(slot));
    }

    public void setJobInSlot(int slot, String jobId) {
        Integer key = Integer.valueOf(slot);

        if (jobId == null) {
            slotJobs.remove(key);
        } else {
            slotJobs.put(key, jobId);
        }

        markViewDirty();
    }

    public int getSlotOfJob(String jobId) {
        for (Map.Entry<Integer, String> entry : slotJobs.entrySet()) {
            if (jobId.equals(entry.getValue())) {
                return entry.getKey().intValue();
            }
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
        markViewDirty();
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
        return end != null && System.currentTimeMillis() < end.longValue();
    }

    public void setBlockCooldown(String key, long durationMs) {
        blockCooldowns.put(
            key,
            Long.valueOf(System.currentTimeMillis() + durationMs)
        );
    }

    public boolean isPvpTargetOnCooldown(UUID target) {
        Long end = pvpTargetCooldowns.get(target);
        return end != null && System.currentTimeMillis() < end.longValue();
    }

    public void setPvpTargetCooldown(UUID target, long durationMs) {
        pvpTargetCooldowns.put(
            target,
            Long.valueOf(System.currentTimeMillis() + durationMs)
        );
    }

    // ─── Identité + persistance ─────────────────────────────

    public UUID getUuid() {
        return uuid;
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public void setFirstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
        dirty = true;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        dirty = true;
    }

    public Map<String, Integer> getJobXP() {
        return jobXP;
    }

    public Map<String, Integer> getJobLevels() {
        return jobLevels;
    }

    public Map<String, Integer> getDailyXPMap() {
        return dailyXP;
    }

    public Map<String, Long> getDailyXpResetTimeMap() {
        return dailyXpResetTime;
    }

    public Map<String, QuestData> getQuestProgress() {
        return questProgress;
    }

    // ─── Bonus Multipliers ──────────────────────────────────

    public double getBonusMultiplier(String jobId) {
        Double specificValue = bonusMultipliers.get(jobId);
        Double globalValue = bonusMultipliers.get("all");

        double specific =
            specificValue == null ? 1.0D : specificValue.doubleValue();

        double global =
            globalValue == null ? 1.0D : globalValue.doubleValue();

        return Math.max(specific, global);
    }

    public void setBonusMultiplier(String jobId, double multiplier) {
        bonusMultipliers.put(
            jobId,
            Double.valueOf(multiplier)
        );
        dirty = true;
    }

    public Map<String, Double> getBonusMultipliers() {
        return bonusMultipliers;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    /**
     * Utilisé notamment quand une structure mutable exposée par PlayerData
     * (ex: questProgress) a été modifiée directement.
     *
     * Comme ce type de modification peut affecter les Views, on incrémente
     * aussi viewRevision.
     */
    public void markDirty() {
        markViewDirty();
    }

    public long getViewRevision() {
        return viewRevision;
    }

    private void markViewDirty() {
        dirty = true;
        viewRevision++;
    }
}
