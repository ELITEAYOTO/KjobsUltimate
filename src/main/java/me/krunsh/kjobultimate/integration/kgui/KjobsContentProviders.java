package me.krunsh.kjobultimate.integration.kgui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.krunsh.kgui.api.ContentItem;
import me.krunsh.kgui.api.ContentProvider;
import me.krunsh.kgui.api.ContentRequest;
import me.krunsh.kgui.api.ContentSnapshot;
import me.krunsh.kgui.api.KguiApi;
import me.krunsh.kgui.api.OwnedRegistration;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.RankingEntry;
import me.krunsh.kjobultimate.hooks.KguiHook;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;
import me.krunsh.kjobultimate.view.JobView;
import me.krunsh.kjobultimate.view.PlayerJobsView;
import me.krunsh.kjobultimate.view.QuestChainView;
import me.krunsh.kjobultimate.view.QuestView;

/**
 * Providers Kjobs V3.
 *
 * Les providers jobs et quêtes consomment les couches View communes :
 *
 * JobsViewService
 * QuestViewService
 *
 * Aucun calcul de progression métier n'est dupliqué dans Kgui.
 */
public final class KjobsContentProviders implements AutoCloseable {

    private static final int PROVIDER_COUNT = 5;

    private final KjobUltimate plugin;
    private final KguiHook hook;

    private final AtomicLong revisionEpoch =
            new AtomicLong(System.currentTimeMillis());

    private final ConcurrentMap<String, RankingCacheEntry> rankingCache =
            new ConcurrentHashMap<String, RankingCacheEntry>();

    private final ConcurrentMap<String, Set<UUID>> rankingWaiters =
            new ConcurrentHashMap<String, Set<UUID>>();

    private final Set<String> rankingLoads =
            Collections.newSetFromMap(
                new ConcurrentHashMap<String, Boolean>()
            );

    private volatile boolean closed;

    public KjobsContentProviders(
            KjobUltimate plugin,
            KguiHook hook) {

        this.plugin = plugin;
        this.hook = hook;
    }

    public void register(
            KguiApi api,
            List<OwnedRegistration> handles) {

        add(api, handles, "jobs", this::jobs);
        add(api, handles, "job_detail", this::jobDetail);
        add(api, handles, "quests", this::quests);
        add(api, handles, "ranking", this::ranking);
        add(api, handles, "leave_confirmation", this::leaveConfirmation);
    }

    public int getProviderCount() {
        return PROVIDER_COUNT;
    }

    public void invalidateRevision() {
        revisionEpoch.incrementAndGet();
    }

    public void clearRankingCache() {
        rankingCache.clear();
        invalidateRevision();
    }

    // -------------------------------------------------------------------------
    // JOBS
    // -------------------------------------------------------------------------

    private ContentSnapshot jobs(ContentRequest request) {

        PlayerJobsView playerView =
                plugin.getJobsViewService()
                    .getPlayer(request.getPlayerId());

        if (playerView == null) {
            return ContentSnapshot.empty(revision());
        }

        List<JobView> definitions =
                new ArrayList<JobView>(
                    playerView.getJobs()
                );

        List<ContentItem> items =
                new ArrayList<ContentItem>();

        for (JobView job : slice(definitions, request)) {

            Map<String, String> attributes =
                    new LinkedHashMap<String, String>();

            putJobAttributes(
                attributes,
                playerView,
                job
            );

            attributes.put(
                "actions",
                "[kjobsultimate:open_detail] job_id="
                    + job.getId()
            );

            String status =
                    job.isFavorite()
                        ? "&6Favori"
                        : job.isActive()
                            ? "&aDébloqué"
                            : "&8Verrouillé";

            String color =
                    job.isFavorite()
                        ? "&6"
                        : job.isActive()
                            ? "&a"
                            : "&8";

            items.add(
                item(
                    "job/" + job.getId(),

                    job.getIconMaterial().isEmpty()
                        ? "PAPER"
                        : job.getIconMaterial(),

                    job.getIconData(),

                    color,

                    job.getDisplayName(),

                    Arrays.asList(
                        "&7Statut: " + status,
                        "&7Niveau: &e"
                            + job.getLevel()
                            + "&7/&e"
                            + job.getMaxLevel(),
                        "&7XP: &a"
                            + job.getXp()
                            + "&7/&a"
                            + job.getXpRequired()
                            + " &8("
                            + job.getXpPercent()
                            + "%)",
                        "",
                        "&eClic: ouvrir le détail"
                    ),

                    attributes
                )
            );
        }

        return new ContentSnapshot(
            revision(),
            items,
            definitions.size()
        );
    }

