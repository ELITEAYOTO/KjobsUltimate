package me.krunsh.kjobultimate.integration.kgui;

import java.util.List;
import java.util.Locale;

import me.krunsh.kgui.api.KguiApi;
import me.krunsh.kgui.api.OwnedRegistration;
import me.krunsh.kgui.api.RequirementContext;
import me.krunsh.kgui.api.RequirementHandler;
import me.krunsh.kgui.api.RequirementResult;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.view.QuestView;

/**
 * Requirements Kjobs fail-closed utilisables par les packs Kgui.
 *
 * V3.8 :
 * - les requirements de présentation ne recalculent plus l'état des quêtes ;
 * - quest_claimable consomme désormais QuestViewService, qui est la source
 *   de vérité commune à Kgui et PlaceholderAPI.
 */
public final class KjobsRequirements {

    private final KjobUltimate plugin;

    public KjobsRequirements(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void register(
            KguiApi api,
            List<OwnedRegistration> handles) {

        add(
            api,
            handles,
            "available",
            context -> RequirementResult.allowed()
        );

        add(
            api,
            handles,
            "data_loaded",
            context -> data(context) == null
                ? RequirementResult.denied(null)
                : RequirementResult.allowed()
        );

        add(
            api,
            handles,
            "job_active",
            context -> jobState(context, true)
        );

        add(
            api,
            handles,
            "job_inactive",
            context -> jobState(context, false)
        );

        add(
            api,
            handles,
            "has_free_slot",
            context -> {

                PlayerData data =
                    data(context);

                return data != null
                        && plugin.getSlotManager()
                            .getFirstFreeSlot(data) > 0
                    ? RequirementResult.allowed()
                    : RequirementResult.denied(null);
            }
        );

        add(
            api,
            handles,
            "quest_claimable",
            this::questClaimable
        );
    }

    private RequirementResult jobState(
            RequirementContext context,
            boolean expected) {

        PlayerData data =
            data(context);

        String jobId =
            normalize(
                context == null
                    ? null
                    : context.getParameters().get("job_id")
            );

        if (data == null
                || jobId == null
                || plugin.getJobRegistry().getJob(jobId) == null) {

            return RequirementResult.denied(null);
        }

        boolean active =
            plugin.getSlotManager()
                .isJobActive(data, jobId);

        return active == expected
            ? RequirementResult.allowed()
            : RequirementResult.denied(null);
    }

    /**
     * La décision de claim ne doit pas être recalculée ici.
     *
     * QuestViewService expose déjà l'état officiel calculé par le domaine
     * des quêtes (claimable, claimed, active, locked, etc.).
     */
    private RequirementResult questClaimable(
            RequirementContext context) {

        if (context == null
                || context.getPlayerId() == null
                || plugin.getQuestViewService() == null) {

            return RequirementResult.denied(null);
        }

        String questId =
            normalize(
                context.getParameters().get("quest_id")
            );

        if (questId == null) {
            return RequirementResult.denied(null);
        }

        QuestView quest =
            plugin.getQuestViewService()
                .getQuest(
                    context.getPlayerId(),
                    questId
                );

        return quest != null
                && quest.isClaimable()
            ? RequirementResult.allowed()
            : RequirementResult.denied(null);
    }

    private PlayerData data(
            RequirementContext context) {

        return context == null
                || context.getPlayerId() == null
            ? null
            : plugin.getPlayerDataManager()
                .get(context.getPlayerId());
    }

    private static String normalize(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim()
                .toLowerCase(Locale.ROOT);

        return normalized.isEmpty()
            ? null
            : normalized;
    }

    private void add(
            KguiApi api,
            List<OwnedRegistration> handles,
            String id,
            RequirementHandler handler) {

        handles.add(
            api.registerRequirement(
                plugin,
                "kjobsultimate:" + id,
                handler
            )
        );
    }
}
