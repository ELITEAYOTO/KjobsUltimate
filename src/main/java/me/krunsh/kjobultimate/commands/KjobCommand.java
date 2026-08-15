package me.krunsh.kjobultimate.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.RankingEntry;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.slots.SlotManager;
import me.krunsh.kjobultimate.view.JobView;
import me.krunsh.kjobultimate.view.PlayerJobsView;

/**
 * Commande joueur /jobs.
 *
 * V3 :
 * - /jobs et /jobs menu utilisent exclusivement Kgui ;
 * - aucun fallback vers l'ancien GUI Kjobs n'existe ;
 * - /jobs text reste un outil texte explicite de diagnostic ;
 * - les lectures de progression passent par JobsViewService.
 */
public final class KjobCommand
        implements CommandExecutor, TabCompleter {

    private final KjobUltimate plugin;

    private final Object topCacheLock =
        new Object();

    private final Map<String, TopCacheEntry> topCache =
        new HashMap<String, TopCacheEntry>();

    public KjobCommand(
            KjobUltimate plugin) {

        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!(sender instanceof Player)) {

            sender.sendMessage(
                message(
                    "player_command.player_only",
                    "Cette commande est reservee aux joueurs."
                )
            );

            return true;
        }

        Player player =
            (Player) sender;

        if (args.length == 0) {

            openRequiredKgui(
                player,
                "kjobs_main",
                Collections.<String, String>emptyMap()
            );

            return true;
        }

        String sub =
            args[0].toLowerCase();

        if ("menu".equals(sub)
                || "gui".equals(sub)) {

            openRequiredKgui(
                player,
                "kjobs_main",
                Collections.<String, String>emptyMap()
            );

            return true;
        }

        if ("text".equals(sub)) {

            openTextMenu(player);
            return true;
        }

        if ("help".equals(sub)
                || "aide".equals(sub)) {

            sendHelp(player);
            return true;
        }

        if ("join".equals(sub)
                || "prendre".equals(sub)
                || "rejoindre".equals(sub)) {

            handleJoin(
                player,
                args.length > 1
                    ? args[1]
                    : null
            );

            return true;
        }

        if ("select".equals(sub)
                || "favori".equals(sub)
                || "favorite".equals(sub)
                || "fav".equals(sub)) {

            handleFavorite(
                player,
                args.length > 1
                    ? args[1]
                    : null
            );

            return true;
        }

        if ("leave".equals(sub)
                || "quitter".equals(sub)) {

            handleLeave(
                player,
                args.length > 1
                    ? args[1]
                    : null
            );

            return true;
        }

        if ("confirm".equals(sub)
                || "confirmer".equals(sub)) {

            handleConfirm(player);
            return true;
        }

        if ("cancel".equals(sub)
                || "annuler".equals(sub)) {

            handleCancel(player);
            return true;
        }

        if ("info".equals(sub)
                || "infos".equals(sub)
                || "stats".equals(sub)) {

            handleInfo(
                player,
                args.length > 1
                    ? args[1]
                    : null
            );

            return true;
        }

        if ("top".equals(sub)
                || "classement".equals(sub)) {

            handleTop(
                player,
                args.length > 1
                    ? args[1]
                    : null
            );

            return true;
        }

        if ("quests".equals(sub)
                || "quest".equals(sub)
                || "quetes".equals(sub)
                || "quete".equals(sub)) {

            openRequiredKgui(
                player,
                "kjobs_quests",
                Collections.<String, String>emptyMap()
            );

            return true;
        }

        if ("hud".equals(sub)) {

            handleHudToggle(
                player,
                args.length > 1
                    ? args[1]
                    : null
            );

            return true;
        }

        sendHelp(player);
        return true;
    }

    // -------------------------------------------------------------------------
    // KGUI
    // -------------------------------------------------------------------------

    /**
     * Kgui est obligatoire au runtime. Un échec d'ouverture signifie donc
     * généralement qu'un menu est absent/invalide ou que Kgui a été désactivé.
     * Il ne faut jamais masquer ce problème avec un renderer de secours.
     */
    private boolean openRequiredKgui(
            Player player,
            String menuId,
            Map<String, String> arguments) {

        boolean opened =
            plugin.getHookManager() != null
                && plugin.getHookManager()
                    .isKguiEnabled()
                && plugin.getHookManager()
                    .openKgui(
                        player,
                        menuId,
                        arguments
                    );

        if (!opened) {

            send(
                player,
                "player_command.gui_unavailable",
                "{prefix}§cImpossible d'ouvrir le menu jobs §7({menu_id})§c. "
                    + "Préviens un membre du staff.",
                "{menu_id}",
                menuId
            );
        }

        return opened;
    }

    // -------------------------------------------------------------------------
    // MODE TEXTE EXPLICITE / DEBUG
    // -------------------------------------------------------------------------

    private void openTextMenu(
            Player player) {

        PlayerJobsView view =
            getJobsView(player);

        if (view == null) {
            return;
        }

        send(
            player,
            "player_command.menu.header",
            "§6§l====== KjobsUltimate ======"
        );

        send(
            player,
            "player_command.menu.summary",
            "§7Jobs debloques: §e{unlocked}§7/§e{slots} "
                + "§8(max {max_slots}) §7| Global: §e{global_level}",
            "{unlocked}",
            String.valueOf(view.getActiveJobCount()),
            "{slots}",
            String.valueOf(view.getUnlockedSlots()),
            "{max_slots}",
            String.valueOf(view.getMaxSlots()),
            "{global_level}",
            String.valueOf(view.getGlobalLevel())
        );

        send(
            player,
            "player_command.menu.favorite",
            "§7Favori: §e{job}",
            "{job}",
            view.hasDisplayJob()
                ? view.getDisplayJobName()
                : message(
                    "player_command.menu.none",
                    "aucun"
                )
        );

        for (JobView job : view.getJobs()) {

            String status =
                job.isActive()
                    ? message(
                        "player_command.menu.status_unlocked",
                        "§a[debloque]"
                    )
                    : message(
                        "player_command.menu.status_locked",
                        "§8[bloque]"
                    );

            if (job.isFavorite()) {

                status =
                    message(
                        "player_command.menu.status_favorite",
                        "§6[favori]"
                    );
            }

            send(
                player,
                "player_command.menu.job_line",
                "§e{job} {status} §7Niv §f{level} "
                    + "§8- §a{xp}§7/§a{xp_next} XP",
                "{job}",
                job.getDisplayName(),
                "{job_id}",
                job.getId(),
                "{status}",
                status,
                "{level}",
                String.valueOf(job.getLevel()),
                "{xp}",
                String.valueOf(job.getXp()),
                "{xp_next}",
                String.valueOf(job.getXpRequired())
            );
        }

        send(
            player,
            "player_command.menu.commands",
            "§8Commandes: §e/jobs join <job> "
                + "§8| §e/jobs select <job> "
                + "§8| §e/jobs leave <job>"
        );
    }

    // -------------------------------------------------------------------------
    // MUTATIONS JOBS
    // -------------------------------------------------------------------------

    private void handleJoin(
            Player player,
            String jobId) {

        if (jobId == null) {

            send(
                player,
                "player_command.join.usage",
                "{prefix}§7Usage: §e/jobs join <job>"
            );

            return;
        }

        jobId =
            jobId.toLowerCase();

        JobDefinition definition =
            plugin.getJobRegistry()
                .getJob(jobId);

        if (definition == null) {

            send(
                player,
                "player_command.job_unknown",
                "{prefix}§cJob inconnu: §e{job_id}",
                "{job_id}",
                jobId
            );

            return;
        }

        PlayerData data =
            getData(player);

        if (data == null) {
            return;
        }

        SlotManager slots =
            plugin.getSlotManager();

        if (slots.isJobActive(
                data,
                jobId)) {

            slots.setFavoriteJob(
                player,
                data,
                jobId
            );

            send(
                player,
                "player_command.join.already_unlocked",
                "{prefix}§7Ce job etait deja debloque."
            );

            return;
        }

        if (!slots.assignJobToFreeSlot(
                player,
                data,
                jobId)) {

            send(
                player,
                "player_command.join.no_free_slot",
                "{prefix}§cAucun emplacement libre. "
                    + "Monte ton niveau global pour debloquer un nouveau job."
            );

            send(
                player,
                "player_command.join.global_level",
                "{prefix}§7Niveau global actuel: §e{global_level}",
                "{global_level}",
                String.valueOf(
                    slots.getGlobalLevel(data)
                )
            );

            return;
        }

        slots.setFavoriteJob(
            player,
            data,
            jobId
        );

        send(
            player,
            "player_command.join.unlocked",
            "{prefix}§aJob debloque: §e{job}",
            "{job}",
            definition.getDisplayName(),
            "{job_id}",
            jobId
        );
    }

    private void handleFavorite(
            Player player,
            String jobId) {

        if (jobId == null) {

            send(
                player,
                "player_command.favorite.usage",
                "{prefix}§7Usage: §e/jobs select <job>"
            );

            return;
        }

        jobId =
            jobId.toLowerCase();

        JobDefinition definition =
            plugin.getJobRegistry()
                .getJob(jobId);

        if (definition == null) {

            send(
                player,
                "player_command.job_unknown",
                "{prefix}§cJob inconnu: §e{job_id}",
                "{job_id}",
                jobId
            );

            return;
        }

        PlayerData data =
            getData(player);

        if (data == null) {
            return;
        }

        if (!plugin.getSlotManager()
                .setFavoriteJob(
                    player,
                    data,
                    jobId
                )) {

            send(
                player,
                "player_command.favorite.must_unlock",
                "{prefix}§cTu dois d'abord debloquer ce job avec "
                    + "§e/jobs join {job_id}§c.",
                "{job_id}",
                jobId,
                "{job}",
                definition.getDisplayName()
            );
        }
    }

    private void handleLeave(
            Player player,
            String jobId) {

        if (jobId == null) {

            send(
                player,
                "player_command.leave.usage",
                "{prefix}§7Usage: §e/jobs leave <job>"
            );

            return;
        }

        jobId =
            jobId.toLowerCase();

        JobDefinition definition =
            plugin.getJobRegistry()
                .getJob(jobId);

        if (definition == null) {

            send(
                player,
                "player_command.job_unknown",
                "{prefix}§cJob inconnu: §e{job_id}",
                "{job_id}",
                jobId
            );

            return;
        }

        PlayerData data =
            getData(player);

        if (data == null) {
            return;
        }

        plugin.getSlotManager()
            .requestLeaveJob(
                player,
                data,
                jobId
            );
    }

    private void handleConfirm(
            Player player) {

        PlayerData data =
            getData(player);

        if (data == null) {
            return;
        }

        plugin.getSlotManager()
            .confirmChange(
                player,
                data
            );
    }

    private void handleCancel(
            Player player) {

        plugin.getSlotManager()
            .cancelChange(player);
    }

    // -------------------------------------------------------------------------
    // LECTURE / INFO
    // -------------------------------------------------------------------------

    private void handleInfo(
            Player player,
            String jobId) {

        PlayerJobsView view =
            getJobsView(player);

        if (view == null) {
            return;
        }

        if (jobId == null) {

            send(
                player,
                "player_command.info.header",
                "§6§l====== Tes jobs ======"
            );

            for (JobView job : view.getJobs()) {
                sendJobLine(
                    player,
                    job
                );
            }

            return;
        }

        JobView job =
            view.getJob(jobId);

        if (job == null) {

            send(
                player,
                "player_command.job_unknown",
                "{prefix}§cJob inconnu: §e{job_id}",
                "{job_id}",
                jobId.toLowerCase()
            );

            return;
        }

        send(
            player,
            "player_command.info.detail_header",
            "§6§l{job}",
            "{job}",
            job.getDisplayName(),
            "{job_id}",
            job.getId()
        );

        send(
            player,
            "player_command.info.status",
            "§7Statut: {status}",
            "{status}",
            job.isActive()
                ? message(
                    "player_command.info.status_unlocked",
                    "§adebloque"
                )
                : message(
                    "player_command.info.status_locked",
                    "§8bloque"
                )
        );

        send(
            player,
            "player_command.info.level",
            "§7Niveau: §e{level}§7/§e{max_level}",
            "{level}",
            String.valueOf(job.getLevel()),
            "{max_level}",
            String.valueOf(job.getMaxLevel())
        );

        send(
            player,
            "player_command.info.xp",
            "§7XP: §a{xp}§7/§a{xp_next} §8({percent}%)",
            "{xp}",
            String.valueOf(job.getXp()),
            "{xp_next}",
            String.valueOf(job.getXpRequired()),
            "{percent}",
            String.valueOf(job.getXpPercent())
        );
    }

    // -------------------------------------------------------------------------
    // CLASSEMENT TEXTE
    // -------------------------------------------------------------------------

    private void handleTop(
            final Player player,
            String jobId) {

        String filter =
            normalizeTopFilter(jobId);

        if ("__invalid__".equals(filter)) {

            send(
                player,
                "player_command.top.invalid_filter",
                "{prefix}§cClassement inconnu: §e{job_id}",
                "{job_id}",
                jobId == null
                    ? ""
                    : jobId.toLowerCase()
            );

            return;
        }

        final String topFilter =
            filter;

        final String cacheKey =
            topFilter == null
                ? "global"
                : topFilter;

        final int limit =
            Math.max(
                1,
                Math.min(
                    50,
                    plugin.getConfigManager()
                        .getMainConfig()
                        .getInt(
                            "top.chat_limit",
                            10
                        )
                )
            );

        send(
            player,
            "player_command.top.loading",
            "{prefix}§7Chargement du classement §e{target}§7...",
            "{target}",
            getTopTargetDisplay(topFilter)
        );

        Bukkit.getScheduler()
            .runTaskAsynchronously(
                plugin,
                new Runnable() {
                    @Override
                    public void run() {

                        List<RankingEntry> entries =
                            getCachedTop(cacheKey);

                        boolean cached =
                            entries != null;

                        int rank;

                        try {

                            if (entries == null) {

                                entries =
                                    plugin.getDatabaseManager()
                                        .getTop(
                                            topFilter,
                                            limit
                                        );

                                putCachedTop(
                                    cacheKey,
                                    entries
                                );
                            }

                            rank =
                                plugin.getDatabaseManager()
                                    .getRank(
                                        player.getUniqueId(),
                                        topFilter
                                    );

                        } catch (Exception failure) {

                            plugin.getLogger()
                                .warning(
                                    "Erreur classement "
                                        + cacheKey
                                        + ": "
                                        + failure.getMessage()
                                );

                            Bukkit.getScheduler()
                                .runTask(
                                    plugin,
                                    new Runnable() {
                                        @Override
                                        public void run() {

                                            if (player.isOnline()) {

                                                send(
                                                    player,
                                                    "player_command.top.error",
                                                    "{prefix}§cImpossible de charger "
                                                        + "le classement pour le moment."
                                                );
                                            }
                                        }
                                    }
                                );

                            return;
                        }

                        final List<RankingEntry> finalEntries =
                            entries;

                        final int finalRank =
                            rank;

                        final boolean finalCached =
                            cached;

                        Bukkit.getScheduler()
                            .runTask(
                                plugin,
                                new Runnable() {
                                    @Override
                                    public void run() {

                                        if (player.isOnline()) {

                                            sendTop(
                                                player,
                                                topFilter,
                                                finalEntries,
                                                finalRank,
                                                finalCached
                                            );
                                        }
                                    }
                                }
                            );
                    }
                }
            );
    }

    // -------------------------------------------------------------------------
    // HUD
    // -------------------------------------------------------------------------

    private void handleHudToggle(
            Player player,
            String target) {

        PlayerData data =
            getData(player);

        if (data == null) {
            return;
        }

        String normalized =
            target == null
                ? "all"
                : target.trim()
                    .toLowerCase();

        if ("bossbar".equals(normalized)
                || "boss".equals(normalized)
                || "bar".equals(normalized)) {

            data.setBossBarHudEnabled(
                !data.isBossBarHudEnabled()
            );

            if (!data.isBossBarHudEnabled()
                    && plugin.getHudManager() != null) {

                plugin.getHudManager()
                    .removePlayer(player);
            }

            send(
                player,
                "hud_toggle.bossbar",
                "{prefix}§7BossBar jobs: {state}",
                "{state}",
                data.isBossBarHudEnabled()
                    ? "§aON"
                    : "§cOFF"
            );

            plugin.notifyJobsUiChanged(
                player.getUniqueId(),
                "kjobs:bossbar",
                "kjobs_settings"
            );

            return;
        }

        if ("actionbar".equals(normalized)
                || "action".equals(normalized)
                || "message".equals(normalized)) {

            data.setActionBarHudEnabled(
                !data.isActionBarHudEnabled()
            );

            if (!data.isActionBarHudEnabled()
                    && plugin.getHudManager() != null) {

                plugin.getHudManager()
                    .clearActionBar(player);
            }

            send(
                player,
                "hud_toggle.actionbar",
                "{prefix}§7ActionBar jobs: {state}",
                "{state}",
                data.isActionBarHudEnabled()
                    ? "§aON"
                    : "§cOFF"
            );

            plugin.notifyJobsUiChanged(
                player.getUniqueId(),
                "kjobs:actionbar",
                "kjobs_settings"
            );

            return;
        }

        if ("on".equals(normalized)
                || "enable".equals(normalized)
                || "activer".equals(normalized)) {

            data.setHudEnabled(true);
            data.setBossBarHudEnabled(true);
            data.setActionBarHudEnabled(true);

        } else if ("off".equals(normalized)
                || "disable".equals(normalized)
                || "desactiver".equals(normalized)) {

            data.setHudEnabled(false);
            data.setBossBarHudEnabled(false);
            data.setActionBarHudEnabled(false);

        } else {

            data.setHudEnabled(
                !data.isHudEnabled()
            );
        }

        if (data.isHudEnabled()) {

            send(
                player,
                "hud_toggle.enabled",
                "{prefix}§aHUD active. "
                    + "§7BossBar={bossbar} §7ActionBar={actionbar}",
                "{bossbar}",
                data.isBossBarHudEnabled()
                    ? "§aON"
                    : "§cOFF",
                "{actionbar}",
                data.isActionBarHudEnabled()
                    ? "§aON"
                    : "§cOFF"
            );

        } else {

            send(
                player,
                "hud_toggle.disabled",
                "{prefix}§cHUD desactive."
            );
        }

        if (!data.isHudEnabled()
                && plugin.getHudManager() != null) {

            plugin.getHudManager()
                .clearActionBar(player);

            plugin.getHudManager()
                .removePlayer(player);

        } else if (!data.isActionBarHudEnabled()
                && plugin.getHudManager() != null) {

            plugin.getHudManager()
                .clearActionBar(player);
        }

        plugin.notifyJobsUiChanged(
            player.getUniqueId(),
            "kjobs:hud",
            "kjobs_settings",
            "kjobs_main"
        );
    }

    // -------------------------------------------------------------------------
    // HELP / TAB COMPLETION
    // -------------------------------------------------------------------------

    private void sendHelp(
            Player player) {

        List<String> lines =
            plugin.getConfigManager()
                .getMessagesConfig()
                .getStringList(
                    "player_command.help.lines"
                );

        if (lines == null
                || lines.isEmpty()) {

            lines =
                Arrays.asList(
                    "§6§l====== Commandes Jobs ======",
                    "§e/jobs §8- §7ouvre le menu Kgui des métiers",
                    "§e/jobs quests §8- §7ouvre les quêtes",
                    "§e/jobs join <job> §8- §7debloque un job si tu as une place",
                    "§e/jobs select <job> §8- §7definit le job favori",
                    "§e/jobs leave <job> §8- §7quitte un job avec confirmation",
                    "§e/jobs info [job] §8- §7affiche les infos texte",
                    "§e/jobs top [job] §8- §7affiche le classement texte",
                    "§e/jobs hud [bossbar|actionbar|on|off] §8- §7regle l'affichage jobs",
                    "§e/jobs text §8- §7diagnostic texte des jobs"
                );
        }

        for (String line : lines) {
            player.sendMessage(
                format(line)
            );
        }
    }

    private void sendJobLine(
            Player player,
            JobView job) {

        send(
            player,
            "player_command.info.job_line",
            "§e{job} §7Niv §f{level} "
                + "§8- §a{xp}§7/§a{xp_next} "
                + "§8- {status}",
            "{job}",
            job.getDisplayName(),
            "{job_id}",
            job.getId(),
            "{level}",
            String.valueOf(job.getLevel()),
            "{xp}",
            String.valueOf(job.getXp()),
            "{xp_next}",
            String.valueOf(job.getXpRequired()),
            "{status}",
            job.isActive()
                ? message(
                    "player_command.info.status_unlocked",
                    "§adebloque"
                )
                : message(
                    "player_command.info.status_locked",
                    "§8bloque"
                )
        );
    }

    private PlayerData getData(
            Player player) {

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(player);

        if (data == null) {

            send(
                player,
                "player_command.data_not_loaded",
                "{prefix}§cDonnees non chargees, reessaie dans un instant."
            );
        }

        return data;
    }

    private PlayerJobsView getJobsView(
            Player player) {

        PlayerJobsView view =
            plugin.getJobsViewService() == null
                ? null
                : plugin.getJobsViewService()
                    .getPlayer(player);

        if (view == null) {

            send(
                player,
                "player_command.data_not_loaded",
                "{prefix}§cDonnees non chargees, reessaie dans un instant."
            );
        }

        return view;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args) {

        if (args.length == 1) {

            return complete(
                args[0],
                Arrays.asList(
                    "help",
                    "menu",
                    "gui",
                    "text",
                    "join",
                    "select",
                    "favori",
                    "leave",
                    "confirm",
                    "cancel",
                    "info",
                    "infos",
                    "stats",
                    "top",
                    "classement",
                    "quests",
                    "quetes",
                    "hud"
                )
            );
        }

        if (args.length == 2) {

            String sub =
                args[0].toLowerCase();

            if ("join".equals(sub)
                    || "prendre".equals(sub)
                    || "rejoindre".equals(sub)) {

                return complete(
                    args[1],
                    plugin.getJobRegistry()
                        .getJobIds()
                );
            }

            if ("select".equals(sub)
                    || "favori".equals(sub)
                    || "favorite".equals(sub)
                    || "fav".equals(sub)
                    || "leave".equals(sub)
                    || "quitter".equals(sub)) {

                if (sender instanceof Player) {

                    PlayerJobsView view =
                        plugin.getJobsViewService()
                            .getPlayer(
                                (Player) sender
                            );

                    if (view != null) {

                        List<String> active =
                            new ArrayList<String>();

                        for (JobView job : view.getJobs()) {

                            if (job.isActive()) {
                                active.add(
                                    job.getId()
                                );
                            }
                        }

                        return complete(
                            args[1],
                            active
                        );
                    }
                }

                return complete(
                    args[1],
                    plugin.getJobRegistry()
                        .getJobIds()
                );
            }

            if ("info".equals(sub)
                    || "infos".equals(sub)
                    || "stats".equals(sub)) {

                return complete(
                    args[1],
                    plugin.getJobRegistry()
                        .getJobIds()
                );
            }

            if ("top".equals(sub)
                    || "classement".equals(sub)) {

                List<String> values =
                    new ArrayList<String>();

                values.add("global");

                values.addAll(
                    plugin.getJobRegistry()
                        .getJobIds()
                );

                return complete(
                    args[1],
                    values
                );
            }

            if ("hud".equals(sub)) {

                return complete(
                    args[1],
                    Arrays.asList(
                        "bossbar",
                        "actionbar",
                        "on",
                        "off"
                    )
                );
            }
        }

        return Collections.emptyList();
    }

    private List<String> complete(
            String token,
            Iterable<String> values) {

        String lower =
            token == null
                ? ""
                : token.toLowerCase();

        List<String> result =
            new ArrayList<String>();

        for (String value : values) {

            if (value != null
                    && value.toLowerCase()
                        .startsWith(lower)) {

                result.add(value);
            }
        }

        Collections.sort(result);
        return result;
    }

    // -------------------------------------------------------------------------
    // MESSAGES
    // -------------------------------------------------------------------------

    private void send(
            Player player,
            String key,
            String fallback,
            String... replacements) {

        String value =
            message(
                key,
                fallback,
                replacements
            );

        if (!value.isEmpty()) {
            player.sendMessage(value);
        }
    }

    private String message(
            String key,
            String fallback,
            String... replacements) {

        return format(
            plugin.getConfigManager()
                .getMessage(
                    key,
                    fallback
                ),
            replacements
        );
    }

    private String format(
            String raw,
            String... replacements) {

        String value =
            raw == null
                ? ""
                : raw.replace(
                    "{prefix}",
                    prefix()
                );

        for (int index = 0;
                index + 1 < replacements.length;
                index += 2) {

            value =
                value.replace(
                    replacements[index],
                    replacements[index + 1] == null
                        ? ""
                        : replacements[index + 1]
                );
        }

        return value.replace(
            "&",
            "§"
        );
    }

    private String prefix() {
        return plugin.getConfigManager()
            .getPrefix();
    }

    // -------------------------------------------------------------------------
    // TOP HELPERS
    // -------------------------------------------------------------------------

    private String normalizeTopFilter(
            String jobId) {

        if (jobId == null
                || jobId.trim().isEmpty()) {

            return null;
        }

        String lower =
            jobId.trim()
                .toLowerCase();

        if ("global".equals(lower)
                || "all".equals(lower)
                || "general".equals(lower)) {

            return null;
        }

        return plugin.getJobRegistry()
                .getJob(lower) == null
            ? "__invalid__"
            : lower;
    }

    private void sendTop(
            Player player,
            String jobId,
            List<RankingEntry> entries,
            int rank,
            boolean cached) {

        String target =
            getTopTargetDisplay(jobId);

        send(
            player,
            "player_command.top.header",
            "§6§l====== Top {target} ====== §8({count})",
            "{target}",
            target,
            "{target_id}",
            getTopTargetId(jobId),
            "{count}",
            String.valueOf(entries.size()),
            "{cached}",
            String.valueOf(cached)
        );

        if (entries.isEmpty()) {

            send(
                player,
                "player_command.top.empty",
                "{prefix}§7Aucune donnee de classement pour le moment."
            );

        } else {

            for (int index = 0;
                    index < entries.size();
                    index++) {

                RankingEntry entry =
                    entries.get(index);

                send(
                    player,
                    "player_command.top.line",
                    "§e#{position} §f{name} "
                        + "§8- §7Niv §a{level} "
                        + "§8- §7XP §b{xp}",
                    "{position}",
                    String.valueOf(index + 1),
                    "{name}",
                    resolveName(entry),
                    "{uuid}",
                    entry.getUuid().toString(),
                    "{job_id}",
                    entry.getJobId(),
                    "{level}",
                    String.valueOf(entry.getLevel()),
                    "{xp}",
                    String.valueOf(entry.getXp())
                );
            }
        }

        send(
            player,
            "player_command.top.own_rank",
            "{prefix}§7Ton classement: §e{rank}",
            "{rank}",
            rank <= 0
                ? message(
                    "player_command.top.unranked",
                    "non classe"
                )
                : "#" + rank,
            "{target}",
            target,
            "{target_id}",
            getTopTargetId(jobId)
        );
    }

    private String getTopTargetDisplay(
            String jobId) {

        if (jobId == null) {

            return message(
                "player_command.top.global_name",
                "global"
            );
        }

        JobDefinition definition =
            plugin.getJobRegistry()
                .getJob(jobId);

        return definition == null
            ? jobId
            : definition.getDisplayName();
    }

    private String getTopTargetId(
            String jobId) {

        return jobId == null
            ? "global"
            : jobId;
    }

    private String resolveName(
            RankingEntry entry) {

        OfflinePlayer offline =
            Bukkit.getOfflinePlayer(
                entry.getUuid()
            );

        String name =
            offline == null
                ? null
                : offline.getName();

        return name == null
                || name.trim().isEmpty()
            ? entry.getUuid()
                .toString()
                .substring(0, 8)
            : name;
    }

    private List<RankingEntry> getCachedTop(
            String key) {

        int cacheSeconds =
            Math.max(
                0,
                plugin.getConfigManager()
                    .getMainConfig()
                    .getInt(
                        "top.cache_seconds",
                        30
                    )
            );

        if (cacheSeconds <= 0) {
            return null;
        }

        synchronized (topCacheLock) {

            TopCacheEntry entry =
                topCache.get(key);

            if (entry == null) {
                return null;
            }

            if (System.currentTimeMillis()
                    - entry.createdAt
                    > cacheSeconds * 1000L) {

                topCache.remove(key);
                return null;
            }

            return entry.entries;
        }
    }

    private void putCachedTop(
            String key,
            List<RankingEntry> entries) {

        synchronized (topCacheLock) {

            topCache.put(
                key,
                new TopCacheEntry(
                    new ArrayList<RankingEntry>(
                        entries
                    )
                )
            );
        }
    }

    private static final class TopCacheEntry {

        private final long createdAt =
            System.currentTimeMillis();

        private final List<RankingEntry> entries;

        private TopCacheEntry(
                List<RankingEntry> entries) {

            this.entries = entries;
        }
    }
}