    private ContentSnapshot jobDetail(ContentRequest request) {

        PlayerJobsView playerView =
                plugin.getJobsViewService()
                    .getPlayer(request.getPlayerId());

        String jobId =
                normalizeJob(
                    request.getArguments()
                        .get("job_id")
                );

        JobView job =
                playerView == null || jobId == null
                    ? null
                    : playerView.getJob(jobId);

        if (playerView == null || job == null) {
            return ContentSnapshot.empty(revision());
        }

        Map<String, String> attributes =
                new LinkedHashMap<String, String>();

        putJobAttributes(
            attributes,
            playerView,
            job
        );

        attributes.put(
            "actions",
            job.isActive()
                ? "[kjobsultimate:favorite_job] job_id=" + jobId
                : "[kjobsultimate:unlock_job] job_id=" + jobId
        );

        if (job.isActive()) {
            attributes.put(
                "right_actions",
                "[kjobsultimate:request_leave] job_id=" + jobId
            );
        }

        List<String> lore =
                new ArrayList<String>();

        lore.add(
            "&7Statut: "
                + (
                    job.isFavorite()
                        ? "&6Favori"
                        : job.isActive()
                            ? "&aDébloqué"
                            : "&8Verrouillé"
                )
        );

        lore.add(
            "&7Niveau: &e"
                + job.getLevel()
                + "&7/&e"
                + job.getMaxLevel()
        );

        lore.add(
            "&7XP: &a"
                + job.getXp()
                + "&7/&a"
                + job.getXpRequired()
                + " &8("
                + job.getXpPercent()
                + "%)"
        );

        lore.add(
            "&7XP restant: &b"
                + job.getXpRemaining()
        );

        lore.add(
            "&7Niveau global: &e"
                + playerView.getGlobalLevel()
        );

        if (job.isDailyXpCapEnabled()) {
            lore.add(
                "&7XP journalier: &d"
                    + job.getDailyXp()
                    + "&7/&d"
                    + job.getDailyXpCap()
            );
        }

        lore.add("");

        lore.add(
            job.isActive()
                ? "&eClic gauche: définir favori"
                : "&aClic gauche: débloquer"
        );

        if (job.isActive()) {
            lore.add(
                "&cClic droit: préparer l'abandon"
            );
        }

        ContentItem detail =
                item(
                    "detail/" + jobId,

                    job.getIconMaterial().isEmpty()
                        ? "PAPER"
                        : job.getIconMaterial(),

                    job.getIconData(),

                    "&6",

                    job.getDisplayName(),

                    lore,

                    attributes
                );

        Map<String, String> questAttributes =
                new LinkedHashMap<String, String>();

        putJobAttributes(
            questAttributes,
            playerView,
            job
        );

        questAttributes.put(
            "actions",
            "[kjobsultimate:open_quests] job_id=" + jobId
        );

        ContentItem quests =
                item(
                    "detail/quests/" + jobId,
                    "BOOK_AND_QUILL",
                    (short) 0,
                    "&6",
                    "Quêtes de " + job.getDisplayName(),
                    Collections.singletonList(
                        "&7Voir uniquement les quêtes de ce métier."
                    ),
                    questAttributes
                );

        Map<String, String> rankingAttributes =
                new LinkedHashMap<String, String>();

        putJobAttributes(
            rankingAttributes,
            playerView,
            job
        );

        rankingAttributes.put(
            "actions",
            "[kjobsultimate:open_top] job_id=" + jobId
        );

        ContentItem ranking =
                item(
                    "detail/ranking/" + jobId,
                    "GOLD_INGOT",
                    (short) 0,
                    "&e",
                    "Classement de " + job.getDisplayName(),
                    Collections.singletonList(
                        "&7Comparer la progression sur ce métier."
                    ),
                    rankingAttributes
                );

        List<ContentItem> all =
                Arrays.asList(
                    detail,
                    quests,
                    ranking
                );

        return new ContentSnapshot(
            revision(),
            slice(all, request),
            all.size()
        );
    }

