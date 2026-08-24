package me.krunsh.kjobultimate.hooks;

import java.util.Locale;
import java.util.Map;
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
 * Placeholders métiers historiques :
 *
 * %kjob_level_<jobId>%          niveau actuel
 * %kjob_xp_<jobId>%             XP actuelle dans le niveau
 * %kjob_xp_next_<jobId>%        XP nécessaire pour le niveau suivant
 * %kjob_percent_<jobId>%        progression entière de 0 à 100
 * %kjob_max_level_<jobId>%      niveau maximum
 *
 * %kjob_display_job%            identifiant du métier affiché dans le HUD
 * %kjob_display_job_name%       nom affiché du métier affiché dans le HUD
 * %kjob_slots%                  nombre de slots débloqués
 *
 * Placeholders TAB V10 :
 *
 * %kjob_main_job%               ID du métier principal (slot 1)
 * %kjob_main_job_name%          nom du métier principal
 *
 * %kjob_active_job_1% ... %kjob_active_job_6%
 *     Nom affiché du métier présent dans le slot.
 *     Retourne "" lorsque le slot est vide.
 *
 * %kjob_active_job_id_1% ... %kjob_active_job_id_6%
 *     ID brut du métier présent dans le slot.
 *
 * %kjob_active_job_level_1% ... %kjob_active_job_level_6%
 *     Niveau actuel du métier présent dans le slot.
 *     Retourne "" lorsque le slot est vide.
 *
 * %kjob_active_job_line_1% ... %kjob_active_job_line_6%
 *     Ligne prête pour le TAB, ex:
 *     "&7Farmer &8» &eNv.9"
 *
 * %kjob_completed_quests%
 *     Nombre total de quêtes marquées complétées dans les données du joueur.
 *
 * Placeholders quêtes historiques :
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

    public PAPIHook(
            KjobUltimate plugin) {

        this.plugin =
            Objects.requireNonNull(
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
        return plugin.getDescription()
            .getVersion();
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

        if (player == null
                || rawParams == null) {

            return "";
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(
                    player
                );

        if (data == null) {
            return "";
        }

        String params =
            normalizeParams(
                rawParams
            );

        if (params.isEmpty()) {
            return null;
        }

        String value =
            resolvePlayerPlaceholder(
                data,
                params
            );

        if (value != null) {
            return value;
        }

        value =
            resolveSlotPlaceholder(
                data,
                params
            );

        if (value != null) {
            return value;
        }

        value =
            resolveJobPlaceholder(
                data,
                params
            );

        if (value != null) {
            return value;
        }

        value =
            resolveQuestPlaceholder(
                data,
                params
            );

        if (value != null) {
            return value;
        }

        return null;
    }

    private String resolvePlayerPlaceholder(
            PlayerData data,
            String params) {

        if ("display_job".equals(
                params)) {

            return normalizeJobId(
                data.getDisplayJob()
            );
        }

        if ("display_job_name".equals(
                params)) {

            return displayName(
                data.getDisplayJob()
            );
        }

        if ("slots".equals(
                params)) {

            return String.valueOf(
                Math.max(
                    0,
                    data.getUnlockedSlots()
                )
            );
        }

        /*
         * Métier principal = slot 1.
         *
         * Contrairement à display_job, cette valeur ne change pas
         * simplement parce qu'un autre métier vient de gagner de l'XP.
         */
        if ("main_job".equals(
                params)) {

            return normalizeJobId(
                data.getJobInSlot(
                    1
                )
            );
        }

        if ("main_job_name".equals(
                params)) {

            return displayName(
                data.getJobInSlot(
                    1
                )
            );
        }

        if ("completed_quests".equals(
                params)) {

            return String.valueOf(
                countCompletedQuests(
                    data
                )
            );
        }

        return null;
    }

    /**
     * Résout les slots de jobs destinés au TAB.
     */
    private String resolveSlotPlaceholder(
            PlayerData data,
            String params) {

        if (params.startsWith(
                "active_job_line_")) {

            int slot =
                parseSlot(
                    params.substring(
                        "active_job_line_".length()
                    )
                );

            if (slot <= 0) {
                return "";
            }

            String jobId =
                normalizeJobId(
                    data.getJobInSlot(
                        slot
                    )
                );

            if (jobId.isEmpty()) {
                return "";
            }

            JobDefinition job =
                plugin.getJobRegistry()
                    .getJob(
                        jobId
                    );

            if (job == null) {
                return "";
            }

            int level =
                sanitizeLevel(
                    data.getLevel(
                        job.getId()
                    ),
                    job
                );

            return "&7"
                + safeDisplayName(
                    job.getDisplayName(),
                    jobId
                )
                + " &8» &eNv."
                + level;
        }

        if (params.startsWith(
                "active_job_level_")) {

            int slot =
                parseSlot(
                    params.substring(
                        "active_job_level_".length()
                    )
                );

            if (slot <= 0) {
                return "";
            }

            String jobId =
                normalizeJobId(
                    data.getJobInSlot(
                        slot
                    )
                );

            if (jobId.isEmpty()) {
                return "";
            }

            JobDefinition job =
                plugin.getJobRegistry()
                    .getJob(
                        jobId
                    );

            if (job == null) {
                return "";
            }

            return String.valueOf(
                sanitizeLevel(
                    data.getLevel(
                        job.getId()
                    ),
                    job
                )
            );
        }

        if (params.startsWith(
                "active_job_id_")) {

            int slot =
                parseSlot(
                    params.substring(
                        "active_job_id_".length()
                    )
                );

            if (slot <= 0) {
                return "";
            }

            return normalizeJobId(
                data.getJobInSlot(
                    slot
                )
            );
        }

        if (params.startsWith(
                "active_job_")) {

            int slot =
                parseSlot(
                    params.substring(
                        "active_job_".length()
                    )
                );

            if (slot <= 0) {
                return "";
            }

            return displayName(
                data.getJobInSlot(
                    slot
                )
            );
        }

        return null;
    }

    private String resolveJobPlaceholder(
            PlayerData data,
            String params) {

        if (params.startsWith(
                "xp_next_")) {

            JobDefinition job =
                findJob(
                    params.substring(
                        "xp_next_".length()
                    )
                );

            if (job == null) {
                return "0";
            }

            return String.valueOf(
                LevelUtil
                    .getRequiredXpForNextLevel(
                        data,
                        job
                    )
            );
        }

        if (params.startsWith(
                "max_level_")) {

            JobDefinition job =
                findJob(
                    params.substring(
                        "max_level_".length()
                    )
                );

            return job == null
                ? "0"
                : String.valueOf(
                    job.getMaxLevel()
                );
        }

        if (params.startsWith(
                "percent_")) {

            JobDefinition job =
                findJob(
                    params.substring(
                        "percent_".length()
                    )
                );

            if (job == null) {
                return "0";
            }

            return String.valueOf(
                LevelUtil
                    .getProgressPercentage(
                        data,
                        job
                    )
            );
        }

        if (params.startsWith(
                "level_")) {

            JobDefinition job =
                findJob(
                    params.substring(
                        "level_".length()
                    )
                );

            if (job == null) {
                return "0";
            }

            return String.valueOf(
                sanitizeLevel(
                    data.getLevel(
                        job.getId()
                    ),
                    job
                )
            );
        }

        if (params.startsWith(
                "xp_")) {

            JobDefinition job =
                findJob(
                    params.substring(
                        "xp_".length()
                    )
                );

            if (job == null) {
                return "0";
            }

            if (job.isMaxLevel(
                    data.getLevel(
                        job.getId()
                    ))) {

                return "0";
            }

            return String.valueOf(
                LevelUtil
                    .getCurrentLevelXp(
                        data,
                        job
                    )
            );
        }

        return null;
    }

    private String resolveQuestPlaceholder(
            PlayerData data,
            String params) {

        QuestManager quests =
            plugin.getQuestManager();

        if (quests == null) {

            return isQuestPlaceholder(
                    params)
                ? ""
                : null;
        }

        if (params.startsWith(
                "quest_state_")) {

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

        if (params.startsWith(
                "quest_progress_")) {

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
                    .get(
                        quest.getId()
                    );

            return String.valueOf(
                progress == null
                    ? 0
                    : Math.max(
                        0,
                        progress.getProgress()
                    )
            );
        }

        if (params.startsWith(
                "quest_amount_")) {

            QuestDefinition quest =
                quests.getQuest(
                    params.substring(
                        "quest_amount_".length()
                    )
                );

            return quest == null
                ? ""
                : String.valueOf(
                    Math.max(
                        1,
                        quest.getAmount()
                    )
                );
        }

        if (params.startsWith(
                "quest_percent_")) {

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
                    .get(
                        quest.getId()
                    );

            int current =
                progress == null
                    ? 0
                    : Math.max(
                        0,
                        progress.getProgress()
                    );

            return String.valueOf(
                calculatePercentage(
                    current,
                    quest.getAmount()
                )
            );
        }

        if (params.startsWith(
                "quest_chain_")) {

            QuestDefinition quest =
                quests.getQuest(
                    params.substring(
                        "quest_chain_".length()
                    )
                );

            return quest == null
                ? ""
                : quest.getChainId();
        }

        if (params.startsWith(
                "quest_stage_")) {

            QuestDefinition quest =
                quests.getQuest(
                    params.substring(
                        "quest_stage_".length()
                    )
                );

            return quest == null
                ? ""
                : String.valueOf(
                    quest.getChainStage()
                );
        }

        if (params.startsWith(
                "chain_active_")) {

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

        if (params.startsWith(
                "chain_completed_")) {

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
                        .get(
                            stage.getId()
                        );

                if (progress != null
                        && progress.isCompleted()) {

                    completed++;
                }
            }

            return String.valueOf(
                completed
            );
        }

        return null;
    }

    private int countCompletedQuests(
            PlayerData data) {

        if (data == null
                || data.getQuestProgress() == null
                || data.getQuestProgress().isEmpty()) {

            return 0;
        }

        int completed =
            0;

        for (Map.Entry<String, QuestData> entry
                : data.getQuestProgress()
                    .entrySet()) {

            QuestData progress =
                entry.getValue();

            if (progress != null
                    && progress.isCompleted()) {

                completed++;
            }
        }

        return completed;
    }

    private String displayName(
            String rawJobId) {

        String jobId =
            normalizeJobId(
                rawJobId
            );

        if (jobId.isEmpty()) {
            return "";
        }

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    jobId
                );

        return job == null
            ? jobId
            : safeDisplayName(
                job.getDisplayName(),
                jobId
            );
    }

    private JobDefinition findJob(
            String rawJobId) {

        String jobId =
            normalizeJobId(
                rawJobId
            );

        if (jobId.isEmpty()) {
            return null;
        }

        return plugin
            .getJobRegistry()
            .getJob(
                jobId
            );
    }

    private static int parseSlot(
            String raw) {

        if (raw == null) {
            return -1;
        }

        try {

            int slot =
                Integer.parseInt(
                    raw.trim()
                );

            /*
             * Le système actuel est configuré pour 6 slots.
             * On garde une borne raisonnable pour éviter des IDs absurdes,
             * sans lier le placeholder à la config.
             */
            return slot >= 1
                    && slot <= 64
                ? slot
                : -1;

        } catch (NumberFormatException ignored) {

            return -1;
        }
    }

    private static int sanitizeLevel(
            int level,
            JobDefinition job) {

        return Math.max(
            0,
            Math.min(
                job.getMaxLevel(),
                level
            )
        );
    }

    private static int calculatePercentage(
            int current,
            int maximum) {

        if (maximum <= 0) {
            return 0;
        }

        double ratio =
            (double) Math.max(
                0,
                current
            )
                / (double) maximum;

        if (Double.isNaN(
                ratio)
                || Double.isInfinite(
                    ratio)) {

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

    private static boolean isQuestPlaceholder(
            String params) {

        return params.startsWith(
                "quest_")
            || params.startsWith(
                "chain_")
            || "completed_quests".equals(
                params);
    }

    private static String safeDisplayName(
            String displayName,
            String fallback) {

        if (displayName == null
                || displayName.trim().isEmpty()) {

            return fallback == null
                ? ""
                : fallback;
        }

        return displayName;
    }

    private static String normalizeParams(
            String value) {

        return value.trim()
            .toLowerCase(
                Locale.ROOT
            );
    }

    private static String normalizeJobId(
            String value) {

        return value == null
            ? ""
            : value.trim()
                .toLowerCase(
                    Locale.ROOT
                );
    }
}
