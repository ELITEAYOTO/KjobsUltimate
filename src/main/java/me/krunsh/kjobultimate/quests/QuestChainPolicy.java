package me.krunsh.kjobultimate.quests;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.krunsh.kjobultimate.data.QuestData;

/**
 * Règles pures de verrouillage et d'activation d'une étape de chaîne.
 *
 * Règle V3 :
 * une étape suivante n'est jamais déverrouillée par le simple fait que
 * l'étape précédente soit "completed". La récompense de l'étape précédente
 * doit avoir été "claimed".
 *
 * Cycle normal :
 *
 * IN_PROGRESS
 *      ↓
 * CLAIMABLE
 *      ↓ claim
 * CLAIMED
 *      ↓
 * étape suivante
 */
public final class QuestChainPolicy {

    public static final String CLAIMED = "claimed";
    public static final String CLAIMABLE = "claimable";
    public static final String PAUSED_JOB = "paused_job";
    public static final String LOCKED_CHAIN = "locked_chain";
    public static final String LOCKED_LEVEL = "locked_level";
    public static final String ACTIVE = "in_progress";

    private QuestChainPolicy() {
    }

    /**
     * Retourne la première étape dont la récompense n'a pas encore été claim.
     *
     * Une étape completed mais non claimed reste donc l'étape courante de la
     * chaîne. Cela empêche toute progression de l'étape suivante avant claim.
     *
     * @return première étape non claim, ou null si toute la chaîne est claim.
     */
    public static QuestDefinition firstUnclaimed(
            QuestChainDefinition chain,
            Map<String, QuestData> progress) {

        if (chain == null) {
            return null;
        }

        for (QuestDefinition quest : chain.getStages()) {

            QuestData data =
                    progress == null
                        ? null
                        : progress.get(quest.getId());

            if (data == null || !data.isClaimed()) {
                return quest;
            }
        }

        return null;
    }

    /**
     * Alias historique conservé pendant la migration V3.
     *
     * Le nom "firstIncomplete" n'est plus sémantiquement exact : depuis V3,
     * l'avancement d'une chaîne est déterminé par le claim, pas seulement par
     * completed. Les nouveaux appels doivent utiliser firstUnclaimed().
     */
    @Deprecated
    public static QuestDefinition firstIncomplete(
            QuestChainDefinition chain,
            Map<String, QuestData> progress) {

        return firstUnclaimed(
            chain,
            progress
        );
    }

    /**
     * Photographie les étapes qui peuvent recevoir de la progression avant
     * qu'une action ne modifie PlayerData.
     *
     * Avec la règle V3, une étape completed/non claimed reste sélectionnée ici.
     * QuestManager l'ignore ensuite car elle est déjà completed, ce qui bloque
     * naturellement l'étape suivante jusqu'au claim.
     */
    public static Set<String> activeQuestIds(
            Collection<QuestDefinition> candidates,
            Map<String, QuestChainDefinition> chains,
            Map<String, QuestData> progress) {

        Set<String> activeIds =
                new HashSet<String>();

        Set<String> visitedChains =
                new HashSet<String>();

        if (candidates == null
                || candidates.isEmpty()
                || chains == null
                || chains.isEmpty()) {

            return activeIds;
        }

        for (QuestDefinition candidate : candidates) {

            if (candidate == null) {
                continue;
            }

            String chainId =
                    QuestChainIndex.normalize(
                        candidate.getChainId()
                    );

            if (!visitedChains.add(chainId)) {
                continue;
            }

            QuestChainDefinition chain =
                    chains.get(chainId);

            QuestDefinition active =
                    firstUnclaimed(
                        chain,
                        progress
                    );

            if (active != null) {
                activeIds.add(
                    active.getId()
                );
            }
        }

        return activeIds;
    }

    /**
     * Calcule l'état d'une étape.
     */
    public static String state(
            QuestChainDefinition chain,
            QuestDefinition quest,
            Map<String, QuestData> progress,
            boolean jobActive,
            int jobLevel) {

        if (quest == null) {
            return LOCKED_CHAIN;
        }

        QuestData data =
                progress == null
                    ? null
                    : progress.get(quest.getId());

        /*
         * Les états terminaux de l'étape ont priorité.
         */
        if (data != null && data.isClaimed()) {
            return CLAIMED;
        }

        if (data != null && data.isCompleted()) {
            return CLAIMABLE;
        }

        /*
         * Une quête non terminée ne peut progresser si son job est inactif.
         */
        if (!jobActive) {
            return PAUSED_JOB;
        }

        /*
         * Seule la première étape non claim de la chaîne est courante.
         */
        QuestDefinition active =
                firstUnclaimed(
                    chain,
                    progress
                );

        if (active == null
                || !active.getId().equals(quest.getId())) {

            return LOCKED_CHAIN;
        }

        if (jobLevel < quest.getMinLevel()) {
            return LOCKED_LEVEL;
        }

        return ACTIVE;
    }
}