    // -------------------------------------------------------------------------
    // QUÊTES V3 — UNE ENTRÉE PAR CHAÎNE
    // -------------------------------------------------------------------------

    private ContentSnapshot quests(ContentRequest request) {

        if (plugin.getQuestManager() == null
                || !plugin.getQuestManager().isEnabled()
                || plugin.getQuestViewService() == null) {

            return ContentSnapshot.empty(revision());
        }

        List<QuestChainView> allChains =
                plugin.getQuestViewService()
                    .getChains(request.getPlayerId());

        String filter =
                normalizeJob(
                    request.getArguments()
                        .get("job_id")
                );

        List<QuestChainView> chains =
                new ArrayList<QuestChainView>();

        for (QuestChainView chain : allChains) {

            if (chain == null) {
                continue;
            }

            if (filter != null
                    && !filter.equalsIgnoreCase(
                        chain.getJobId()
                    )) {

                continue;
            }

            chains.add(chain);
        }

        List<ContentItem> items =
                new ArrayList<ContentItem>();

        for (QuestChainView chain
                : slice(chains, request)) {

            QuestView current =
                    currentQuest(chain);

            Map<String, String> attributes =
                    new LinkedHashMap<String, String>();

            putQuestChainAttributes(
                attributes,
                chain
            );

            if (current != null) {

                putQuestAttributes(
                    attributes,
                    current
                );

                if (current.isClaimable()) {
                    attributes.put(
                        "actions",
                        "[kjobsultimate:claim_quest] quest_id="
                            + current.getId()
                    );
                }
            }

            String state =
                    current == null
                        ? chain.getState()
                        : current.getState();

            String stateName =
                    current == null
                        ? chain.getStateName()
                        : current.getStateName();

            List<String> lore =
                    new ArrayList<String>();

            lore.add(
                "&7Job: &f"
                    + displayJobName(chain.getJobId())
            );

            lore.add(
                "&7Chaîne: &f"
                    + chain.getDisplayName()
            );

            lore.add(
                "&7État: "
                    + questColor(state)
                    + stateName
            );

            lore.add(
                "&7Étape: &e"
                    + chain.getCurrentStage()
                    + "&7/&e"
                    + chain.getStageTotal()
            );

            if (current != null) {

                lore.add("");

                lore.add(
                    "&f" + current.getDisplayName()
                );

                lore.add(
                    "&7Progression: &a"
                        + current.getProgress()
                        + "&7/&a"
                        + current.getAmount()
                        + " &8("
                        + current.getPercent()
                        + "%)"
                );

                lore.add(
                    "&7Restant: &b"
                        + current.getRemaining()
                );

                lore.add(
                    "&7Récompense XP: &d"
                        + current.getRewardXp()
                );

                if (current.isClaimable()) {
                    lore.add("");
                    lore.add(
                        "&aClic: récupérer la récompense"
                    );
                }
            } else {
                lore.add("");
                lore.add(
                    "&7Progression globale: &a"
                        + chain.getPercent()
                        + "%"
                );
            }

            items.add(
                item(
                    "quest-chain/"
                        + stableId(chain.getId()),

                    questMaterial(state),

                    questData(state),

                    questColor(state),

                    chain.getDisplayName(),

                    lore,

                    attributes
                )
            );
        }

        return new ContentSnapshot(
            revision(),
            items,
            chains.size()
        );
    }

