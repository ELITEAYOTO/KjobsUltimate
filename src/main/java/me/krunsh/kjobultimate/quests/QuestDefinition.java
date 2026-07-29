package me.krunsh.kjobultimate.quests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Definition statique d'une quete permanente chargee depuis quests.yml.
 */
public final class QuestDefinition {

    private final String id;
    private final String displayName;
    private final String jobId;
    private final String type;
    private final String target;
    private final int amount;
    private final int minLevel;
    private final int rewardXp;
    private final List<String> rewardCommands;
    private final String chainId;
    private final int chainStage;
    private final boolean explicitChain;

    public QuestDefinition(String id, String displayName, String jobId, String type,
                           String target, int amount, int minLevel, int rewardXp,
                           List<String> rewardCommands) {
        this(id, displayName, jobId, type, target, amount, minLevel, rewardXp,
                rewardCommands, id, 1, false);
    }

    public QuestDefinition(String id, String displayName, String jobId, String type,
                           String target, int amount, int minLevel, int rewardXp,
                           List<String> rewardCommands, String chainId,
                           int chainStage, boolean explicitChain) {
        this.id = id;
        this.displayName = displayName;
        this.jobId = jobId;
        this.type = normalize(type);
        this.target = normalize(target);
        this.amount = Math.max(1, amount);
        this.minLevel = Math.max(0, minLevel);
        this.rewardXp = Math.max(0, rewardXp);
        this.rewardCommands = Collections.unmodifiableList(new ArrayList<String>(rewardCommands));
        this.chainId = chainId == null || chainId.trim().isEmpty()
                ? id : chainId.trim().toLowerCase();
        this.chainStage = Math.max(1, chainStage);
        this.explicitChain = explicitChain;
    }

    public boolean matches(String eventType, String eventTarget) {
        if (!type.equals(normalize(eventType))) return false;
        String normalizedTarget = normalize(eventTarget);
        if ("*".equals(target) || "ALL".equals(target) || target.equals(normalizedTarget)) {
            return true;
        }

        // Un objectif sans data (STONE) accepte toutes ses variantes
        // (STONE:0, STONE:1...), tandis qu'un objectif explicite
        // (PRISMARINE:2) conserve une comparaison exacte.
        return target.indexOf(':') < 0 && normalizedTarget.startsWith(target + ":");
    }

    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) return "*";
        return value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getJobId() { return jobId; }
    public String getType() { return type; }
    public String getTarget() { return target; }
    public int getAmount() { return amount; }
    public int getMinLevel() { return minLevel; }
    public int getRewardXp() { return rewardXp; }
    public List<String> getRewardCommands() { return rewardCommands; }
    public String getChainId() { return chainId; }
    public int getChainStage() { return chainStage; }
    public boolean isExplicitChain() { return explicitChain; }
}
