package me.krunsh.kjobultimate.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.view.JobView;
import me.krunsh.kjobultimate.view.PlayerJobsView;
import me.krunsh.kjobultimate.view.QuestChainView;
import me.krunsh.kjobultimate.view.QuestView;
import me.krunsh.kjobultimate.view.QuestViewService;

/**
 * Expansion PlaceholderAPI V3 de KjobsUltimate.
 *
 * Les informations jobs et quêtes sont désormais lues via les couches View :
 *
 *   JobsViewService
 *   QuestViewService
 *
 * PAPI n'effectue donc plus lui-même les calculs de progression.
 *
 * ---------------------------------------------------------------------------
 * JOUEUR / GLOBAL
 * ---------------------------------------------------------------------------
 *
 * %kjob_display_job%
 * %kjob_display_job_name%
 * %kjob_has_display_job%
 *
 * %kjob_global_level%
 *
 * %kjob_slots%
 * %kjob_slots_unlocked%
 * %kjob_slots_used%
 * %kjob_slots_free%
 * %kjob_slots_max%
 *
 * %kjob_active_jobs%
 * %kjob_active_jobs_names%
 * %kjob_active_jobs_count%
 * %kjob_job_count%
 *
 * %kjob_quest_count%
 * %kjob_chain_count%
 * %kjob_claimable_quests%
 *
 * ---------------------------------------------------------------------------
 * MÉTIER
 * ---------------------------------------------------------------------------
 *
 * %kjob_name_<jobId>%
 *
 * %kjob_level_<jobId>%
 * %kjob_max_level_<jobId>%
 *
 * %kjob_xp_<jobId>%
 * %kjob_xp_next_<jobId>%          alias historique
 * %kjob_xp_required_<jobId>%
 * %kjob_xp_remaining_<jobId>%
 * %kjob_percent_<jobId>%
 *
 * %kjob_active_<jobId>%
 * %kjob_favorite_<jobId>%
 * %kjob_slot_<jobId>%
 *
 * %kjob_state_<jobId>%
 * %kjob_state_name_<jobId>%
 * %kjob_state_color_<jobId>%
 *
 * %kjob_max_level_reached_<jobId>%
 *
 * %kjob_daily_xp_<jobId>%
 * %kjob_daily_cap_<jobId>%
 * %kjob_daily_remaining_<jobId>%
 * %kjob_daily_cap_enabled_<jobId>%
 *
 * %kjob_icon_material_<jobId>%
 * %kjob_icon_data_<jobId>%
 * %kjob_cit_<jobId>%
 *
 * ---------------------------------------------------------------------------
 * QUÊTE
 * ---------------------------------------------------------------------------
 *
 * Compatibilité :
 * %kjob_quest_state_<questId>%
 * %kjob_quest_progress_<questId>%
 * %kjob_quest_amount_<questId>%
 * %kjob_quest_percent_<questId>%
 * %kjob_quest_chain_<questId>%
 * %kjob_quest_stage_<questId>%
 *
 * V3 :
 * %kjob_quest_name_<questId>%
 * %kjob_quest_job_<questId>%
 * %kjob_quest_type_<questId>%
 * %kjob_quest_target_<questId>%
 * %kjob_quest_remaining_<questId>%
 * %kjob_quest_min_level_<questId>%
 * %kjob_quest_reward_xp_<questId>%
 * %kjob_quest_stage_total_<questId>%
 * %kjob_quest_state_name_<questId>%
 * %kjob_quest_state_color_<questId>%
 * %kjob_quest_completed_<questId>%
 * %kjob_quest_claimed_<questId>%
 * %kjob_quest_claimable_<questId>%
 * %kjob_quest_active_<questId>%
 * %kjob_quest_locked_<questId>%
 * %kjob_quest_job_active_<questId>%
 * %kjob_quest_completed_at_<questId>%
 *
 * ---------------------------------------------------------------------------
 * CHAÎNE
 * ---------------------------------------------------------------------------
 *
 * Compatibilité :
 * %kjob_chain_active_<chainId>%
 * %kjob_chain_completed_<chainId>%
 *
 * V3 :
 * %kjob_chain_name_<chainId>%
 * %kjob_chain_job_<chainId>%
 * %kjob_chain_stage_<chainId>%
 * %kjob_chain_total_<chainId>%
 * %kjob_chain_claimed_<chainId>%
 * %kjob_chain_claimable_<chainId>%
 * %kjob_chain_remaining_<chainId>%
 * %kjob_chain_unclaimed_<chainId>%
 * %kjob_chain_progress_<chainId>%
 * %kjob_chain_amount_<chainId>%
 * %kjob_chain_percent_<chainId>%
 * %kjob_chain_complete_<chainId>%
 * %kjob_chain_fully_claimed_<chainId>%
 * %kjob_chain_job_active_<chainId>%
 * %kjob_chain_state_<chainId>%
 * %kjob_chain_state_name_<chainId>%
 * %kjob_chain_state_color_<chainId>%
 *
 * %kjob_chain_active_name_<chainId>%
 * %kjob_chain_active_state_<chainId>%
 * %kjob_chain_active_progress_<chainId>%
 * %kjob_chain_active_amount_<chainId>%
 * %kjob_chain_active_percent_<chainId>%
 */