    private QuestView currentQuest(
            QuestChainView chain) {

        if (chain == null) {
            return null;
        }

        if (chain.getActiveQuest() != null) {
            return chain.getActiveQuest();
        }

        List<QuestView> stages =
                chain.getStages();

        if (stages == null || stages.isEmpty()) {
            return null;
        }

        /*
         * Chaîne entièrement claim : on conserve la dernière étape comme
         * contexte visuel, mais aucune action n'est attachée.
         */
        return stages.get(
            stages.size() - 1
        );
    }

    private void putQuestChainAttributes(
            Map<String, String> attributes,
            QuestChainView chain) {

        if (attributes == null || chain == null) {
            return;
        }

        attributes.put(
            "chain_id",
            chain.getId()
        );

        attributes.put(
            "chain_name",
            chain.getDisplayName()
        );

        attributes.put(
            "job_id",
            chain.getJobId()
        );

        attributes.put(
            "chain_stage",
            String.valueOf(chain.getCurrentStage())
        );

        attributes.put(
            "chain_total",
            String.valueOf(chain.getStageTotal())
        );

        attributes.put(
            "chain_completed",
            String.valueOf(chain.getCompletedStages())
        );

        attributes.put(
            "chain_claimed",
            String.valueOf(chain.getClaimedStages())
        );

        attributes.put(
            "chain_claimable",
            String.valueOf(chain.getClaimableStages())
        );

        attributes.put(
            "chain_remaining",
            String.valueOf(chain.getRemainingStages())
        );

        attributes.put(
            "chain_unclaimed",
            String.valueOf(chain.getUnclaimedStages())
        );

        attributes.put(
            "chain_progress",
            String.valueOf(chain.getProgress())
        );

        attributes.put(
            "chain_amount",
            String.valueOf(chain.getAmount())
        );

        attributes.put(
            "chain_percent",
            String.valueOf(chain.getPercent())
        );

        attributes.put(
            "chain_complete",
            String.valueOf(chain.isComplete())
        );

        attributes.put(
            "chain_fully_claimed",
            String.valueOf(chain.isFullyClaimed())
        );

        attributes.put(
            "chain_job_active",
            String.valueOf(chain.isJobActive())
        );

        attributes.put(
            "chain_state",
            chain.getState()
        );

        attributes.put(
            "chain_state_name",
            chain.getStateName()
        );

        attributes.put(
            "chain_state_color",
            chain.getStateColor()
        );
    }

    private void putQuestAttributes(
            Map<String, String> attributes,
            QuestView quest) {

        if (attributes == null || quest == null) {
            return;
        }

        attributes.put(
            "quest_id",
            quest.getId()
        );

        attributes.put(
            "quest_name",
            quest.getDisplayName()
        );

        attributes.put(
            "quest_job_id",
            quest.getJobId()
        );

        attributes.put(
            "quest_type",
            quest.getType()
        );

        attributes.put(
            "quest_target",
            quest.getTarget()
        );

        attributes.put(
            "quest_progress",
            String.valueOf(quest.getProgress())
        );

        attributes.put(
            "quest_amount",
            String.valueOf(quest.getAmount())
        );

        attributes.put(
            "quest_remaining",
            String.valueOf(quest.getRemaining())
        );

        attributes.put(
            "quest_percent",
            String.valueOf(quest.getPercent())
        );

        attributes.put(
            "quest_min_level",
            String.valueOf(quest.getMinLevel())
        );

        attributes.put(
            "quest_reward_xp",
            String.valueOf(quest.getRewardXp())
        );

        attributes.put(
            "quest_stage",
            String.valueOf(quest.getStage())
        );

        attributes.put(
            "quest_stage_total",
            String.valueOf(quest.getStageTotal())
        );

        attributes.put(
            "quest_state",
            quest.getState()
        );

        attributes.put(
            "quest_state_name",
            quest.getStateName()
        );

        attributes.put(
            "quest_state_color",
            quest.getStateColor()
        );

        attributes.put(
            "quest_completed",
            String.valueOf(quest.isCompleted())
        );

        attributes.put(
            "quest_claimed",
            String.valueOf(quest.isClaimed())
        );

        attributes.put(
            "quest_claimable",
            String.valueOf(quest.isClaimable())
        );

        attributes.put(
            "quest_active",
            String.valueOf(quest.isActive())
        );

        attributes.put(
            "quest_locked",
            String.valueOf(quest.isLocked())
        );

        attributes.put(
            "quest_job_active",
            String.valueOf(quest.isJobActive())
        );

        attributes.put(
            "quest_completed_at",
            String.valueOf(quest.getCompletedAt())
        );
    }

