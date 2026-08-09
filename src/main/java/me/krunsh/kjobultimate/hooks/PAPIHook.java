package me.krunsh.kjobultimate.hooks;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.QuestData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.quests.QuestChainDefinition;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.quests.QuestManager;
import me.krunsh.kjobultimate.util.LevelUtil;

/**
 * Intégration PlaceholderAPI de KjobsUltimate.
 *
 * Tous les placeholders liés à l'XP utilisent LevelUtil et la convention
 * officielle du projet :
 *
 * - le niveau stocké dans PlayerData est le niveau ACTUEL ;
 * - l'XP stockée correspond à la progression dans ce niveau ;
 * - l'XP suivante est calculée avec
 *   JobDefinition#getXpRequiredForNextLevel(currentLevel).
 *
 * Placeholders métiers :
 *
 * %kjob_level_<jobId>%          niveau actuel
 * %kjob_xp_<jobId>%             XP actuelle dans le niveau
 * %kjob_xp_next_<jobId>%        XP nécessaire pour le niveau suivant
 * %kjob_percent_<jobId>%        progression entière de 0 à 100
 * %kjob_max_level_<jobId>%      niveau maximum
 *
 * %kjob_display_job%            identifiant du métier favori
 * %kjob_display_job_name%       nom affiché du métier favori
 * %kjob_slots%                  nombre de slots débloqués
 *
 * Placeholders quêtes :
 *
 * %kjob_quest_state_<questId>%
 * %kjob_quest_progress_<questId>%
 * %kjob_quest_amount_<questId>%
 * %kjob_quest_percent_<questId>%
 * %kjob_quest_chain_<questId>%
 * %kjob_quest_stage_<questId>%
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
            "KjobUltimate ne peut pas être null.");
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
     * Conservé comme point d'entrée explicite pour HookManager.
     */
    public boolean register() {
        return super.register();
    }

    @Override
    public String onPlaceholderRequest(Player player, String rawParams) {
        if (player == null || rawParams == null) {
            return "";
        }

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            return "";
        }

        String params = normalizeParams(rawParams);
        if (params.isEmpty()) {
            return null;
        }

        String value = resolvePlayerPlaceholder(data, params);
        if (value != null) {
            return value;
        }

        value = resolveJobPlaceholder(data, params);
        if (value != null) {
            return value;
        }

        value = resolveQuestPlaceholder(data, params);
        if (value != null) {
            return value;
        }

        // Placeholder inconnu : convention PlaceholderAPI.
        return null;
    }

    private String resolvePlayerPlaceholder(
            PlayerData data,
            String params) {

        if ("display_job".equals(params)) {
            return normalizeJobId(data.getDisplayJob());
        }

        if ("display_job_name".equals(params)) {
            String displayJob = normalizeJobId(data.getDisplayJob());
            if (displayJob.isEmpty()) {
                return "";
            }

            JobDefinition job =
                plugin.getJobRegistry().getJob(displayJob);

            return job == null
                ? displayJob
                : job.getDisplayName();
        }

        if ("slots".equals(params)) {
            return String.valueOf(
                Math.max(0, data.getUnlockedSlots()));
        }

        return null;
    }

    private String resolveJobPlaceholder(
            PlayerData data,
            String params) {

        if (params.startsWith("xp_next_")) {
            JobDefinition job =
                findJob(params.substring("xp_next_".length()));

            if (job == null) {
                return "0";
            }

            return String.valueOf(
                LevelUtil.getRequiredXpForNextLevel(data, job));
        }

        if (params.startsWith("max_level_")) {
            JobDefinition job =
                findJob(params.substring("max_level_".length()));

            return job == null
                ? "0"
                : String.valueOf(job.getMaxLevel());
        }

        if (params.startsWith("percent_")) {
            JobDefinition job =
                findJob(params.substring("percent_".length()));

            if (job == null) {
                return "0";
            }

            return String.valueOf(
                LevelUtil.getProgressPercentage(data, job));
        }

        if (params.startsWith("level_")) {
            JobDefinition job =
                findJob(params.substring("level_".length()));

            if (job == null) {
                return "0";
            }

            return String.valueOf(
                sanitizeLevel(data.getLevel(job.getId()), job));
        }

        if (params.startsWith("xp_")) {
            JobDefinition job =
                findJob(params.substring("xp_".length()));

            if (job == null) {
                return "0";
            }

            if (job.isMaxLevel(data.getLevel(job.getId()))) {
                return "0";
            }

            return String.valueOf(
                LevelUtil.getCurrentLevelXp(data, job));
        }

        return null;
    }

    private String resolveQuestPlaceholder(
            PlayerData data,
            String params) {

        QuestManager quests = plugin.getQuestManager();
        if (quests == null) {
            return isQuestPlaceholder(params) ? "" : null;
        }

        if (params.startsWith("quest_state_")) {
            QuestDefinition quest =
                quests.getQuest(
                    params.substring("quest_state_".length()));

            return quest == null
                ? ""
                : quests.getQuestState(data, quest);
        }

        if (params.startsWith("quest_progress_")) {
            QuestDefinition quest =
                quests.getQuest(
                    params.substring("quest_progress_".length()));

            if (quest == null) {
                return "";
            }

            QuestData progress =
                data.getQuestProgress().get(quest.getId());

            return String.valueOf(
                progress == null
                    ? 0
                    : Math.max(0, progress.getProgress()));
        }

        if (params.startsWith("quest_amount_")) {
            QuestDefinition quest =
                quests.getQuest(
                    params.substring("quest_amount_".length()));

            return quest == null
                ? ""
                : String.valueOf(Math.max(1, quest.getAmount()));
        }

        if (params.startsWith("quest_percent_")) {
            QuestDefinition quest =
                quests.getQuest(
                    params.substring("quest_percent_".length()));

            if (quest == null) {
                return "";
            }

            QuestData progress =
                data.getQuestProgress().get(quest.getId());

            int current = progress == null
                ? 0
                : Math.max(0, progress.getProgress());

            return String.valueOf(
                calculatePercentage(current, quest.getAmount()));
        }

        if (params.startsWith("quest_chain_")) {
            QuestDefinition quest =
                quests.getQuest(
                    params.substring("quest_chain_".length()));

            return quest == null
                ? ""
                : quest.getChainId();
        }

        if (params.startsWith("quest_stage_")) {
            QuestDefinition quest =
                quests.getQuest(
                    params.substring("quest_stage_".length()));

            return quest == null
                ? ""
                : String.valueOf(quest.getChainStage());
        }

        if (params.startsWith("chain_active_")) {
            QuestDefinition active =
                quests.getActiveQuest(
                    data,
                    params.substring("chain_active_".length()));

            return active == null
                ? ""
                : active.getId();
        }

        if (params.startsWith("chain_completed_")) {
            QuestChainDefinition chain =
                quests.getChain(
                    params.substring("chain_completed_".length()));

            if (chain == null) {
                return "";
            }

            int completed = 0;
            for (QuestDefinition stage : chain.getStages()) {
                QuestData progress =
                    data.getQuestProgress().get(stage.getId());

                if (progress != null && progress.isCompleted()) {
                    completed++;
                }
            }

            return String.valueOf(completed);
        }

        return null;
    }

    private JobDefinition findJob(String rawJobId) {
        String jobId = normalizeJobId(rawJobId);
        if (jobId.isEmpty()) {
            return null;
        }
        return plugin.getJobRegistry().getJob(jobId);
    }

    private static int sanitizeLevel(
            int level,
            JobDefinition job) {

        return Math.max(
            0,
            Math.min(job.getMaxLevel(), level));
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
                (int) Math.floor(ratio * 100.0D)));
    }

    private static boolean isQuestPlaceholder(
            String params) {

        return params.startsWith("quest_")
            || params.startsWith("chain_");
    }

    private static String normalizeParams(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeJobId(String value) {
        return value == null
            ? ""
            : value.trim().toLowerCase(Locale.ROOT);
    }
}