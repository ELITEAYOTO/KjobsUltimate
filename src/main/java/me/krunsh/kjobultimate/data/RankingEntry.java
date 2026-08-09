package me.krunsh.kjobultimate.data;

import java.util.UUID;

public final class RankingEntry {

    private final UUID uuid;
    private final String jobId;
    private final int level;
    private final int xp;

    public RankingEntry(UUID uuid, String jobId, int level, int xp) {
        this.uuid = uuid;
        this.jobId = jobId;
        this.level = level;
        this.xp = xp;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getJobId() {
        return jobId;
    }

    public int getLevel() {
        return level;
    }

    public int getXp() {
        return xp;
    }
}