    // -------------------------------------------------------------------------
    // CLASSEMENT
    // -------------------------------------------------------------------------

    private ContentSnapshot ranking(ContentRequest request) {

        String filter =
                normalizeJob(
                    request.getArguments()
                        .get("job_id")
                );

        String key =
                filter == null
                    ? "global"
                    : filter;

        RankingCacheEntry cached =
                rankingCache.get(key);

        long now =
                System.currentTimeMillis();

        if (cached == null
                || cached.expiresAt <= now) {

            triggerRankingLoad(
                key,
                filter,
                request.getPlayerId()
            );
        }

        if (cached == null) {

            if (request.getOffset() > 0) {
                return new ContentSnapshot(
                    revision(),
                    Collections.<ContentItem>emptyList(),
                    1
                );
            }

            ContentItem loading =
                    item(
                        "ranking/loading",
                        "WATCH",
                        (short) 0,
                        "&e",
                        "Chargement du classement…",
                        Collections.singletonList(
                            "&7Requête SQL asynchrone."
                        ),
                        Collections.<String, String>emptyMap()
                    );

            return new ContentSnapshot(
                revision(),
                Collections.singletonList(loading),
                1
            );
        }

        List<ContentItem> items =
                new ArrayList<ContentItem>();

        List<RankingEntry> page =
                slice(cached.entries, request);

        int position =
                request.getOffset();

        for (RankingEntry entry : page) {

            position++;

            String name =
                    playerName(entry.getUuid());

            items.add(
                item(
                    "rank/" + entry.getUuid(),

                    position <= 3
                        ? "GOLD_INGOT"
                        : "PAPER",

                    (short) 0,

                    position <= 3
                        ? "&6"
                        : "&e",

                    "#" + position + " " + name,

                    Arrays.asList(
                        "&7Classement: &f" + key,
                        "&7Niveau: &a" + entry.getLevel(),
                        "&7XP: &b" + entry.getXp()
                    ),

                    Collections.<String, String>emptyMap()
                )
            );
        }

        return new ContentSnapshot(
            revision(),
            items,
            cached.entries.size()
        );
    }

    // -------------------------------------------------------------------------
    // ABANDON
    // -------------------------------------------------------------------------

    private ContentSnapshot leaveConfirmation(ContentRequest request) {

        PlayerData data =
                data(request.getPlayerId());

        String jobId =
                normalizeJob(
                    request.getArguments()
                        .get("job_id")
                );

        JobDefinition job =
                jobId == null
                    ? null
                    : plugin.getJobRegistry().getJob(jobId);

        if (data == null
                || job == null
                || request.getOffset() > 0
                || !plugin.getSlotManager().isJobActive(data, jobId)
                || !plugin.getSlotManager().hasPendingChange(
                    request.getPlayerId()
                )) {

            return ContentSnapshot.empty(revision());
        }

        Map<String, String> attributes =
                new LinkedHashMap<String, String>();

        attributes.put(
            "job_id",
            jobId
        );

        attributes.put(
            "actions",
            "[kjobsultimate:confirm_leave]"
        );

        attributes.put(
            "right_actions",
            "[kjobsultimate:cancel_leave]"
        );

        ContentItem confirmation =
                item(
                    "leave/" + jobId,
                    iconMaterial(job),
                    iconData(job),
                    "&c",
                    "Confirmer l'abandon de "
                        + job.getDisplayName(),
                    Arrays.asList(
                        "&cToute la progression de ce job sera supprimée.",
                        "",
                        "&aClic gauche: confirmer",
                        "&7Clic droit: annuler"
                    ),
                    attributes
                );

        return new ContentSnapshot(
            revision(),
            Collections.singletonList(confirmation),
            1
        );
    }

