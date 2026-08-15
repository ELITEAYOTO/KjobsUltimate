package me.krunsh.kjobultimate.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.QuestData;
import me.krunsh.kjobultimate.quests.QuestChainDefinition;
import me.krunsh.kjobultimate.quests.QuestChainPolicy;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.quests.QuestManager;

/**
 * Source unique de construction des vues Quêtes V3.
 *
 * Règles :
 * - aucune lecture SQL ;
 * - aucune écriture dans PlayerData ;
 * - aucun cache dans cette première version ;
 * - l'état officiel d'une quête vient de QuestManager / QuestChainPolicy ;
 * - les informations de job consommées ici viennent de JobsViewService.
 *
 * Cette couche sera utilisée par PlaceholderAPI puis par Kgui.
 */
public final class QuestViewService {

    private final KjobUltimate plugin;

    public QuestViewService(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                "KjobUltimate ne peut pas être null."
            );
        }

        this.plugin = plugin;
    }

    public QuestView getQuest(
            UUID playerId,
            String questId) {

        if (playerId == null
                || questId == null
                || plugin.getPlayerDataManager() == null
                || plugin.getQuestManager() == null) {

            return null;
        }

        PlayerData data =
                plugin.getPlayerDataManager()
                    .get(playerId);

        QuestDefinition quest =
                plugin.getQuestManager()
                    .getQuest(questId);

        if (data == null || quest == null) {
            return null;
        }

        PlayerJobsView jobsView =
                getJobsView(playerId);

        return buildQuestView(
            data,
            quest,
            jobsView
        );
    }

    public QuestView getQuest(
            Player player,
            String questId) {

        return player == null
            ? null
            : getQuest(
                player.getUniqueId(),
                questId
            );
    }

    public QuestChainView getChain(
            UUID playerId,
            String chainId) {

        if (playerId == null
                || chainId == null
                || plugin.getPlayerDataManager() == null
                || plugin.getQuestManager() == null) {

            return null;
        }

        PlayerData data =
                plugin.getPlayerDataManager()
                    .get(playerId);

        QuestChainDefinition chain =
                plugin.getQuestManager()
                    .getChain(chainId);

        if (data == null || chain == null) {
            return null;
        }

        PlayerJobsView jobsView =
                getJobsView(playerId);

        return buildChainView(
            data,
            chain,
            jobsView
        );
    }

    public QuestChainView getChain(
            Player player,
            String chainId) {

        return player == null
            ? null
            : getChain(
                player.getUniqueId(),
                chainId
            );
    }

    public List<QuestView> getQuests(
            UUID playerId) {

        if (playerId == null
                || plugin.getPlayerDataManager() == null
                || plugin.getQuestManager() == null) {

            return Collections.emptyList();
        }

        PlayerData data =
                plugin.getPlayerDataManager()
                    .get(playerId);

        if (data == null) {
            return Collections.emptyList();
        }

        PlayerJobsView jobsView =
                getJobsView(playerId);

        List<QuestView> result =
                new ArrayList<QuestView>();

        for (QuestDefinition quest
                : plugin.getQuestManager().getQuests()) {

            if (quest != null) {
                result.add(
                    buildQuestView(
                        data,
                        quest,
                        jobsView
                    )
                );
            }
        }

        return Collections.unmodifiableList(result);
    }

    public List<QuestView> getQuests(
            Player player) {

        return player == null
            ? Collections.<QuestView>emptyList()
            : getQuests(player.getUniqueId());
    }

    public List<QuestView> getQuestsForJob(
            UUID playerId,
            String rawJobId) {

        String jobId =
                normalize(rawJobId);

        if (jobId.isEmpty()
                || playerId == null
                || plugin.getPlayerDataManager() == null
                || plugin.getQuestManager() == null) {

            return Collections.emptyList();
        }

        PlayerData data =
                plugin.getPlayerDataManager()
                    .get(playerId);

        if (data == null) {
            return Collections.emptyList();
        }

        PlayerJobsView jobsView =
                getJobsView(playerId);

        List<QuestView> result =
                new ArrayList<QuestView>();

        for (QuestDefinition quest
                : plugin.getQuestManager()
                    .getQuestsForJob(jobId)) {

            if (quest != null) {
                result.add(
                    buildQuestView(
                        data,
                        quest,
                        jobsView
                    )
                );
            }
        }

        return Collections.unmodifiableList(result);
    }

    public List<QuestChainView> getChains(
            UUID playerId) {

        if (playerId == null
                || plugin.getPlayerDataManager() == null
                || plugin.getQuestManager() == null) {

            return Collections.emptyList();
        }

        PlayerData data =
                plugin.getPlayerDataManager()
                    .get(playerId);

        if (data == null) {
            return Collections.emptyList();
        }

        PlayerJobsView jobsView =
                getJobsView(playerId);

        List<QuestChainView> result =
                new ArrayList<QuestChainView>();

        for (QuestChainDefinition chain
                : plugin.getQuestManager().getChains()) {

            if (chain != null) {
                result.add(
                    buildChainView(
                        data,
                        chain,
                        jobsView
                    )
                );
            }
        }

        return Collections.unmodifiableList(result);
    }

    public List<QuestChainView> getChains(
            Player player) {

        return player == null
            ? Collections.<QuestChainView>emptyList()
            : getChains(player.getUniqueId());
    }

    public int getClaimableQuestCount(
            UUID playerId) {

        int count = 0;

        for (QuestView quest : getQuests(playerId)) {
            if (quest != null && quest.isClaimable()) {
                count++;
            }
        }

        return count;
    }

    public int getQuestCount() {
        QuestManager manager =
                plugin.getQuestManager();

        return manager == null
            ? 0
            : manager.getQuests().size();
    }

    public int getChainCount() {
        QuestManager manager =
                plugin.getQuestManager();

        return manager == null
            ? 0
            : manager.getChains().size();
    }

    private QuestChainView buildChainView(
            PlayerData data,
            QuestChainDefinition chain,
            PlayerJobsView jobsView) {

        List<QuestView> stages =
                new ArrayList<QuestView>();

        int completedStages = 0;
        int claimedStages = 0;
        int claimableStages = 0;

        long totalProgress = 0L;
        long totalAmount = 0L;

        for (QuestDefinition stage
                : chain.getStages()) {

            QuestView view =
                    buildQuestView(
                        data,
                        stage,
                        jobsView
                    );

            stages.add(view);

            if (view.isCompleted()) {
                completedStages++;
            }

            if (view.isClaimed()) {
                claimedStages++;
            }

            if (view.isClaimable()) {
                claimableStages++;
            }

            totalProgress += Math.min(
                view.getProgress(),
                view.getAmount()
            );

            totalAmount += view.getAmount();
        }

        QuestDefinition activeDefinition =
                plugin.getQuestManager()
                    .getActiveQuest(
                        data,
                        chain.getId()
                    );

        QuestView activeQuest =
                activeDefinition == null
                    ? null
                    : findQuestView(
                        stages,
                        activeDefinition.getId()
                    );

        JobView job =
                jobsView == null
                    ? null
                    : jobsView.getJob(
                        chain.getJobId()
                    );

        boolean jobActive =
                job != null
                    && job.isActive();

        String state;

        if (activeQuest != null) {
            state = activeQuest.getState();
        } else if (!stages.isEmpty()
                && claimedStages >= stages.size()) {

            state = QuestChainPolicy.CLAIMED;
        } else if (!stages.isEmpty()
                && completedStages >= stages.size()) {

            state = QuestChainPolicy.CLAIMABLE;
        } else if (!jobActive) {
            state = QuestChainPolicy.PAUSED_JOB;
        } else {
            state = QuestChainPolicy.LOCKED_CHAIN;
        }

        return new QuestChainView(
            chain.getId(),
            chain.getDisplayName(),
            chain.getJobId(),
            stages,
            activeQuest,
            completedStages,
            claimedStages,
            claimableStages,
            totalProgress,
            totalAmount,
            jobActive,
            state,
            stateName(state),
            stateColor(state)
        );
    }

    private QuestView buildQuestView(
            PlayerData data,
            QuestDefinition quest,
            PlayerJobsView jobsView) {

        QuestData questData =
                data.getQuestProgress()
                    .get(quest.getId());

        int progress =
                questData == null
                    ? 0
                    : Math.max(
                        0,
                        questData.getProgress()
                    );

        int amount =
                Math.max(
                    1,
                    quest.getAmount()
                );

        int remaining =
                Math.max(
                    0,
                    amount - progress
                );

        int percent =
                calculatePercent(
                    progress,
                    amount
                );

        boolean completed =
                questData != null
                    && questData.isCompleted();

        boolean claimed =
                questData != null
                    && questData.isClaimed();

        long completedAt =
                questData == null
                    ? 0L
                    : Math.max(
                        0L,
                        questData.getCompletedAt()
                    );

        String state =
                plugin.getQuestManager()
                    .getQuestState(
                        data,
                        quest
                    );

        JobView job =
                jobsView == null
                    ? null
                    : jobsView.getJob(
                        quest.getJobId()
                    );

        boolean jobActive =
                job != null
                    && job.isActive();

        QuestChainDefinition chain =
                plugin.getQuestManager()
                    .getChain(
                        quest.getChainId()
                    );

        int stageTotal =
                chain == null
                    ? 1
                    : Math.max(
                        1,
                        chain.getStages().size()
                    );

        boolean claimable =
                QuestChainPolicy.CLAIMABLE.equals(state);

        boolean active =
                QuestChainPolicy.ACTIVE.equals(state);

        boolean locked =
                QuestChainPolicy.LOCKED_CHAIN.equals(state)
                    || QuestChainPolicy.LOCKED_LEVEL.equals(state);

        return new QuestView(
            quest.getId(),
            quest.getDisplayName(),
            quest.getJobId(),
            quest.getType(),
            quest.getTarget(),
            progress,
            amount,
            remaining,
            percent,
            quest.getMinLevel(),
            quest.getRewardXp(),
            quest.getChainId(),
            quest.getChainStage(),
            stageTotal,
            state,
            stateName(state),
            stateColor(state),
            completed,
            claimed,
            claimable,
            active,
            locked,
            jobActive,
            completedAt
        );
    }

    private PlayerJobsView getJobsView(
            UUID playerId) {

        return plugin.getJobsViewService() == null
            ? null
            : plugin.getJobsViewService()
                .getPlayer(playerId);
    }

    private static QuestView findQuestView(
            List<QuestView> quests,
            String questId) {

        if (questId == null || quests == null) {
            return null;
        }

        for (QuestView quest : quests) {
            if (quest != null
                    && questId.equalsIgnoreCase(
                        quest.getId()
                    )) {

                return quest;
            }
        }

        return null;
    }

    private static int calculatePercent(
            int current,
            int maximum) {

        if (maximum <= 0) {
            return 0;
        }

        double ratio =
                (double) Math.max(0, current)
                    / (double) maximum;

        if (Double.isNaN(ratio)
                || Double.isInfinite(ratio)) {

            return 0;
        }

        return Math.max(
            0,
            Math.min(
                100,
                (int) Math.floor(
                    ratio * 100.0D
                )
            )
        );
    }

    private static String stateName(
            String state) {

        if (QuestChainPolicy.CLAIMED.equals(state)) {
            return "Récupérée";
        }

        if (QuestChainPolicy.CLAIMABLE.equals(state)) {
            return "À récupérer";
        }

        if (QuestChainPolicy.PAUSED_JOB.equals(state)) {
            return "Job inactif";
        }

        if (QuestChainPolicy.LOCKED_LEVEL.equals(state)) {
            return "Niveau requis";
        }

        if (QuestChainPolicy.LOCKED_CHAIN.equals(state)) {
            return "Verrouillée";
        }

        if (QuestChainPolicy.ACTIVE.equals(state)) {
            return "En cours";
        }

        return state == null
            ? ""
            : state;
    }

    private static String stateColor(
            String state) {

        if (QuestChainPolicy.CLAIMED.equals(state)) {
            return "&8";
        }

        if (QuestChainPolicy.CLAIMABLE.equals(state)) {
            return "&a";
        }

        if (QuestChainPolicy.PAUSED_JOB.equals(state)) {
            return "&6";
        }

        if (QuestChainPolicy.LOCKED_LEVEL.equals(state)
                || QuestChainPolicy.LOCKED_CHAIN.equals(state)) {

            return "&c";
        }

        if (QuestChainPolicy.ACTIVE.equals(state)) {
            return "&e";
        }

        return "&7";
    }

    private static String normalize(
            String value) {

        return value == null
            ? ""
            : value.trim()
                .toLowerCase(Locale.ROOT);
    }
}
