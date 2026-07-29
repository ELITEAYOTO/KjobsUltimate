package me.krunsh.kjobultimate.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.QuestData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.quests.QuestChainDefinition;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.quests.QuestManager;
import org.bukkit.entity.Player;

/**
 * Intégration PlaceholderAPI — expose les placeholders %kjob_*%.
 *
 * Placeholders disponibles :
 *   %kjob_level_<jobId>%       → niveau actuel du joueur dans ce job
 *   %kjob_xp_<jobId>%          → XP actuel dans le niveau courant
 *   %kjob_xp_next_<jobId>%     → XP requis pour le niveau suivant
 *   %kjob_display_job%         → jobId du job actuellement affiché (bossbar)
 *   %kjob_display_job_name%    → nom affiché du job actif (avec codes couleur)
 *   %kjob_slots%               → nombre de slots débloqués
 */
public final class PAPIHook extends PlaceholderExpansion {

    private final KjobUltimate plugin;

    public PAPIHook(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() { return "kjob"; }

    @Override
    public String getAuthor() { return "krunsh"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public boolean canRegister() { return true; }

    public boolean register() {
        return super.register();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return "";

        // %kjob_level_mineur%
        if (params.startsWith("level_")) {
            String jobId = params.substring(6);
            return String.valueOf(data.getLevel(jobId));
        }

        // %kjob_xp_mineur%
        if (params.startsWith("xp_") && !params.startsWith("xp_next_")) {
            String jobId = params.substring(3);
            return String.valueOf(data.getXP(jobId));
        }

        // %kjob_xp_next_mineur%
        if (params.startsWith("xp_next_")) {
            String jobId = params.substring(8);
            JobDefinition def = plugin.getJobRegistry().getJob(jobId);
            if (def == null) return "0";
            int nextLevel = data.getLevel(jobId) + 1;
            return String.valueOf(def.getXpForLevel(nextLevel));
        }

        // %kjob_display_job%
        if (params.equals("display_job")) {
            String displayJob = data.getDisplayJob();
            return displayJob != null ? displayJob : "";
        }

        // %kjob_display_job_name%
        if (params.equals("display_job_name")) {
            String displayJob = data.getDisplayJob();
            if (displayJob == null) return "";
            JobDefinition def = plugin.getJobRegistry().getJob(displayJob);
            return def != null ? def.getDisplayName() : displayJob;
        }

        // %kjob_slots%
        if (params.equals("slots")) {
            return String.valueOf(data.getUnlockedSlots());
        }

        QuestManager quests = plugin.getQuestManager();
        if (quests != null) {
            if (params.startsWith("quest_state_")) {
                QuestDefinition quest = quests.getQuest(params.substring(12));
                return quest == null ? "" : quests.getQuestState(data, quest);
            }
            if (params.startsWith("quest_progress_")) {
                QuestDefinition quest = quests.getQuest(params.substring(15));
                QuestData progress = quest == null ? null
                        : data.getQuestProgress().get(quest.getId());
                return quest == null ? "" : String.valueOf(
                        progress == null ? 0 : progress.getProgress());
            }
            if (params.startsWith("quest_amount_")) {
                QuestDefinition quest = quests.getQuest(params.substring(13));
                return quest == null ? "" : String.valueOf(quest.getAmount());
            }
            if (params.startsWith("quest_percent_")) {
                QuestDefinition quest = quests.getQuest(params.substring(14));
                if (quest == null) return "";
                QuestData progress = data.getQuestProgress().get(quest.getId());
                int value = progress == null ? 0 : progress.getProgress();
                return String.valueOf(Math.min(100,
                        (int) ((double) value / Math.max(1, quest.getAmount()) * 100D)));
            }
            if (params.startsWith("quest_chain_")) {
                QuestDefinition quest = quests.getQuest(params.substring(12));
                return quest == null ? "" : quest.getChainId();
            }
            if (params.startsWith("quest_stage_")) {
                QuestDefinition quest = quests.getQuest(params.substring(12));
                return quest == null ? ""
                        : String.valueOf(quest.getChainStage());
            }
            if (params.startsWith("chain_active_")) {
                QuestDefinition active = quests.getActiveQuest(
                        data, params.substring(13));
                return active == null ? "" : active.getId();
            }
            if (params.startsWith("chain_completed_")) {
                QuestChainDefinition chain =
                        quests.getChain(params.substring(16));
                if (chain == null) return "";
                int completed = 0;
                for (QuestDefinition stage : chain.getStages()) {
                    QuestData progress =
                            data.getQuestProgress().get(stage.getId());
                    if (progress != null && progress.isCompleted()) completed++;
                }
                return String.valueOf(completed);
            }
        }

        // %kjob_percent_mineur% → progression en % dans le niveau courant
        if (params.startsWith("percent_")) {
            String jobId = params.substring(8);
            JobDefinition def = plugin.getJobRegistry().getJob(jobId);
            if (def == null) return "0";
            int xp = data.getXP(jobId);
            int xpNext = def.getXpForLevel(data.getLevel(jobId) + 1);
            if (xpNext <= 0) return "100";
            return String.valueOf(Math.min(100, (int) ((double) xp / xpNext * 100)));
        }

        // %kjob_max_level_mineur%
        if (params.startsWith("max_level_")) {
            String jobId = params.substring(10);
            JobDefinition def = plugin.getJobRegistry().getJob(jobId);
            if (def == null) return "0";
            return String.valueOf(def.getMaxLevel());
        }

        return null;
    }
}