    // -------------------------------------------------------------------------
    // CONTRAT ATTRIBUTS JOB
    // -------------------------------------------------------------------------

    private void putJobAttributes(
            Map<String, String> attributes,
            PlayerJobsView player,
            JobView job) {

        if (attributes == null
                || player == null
                || job == null) {

            return;
        }

        String state =
                job.isFavorite()
                    ? "favorite"
                    : job.isActive()
                        ? "active"
                        : "locked";

        String stateName =
                job.isFavorite()
                    ? "Favori"
                    : job.isActive()
                        ? "Débloqué"
                        : "Verrouillé";

        String stateColor =
                job.isFavorite()
                    ? "&6"
                    : job.isActive()
                        ? "&a"
                        : "&8";

        attributes.put(
            "job_id",
            job.getId()
        );

        attributes.put(
            "job_name",
            job.getDisplayName()
        );

        attributes.put(
            "level",
            String.valueOf(job.getLevel())
        );

        attributes.put(
            "max_level",
            String.valueOf(job.getMaxLevel())
        );

        attributes.put(
            "xp",
            String.valueOf(job.getXp())
        );

        attributes.put(
            "xp_required",
            String.valueOf(job.getXpRequired())
        );

        attributes.put(
            "xp_remaining",
            String.valueOf(job.getXpRemaining())
        );

        attributes.put(
            "xp_percent",
            String.valueOf(job.getXpPercent())
        );

        attributes.put(
            "max_level_reached",
            String.valueOf(job.isMaxLevelReached())
        );

        attributes.put(
            "active",
            String.valueOf(job.isActive())
        );

        attributes.put(
            "favorite",
            String.valueOf(job.isFavorite())
        );

        attributes.put(
            "slot",
            String.valueOf(job.getSlot())
        );

        attributes.put(
            "state",
            state
        );

        attributes.put(
            "state_name",
            stateName
        );

        attributes.put(
            "state_color",
            stateColor
        );

        attributes.put(
            "daily_xp",
            String.valueOf(job.getDailyXp())
        );

        attributes.put(
            "daily_xp_cap",
            String.valueOf(job.getDailyXpCap())
        );

        attributes.put(
            "daily_xp_remaining",
            String.valueOf(job.getDailyXpRemaining())
        );

        attributes.put(
            "daily_xp_cap_enabled",
            String.valueOf(job.isDailyXpCapEnabled())
        );

        attributes.put(
            "global_level",
            String.valueOf(player.getGlobalLevel())
        );

        attributes.put(
            "slots_unlocked",
            String.valueOf(player.getUnlockedSlots())
        );

        attributes.put(
            "slots_used",
            String.valueOf(player.getUsedSlots())
        );

        attributes.put(
            "slots_free",
            String.valueOf(player.getFreeSlots())
        );

        attributes.put(
            "slots_max",
            String.valueOf(player.getMaxSlots())
        );

        attributes.put(
            "icon_material",
            job.getIconMaterial()
        );

        attributes.put(
            "icon_data",
            String.valueOf(job.getIconData())
        );

        if (!job.getCit().isEmpty()) {
            attributes.put(
                "cit",
                job.getCit()
            );
        }
    }

    // -------------------------------------------------------------------------
    // RANKING ASYNC
    // -------------------------------------------------------------------------

