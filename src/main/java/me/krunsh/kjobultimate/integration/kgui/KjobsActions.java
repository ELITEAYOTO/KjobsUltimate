package me.krunsh.kjobultimate.integration.kgui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.krunsh.kgui.api.ActionContext;
import me.krunsh.kgui.api.ActionHandler;
import me.krunsh.kgui.api.ActionResult;
import me.krunsh.kgui.api.KguiApi;
import me.krunsh.kgui.api.OwnedRegistration;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.hooks.KguiHook;
import me.krunsh.kjobultimate.jobs.JobDefinition;

/** Actions Kjobs validées directement par les services métier, sans commande texte. */
public final class KjobsActions {

    private final KjobUltimate plugin;
    private final KguiHook hook;

    public KjobsActions(KjobUltimate plugin, KguiHook hook) {
        this.plugin = plugin;
        this.hook = hook;
    }

    public void register(KguiApi api, List<OwnedRegistration> handles) {
        add(api, handles, "open_main", context -> withPlayer(context,
                player -> open(player, "kjobs_main", Collections.<String, String>emptyMap())));
        add(api, handles, "open_detail", context -> withPlayer(context, player -> {
            String jobId = job(context);
            return open(player, "kjobs_detail", singleton("job_id", jobId));
        }));
        add(api, handles, "open_quests", context -> withPlayer(context, player -> {
            String jobId = optionalJob(context);
            return open(player, "kjobs_quests", jobId == null
                    ? Collections.<String, String>emptyMap() : singleton("job_id", jobId));
        }));
        add(api, handles, "open_top", context -> withPlayer(context, player -> {
            String jobId = optionalJob(context);
            return open(player, "kjobs_top", jobId == null
                    ? Collections.<String, String>emptyMap() : singleton("job_id", jobId));
        }));
        add(api, handles, "open_settings", context -> withPlayer(context,
                player -> open(player, "kjobs_settings", Collections.<String, String>emptyMap())));

        add(api, handles, "unlock_job", context -> withPlayer(context, player -> {
            PlayerData data = requireData(player);
            String jobId = job(context);
            if (plugin.getSlotManager().isJobActive(data, jobId)) {
                plugin.getSlotManager().setFavoriteJob(player, data, jobId);
                return ActionResult.handled();
            }
            if (!plugin.getSlotManager().assignJobToFreeSlot(player, data, jobId)) {
                send(player, "§cAucun emplacement de job libre.");
                return ActionResult.denied(null);
            }
            plugin.getSlotManager().setFavoriteJob(player, data, jobId);
            return ActionResult.handled();
        }));
        add(api, handles, "favorite_job", context -> withPlayer(context, player -> {
            PlayerData data = requireData(player);
            if (!plugin.getSlotManager().setFavoriteJob(player, data, job(context))) {
                send(player, "§cCe job doit être débloqué avant de devenir favori.");
                return ActionResult.denied(null);
            }
            return ActionResult.handled();
        }));
        add(api, handles, "request_leave", context -> withPlayer(context, player -> {
            PlayerData data = requireData(player);
            String jobId = job(context);
            if (!plugin.getSlotManager().requestLeaveJob(player, data, jobId)) {
                return ActionResult.denied(null);
            }
            openLater(player, "kjobs_confirm_leave", singleton("job_id", jobId));
            return ActionResult.handled();
        }));
        add(api, handles, "confirm_leave", context -> withPlayer(context, player -> {
            PlayerData data = requireData(player);
            if (!plugin.getSlotManager().confirmChange(player, data)) return ActionResult.denied(null);
            openLater(player, "kjobs_main", Collections.<String, String>emptyMap());
            return ActionResult.handled();
        }));
        add(api, handles, "cancel_leave", context -> withPlayer(context, player -> {
            plugin.getSlotManager().cancelChange(player);
            openLater(player, "kjobs_main", Collections.<String, String>emptyMap());
            return ActionResult.handled();
        }));
        add(api, handles, "claim_quest", context -> withPlayer(context, player -> {
            String questId = required(context, "quest_id");
            return plugin.getQuestManager().claimReward(player, questId)
                    ? ActionResult.handled() : ActionResult.denied(null);
        }));
        add(api, handles, "toggle_hud", context -> withPlayer(context, player -> {
            PlayerData data = requireData(player);
            data.setHudEnabled(!data.isHudEnabled());
            if (!data.isHudEnabled() && plugin.getHudManager() != null) {
                plugin.getHudManager().clearActionBar(player);
                plugin.getHudManager().removePlayer(player);
            }
            plugin.notifyJobsUiChanged(player.getUniqueId(), "kjobs:hud", "kjobs_settings", "kjobs_main");
            return ActionResult.handled();
        }));
        add(api, handles, "toggle_bossbar", context -> withPlayer(context, player -> {
            PlayerData data = requireData(player);
            data.setBossBarHudEnabled(!data.isBossBarHudEnabled());
            if (!data.isBossBarHudEnabled() && plugin.getHudManager() != null) {
                plugin.getHudManager().removePlayer(player);
            }
            plugin.notifyJobsUiChanged(player.getUniqueId(), "kjobs:bossbar", "kjobs_settings");
            return ActionResult.handled();
        }));
        add(api, handles, "toggle_actionbar", context -> withPlayer(context, player -> {
            PlayerData data = requireData(player);
            data.setActionBarHudEnabled(!data.isActionBarHudEnabled());
            if (!data.isActionBarHudEnabled() && plugin.getHudManager() != null) {
                plugin.getHudManager().clearActionBar(player);
            }
            plugin.notifyJobsUiChanged(player.getUniqueId(), "kjobs:actionbar", "kjobs_settings");
            return ActionResult.handled();
        }));
    }

