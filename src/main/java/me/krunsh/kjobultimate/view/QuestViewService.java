package me.krunsh.kjobultimate.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.QuestData;
import me.krunsh.kjobultimate.quests.QuestChainDefinition;
import me.krunsh.kjobultimate.quests.QuestChainPolicy;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.quests.QuestManager;

/**
 * Source unique des snapshots Quêtes V3.
 *
 * V3.9 :
 * - toutes les quêtes et chaînes d'un joueur sont construites une seule fois
 *   par snapshot ;
 * - les appels PAPI/Kgui successifs réutilisent ce snapshot ;
 * - invalidation immédiate via PlayerData.viewRevision ;
 * - TTL de sécurité pour les reloads de catalogue/config ;
 * - aucune lecture SQL.
 */
public final class QuestViewService {

    private static final long CACHE_TTL_MS = 1000L;

    private final KjobUltimate plugin;

    private final ConcurrentMap<UUID, CacheEntry> cache =
        new ConcurrentHashMap<UUID, CacheEntry>();

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

        if (questId == null) {
            return null;
        }

        PlayerQuestSnapshot snapshot =
            getSnapshot(playerId);

        return snapshot == null
            ? null
            : snapshot.questsById.get(
                normalize(questId)
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

        if (chainId == null) {
            return null;
        }

        PlayerQuestSnapshot snapshot =
            getSnapshot(playerId);

        return snapshot == null
            ? null
            : snapshot.chainsById.get(
                normalize(chainId)
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

        PlayerQuestSnapshot snapshot =
            getSnapshot(playerId);

        return snapshot == null
            ? Collections.<QuestView>emptyList()
            : snapshot.quests;
    }

    public List<QuestView> getQuests(
            Player player) {

        return player == null
            ? Collections.<QuestView>emptyList()
            : getQuests(
                player.getUniqueId()
            );
    }

    public List<QuestView> getQuestsForJob(
            UUID playerId,
            String rawJobId) {

        String jobId =
            normalize(rawJobId);

        if (jobId.isEmpty()) {
            return Collections.emptyList();
        }

        PlayerQuestSnapshot snapshot =
            getSnapshot(playerId);

        if (snapshot == null) {
            return Collections.emptyList();
        }

        List<QuestView> result =
            new ArrayList<QuestView>();

        for (QuestView quest : snapshot.quests) {
            if (quest != null
                    && jobId.equals(
                        normalize(quest.getJobId())
                    )) {

                result.add(quest);
            }
        }

        return Collections.unmodifiableList(
            result
        );
    }

    public List<QuestChainView> getChains(
            UUID playerId) {

        PlayerQuestSnapshot snapshot =
            getSnapshot(playerId);

        return snapshot == null
            ? Collections.<QuestChainView>emptyList()
            : snapshot.chains;
    }

    public List<QuestChainView> getChains(
            Player player) {

        return player == null
            ? Collections.<QuestChainView>emptyList()
            : getChains(
                player.getUniqueId()
            );
    }

    public int getClaimableQuestCount(
            UUID playerId) {

        PlayerQuestSnapshot snapshot =
            getSnapshot(playerId);

        return snapshot == null
            ? 0
            : snapshot.claimableQuestCount;
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

    public void invalidate(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    public void clearCache() {
        cache.clear();
    }

    public int getCachedPlayerCount() {
        return cache.size();
    }

    private PlayerQuestSnapshot getSnapshot(
            UUID playerId) {

        if (playerId == null
                || plugin.getPlayerDataManager() == null
                || plugin.getQuestManager() == null) {

            return null;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(playerId);

        if (data == null) {
            cache.remove(playerId);
            return null;
        }

        long revision =
            data.getViewRevision();

        long now =
            System.currentTimeMillis();

        CacheEntry cached =
            cache.get(playerId);

        if (cached != null
                && cached.revision == revision
                && now - cached.createdAt <= CACHE_TTL_MS) {

            return cached.snapshot;
        }

        PlayerQuestSnapshot rebuilt =
            buildSnapshot(
                playerId,
                data
            );

        cache.put(
            playerId,
            new CacheEntry(
                revision,
                now,
                rebuilt
            )
        );

        return rebuilt;
    }

    private PlayerQuestSnapshot buildSnapshot(
            UUID playerId,
            PlayerData data) {

        PlayerJobsView jobsView =
            getJobsView(playerId);

        List<QuestView> quests =
            new ArrayList<QuestView>();

        Map<String, QuestView> questsById =
            new LinkedHashMap<String, QuestView>();

        int claimableQuestCount = 0;

        for (QuestDefinition quest
                : plugin.getQuestManager()
                    .getQuests()) {

            if (quest == null) {
                continue;
            }

            QuestView view =
                buildQuestView(
                    data,
                    quest,
                    jobsView
                );

            quests.add(view);

            questsById.put(
                normalize(view.getId()),
                view
            );

            if (view.isClaimable()) {
                claimableQuestCount++;
            }
        }

        List<QuestChainView> chains =
            new ArrayList<QuestChainView>();

        Map<String, QuestChainView> chainsById =
            new LinkedHashMap<String, QuestChainView>();

        for (QuestChainDefinition chain
                : plugin.getQuestManager()
                    .getChains()) {

            if (chain == null) {
                continue;
            }

            QuestChainView view =
                buildChainView(
                    data,
                    chain,
                    jobsView,
                    questsById
                );

            chains.add(view);

            chainsById.put(
                normalize(view.getId()),
                view
            );
        }

        return new PlayerQuestSnapshot(
            Collections.unmodifiableList(
                quests
            ),
            Collections.unmodifiableMap(
                questsById
            ),
            Collections.unmodifiableList(
                chains
            ),
            Collections.unmodifiableMap(
                chainsById
            ),
            claimableQuestCount
        );
    }

    private QuestChainView buildChainView(
            PlayerData data,
            QuestChainDefinition chain,
            PlayerJobsView jobsView,
            Map<String, QuestView> questsById) {

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
                questsById.get(
                    normalize(stage.getId())
                );

            if (view == null) {
                view =
                    buildQuestView(
                        data,
                        stage,
                        jobsView
                    );
            }

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

            totalProgress +=
                Math.min(
                    view.getProgress(),
                    view.getAmount()
                );

            totalAmount +=
                view.getAmount();
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
                : questsById.get(
                    normalize(
                        activeDefinition.getId()
                    )
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
            QuestChainPolicy.CLAIMABLE
                .equals(state);

        boolean active =
            QuestChainPolicy.ACTIVE
                .equals(state);

        boolean locked =
            QuestChainPolicy.LOCKED_CHAIN
                .equals(state)
            || QuestChainPolicy.LOCKED_LEVEL
                .equals(state);

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

    private static int calculatePercent(
            int current,
            int maximum) {

        if (maximum <= 0) {
            return 0;
        }

        double ratio =
            (double) Math.max(
                0,
                current
            ) / (double) maximum;

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

    private static final class CacheEntry {

        private final long revision;
        private final long createdAt;
        private final PlayerQuestSnapshot snapshot;

        private CacheEntry(
                long revision,
                long createdAt,
                PlayerQuestSnapshot snapshot) {

            this.revision = revision;
            this.createdAt = createdAt;
            this.snapshot = snapshot;
        }
    }

    private static final class PlayerQuestSnapshot {

        private final List<QuestView> quests;
        private final Map<String, QuestView> questsById;

        private final List<QuestChainView> chains;
        private final Map<String, QuestChainView> chainsById;

        private final int claimableQuestCount;

        private PlayerQuestSnapshot(
                List<QuestView> quests,
                Map<String, QuestView> questsById,
                List<QuestChainView> chains,
                Map<String, QuestChainView> chainsById,
                int claimableQuestCount) {

            this.quests = quests;
            this.questsById = questsById;
            this.chains = chains;
            this.chainsById = chainsById;
            this.claimableQuestCount =
                Math.max(
                    0,
                    claimableQuestCount
                );
        }
    }
}