    private void triggerRankingLoad(
            final String key,
            final String filter,
            UUID viewer) {

        rankingWaiters
            .computeIfAbsent(
                key,
                ignored -> Collections.newSetFromMap(
                    new ConcurrentHashMap<UUID, Boolean>()
                )
            )
            .add(viewer);

        if (!rankingLoads.add(key) || closed) {
            return;
        }

        final int limit =
                Math.max(
                    1,
                    Math.min(
                        50,
                        plugin.getConfigManager()
                            .getMainConfig()
                            .getInt("top.gui_limit", 50)
                    )
                );

        final long ttl =
                Math.max(
                    1L,
                    plugin.getConfigManager()
                        .getMainConfig()
                        .getLong("top.cache_seconds", 10L)
                ) * 1000L;

        Bukkit.getScheduler()
            .runTaskAsynchronously(
                plugin,
                new Runnable() {
                    @Override
                    public void run() {

                        List<RankingEntry> loaded =
                                Collections.emptyList();

                        try {
                            loaded =
                                plugin.getDatabaseManager()
                                    .getTop(filter, limit);
                        } catch (Exception failure) {
                            KjobLogger.error(
                                "[Kgui] Chargement du classement "
                                    + key
                                    + " impossible",
                                failure
                            );
                        }

                        final List<RankingEntry> result =
                                Collections.unmodifiableList(
                                    new ArrayList<RankingEntry>(loaded)
                                );

                        if (closed || !plugin.isEnabled()) {
                            rankingLoads.remove(key);
                            rankingWaiters.remove(key);
                            return;
                        }

                        try {
                            Bukkit.getScheduler()
                                .runTask(
                                    plugin,
                                    new Runnable() {
                                        @Override
                                        public void run() {

                                            rankingLoads.remove(key);

                                            if (closed) {
                                                return;
                                            }

                                            rankingCache.put(
                                                key,
                                                new RankingCacheEntry(
                                                    result,
                                                    System.currentTimeMillis()
                                                        + ttl
                                                )
                                            );

                                            Set<UUID> viewers =
                                                    rankingWaiters.remove(key);

                                            if (viewers == null) {
                                                return;
                                            }

                                            for (UUID playerId : viewers) {
                                                hook.invalidate(
                                                    playerId,
                                                    "kjobs:ranking-loaded",
                                                    "kjobs_top"
                                                );
                                            }
                                        }
                                    }
                                );
                        } catch (RuntimeException disabledDuringLoad) {
                            rankingLoads.remove(key);
                            rankingWaiters.remove(key);
                        }
                    }
                }
            );
    }

    // -------------------------------------------------------------------------
    // UTILITAIRES
    // -------------------------------------------------------------------------

    private PlayerData data(UUID playerId) {
        return playerId == null
            ? null
            : plugin.getPlayerDataManager().get(playerId);
    }

    private long revision() {
        return revisionEpoch.get();
    }

    private String displayJobName(String jobId) {

        JobDefinition job =
                plugin.getJobRegistry()
                    .getJob(jobId);

        return job == null
            ? jobId
            : job.getDisplayName();
    }

    private String playerName(UUID uuid) {

        Player online =
                Bukkit.getPlayer(uuid);

        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offline =
                Bukkit.getOfflinePlayer(uuid);

        String name =
                offline == null
                    ? null
                    : offline.getName();

        if (name != null
                && !name.trim().isEmpty()) {

            return name;
        }

        String value =
                uuid.toString();

        return value.substring(
            0,
            Math.min(8, value.length())
        );
    }

    private String normalizeJob(String raw) {

        if (raw == null
                || raw.trim().isEmpty()
                || "all".equalsIgnoreCase(raw)
                || "global".equalsIgnoreCase(raw)) {

            return null;
        }

        String value =
                raw.trim()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    );

