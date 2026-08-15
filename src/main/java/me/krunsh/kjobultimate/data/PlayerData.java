package me.krunsh.kjobultimate.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modèle RAM d'un joueur connecté.
 *
 * V3.14 :
 * - les cooldowns de blocs quittent PlayerData et passent dans
 *   BlockCooldownService ;
 * - aucun état de cooldown bloc n'est persistant ;
 * - le sweep quotidien global possède un timestamp RAM pour ne pas rescanner
 *   tous les métiers à chaque gain XP ;
 * - les cooldowns PvP expirés sont supprimés opportunistement.
 */
public final class PlayerData {

    private final UUID uuid;
    private long firstJoin;
    private long lastSeen;

    private final Map<String, Integer> jobXP =
        new HashMap<String, Integer>();
    private final Map<String, Integer> jobLevels =
        new HashMap<String, Integer>();
    private final Map<String, Integer> dailyXP =
        new HashMap<String, Integer>();
    private final Map<String, Long> dailyXpResetTime =
        new HashMap<String, Long>();

    private int unlockedSlots = 1;
    private final Map<Integer, String> slotJobs =
        new HashMap<Integer, String>();

    private String displayJob;
    private long lastXpTimestamp;
    private boolean hudEnabled = true;
    private boolean bossBarHudEnabled = true;
    private boolean actionBarHudEnabled = true;

    private long lastJobChangeAt;

    /* Cooldown PvP uniquement. Les blocs sont gérés par BlockCooldownService. */
    private final Map<UUID, Long> pvpTargetCooldowns =
        new HashMap<UUID, Long>();

    private final Map<String, QuestData> questProgress =
        new HashMap<String, QuestData>();

    private final Map<String, Double> bonusMultipliers =
        new HashMap<String, Double>();

    private volatile boolean dirty;
    private volatile long viewRevision;

    /**
     * RAM uniquement. 0 = aucun sweep global encore effectué.
     */
    private long lastGlobalDailySweepAt;

    public PlayerData(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException(
                "uuid ne peut pas être null."
            );
        }
        this.uuid = uuid;
    }

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

    public int getDailyXP(String jobId) {
        Integer value = dailyXP.get(jobId);
        return value == null ? 0 : value.intValue();
    }

    public void addDailyXP(String jobId, int amount) {
        dailyXP.put(
            jobId,
            Integer.valueOf(
                saturatingAdd(
                    getDailyXP(jobId),
                    amount
                )
            )
        );
        markViewDirty();
    }

    /**
     * Mutation groupée utilisée par le hot path XP normal.
     *
     * Daily XP + niveau + XP courante + job affiché ne provoquent qu'une seule
     * révision de View au lieu de plusieurs mutations successives.
     */
    public void applyJobXpGain(
            String jobId,
            int level,
            int remainingXp,
            int dailyXpAmount,
            long now) {

        dailyXP.put(
            jobId,
            Integer.valueOf(
                saturatingAdd(
                    getDailyXP(jobId),
                    dailyXpAmount
                )
            )
        );

        jobLevels.put(
            jobId,
            Integer.valueOf(
                Math.max(0, level)
            )
        );

        jobXP.put(
            jobId,
            Integer.valueOf(
                Math.max(0, remainingXp)
            )
        );

        displayJob = jobId;
        lastXpTimestamp = now;
        markViewDirty();
    }

    public void resetDailyXP(String jobId) {
        resetDailyXP(jobId, System.currentTimeMillis());
    }

    public void resetDailyXP(String jobId, long now) {
        dailyXP.put(jobId, Integer.valueOf(0));
        dailyXpResetTime.put(jobId, Long.valueOf(now));
        markViewDirty();
    }

    public int getUnlockedSlots() {
        return unlockedSlots;
    }

    public void setUnlockedSlots(int count) {
        unlockedSlots = count;
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
        if (jobId == null) {
            return -1;
        }
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

    public String getDisplayJob() {
        return displayJob;
    }

    public void setDisplayJob(String jobId) {
        displayJob = jobId;
        lastXpTimestamp = System.currentTimeMillis();
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
        hudEnabled = enabled;
        dirty = true;
    }

    public boolean isBossBarHudEnabled() {
        return bossBarHudEnabled;
    }

    public void setBossBarHudEnabled(boolean enabled) {
        bossBarHudEnabled = enabled;
        dirty = true;
    }

    public boolean isActionBarHudEnabled() {
        return actionBarHudEnabled;
    }

    public void setActionBarHudEnabled(boolean enabled) {
        actionBarHudEnabled = enabled;
        dirty = true;
    }

    public boolean isPvpTargetOnCooldown(UUID target) {
        if (target == null) {
            return false;
        }

        Long end = pvpTargetCooldowns.get(target);
        if (end == null) {
            return false;
        }

        if (System.currentTimeMillis() >= end.longValue()) {
            pvpTargetCooldowns.remove(target);
            return false;
        }

        return true;
    }

    public void setPvpTargetCooldown(UUID target, long durationMs) {
        if (target == null || durationMs <= 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        long end = durationMs > Long.MAX_VALUE - now
            ? Long.MAX_VALUE
            : now + durationMs;

        pvpTargetCooldowns.put(target, Long.valueOf(end));
    }

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

    public double getBonusMultiplier(String jobId) {
        Double specificValue = bonusMultipliers.get(jobId);
        Double globalValue = bonusMultipliers.get("all");

        double specific = specificValue == null
            ? 1D
            : specificValue.doubleValue();
        double global = globalValue == null
            ? 1D
            : globalValue.doubleValue();

        return Math.max(specific, global);
    }

    public void setBonusMultiplier(String jobId, double multiplier) {
        bonusMultipliers.put(jobId, Double.valueOf(multiplier));
        dirty = true;
    }

    public Map<String, Double> getBonusMultipliers() {
        return bonusMultipliers;
    }

    public long getLastGlobalDailySweepAt() {
        return lastGlobalDailySweepAt;
    }

    public void setLastGlobalDailySweepAt(long value) {
        lastGlobalDailySweepAt = value;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public void markDirty() {
        markViewDirty();
    }

    public long getViewRevision() {
        return viewRevision;
    }

    private static int saturatingAdd(
            int current,
            int amount) {

        long next =
            (long) current + amount;

        if (next >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) Math.max(0L, next);
    }

    private void markViewDirty() {
        dirty = true;
        viewRevision++;
    }
}