public final class PAPIHook extends PlaceholderExpansion {

    private static final String IDENTIFIER = "kjob";
    private static final String AUTHOR = "krunsh";

    private final KjobUltimate plugin;

    public PAPIHook(KjobUltimate plugin) {
        this.plugin = Objects.requireNonNull(
            plugin,
            "KjobUltimate ne peut pas être null."
        );
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getAuthor() {
        return AUTHOR;
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    public boolean register() {
        return super.register();
    }

    @Override
    public String onPlaceholderRequest(
            Player player,
            String rawParams) {

        if (player == null || rawParams == null) {
            return "";
        }

        String params =
                normalizeParams(rawParams);

        if (params.isEmpty()) {
            return null;
        }

        PlayerJobsView jobsView =
                plugin.getJobsViewService() == null
                    ? null
                    : plugin.getJobsViewService()
                        .getPlayer(player);

        if (jobsView != null) {

            String value =
                    resolvePlayerPlaceholder(
                        jobsView,
                        params
                    );

            if (value != null) {
                return value;
            }

            value =
                resolveJobPlaceholder(
                    jobsView,
                    params
                );

            if (value != null) {
                return value;
            }
        }

        QuestViewService questService =
                plugin.getQuestViewService();

        if (questService != null) {

            String value =
                    resolveQuestGlobalPlaceholder(
                        player,
                        questService,
                        params
                    );

            if (value != null) {
                return value;
            }

            value =
                resolveQuestPlaceholder(
                    player,
                    questService,
                    params
                );

            if (value != null) {
                return value;
            }

            value =
                resolveChainPlaceholder(
                    player,
                    questService,
                    params
                );

            if (value != null) {
                return value;
            }
        }

        if (isKnownKjobsNamespace(params)
                && (jobsView == null
                    || questService == null)) {

            return "";
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // JOUEUR / GLOBAL
    // -------------------------------------------------------------------------

    private String resolvePlayerPlaceholder(
            PlayerJobsView view,
            String params) {

        if ("display_job".equals(params)) {
            return view.getDisplayJobId();
        }

        if ("display_job_name".equals(params)) {
            return view.getDisplayJobName();
        }

        if ("has_display_job".equals(params)) {
            return bool(view.hasDisplayJob());
        }

        if ("global_level".equals(params)) {
            return number(view.getGlobalLevel());
        }

        if ("slots".equals(params)
                || "slots_unlocked".equals(params)) {

            return number(
                view.getUnlockedSlots()
            );
        }

        if ("slots_used".equals(params)) {
            return number(
                view.getUsedSlots()
            );
        }

        if ("slots_free".equals(params)) {
            return number(
                view.getFreeSlots()
            );
        }

        if ("slots_max".equals(params)) {
            return number(
                view.getMaxSlots()
            );
        }

        if ("active_jobs_count".equals(params)) {
            return number(
                view.getActiveJobCount()
            );
        }

        if ("job_count".equals(params)) {
            return number(
                view.getJobCount()
            );
        }

        if ("active_jobs".equals(params)) {
            return joinActiveJobs(
                view,
                false
            );
        }

        if ("active_jobs_names".equals(params)) {
            return joinActiveJobs(
                view,
                true
            );
        }

        return null;
    }

    private String resolveQuestGlobalPlaceholder(
            Player player,
            QuestViewService service,
            String params) {

        if ("quest_count".equals(params)) {
            return number(
                service.getQuestCount()
            );
        }

        if ("chain_count".equals(params)) {
            return number(
                service.getChainCount()
            );
        }

        if ("claimable_quests".equals(params)) {
            return number(
                service.getClaimableQuestCount(
                    player.getUniqueId()
                )
            );
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // MÉTIERS
    // -------------------------------------------------------------------------

    private String resolveJobPlaceholder(
            PlayerJobsView view,
            String params) {

        JobPlaceholder parsed =
                parseJobPlaceholder(params);

        if (parsed == null) {
            return null;
        }

        JobView job =
                view.getJob(parsed.jobId);

        if (job == null) {
            return defaultValueForJobField(
                parsed.field
            );
        }

        if ("name".equals(parsed.field)) {
            return job.getDisplayName();
        }

        if ("level".equals(parsed.field)) {
            return number(job.getLevel());
        }

        if ("max_level".equals(parsed.field)) {
            return number(job.getMaxLevel());
        }

        if ("xp".equals(parsed.field)) {
            return number(job.getXp());
        }

        if ("xp_next".equals(parsed.field)
                || "xp_required".equals(parsed.field)) {

            return number(
                job.getXpRequired()
            );
        }

        if ("xp_remaining".equals(parsed.field)) {
            return number(
                job.getXpRemaining()
            );
        }

        if ("percent".equals(parsed.field)) {
            return number(
                job.getXpPercent()
            );
        }

        if ("active".equals(parsed.field)) {
            return bool(job.isActive());
        }

        if ("favorite".equals(parsed.field)) {
            return bool(job.isFavorite());
        }

        if ("slot".equals(parsed.field)) {
            return number(job.getSlot());
        }

        if ("state".equals(parsed.field)) {
            return jobState(job);
        }

        if ("state_name".equals(parsed.field)) {
            return jobStateName(job);
        }

        if ("state_color".equals(parsed.field)) {
            return jobStateColor(job);
        }

        if ("max_level_reached".equals(parsed.field)) {
            return bool(
                job.isMaxLevelReached()
            );
        }

        if ("daily_xp".equals(parsed.field)) {
            return number(
                job.getDailyXp()
            );
        }

        if ("daily_cap".equals(parsed.field)) {
            return number(
                job.getDailyXpCap()
            );
        }

        if ("daily_remaining".equals(parsed.field)) {
            return number(
                job.getDailyXpRemaining()
            );
        }

        if ("daily_cap_enabled".equals(parsed.field)) {
            return bool(
                job.isDailyXpCapEnabled()
            );
        }

        if ("icon_material".equals(parsed.field)) {
            return job.getIconMaterial();
        }

        if ("icon_data".equals(parsed.field)) {
            return number(
                job.getIconData()
            );
        }

        if ("cit".equals(parsed.field)) {
            return job.getCit();
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // QUÊTES
    // -------------------------------------------------------------------------

    private String resolveQuestPlaceholder(
            Player player,
            QuestViewService service,
            String params) {

        QuestPlaceholder parsed =
                parseQuestPlaceholder(params);

        if (parsed == null) {
            return null;
        }

        QuestView quest =
                service.getQuest(
                    player,
                    parsed.questId
                );

        if (quest == null) {
            return defaultValueForQuestField(
                parsed.field
            );
        }

        if ("state".equals(parsed.field)) {
            return quest.getState();
        }

        if ("state_name".equals(parsed.field)) {
            return quest.getStateName();
        }

        if ("state_color".equals(parsed.field)) {
            return quest.getStateColor();
        }

        if ("progress".equals(parsed.field)) {
            return number(
                quest.getProgress()
            );
        }

        if ("amount".equals(parsed.field)) {
            return number(
                quest.getAmount()
            );
        }

        if ("remaining".equals(parsed.field)) {
            return number(
                quest.getRemaining()
            );
        }

        if ("percent".equals(parsed.field)) {
            return number(
                quest.getPercent()
            );
        }

        if ("chain".equals(parsed.field)) {
            return quest.getChainId();
        }

        if ("stage".equals(parsed.field)) {
            return number(
                quest.getStage()
            );
        }

        if ("stage_total".equals(parsed.field)) {
            return number(
                quest.getStageTotal()
            );
        }

        if ("name".equals(parsed.field)) {
            return quest.getDisplayName();
        }

        if ("job".equals(parsed.field)) {
            return quest.getJobId();
        }

        if ("type".equals(parsed.field)) {
            return quest.getType();
        }

        if ("target".equals(parsed.field)) {
            return quest.getTarget();
        }

        if ("min_level".equals(parsed.field)) {
            return number(
                quest.getMinLevel()
            );
        }

        if ("reward_xp".equals(parsed.field)) {
            return number(
                quest.getRewardXp()
            );
        }

        if ("completed".equals(parsed.field)) {
            return bool(
                quest.isCompleted()
            );
        }

        if ("claimed".equals(parsed.field)) {
            return bool(
                quest.isClaimed()
            );
        }

        if ("claimable".equals(parsed.field)) {
            return bool(
                quest.isClaimable()
            );
        }

        if ("active".equals(parsed.field)) {
            return bool(
                quest.isActive()
            );
        }

        if ("locked".equals(parsed.field)) {
            return bool(
                quest.isLocked()
            );
        }

        if ("job_active".equals(parsed.field)) {
            return bool(
                quest.isJobActive()
            );
        }

        if ("completed_at".equals(parsed.field)) {
            return number(
                quest.getCompletedAt()
            );
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // CHAÎNES
    // -------------------------------------------------------------------------

    private String resolveChainPlaceholder(
            Player player,
            QuestViewService service,
            String params) {

        ChainPlaceholder parsed =
                parseChainPlaceholder(params);

        if (parsed == null) {
            return null;
        }

        QuestChainView chain =
                service.getChain(
                    player,
                    parsed.chainId
                );

        if (chain == null) {
            return defaultValueForChainField(
                parsed.field
            );
        }

        QuestView active =
                chain.getActiveQuest();

        /*
         * Compatibilité historique :
         * chain_active retourne l'ID de l'étape active.
         */
        if ("active".equals(parsed.field)) {
            return active == null
                ? ""
                : active.getId();
        }

        if ("active_name".equals(parsed.field)) {
            return active == null
                ? ""
                : active.getDisplayName();
        }

        if ("active_state".equals(parsed.field)) {
            return active == null
                ? ""
                : active.getState();
        }

        if ("active_progress".equals(parsed.field)) {
            return active == null
                ? "0"
                : number(active.getProgress());
        }

        if ("active_amount".equals(parsed.field)) {
            return active == null
                ? "0"
                : number(active.getAmount());
        }

        if ("active_percent".equals(parsed.field)) {
            return active == null
                ? "0"
                : number(active.getPercent());
        }

        if ("name".equals(parsed.field)) {
            return chain.getDisplayName();
        }

        if ("job".equals(parsed.field)) {
            return chain.getJobId();
        }

        if ("stage".equals(parsed.field)) {
            return number(
                chain.getCurrentStage()
            );
        }

        if ("total".equals(parsed.field)) {
            return number(
                chain.getStageTotal()
            );
        }

        if ("completed".equals(parsed.field)) {
            return number(
                chain.getCompletedStages()
            );
        }

        if ("claimed".equals(parsed.field)) {
            return number(
                chain.getClaimedStages()
            );
        }

        if ("claimable".equals(parsed.field)) {
            return number(
                chain.getClaimableStages()
            );
        }

        if ("remaining".equals(parsed.field)) {
            return number(
                chain.getRemainingStages()
            );
        }

        if ("unclaimed".equals(parsed.field)) {
            return number(
                chain.getUnclaimedStages()
            );
        }

        if ("progress".equals(parsed.field)) {
            return number(
                chain.getProgress()
            );
        }

        if ("amount".equals(parsed.field)) {
            return number(
                chain.getAmount()
            );
        }

        if ("percent".equals(parsed.field)) {
            return number(
                chain.getPercent()
            );
        }

        if ("complete".equals(parsed.field)) {
            return bool(
                chain.isComplete()
            );
        }

        if ("fully_claimed".equals(parsed.field)) {
            return bool(
                chain.isFullyClaimed()
            );
        }

        if ("job_active".equals(parsed.field)) {
            return bool(
                chain.isJobActive()
            );
        }

        if ("state".equals(parsed.field)) {
            return chain.getState();
        }

        if ("state_name".equals(parsed.field)) {
            return chain.getStateName();
        }

        if ("state_color".equals(parsed.field)) {
            return chain.getStateColor();
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // PARSERS
    // -------------------------------------------------------------------------

    private static JobPlaceholder parseJobPlaceholder(
            String params) {

        String[] fields = {
            "max_level_reached",
            "daily_cap_enabled",
            "daily_remaining",
            "icon_material",
            "xp_remaining",
            "xp_required",
            "state_color",
            "state_name",
            "daily_cap",
            "daily_xp",
            "max_level",
            "icon_data",
            "favorite",
            "xp_next",
            "percent",
            "active",
            "state",
            "level",
            "slot",
            "name",
            "cit",
            "xp"
        };

        for (String field : fields) {

            String prefix =
                    field + "_";

            if (!params.startsWith(prefix)) {
                continue;
            }

            String jobId =
                    normalizeId(
                        params.substring(
                            prefix.length()
                        )
                    );

            if (jobId.isEmpty()) {
                return null;
            }

            return new JobPlaceholder(
                field,
                jobId
            );
        }

        return null;
    }

    private static QuestPlaceholder parseQuestPlaceholder(
            String params) {

        if (!params.startsWith("quest_")) {
            return null;
        }

        String payload =
                params.substring(
                    "quest_".length()
                );

        String[] fields = {
            "completed_at",
            "stage_total",
            "state_color",
            "state_name",
            "job_active",
            "min_level",
            "reward_xp",
            "remaining",
            "completed",
            "claimable",
            "progress",
            "percent",
            "claimed",
            "locked",
            "amount",
            "target",
            "active",
            "chain",
            "stage",
            "state",
            "type",
            "name",
            "job"
        };

        for (String field : fields) {

            String prefix =
                    field + "_";

            if (!payload.startsWith(prefix)) {
                continue;
            }

            String questId =
                    payload.substring(
                        prefix.length()
                    ).trim();

            if (questId.isEmpty()) {
                return null;
            }

            return new QuestPlaceholder(
                field,
                questId
            );
        }

        return null;
    }

    private static ChainPlaceholder parseChainPlaceholder(
            String params) {

        if (!params.startsWith("chain_")) {
            return null;
        }

        String payload =
                params.substring(
                    "chain_".length()
                );

        String[] fields = {
            "active_progress",
            "active_percent",
            "active_amount",
            "active_state",
            "active_name",
            "fully_claimed",
            "state_color",
            "state_name",
            "job_active",
            "claimable",
            "remaining",
            "unclaimed",
            "completed",
            "progress",
            "percent",
            "claimed",
            "complete",
            "active",
            "amount",
            "state",
            "stage",
            "total",
            "name",
            "job"
        };

        for (String field : fields) {

            String prefix =
                    field + "_";

            if (!payload.startsWith(prefix)) {
                continue;
            }

            String chainId =
                    payload.substring(
                        prefix.length()
                    ).trim();

            if (chainId.isEmpty()) {
                return null;
            }

            return new ChainPlaceholder(
                field,
                chainId
            );
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // VALEURS PAR DÉFAUT
    // -------------------------------------------------------------------------

    private static String defaultValueForJobField(
            String field) {

        if ("name".equals(field)
                || "state".equals(field)
                || "state_name".equals(field)
                || "state_color".equals(field)
                || "icon_material".equals(field)
                || "cit".equals(field)) {

            return "";
        }

        if ("active".equals(field)
                || "favorite".equals(field)
                || "max_level_reached".equals(field)
                || "daily_cap_enabled".equals(field)) {

            return "false";
        }

        if ("slot".equals(field)) {
            return "-1";
        }

        return "0";
    }

    private static String defaultValueForQuestField(
            String field) {

        if ("state".equals(field)
                || "state_name".equals(field)
                || "state_color".equals(field)
                || "chain".equals(field)
                || "name".equals(field)
                || "job".equals(field)
                || "type".equals(field)
                || "target".equals(field)) {

            return "";
        }

        if ("completed".equals(field)
                || "claimed".equals(field)
                || "claimable".equals(field)
                || "active".equals(field)
                || "locked".equals(field)
                || "job_active".equals(field)) {

            return "false";
        }

        return "0";
    }

    private static String defaultValueForChainField(
            String field) {

        if ("active".equals(field)
                || "active_name".equals(field)
                || "active_state".equals(field)
                || "name".equals(field)
                || "job".equals(field)
                || "state".equals(field)
                || "state_name".equals(field)
                || "state_color".equals(field)) {

            return "";
        }

        if ("complete".equals(field)
                || "fully_claimed".equals(field)
                || "job_active".equals(field)) {

            return "false";
        }

        return "0";
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private static String jobState(
            JobView job) {

        if (job.isFavorite()) {
            return "favorite";
        }

        if (job.isActive()) {
            return "active";
        }

        return "locked";
    }

    private static String jobStateName(
            JobView job) {

        if (job.isFavorite()) {
            return "Favori";
        }

        if (job.isActive()) {
            return "Débloqué";
        }

        return "Verrouillé";
    }

    private static String jobStateColor(
            JobView job) {

        if (job.isFavorite()) {
            return "&6";
        }

        if (job.isActive()) {
            return "&a";
        }

        return "&8";
    }

    private static String joinActiveJobs(
            PlayerJobsView view,
            boolean displayNames) {

        List<String> values =
                new ArrayList<String>();

        for (JobView job : view.getJobs()) {

            if (job == null
                    || !job.isActive()) {

                continue;
            }

            values.add(
                displayNames
                    ? job.getDisplayName()
                    : job.getId()
            );
        }

        if (values.isEmpty()) {
            return "";
        }

        StringBuilder output =
                new StringBuilder();

        for (String value : values) {

            if (output.length() > 0) {
                output.append(", ");
            }

            output.append(value);
        }

        return output.toString();
    }

    private static boolean isKnownKjobsNamespace(
            String params) {

        return params.startsWith("quest_")
            || params.startsWith("chain_")
            || params.startsWith("display_job")
            || params.startsWith("slots")
            || params.startsWith("active_jobs")
            || params.startsWith("global_level")
            || params.startsWith("job_count")
            || params.startsWith("claimable_quests")
            || parseJobPlaceholder(params) != null;
    }

    private static String normalizeParams(
            String value) {

        return value.trim()
            .toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(
            String value) {

        return value == null
            ? ""
            : value.trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String bool(
            boolean value) {

        return value
            ? "true"
            : "false";
    }

    private static String number(
            int value) {

        return String.valueOf(value);
    }

    private static String number(
            long value) {

        return String.valueOf(value);
    }

    private static final class JobPlaceholder {

        private final String field;
        private final String jobId;

        private JobPlaceholder(
                String field,
                String jobId) {

            this.field = field;
            this.jobId = jobId;
        }
    }

    private static final class QuestPlaceholder {

        private final String field;
        private final String questId;

        private QuestPlaceholder(
                String field,
                String questId) {

            this.field = field;
            this.questId = questId;
        }
    }

    private static final class ChainPlaceholder {

        private final String field;
        private final String chainId;

        private ChainPlaceholder(
                String field,
                String chainId) {

            this.field = field;
            this.chainId = chainId;
        }
    }
}
