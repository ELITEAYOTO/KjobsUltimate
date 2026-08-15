package me.krunsh.kjobultimate.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.QuestData;
import me.krunsh.kjobultimate.quests.QuestChainDefinition;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.quests.QuestManager;
import me.krunsh.kjobultimate.view.JobView;
import me.krunsh.kjobultimate.view.PlayerJobsView;

/**
 * Expansion PlaceholderAPI V3 de KjobsUltimate.
 *
 * Principe V3 :
 * - toutes les informations de jobs passent par JobsViewService ;
 * - PAPI ne recalcule plus niveau, XP, pourcentage, slots ou état des jobs ;
 * - les anciens placeholders restent compatibles ;
 * - les nouveaux placeholders exposent un contrat beaucoup plus complet.
 *
 * Préfixe PlaceholderAPI :
 *
 *   %kjob_<placeholder>%
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
 * %kjob_slots%                 alias historique de slots_unlocked
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
 * ---------------------------------------------------------------------------
 * MÉTIER — remplacer <jobId> par mineur, farmer, hunter, etc.
 * ---------------------------------------------------------------------------
 *
 * %kjob_name_<jobId>%
 *
 * %kjob_level_<jobId>%
 * %kjob_max_level_<jobId>%
 *
 * %kjob_xp_<jobId>%
 * %kjob_xp_next_<jobId>%       alias historique
 * %kjob_xp_required_<jobId>%
 * %kjob_xp_remaining_<jobId>%
 * %kjob_percent_<jobId>%
 *
 * %kjob_active_<jobId>%
 * %kjob_favorite_<jobId>%
 * %kjob_slot_<jobId>%
 *
 * %kjob_state_<jobId>%         favorite / active / locked
 * %kjob_state_name_<jobId>%    Favori / Débloqué / Verrouillé
 * %kjob_state_color_<jobId>%   &6 / &a / &8
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
 * QUÊTES — compatibilité V2, migration vers QuestView prévue en V3.4
 * ---------------------------------------------------------------------------
 *
 * %kjob_quest_state_<questId>%
 * %kjob_quest_progress_<questId>%
 * %kjob_quest_amount_<questId>%
 * %kjob_quest_percent_<questId>%
 * %kjob_quest_chain_<questId>%
 * %kjob_quest_stage_<questId>%
 *
 * %kjob_chain_active_<chainId>%
 * %kjob_chain_completed_<chainId>%
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

    /**
     * Garde l'expansion active lors d'un /papi reload.
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * Point d'entrée explicite conservé pour HookManager.
     */
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

        String params = normalizeParams(rawParams);

        if (params.isEmpty()) {
            return null;
        }

        PlayerJobsView jobsView =
                plugin.getJobsViewService() == null
                    ? null
                    : plugin.getJobsViewService()
                        .getPlayer(player);

        PlayerData data =
                plugin.getPlayerDataManager() == null
                    ? null
                    : plugin.getPlayerDataManager()
                        .get(player);

        /*
         * Les placeholders jobs/global utilisent uniquement la couche View V3.
         */
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

        /*
         * Les quêtes restent temporairement sur PlayerData/QuestManager.
         * Elles seront migrées vers QuestView/QuestChainView en V3.4.
         */
        if (data != null) {

            String value =
                    resolveQuestPlaceholder(
                        data,
                        params
                    );

            if (value != null) {
                return value;
            }
        }

        /*
         * Si le placeholder appartient clairement à Kjobs mais que les données
         * du joueur ne sont pas encore prêtes, retourner une valeur vide évite
         * d'afficher le placeholder brut pendant le chargement.
         */
        if (isKnownKjobsNamespace(params)
                && (jobsView == null || data == null)) {

            return "";
        }

        /*
         * Placeholder inconnu : convention PlaceholderAPI.
         */
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

        /*
         * Alias historique conservé.
         */
        if ("slots".equals(params)
                || "slots_unlocked".equals(params)) {

            return number(view.getUnlockedSlots());
        }

        if ("slots_used".equals(params)) {
            return number(view.getUsedSlots());
        }

        if ("slots_free".equals(params)) {
            return number(view.getFreeSlots());
        }

        if ("slots_max".equals(params)) {
            return number(view.getMaxSlots());
        }

        if ("active_jobs_count".equals(params)) {
            return number(view.getActiveJobCount());
        }

        if ("job_count".equals(params)) {
            return number(view.getJobCount());
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

        /*
         * Le préfixe est valide mais le job n'existe pas :
         * on retourne une valeur neutre au lieu de laisser apparaître le
         * placeholder brut.
         */
        if (job == null) {
            return defaultValueForField(
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

        /*
         * xp_next est l'ancien nom.
         * xp_required est le nom V3 recommandé.
         */
        if ("xp_next".equals(parsed.field)
                || "xp_required".equals(parsed.field)) {

            return number(job.getXpRequired());
        }

        if ("xp_remaining".equals(parsed.field)) {
            return number(job.getXpRemaining());
        }

        if ("percent".equals(parsed.field)) {
            return number(job.getXpPercent());
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
            return state(job);
        }

        if ("state_name".equals(parsed.field)) {
            return stateName(job);
        }

        if ("state_color".equals(parsed.field)) {
            return stateColor(job);
        }

        if ("max_level_reached".equals(parsed.field)) {
            return bool(job.isMaxLevelReached());
        }

        if ("daily_xp".equals(parsed.field)) {
            return number(job.getDailyXp());
        }

        if ("daily_cap".equals(parsed.field)) {
            return number(job.getDailyXpCap());
        }

        if ("daily_remaining".equals(parsed.field)) {
            return number(job.getDailyXpRemaining());
        }

        if ("daily_cap_enabled".equals(parsed.field)) {
            return bool(job.isDailyXpCapEnabled());
        }

        if ("icon_material".equals(parsed.field)) {
            return job.getIconMaterial();
        }

        if ("icon_data".equals(parsed.field)) {
            return number(job.getIconData());
        }

        if ("cit".equals(parsed.field)) {
            return job.getCit();
        }

        return null;
    }

    /**
     * Parse les placeholders par préfixes explicites.
     *
     * On évite de simplement couper au premier "_" car plusieurs champs V3
     * contiennent eux-mêmes des underscores :
     *
     * xp_required_mineur
     * daily_cap_enabled_mineur
     * state_color_mineur
     */
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
                    normalizeJobId(
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

    private static String defaultValueForField(
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

        /*
         * slot d'un job inexistant/inactif.
         */
        if ("slot".equals(field)) {
            return "-1";
        }

        return "0";
    }

    private static String state(JobView job) {

        if (job.isFavorite()) {
            return "favorite";
        }

        if (job.isActive()) {
            return "active";
        }

        return "locked";
    }

    private static String stateName(JobView job) {

        if (job.isFavorite()) {
            return "Favori";
        }

        if (job.isActive()) {
            return "Débloqué";
        }

        return "Verrouillé";
    }

    private static String stateColor(JobView job) {

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

            if (job == null || !job.isActive()) {
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

    // -------------------------------------------------------------------------
    // QUÊTES — compatibilité V2
    // -------------------------------------------------------------------------

    private String resolveQuestPlaceholder(
            PlayerData data,
            String params) {

        QuestManager quests =
                plugin.getQuestManager();

        if (quests == null) {
            return isQuestPlaceholder(params)
                ? ""
                : null;
        }

        if (params.startsWith("quest_state_")) {

            QuestDefinition quest =
                    quests.getQuest(
                        params.substring(
                            "quest_state_".length()
                        )
                    );

            return quest == null
                ? ""
                : quests.getQuestState(
                    data,
                    quest
                );
        }

        if (params.startsWith("quest_progress_")) {

            QuestDefinition quest =
                    quests.getQuest(
                        params.substring(
                            "quest_progress_".length()
                        )
                    );

            if (quest == null) {
                return "";
            }

            QuestData progress =
                    data.getQuestProgress()
                        .get(quest.getId());

            return number(
                progress == null
                    ? 0
                    : Math.max(
                        0,
                        progress.getProgress()
                    )
            );
        }

        if (params.startsWith("quest_amount_")) {

            QuestDefinition quest =
                    quests.getQuest(
                        params.substring(
                            "quest_amount_".length()
                        )
                    );

            return quest == null
                ? ""
                : number(
                    Math.max(
                        1,
                        quest.getAmount()
                    )
                );
        }

        if (params.startsWith("quest_percent_")) {

            QuestDefinition quest =
                    quests.getQuest(
                        params.substring(
                            "quest_percent_".length()
                        )
                    );

            if (quest == null) {
                return "";
            }

            QuestData progress =
                    data.getQuestProgress()
                        .get(quest.getId());

            int current =
                    progress == null
                        ? 0
                        : Math.max(
                            0,
                            progress.getProgress()
                        );

            return number(
                calculatePercentage(
                    current,
                    quest.getAmount()
                )
            );
        }

        if (params.startsWith("quest_chain_")) {

            QuestDefinition quest =
                    quests.getQuest(
                        params.substring(
                            "quest_chain_".length()
                        )
                    );

            return quest == null
                ? ""
                : safe(quest.getChainId());
        }

        if (params.startsWith("quest_stage_")) {

            QuestDefinition quest =
                    quests.getQuest(
                        params.substring(
                            "quest_stage_".length()
                        )
                    );

            return quest == null
                ? ""
                : number(quest.getChainStage());
        }

        if (params.startsWith("chain_active_")) {

            QuestDefinition active =
                    quests.getActiveQuest(
                        data,
                        params.substring(
                            "chain_active_".length()
                        )
                    );

            return active == null
                ? ""
                : active.getId();
        }

        if (params.startsWith("chain_completed_")) {

            QuestChainDefinition chain =
                    quests.getChain(
                        params.substring(
                            "chain_completed_".length()
                        )
                    );

            if (chain == null) {
                return "";
            }

            int completed = 0;

            for (QuestDefinition stage
                    : chain.getStages()) {

                QuestData progress =
                        data.getQuestProgress()
                            .get(stage.getId());

                if (progress != null
                        && progress.isCompleted()) {

                    completed++;
                }
            }

            return number(completed);
        }

        return null;
    }

    private static int calculatePercentage(
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

    // -------------------------------------------------------------------------
    // UTILITAIRES
    // -------------------------------------------------------------------------

    private static boolean isQuestPlaceholder(
            String params) {

        return params.startsWith("quest_")
            || params.startsWith("chain_");
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
            || parseJobPlaceholder(params) != null;
    }

    private static String normalizeParams(
            String value) {

        return value.trim()
            .toLowerCase(Locale.ROOT);
    }

    private static String normalizeJobId(
            String value) {

        return value == null
            ? ""
            : value.trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String safe(
            String value) {

        return value == null
            ? ""
            : value;
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

    /**
     * Résultat du parser métier.
     */
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
}