        return plugin.getJobRegistry()
                .isValidJob(value)
            ? value
            : null;
    }

    private static String iconMaterial(JobDefinition job) {

        if (job.getIcon() == null) {
            return "BOOK";
        }

        String material =
                job.getIcon()
                    .getMaterial();

        return material == null
                || material.trim().isEmpty()
            ? "BOOK"
            : material;
    }

    private static short iconData(JobDefinition job) {
        return job.getIcon() == null
            ? 0
            : job.getIcon().getData();
    }

    private static String questMaterial(String state) {

        if ("claimable".equalsIgnoreCase(state)) {
            return "ENCHANTED_BOOK";
        }

        if ("claimed".equalsIgnoreCase(state)) {
            return "BOOK";
        }

        if ("locked_level".equalsIgnoreCase(state)) {
            return "REDSTONE";
        }

        if ("locked_chain".equalsIgnoreCase(state)) {
            return "IRON_FENCE";
        }

        if ("paused_job".equalsIgnoreCase(state)) {
            return "INK_SACK";
        }

        return "PAPER";
    }

    private static short questData(String state) {
        return "paused_job".equalsIgnoreCase(state)
            ? (short) 14
            : 0;
    }

    private static String questColor(String state) {

        if ("claimable".equalsIgnoreCase(state)) {
            return "&a";
        }

        if ("claimed".equalsIgnoreCase(state)) {
            return "&8";
        }

        if (state != null
                && state.startsWith("locked")) {

            return "&c";
        }

        if ("paused_job".equalsIgnoreCase(state)) {
            return "&6";
        }

        return "&e";
    }

    private static <T> List<T> slice(
            List<T> source,
            ContentRequest request) {

        if (source == null
                || source.isEmpty()
                || request.getOffset() >= source.size()) {

            return Collections.emptyList();
        }

        int end =
                Math.min(
                    source.size(),
                    request.getOffset()
                        + request.getLimit()
                );

        return new ArrayList<T>(
            source.subList(
                request.getOffset(),
                end
            )
        );
    }

    private static ContentItem item(
            String id,
            String material,
            short data,
            String color,
            String name,
            List<String> lore,
            Map<String, String> attributes) {

        return new ContentItem(
            id,
            material(material),
            data,
            1,
            bounded(
                safe(color, "")
                    + safe(name, id),
                512
            ),
            sanitizeLore(lore),
            sanitizeAttributes(attributes)
        );
    }

    private static String material(String requested) {

        String value =
                safe(
                    requested,
                    "BOOK"
                ).toUpperCase(
                    java.util.Locale.ROOT
                );

        return Material.getMaterial(value) == null
            ? "BOOK"
            : value;
    }

    private static List<String> sanitizeLore(
            List<String> source) {

        if (source == null
                || source.isEmpty()) {

            return Collections.emptyList();
        }

        List<String> result =
                new ArrayList<String>(
                    Math.min(64, source.size())
                );

        for (String line : source) {

            if (result.size() == 64) {
                break;
            }

            if (line != null) {
                result.add(
                    bounded(line, 1024)
                );
            }
        }

        return result;
    }

    private static Map<String, String> sanitizeAttributes(
            Map<String, String> source) {

        Map<String, String> result =
                new LinkedHashMap<String, String>();

        if (source == null) {
            return result;
        }

        for (Map.Entry<String, String> entry
                : source.entrySet()) {

            if (result.size() == 64) {
                break;
            }

            String key =
                    entry.getKey();

            String value =
                    entry.getValue();

            if (key == null
                    || value == null
                    || key.length() > 64
                    || !key.matches("[A-Za-z0-9_.-]+")) {

                continue;
            }

            result.put(
                key,
                bounded(value, 4096)
            );
        }

        return result;
    }

    private static String stableId(String value) {

        String safe =
                safe(
                    value,
                    "unknown"
                )
                .toLowerCase(
                    java.util.Locale.ROOT
                )
                .replaceAll(
                    "[^a-z0-9_.-]",
                    "_"
                );

        if (safe.length() > 72) {
            safe = safe.substring(0, 72);
        }

        return safe
            + "_"
            + Integer.toHexString(
                value == null
                    ? 0
                    : value.hashCode()
            );
    }

    private static String safe(
            String value,
            String fallback) {

        return value == null
                || value.trim().isEmpty()
            ? fallback
            : value;
    }

    private static String bounded(
            String value,
            int maximum) {

        return value == null
                || value.length() <= maximum
            ? value
            : value.substring(0, maximum);
    }

    private void add(
            KguiApi api,
            List<OwnedRegistration> handles,
            String id,
            ContentProvider provider) {

        handles.add(
            api.registerProvider(
                plugin,
                "kjobsultimate:" + id,
                provider
            )
        );
    }

    @Override
    public void close() {
        closed = true;
        rankingCache.clear();
        rankingWaiters.clear();
        rankingLoads.clear();
    }

    private static final class RankingCacheEntry {

        private final List<RankingEntry> entries;
        private final long expiresAt;

        private RankingCacheEntry(
                List<RankingEntry> entries,
                long expiresAt) {

            this.entries = entries;
            this.expiresAt = expiresAt;
        }
    }
}