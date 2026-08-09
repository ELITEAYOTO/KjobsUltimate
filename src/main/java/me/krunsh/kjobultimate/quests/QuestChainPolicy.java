package me.krunsh.kjobultimate.quests;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.krunsh.kjobultimate.data.QuestData;

/** Règles pures de verrouillage et d'activation d'une étape de chaîne. */
public final class QuestChainPolicy {
    public static final String CLAIMED = "claimed";
    public static final String CLAIMABLE = "claimable";
    public static final String PAUSED_JOB = "paused_job";
    public static final String LOCKED_CHAIN = "locked_chain";
    public static final String LOCKED_LEVEL = "locked_level";
    public static final String ACTIVE = "in_progress";

    private QuestChainPolicy() {}

    public static QuestDefinition firstIncomplete(QuestChainDefinition chain,
            Map<String, QuestData> progress) {
        for (QuestDefinition quest : chain.getStages()) {
            QuestData data = progress.get(quest.getId());
            if (data == null || !data.isCompleted()) return quest;
        }
        return null;
    }

    /**
     * Photographie les etapes actives avant qu'une action ne modifie la
     * progression. Cette photographie empeche une action qui termine l'etape
     * N de crediter aussi l'etape N+1 pendant la meme boucle.
     */
    public static Set<String> activeQuestIds(
            Collection<QuestDefinition> candidates,
            Map<String, QuestChainDefinition> chains,
            Map<String, QuestData> progress) {
        Set<String> activeIds = new HashSet<String>();
        Set<String> visitedChains = new HashSet<String>();
        for (QuestDefinition candidate : candidates) {
            String chainId = QuestChainIndex.normalize(candidate.getChainId());
            if (!visitedChains.add(chainId)) continue;
            QuestChainDefinition chain = chains.get(chainId);
            QuestDefinition active =
                    chain == null ? null : firstIncomplete(chain, progress);
            if (active != null) activeIds.add(active.getId());
        }
        return activeIds;
    }

    public static String state(QuestChainDefinition chain,
            QuestDefinition quest, Map<String, QuestData> progress,
            boolean jobActive, int jobLevel) {
        QuestData data = progress.get(quest.getId());
        if (data != null && data.isClaimed()) return CLAIMED;
        if (data != null && data.isCompleted()) return CLAIMABLE;
        if (!jobActive) return PAUSED_JOB;
        QuestDefinition active = firstIncomplete(chain, progress);
        if (active == null || !active.getId().equals(quest.getId())) {
            return LOCKED_CHAIN;
        }
        if (jobLevel < quest.getMinLevel()) return LOCKED_LEVEL;
        return ACTIVE;
    }
}
