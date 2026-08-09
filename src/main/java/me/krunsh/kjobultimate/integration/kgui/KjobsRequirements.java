package me.krunsh.kjobultimate.integration.kgui;

import java.util.List;

import me.krunsh.kgui.api.KguiApi;
import me.krunsh.kgui.api.OwnedRegistration;
import me.krunsh.kgui.api.RequirementContext;
import me.krunsh.kgui.api.RequirementHandler;
import me.krunsh.kgui.api.RequirementResult;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.QuestData;
import me.krunsh.kjobultimate.quests.QuestDefinition;

/** Requirements Kjobs fail-closed utilisables par les packs configurables. */
public final class KjobsRequirements {

    private final KjobUltimate plugin;

    public KjobsRequirements(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void register(KguiApi api, List<OwnedRegistration> handles) {
        add(api, handles, "available", context -> RequirementResult.allowed());
        add(api, handles, "data_loaded", context -> data(context) == null
                ? RequirementResult.denied(null) : RequirementResult.allowed());
        add(api, handles, "job_active", context -> jobState(context, true));
        add(api, handles, "job_inactive", context -> jobState(context, false));
        add(api, handles, "has_free_slot", context -> {
            PlayerData data = data(context);
            return data != null && plugin.getSlotManager().getFirstFreeSlot(data) > 0
                    ? RequirementResult.allowed() : RequirementResult.denied(null);
        });
        add(api, handles, "quest_claimable", this::questClaimable);
    }

    private RequirementResult jobState(RequirementContext context, boolean expected) {
        PlayerData data = data(context);
        String jobId = normalize(context.getParameters().get("job_id"));
        if (data == null || jobId == null || plugin.getJobRegistry().getJob(jobId) == null) {
            return RequirementResult.denied(null);
        }
        boolean active = plugin.getSlotManager().isJobActive(data, jobId);
        return active == expected ? RequirementResult.allowed() : RequirementResult.denied(null);
    }

    private RequirementResult questClaimable(RequirementContext context) {
        PlayerData data = data(context);
        String questId = context.getParameters().get("quest_id");
        QuestDefinition quest = questId == null ? null : plugin.getQuestManager().getQuest(questId);
        QuestData progress = data == null || quest == null ? null : data.getQuestProgress().get(quest.getId());
        return progress != null && progress.isCompleted() && !progress.isClaimed()
                ? RequirementResult.allowed() : RequirementResult.denied(null);
    }

    private PlayerData data(RequirementContext context) {
        return context == null || context.getPlayerId() == null
                ? null : plugin.getPlayerDataManager().get(context.getPlayerId());
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void add(KguiApi api, List<OwnedRegistration> handles,
                     String id, RequirementHandler handler) {
        handles.add(api.registerRequirement(plugin, "kjobsultimate:" + id, handler));
    }
}