    private ActionResult withPlayer(ActionContext context, PlayerAction action) {
        try {
            if (!Bukkit.isPrimaryThread()) return ActionResult.error(null, "main thread required");
            Player player = Bukkit.getPlayer(context.getPlayerId());
            if (player == null || !player.isOnline()) return ActionResult.denied(null);
            return action.execute(player);
        } catch (IllegalArgumentException failure) {
            Player player = Bukkit.getPlayer(context.getPlayerId());
            if (player != null) send(player, "§cParamètre de menu jobs invalide.");
            return ActionResult.denied(null);
        } catch (RuntimeException failure) {
            return ActionResult.error(null, failure.getMessage());
        }
    }

    private ActionResult open(Player player, String menuId, Map<String, String> arguments) {
        return hook.openMenu(player, menuId, arguments)
                ? ActionResult.handled() : ActionResult.error(null, "menu unavailable");
    }

    private void openLater(final Player player, final String menuId, final Map<String, String> arguments) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) hook.openMenu(player, menuId, arguments);
            }
        });
    }

    private PlayerData requireData(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) throw new IllegalArgumentException("player data unavailable");
        return data;
    }

    private String job(ActionContext context) {
        String jobId = required(context, "job_id").toLowerCase(java.util.Locale.ROOT);
        JobDefinition job = plugin.getJobRegistry().getJob(jobId);
        if (job == null) throw new IllegalArgumentException("unknown job");
        return job.getId();
    }

    private String optionalJob(ActionContext context) {
        String raw = context.getParameters().get("job_id");
        if (raw == null || raw.trim().isEmpty() || "all".equalsIgnoreCase(raw)
                || "global".equalsIgnoreCase(raw)) return null;
        return job(context);
    }

    private static String required(ActionContext context, String key) {
        String value = context.getParameters().get(key);
        if ((value == null || value.trim().isEmpty()) && "job_id".equals(key)) {
            value = context.getParameters().get("raw");
        }
        if (value == null || value.trim().isEmpty() || value.length() > 128
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("missing or unsafe " + key);
        }
        return value.trim();
    }

    private static Map<String, String> singleton(String key, String value) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put(key, value);
        return result;
    }

    private static void send(Player player, String message) {
        if (player != null && message != null && !message.isEmpty()) player.sendMessage(message);
    }

    private void add(KguiApi api, List<OwnedRegistration> handles,
                     String id, ActionHandler handler) {
        handles.add(api.registerAction(plugin, "kjobsultimate:" + id, handler));
    }

    private interface PlayerAction {
        ActionResult execute(Player player);
    }
}
