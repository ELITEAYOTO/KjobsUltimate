package me.krunsh.kjobultimate.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Commandes staff KjobsUltimate.
 *
 * V3.11 :
 * les commandes TAB ont été retirées. Elles appartiendront au plugin Ktab.
 */
public final class KjobAdminCommand
        implements CommandExecutor, TabCompleter {

    private final KjobUltimate plugin;

    public KjobAdminCommand(
            KjobUltimate plugin) {

        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!hasAdminPermission(sender)) {
            send(
                sender,
                "commands.no_permission",
                "{prefix}§cTu n'as pas la permission."
            );
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub =
            args[0].toLowerCase();

        if ("status".equals(sub)
                || "statut".equals(sub)) {

            handleStatus(sender);
            return true;
        }

        if ("reload".equals(sub)) {
            handleReload(sender);
            return true;
        }

        if ("xp".equals(sub)
                || "addxp".equals(sub)) {

            handleXp(sender, args);
            return true;
        }

        if ("resetxp".equals(sub)) {
            handleResetXp(sender, args);
            return true;
        }

        if ("level".equals(sub)
                || "setlvl".equals(sub)) {

            handleLevel(sender, args);
            return true;
        }

        if ("reset".equals(sub)
                || "resetjob".equals(sub)) {

            handleReset(sender, args);
            return true;
        }

        if ("setdisplay".equals(sub)) {
            handleSetDisplay(sender, args);
            return true;
        }

        if ("forcejoin".equals(sub)
                || "adminjoin".equals(sub)) {

            handleForceJoin(sender, args);
            return true;
        }

        if ("forceleave".equals(sub)
                || "adminleave".equals(sub)) {

            handleForceLeave(sender, args);
            return true;
        }

        if ("clearcooldown".equals(sub)
                || "resetcooldown".equals(sub)) {

            handleClearCooldown(
                sender,
                args
            );
            return true;
        }

        if ("event".equals(sub)) {
            handleEvent(sender, args);
            return true;
        }

        if ("bonus".equals(sub)) {
            handleBonus(sender, args);
            return true;
        }

        if ("questcomplete".equals(sub)
                || "completequest".equals(sub)
                || "questgive".equals(sub)) {

            handleQuestComplete(
                sender,
                args
            );
            return true;
        }

        if ("resetquest".equals(sub)
                || "questreset".equals(sub)) {

            handleQuestReset(
                sender,
                args
            );
            return true;
        }

        if ("testhud".equals(sub)) {
            handleTestHud(sender, args);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args) {

        if (!hasAdminPermission(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {

            return complete(
                args[0],
                Arrays.asList(
                    "help",
                    "status",
                    "statut",
                    "reload",
                    "addxp",
                    "xp",
                    "resetxp",
                    "setlvl",
                    "level",
                    "reset",
                    "resetjob",
                    "setdisplay",
                    "forcejoin",
                    "forceleave",
                    "clearcooldown",
                    "event",
                    "bonus",
                    "questcomplete",
                    "questgive",
                    "questreset",
                    "resetquest",
                    "testhud"
                )
            );
        }

        String sub =
            args[0].toLowerCase();

        if (args.length == 2) {

            if ("testhud".equals(sub)) {

                return complete(
                    args[1],
                    Arrays.asList(
                        "wither",
                        "dragon",
                        "ender_dragon",
                        "below",
                        "above",
                        "front",
                        "eye_front",
                        "player",
                        "visible",
                        "invisible"
                    )
                );
            }

            if (needsPlayer(sub)) {
                return complete(
                    args[1],
                    onlinePlayerNames()
                );
            }

            if ("event".equals(sub)) {

                return complete(
                    args[1],
                    Arrays.asList(
                        "0.5",
                        "1.0",
                        "1.5",
                        "2.0",
                        "3.0"
                    )
                );
            }

            return Collections.emptyList();
        }

        if (args.length == 3) {

            if ("testhud".equals(sub)) {

                return complete(
                    args[2],
                    Arrays.asList(
                        "below",
                        "above",
                        "front",
                        "eye_front",
                        "player",
                        "visible",
                        "invisible"
                    )
                );
            }

            if (isQuestCompleteCommand(sub)) {
                return complete(
                    args[2],
                    questIds(false)
                );
            }

            if (isQuestResetCommand(sub)) {
                return complete(
                    args[2],
                    questIds(true)
                );
            }

            if (needsJob(sub)) {

                List<String> jobs =
                    new ArrayList<String>(
                        plugin.getJobRegistry()
                            .getJobIds()
                    );

                if ("bonus".equals(sub)) {
                    jobs.add("all");
                }

                return complete(
                    args[2],
                    jobs
                );
            }

            return Collections.emptyList();
        }

        if (args.length == 4) {

            if ("testhud".equals(sub)) {

                return complete(
                    args[3],
                    Arrays.asList(
                        "visible",
                        "invisible"
                    )
                );
            }

            if ("setlvl".equals(sub)
                    || "level".equals(sub)) {

                return complete(
                    args[3],
                    levelSamples()
                );
            }

            if ("forcejoin".equals(sub)
                    || "adminjoin".equals(sub)) {

                return complete(
                    args[3],
                    slotSamples()
                );
            }

            if ("addxp".equals(sub)
                    || "xp".equals(sub)) {

                return complete(
                    args[3],
                    Arrays.asList(
                        "100",
                        "500",
                        "1000",
                        "-100"
                    )
                );
            }

            if ("bonus".equals(sub)) {

                return complete(
                    args[3],
                    Arrays.asList(
                        "1.0",
                        "1.25",
                        "1.5",
                        "2.0",
                        "0.0"
                    )
                );
            }
        }

        return Collections.emptyList();
    }

    private boolean needsPlayer(
            String sub) {

        return "addxp".equals(sub)
            || "xp".equals(sub)
            || "resetxp".equals(sub)
            || "setlvl".equals(sub)
            || "level".equals(sub)
            || "reset".equals(sub)
            || "resetjob".equals(sub)
            || "setdisplay".equals(sub)
            || "forcejoin".equals(sub)
            || "adminjoin".equals(sub)
            || "forceleave".equals(sub)
            || "adminleave".equals(sub)
            || "clearcooldown".equals(sub)
            || "resetcooldown".equals(sub)
            || "bonus".equals(sub)
            || isQuestCompleteCommand(sub)
            || isQuestResetCommand(sub);
    }

    private boolean needsJob(
            String sub) {

        return "addxp".equals(sub)
            || "xp".equals(sub)
            || "resetxp".equals(sub)
            || "setlvl".equals(sub)
            || "level".equals(sub)
            || "setdisplay".equals(sub)
            || "forcejoin".equals(sub)
            || "adminjoin".equals(sub)
            || "forceleave".equals(sub)
            || "adminleave".equals(sub)
            || "bonus".equals(sub);
    }

    private List<String> onlinePlayerNames() {

        List<String> names =
            new ArrayList<String>();

        for (Player player
                : Bukkit.getOnlinePlayers()) {

            names.add(
                player.getName()
            );
        }

        return names;
    }

    private List<String> levelSamples() {

        int max = 50;

        for (JobDefinition def
                : plugin.getJobRegistry()
                    .getAllJobs()) {

            max =
                Math.max(
                    max,
                    def.getMaxLevel()
                );
        }

        return Arrays.asList(
            "0",
            "1",
            "5",
            "10",
            "25",
            String.valueOf(max)
        );
    }

    private List<String> slotSamples() {

        List<String> slots =
            new ArrayList<String>();

        int max =
            plugin.getConfigManager()
                .getMaxSlots();

        for (int i = 1;
                i <= max;
                i++) {

            slots.add(
                String.valueOf(i)
            );
        }

        return slots;
    }

    private List<String> questIds(
            boolean includeAll) {

        List<String> ids =
            new ArrayList<String>();

        if (includeAll) {
            ids.add("all");
        }

        if (plugin.getQuestManager()
                != null) {

            ids.addAll(
                plugin.getQuestManager()
                    .getQuestIds()
            );
        }

        return ids;
    }

    private boolean isQuestCompleteCommand(
            String sub) {

        return "questcomplete".equals(sub)
            || "completequest".equals(sub)
            || "questgive".equals(sub);
    }

    private boolean isQuestResetCommand(
            String sub) {

        return "questreset".equals(sub)
            || "resetquest".equals(sub);
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

    private boolean hasAdminPermission(
            CommandSender sender) {

        return sender.hasPermission(
                "kjobsultimate.admin")
            || sender.hasPermission(
                "kjobsultimate.admin.*")
            || sender.hasPermission(
                "kjob.admin")
            || sender.isOp();
    }

    private void sendHelp(
            CommandSender sender) {

        List<String> lines =
            plugin.getConfigManager()
                .getMessagesConfig()
                .getStringList(
                    "admin_command.help.lines"
                );

        if (lines == null
                || lines.isEmpty()) {

            lines =
                Arrays.asList(
                    "§8----------------------------------------",
                    "§6§l KjobsUltimate §7- Commandes Staff",
                    "§8----------------------------------------",
                    "§e/kjobs status §7- diagnostic plugin",
                    "§e/kjobs reload",
                    "§e/kjobs addxp §f<joueur> <jobId> <+/-montant>",
                    "§e/kjobs resetxp §f<joueur> [jobId]",
                    "§e/kjobs setlvl §f<joueur> <jobId> <niveau>",
                    "§e/kjobs resetjob §f<joueur>",
                    "§e/kjobs setdisplay §f<joueur> <jobId>",
                    "§e/kjobs forcejoin §f<joueur> <jobId> [slot]",
                    "§e/kjobs forceleave §f<joueur> <jobId>",
                    "§e/kjobs clearcooldown §f<joueur>",
                    "§e/kjobs event §f<multiplicateur>",
                    "§e/kjobs bonus §f<joueur> <jobId|all> <multiplicateur>",
                    "§e/kjobs questcomplete §f<joueur> <questId>",
                    "§e/kjobs questreset §f<joueur> <questId|all>",
                    "§e/kjobs testhud §f[wither|dragon] [position] [visible|invisible]",
                    "§8----------------------------------------",
                    "§7TAB extrait vers le plugin Ktab.",
                    "§7Aliases : /kjob /kjobadmin /kjobadm"
                );
        }

        for (String line : lines) {
            sender.sendMessage(
                format(line)
            );
        }
    }

    private void handleStatus(
            CommandSender sender) {

        List<String> lines =
            plugin.getConfigManager()
                .getMessagesConfig()
                .getStringList(
                    "admin_command.status.lines"
                );

        if (lines == null
                || lines.isEmpty()) {

            lines =
                Arrays.asList(
                    "&8----------------------------------------",
                    "&6&l KjobsUltimate &7- Status",
                    "&8----------------------------------------",
                    "&7Version: &f{version} &8| &7Online: &f{online}/{max_online}",
                    "&7Storage: {storage_status} &f{storage_type} &8- &7{storage_path}",
                    "&7Jobs: &f{jobs_loaded}/{jobs_expected} &8- &7{jobs}",
                    "&7Players cache: &f{cache_size}",
                    "&7Views cache: &fjobs={jobs_view_cache} quests={quest_view_cache}",
                    "&7Hooks: &f{hooks}",
                    "&7GUI: {gui_status} &8| &7HUD: {hud_status}",
                    "&7TAB: &fextrait vers Ktab",
                    "&7XP: event x{event_multiplier} &8| &7debug={debug} xp={debug_xp} hud={debug_hud}",
                    "&8----------------------------------------"
                );
        }

        String hooks =
            "Kgui="
                + yn(
                    plugin.getHookManager() != null
                        && plugin.getHookManager()
                            .isKguiEnabled()
                )
                + ", Vault="
                + yn(
                    plugin.getHookManager() != null
                        && plugin.getHookManager()
                            .isVaultEnabled()
                )
                + ", PAPI="
                + yn(
                    plugin.getHookManager() != null
                        && plugin.getHookManager()
                            .isPAPIEnabled()
                )
                + ", Kcraft="
                + yn(
                    plugin.getHookManager() != null
                        && plugin.getHookManager()
                            .isKcraftEnabled()
                )
                + ", Kfaction="
                + yn(
                    plugin.getHookManager() != null
                        && plugin.getHookManager()
                            .isKfactionEnabled()
                )
                + ", KStacker="
                + yn(
                    plugin.getHookManager() != null
                        && plugin.getHookManager()
                            .isKstackerEnabled()
                );

        String guiStatus =
            plugin.getHookManager() != null
                    && plugin.getHookManager()
                        .isKguiEnabled()
                ? "&aKgui V2 ON &7(providers="
                    + plugin.getHookManager()
                        .getRegisteredProviders()
                    + ")"
                : "&cKgui OFF";

        String hudStatus =
            plugin.getHudManager() != null
                ? "&aON &7(ab="
                    + yn(
                        plugin.getHudManager()
                            .isActionBarEnabled()
                    )
                    + ", boss="
                    + yn(
                        plugin.getHudManager()
                            .isBossBarEnabled()
                    )
                    + ", tracked="
                    + plugin.getHudManager()
                        .getTrackedPlayers()
                    + ", nms="
                    + plugin.getHudManager()
                        .getNMS()
                    + ")"
                : "&cOFF";

        int jobsViewCache =
            plugin.getJobsViewService() == null
                ? 0
                : plugin.getJobsViewService()
                    .getCachedPlayerCount();

        int questViewCache =
            plugin.getQuestViewService() == null
                ? 0
                : plugin.getQuestViewService()
                    .getCachedPlayerCount();

        for (String line : lines) {

            sender.sendMessage(
                format(
                    line,
                    "{version}",
                    plugin.getDescription()
                        .getVersion(),
                    "{online}",
                    String.valueOf(
                        Bukkit.getOnlinePlayers()
                            .size()
                    ),
                    "{max_online}",
                    String.valueOf(
                        Bukkit.getMaxPlayers()
                    ),
                    "{storage_status}",
                    plugin.getDatabaseManager() != null
                            && plugin.getDatabaseManager()
                                .isOpen()
                        ? "&aOK"
                        : "&cKO",
                    "{storage_type}",
                    plugin.getDatabaseManager() != null
                        ? plugin.getDatabaseManager()
                            .getStorageTypeName()
                        : "UNKNOWN",
                    "{storage_path}",
                    plugin.getDatabaseManager() != null
                        ? plugin.getDatabaseManager()
                            .getDbPath()
                        : "n/a",
                    "{pool_status}",
                    plugin.getDatabaseManager() != null
                        ? plugin.getDatabaseManager()
                            .getPoolStatus()
                        : "n/a",
                    "{jobs_loaded}",
                    String.valueOf(
                        plugin.getJobRegistry()
                            .getJobCount()
                    ),
                    "{jobs_expected}",
                    String.valueOf(
                        plugin.getJobRegistry()
                            .getExpectedJobIds()
                            .size()
                    ),
                    "{jobs}",
                    joinJobIds(),
                    "{cache_size}",
                    plugin.getPlayerDataManager() != null
                        ? String.valueOf(
                            plugin.getPlayerDataManager()
                                .getCacheSize()
                        )
                        : "0",
                    "{jobs_view_cache}",
                    String.valueOf(
                        jobsViewCache
                    ),
                    "{quest_view_cache}",
                    String.valueOf(
                        questViewCache
                    ),
                    "{hooks}",
                    hooks,
                    "{gui_status}",
                    guiStatus,
                    "{hud_status}",
                    hudStatus,
                    "{tab_status}",
                    "&7externalise vers Ktab",
                    "{virtual_tab_status}",
                    "&7externalise vers Ktab",
                    "{event_multiplier}",
                    String.valueOf(
                        plugin.getXpManager()
                            .getEventMultiplier()
                    ),
                    "{debug}",
                    yn(
                        plugin.getConfigManager()
                            .isDebug()
                    ),
                    "{debug_xp}",
                    yn(
                        plugin.getConfigManager()
                            .isDebugXp()
                    ),
                    "{debug_hud}",
                    yn(
                        plugin.getConfigManager()
                            .isDebugHud()
                    )
                )
            );
        }
    }

    private void handleTestHud(
            CommandSender sender,
            String[] args) {

        if (!(sender instanceof Player)) {

            send(
                sender,
                "admin_command.testhud.player_only",
                "{prefix}§c/kjobs testhud reserve aux joueurs en jeu."
            );

            return;
        }

        Player player =
            (Player) sender;

        if (plugin.getHudManager() == null) {

            send(
                sender,
                "admin_command.testhud.hud_null",
                "§c[TESTHUD] HudManager est NULL."
            );

            return;
        }

        send(
            sender,
            "admin_command.testhud.hud_ok",
            "§a[TESTHUD] HudManager OK - NMS version: {nms}",
            "{nms}",
            plugin.getHudManager()
                .getNMS()
        );

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(player);

        if (data == null) {

            send(
                sender,
                "admin_command.testhud.data_null",
                "§c[TESTHUD] PlayerData NULL."
            );

            return;
        }

        String entityOverride = null;
        String positionOverride = null;
        Boolean invisibleOverride = null;

        for (int i = 1;
                i < args.length;
                i++) {

            String token =
                args[i].toLowerCase()
                    .replace(
                        '-',
                        '_'
                    );

            if ("wither".equals(token)) {
                entityOverride = "WITHER";

            } else if ("dragon".equals(token)
                    || "ender_dragon".equals(token)) {

                entityOverride = "ENDER_DRAGON";

            } else if ("below".equals(token)
                    || "above".equals(token)
                    || "front".equals(token)
                    || "eye_front".equals(token)
                    || "eyefront".equals(token)
                    || "player".equals(token)) {

                positionOverride = token;

            } else if ("visible".equals(token)) {
                invisibleOverride = Boolean.FALSE;

            } else if ("invisible".equals(token)) {
                invisibleOverride = Boolean.TRUE;
            }
        }

        try {

            plugin.getHudManager()
                .testBossBar(
                    player,
                    entityOverride,
                    positionOverride,
                    invisibleOverride
                );

            send(
                sender,
                "admin_command.testhud.bossbar_ok",
                "§a[TESTHUD] BossBar de test envoyee."
            );

        } catch (Exception failure) {

            send(
                sender,
                "admin_command.testhud.bossbar_error",
                "§c[TESTHUD] testBossBar ERREUR: {error}",
                "{error}",
                failure.getMessage()
            );

            KjobLogger.warn(
                "[TESTHUD] testBossBar: "
                    + failure
            );
        }
    }

    private void handleReload(
            CommandSender sender) {

        try {

            plugin.getConfigManager()
                .loadAll();

            plugin.getJobRegistry()
                .loadAll();

            if (plugin.getQuestManager()
                    != null) {

                plugin.getQuestManager()
                    .loadAll();
            }

            new me.krunsh.kjobultimate.validation.ConfigValidator(
                plugin
            ).validateOrThrow();

            plugin.clearViewCaches();

            if (plugin.getHudManager()
                    != null) {

                plugin.getHudManager()
                    .reloadHudConfig();
            }

            send(
                sender,
                "commands.reload_success",
                "{prefix}§aConfigs et jobs recharges."
            );

            KjobLogger.reload(
                "Rechargement admin par "
                    + sender.getName()
            );

        } catch (Exception failure) {

            send(
                sender,
                "admin_command.reload.error",
                "{prefix}§cErreur lors du rechargement: {error}",
                "{error}",
                failure.getMessage()
            );

            KjobLogger.error(
                "Erreur rechargement admin",
                failure
            );
        }
    }

    private void handleXp(
            CommandSender sender,
            String[] args) {

        if (args.length < 4) {
            send(
                sender,
                "admin_command.usage.addxp",
                "§c§7Usage: /kjobs addxp <joueur> <jobId> <+/-montant>"
            );
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        String jobId =
            args[2].toLowerCase();

        JobDefinition def =
            requireJob(
                sender,
                jobId
            );

        if (def == null) {
            return;
        }

        Integer amount =
            parseInt(
                sender,
                args[3],
                "admin_command.invalid.amount",
                "{prefix}§cMontant invalide: §e{value}"
            );

        if (amount == null) {
            return;
        }

        PlayerData data =
            requireData(
                sender,
                target
            );

        if (data == null) {
            return;
        }

        LevelUpResult result =
            plugin.getXpManager()
                .adminAddXp(
                    target,
                    data,
                    jobId,
                    amount.intValue()
                );

        if (result.isLeveledUp()) {

            plugin.getXpManager()
                .handleLevelUp(
                    target,
                    data,
                    jobId,
                    result
                );
        }

        plugin.notifyJobsUiChanged(
            target.getUniqueId(),
            "kjobs:admin-xp"
        );

        send(
            sender,
            "admin_command.xp.modified",
            "{prefix}§aXP de §e{player} §7en §e{job_id} §7modifie - niveau §f{level} §7- §f{xp} §7XP",
            "{player}",
            target.getName(),
            "{job}",
            def.getDisplayName(),
            "{job_id}",
            jobId,
            "{level}",
            String.valueOf(
                result.getNewLevel()
            ),
            "{xp}",
            String.valueOf(
                result.getRemainingXP()
            ),
            "{amount}",
            String.valueOf(amount)
        );
    }

    private void handleLevel(
            CommandSender sender,
            String[] args) {

        if (args.length < 4) {
            send(
                sender,
                "admin_command.usage.setlvl",
                "§c§7Usage: /kjobs setlvl <joueur> <jobId> <niveau>"
            );
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        String jobId =
            args[2].toLowerCase();

        JobDefinition def =
            requireJob(
                sender,
                jobId
            );

        if (def == null) {
            return;
        }

        Integer level =
            parseInt(
                sender,
                args[3],
                "admin_command.invalid.level",
                "{prefix}§cNiveau invalide: §e{value}"
            );

        if (level == null) {
            return;
        }

        if (level.intValue() < 0
                || level.intValue()
                    > def.getMaxLevel()) {

            send(
                sender,
                "admin_command.invalid.level_range",
                "{prefix}§cNiveau hors limites (0-{max_level}).",
                "{max_level}",
                String.valueOf(
                    def.getMaxLevel()
                )
            );

            return;
        }

        PlayerData data =
            requireData(
                sender,
                target
            );

        if (data == null) {
            return;
        }

        data.setLevel(
            jobId,
            level.intValue()
        );

        data.setXP(
            jobId,
            0
        );

        plugin.notifyJobsUiChanged(
            target.getUniqueId(),
            "kjobs:admin-level"
        );

        send(
            sender,
            "admin_command.level.set",
            "{prefix}§aNiveau de §e{player} §7en §e{job_id} §7defini a §f{level}",
            "{player}",
            target.getName(),
            "{job}",
            def.getDisplayName(),
            "{job_id}",
            jobId,
            "{level}",
            String.valueOf(level)
        );
    }

    private void handleReset(
            CommandSender sender,
            String[] args) {

        if (args.length < 2) {
            send(
                sender,
                "admin_command.usage.resetjob",
                "§c§7Usage: /kjobs resetjob <joueur>"
            );
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        PlayerData data =
            requireData(
                sender,
                target
            );

        if (data == null) {
            return;
        }

        for (String jobId
                : plugin.getJobRegistry()
                    .getJobIds()) {

            data.setLevel(jobId, 0);
            data.setXP(jobId, 0);
        }

        data.setDisplayJob(null);
        data.markDirty();

        plugin.notifyJobsUiChanged(
            target.getUniqueId(),
            "kjobs:admin-reset"
        );

        send(
            sender,
            "admin_command.resetjob.done",
            "{prefix}§aJobs de §e{player} §7remis a zero.",
            "{player}",
            target.getName()
        );
    }

    private void handleSetDisplay(
            CommandSender sender,
            String[] args) {

        if (args.length < 3) {
            send(
                sender,
                "admin_command.usage.setdisplay",
                "§c§7Usage: /kjobs setdisplay <joueur> <jobId>"
            );
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        String jobId =
            args[2].toLowerCase();

        JobDefinition def =
            requireJob(
                sender,
                jobId
            );

        if (def == null) {
            return;
        }

        PlayerData data =
            requireData(
                sender,
                target
            );

        if (data == null) {
            return;
        }

        data.setDisplayJob(jobId);

        plugin.notifyJobsUiChanged(
            target.getUniqueId(),
            "kjobs:admin-display",
            "kjobs_main",
            "kjobs_detail"
        );

        send(
            sender,
            "admin_command.display.set",
            "{prefix}§aJob actif de §e{player} §7defini a §e{job}",
            "{player}",
            target.getName(),
            "{job}",
            def.getDisplayName(),
            "{job_id}",
            jobId
        );
    }

    private void handleEvent(
            CommandSender sender,
            String[] args) {

        if (args.length < 2) {

            double current =
                plugin.getConfigManager()
                    .getMainConfig()
                    .getDouble(
                        "xp_multipliers.event_multiplier",
                        1.0D
                    );

            send(
                sender,
                "admin_command.event.current",
                "{prefix}§aMultiplicateur XP event actuel: §ex{multiplier}",
                "{multiplier}",
                String.valueOf(current)
            );

            return;
        }

        Double multiplier =
            parseDouble(
                sender,
                args[1],
                "admin_command.invalid.value",
                "{prefix}§cValeur invalide: §e{value}"
            );

        if (multiplier == null) {
            return;
        }

        if (multiplier.doubleValue() < 0.0D) {

            send(
                sender,
                "admin_command.invalid.negative_multiplier",
                "{prefix}§cLe multiplicateur ne peut pas etre negatif."
            );

            return;
        }

        plugin.getConfigManager()
            .getMainConfig()
            .set(
                "xp_multipliers.event_multiplier",
                multiplier.doubleValue()
            );

        send(
            sender,
            "admin_command.event.set",
            "{prefix}§aMultiplicateur XP event defini a §ex{multiplier}§7.",
            "{multiplier}",
            String.valueOf(multiplier)
        );
    }

    private void handleBonus(
            CommandSender sender,
            String[] args) {

        if (args.length < 4) {
            send(
                sender,
                "admin_command.usage.bonus",
                "§c§7Usage: /kjobs bonus <joueur> <jobId|all> <valeur>"
            );
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        String jobId =
            args[2].toLowerCase();

        Double multiplier =
            parseDouble(
                sender,
                args[3],
                "admin_command.invalid.value",
                "{prefix}§cValeur invalide: §e{value}"
            );

        if (multiplier == null) {
            return;
        }

        if (multiplier.doubleValue() < 0.0D) {

            send(
                sender,
                "admin_command.invalid.negative_multiplier",
                "{prefix}§cLe multiplicateur ne peut pas etre negatif."
            );

            return;
        }

        PlayerData data =
            requireData(
                sender,
                target
            );

        if (data == null) {
            return;
        }

        data.setBonusMultiplier(
            jobId,
            multiplier.doubleValue()
        );

        final String finalJobId =
            jobId;

        final String setBy =
            sender.getName();

        final double finalMultiplier =
            multiplier.doubleValue();

        plugin.getServer()
            .getScheduler()
            .runTaskAsynchronously(
                plugin,
                new Runnable() {
                    @Override
                    public void run() {

                        try {

                            plugin.getDatabaseManager()
                                .saveBonusMultiplier(
                                    target.getUniqueId(),
                                    finalJobId,
                                    finalMultiplier,
                                    setBy
                                );

                        } catch (Exception failure) {

                            KjobLogger.error(
                                "Erreur sauvegarde bonus multiplier",
                                failure
                            );
                        }
                    }
                }
            );

        send(
            sender,
            "admin_command.bonus.set",
            "{prefix}§aBonus multiplier de §e{player}§7 pour §e{job_id}§7 -> §fx{multiplier}",
            "{player}",
            target.getName(),
            "{job_id}",
            jobId,
            "{multiplier}",
            String.valueOf(multiplier)
        );
    }

    private void handleForceJoin(
            CommandSender sender,
            String[] args) {

        if (args.length < 3) {
            send(
                sender,
                "admin_command.usage.forcejoin",
                "§c§7Usage: /kjobs forcejoin <joueur> <jobId> [slot]"
            );
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        String jobId =
            args[2].toLowerCase();

        JobDefinition def =
            requireJob(
                sender,
                jobId
            );

        if (def == null) {
            return;
        }

        PlayerData data =
            requireData(
                sender,
                target
            );

        if (data == null) {
            return;
        }

        if (plugin.getSlotManager()
                .isJobActive(
                    data,
                    jobId
                )) {

            plugin.getSlotManager()
                .setFavoriteJob(
                    target,
                    data,
                    jobId
                );

            send(
                sender,
                "admin_command.forcejoin.already",
                "{prefix}§a{player} avait deja ce job. Favori mis a jour.",
                "{player}",
                target.getName(),
                "{job}",
                def.getDisplayName(),
                "{job_id}",
                jobId
            );

            return;
        }

        if (args.length >= 4) {

            Integer slotValue =
                parseInt(
                    sender,
                    args[3],
                    "admin_command.invalid.slot",
                    "{prefix}§cSlot invalide: §e{value}"
                );

            if (slotValue == null) {
                return;
            }

            int slot =
                slotValue.intValue();

            int maxSlots =
                plugin.getConfigManager()
                    .getMaxSlots();

            if (slot < 1
                    || slot > maxSlots) {

                send(
                    sender,
                    "admin_command.invalid.slot_range",
                    "{prefix}§cSlot hors limites (1-{max_slots}).",
                    "{max_slots}",
                    String.valueOf(maxSlots)
                );

                return;
            }

            if (slot
                    > data.getUnlockedSlots()) {

                data.setUnlockedSlots(
                    slot
                );
            }

            String oldJob =
                data.getJobInSlot(slot);

            if (oldJob != null) {

                plugin.getSlotManager()
                    .forceLeaveJob(
                        target,
                        data,
                        oldJob,
                        true
                    );
            }

            plugin.getSlotManager()
                .assignJobToSlot(
                    target,
                    data,
                    slot,
                    jobId
                );

        } else if (!plugin.getSlotManager()
                .assignJobToFreeSlot(
                    target,
                    data,
                    jobId
                )) {

            if (data.getUnlockedSlots()
                    < plugin.getConfigManager()
                        .getMaxSlots()) {

                data.setUnlockedSlots(
                    data.getUnlockedSlots()
                        + 1
                );

                plugin.getSlotManager()
                    .assignJobToFreeSlot(
                        target,
                        data,
                        jobId
                    );

            } else {

                send(
                    sender,
                    "admin_command.forcejoin.no_slot",
                    "{prefix}§cAucun slot libre."
                );

                return;
            }
        }

        plugin.getSlotManager()
            .setFavoriteJob(
                target,
                data,
                jobId
            );

        plugin.notifyJobsUiChanged(
            target.getUniqueId(),
            "kjobs:admin-forcejoin"
        );

        send(
            sender,
            "admin_command.forcejoin.done",
            "{prefix}§aJob §e{job} §7force pour §e{player}§7.",
            "{player}",
            target.getName(),
            "{job}",
            def.getDisplayName(),
            "{job_id}",
            jobId
        );
    }

    private void handleForceLeave(
            CommandSender sender,
            String[] args) {

        if (args.length < 3) {
            send(
                sender,
                "admin_command.usage.forceleave",
                "§c§7Usage: /kjobs forceleave <joueur> <jobId>"
            );
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        String jobId =
            args[2].toLowerCase();

        JobDefinition def =
            requireJob(
                sender,
                jobId
            );

        if (def == null) {
            return;
        }

        PlayerData data =
            requireData(
                sender,
                target
            );

        if (data == null) {
            return;
        }

        if (!plugin.getSlotManager()
                .forceLeaveJob(
                    target,
                    data,
                    jobId,
                    true
                )) {

            send(
                sender,
                "admin_command.forceleave.not_unlocked",
                "{prefix}§cCe joueur n'a pas debloque ce job.",
                "{player}",
                target.getName(),
                "{job}",
                def.getDisplayName(),
                "{job_id}",
                jobId
            );

            return;
        }

        plugin.notifyJobsUiChanged(
            target.getUniqueId(),
            "kjobs:admin-forceleave"
        );

        send(
            sender,
            "admin_command.forceleave.done",
            "{prefix}§aJob §e{job} §7retire de §e{player}§7, progression reset.",
            "{player}",
            target.getName(),
            "{job}",
            def.getDisplayName(),
            "{job_id}",
            jobId
        );
    }

    private void handleClearCooldown(
            CommandSender sender,
            String[] args) {

        if (args.length < 2) {
            send(
                sender,
                "admin_command.usage.clearcooldown",
                "§c§7Usage: /kjobs clearcooldown <joueur>"
            );
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        PlayerData data =
            requireData(
                sender,
                target
            );

        if (data == null) {
            return;
        }

        plugin.getSlotManager()
            .clearJobChangeCooldown(data);

        send(
            sender,
            "admin_command.cooldown.cleared",
            "{prefix}§aCooldown jobs retire pour §e{player}§7.",
            "{player}",
            target.getName()
        );
    }

    private void handleResetXp(
            CommandSender sender,
            String[] args) {

        if (args.length < 2) {
            send(
                sender,
                "admin_command.usage.resetxp",
                "§c§7Usage: /kjobs resetxp <joueur> [jobId]"
            );
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        PlayerData data =
            requireData(
                sender,
                target
            );

        if (data == null) {
            return;
        }

        if (args.length >= 3) {

            String jobId =
                args[2].toLowerCase();

            JobDefinition def =
                requireJob(
                    sender,
                    jobId
                );

            if (def == null) {
                return;
            }

            data.setXP(
                jobId,
                0
            );

            send(
                sender,
                "admin_command.resetxp.job",
                "{prefix}§aXP de §e{player} §7en §e{job_id} §7remis a §f0§7.",
                "{player}",
                target.getName(),
                "{job}",
                def.getDisplayName(),
                "{job_id}",
                jobId
            );

        } else {

            for (String jobId
                    : plugin.getJobRegistry()
                        .getJobIds()) {

                data.setXP(
                    jobId,
                    0
                );
            }

            send(
                sender,
                "admin_command.resetxp.all",
                "{prefix}§aXP de §e{player} §7remis a §f0 §7pour tous les jobs.",
                "{player}",
                target.getName()
            );
        }

        plugin.notifyJobsUiChanged(
            target.getUniqueId(),
            "kjobs:admin-reset-xp"
        );
    }

    private void handleQuestComplete(
            CommandSender sender,
            String[] args) {

        if (args.length < 3) {
            send(
                sender,
                "admin_command.usage.questcomplete",
                "§c§7Usage: /kjobs questcomplete <joueur> <questId>"
            );
            return;
        }

        if (!isQuestSystemReady(sender)) {
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        QuestDefinition quest =
            requireQuest(
                sender,
                args[2]
            );

        if (quest == null) {
            return;
        }

        if (!plugin.getQuestManager()
                .forceComplete(
                    target,
                    quest.getId()
                )) {

            send(
                sender,
                "admin_command.questcomplete.error",
                "{prefix}§cImpossible de completer la quete §e{quest_id}§c.",
                "{player}",
                target.getName(),
                "{quest}",
                quest.getDisplayName(),
                "{quest_id}",
                quest.getId()
            );

            return;
        }

        send(
            sender,
            "admin_command.questcomplete.done",
            "{prefix}§aQuete §f{quest} §7rendue claimable pour §e{player}§7.",
            "{player}",
            target.getName(),
            "{quest}",
            quest.getDisplayName(),
            "{quest_id}",
            quest.getId()
        );
    }

    private void handleQuestReset(
            CommandSender sender,
            String[] args) {

        if (args.length < 3) {
            send(
                sender,
                "admin_command.usage.questreset",
                "§c§7Usage: /kjobs questreset <joueur> <questId|all>"
            );
            return;
        }

        if (!isQuestSystemReady(sender)) {
            return;
        }

        Player target =
            requirePlayer(
                sender,
                args[1]
            );

        if (target == null) {
            return;
        }

        String questId =
            args[2].toLowerCase();

        if ("all".equals(questId)
                || "*".equals(questId)) {

            int count =
                plugin.getQuestManager()
                    .resetAllQuests(target);

            send(
                sender,
                "admin_command.questreset.all",
                "{prefix}§a{count} quete(s) reset pour §e{player}§7.",
                "{player}",
                target.getName(),
                "{count}",
                String.valueOf(count)
            );

            return;
        }

        QuestDefinition quest =
            requireQuest(
                sender,
                questId
            );

        if (quest == null) {
            return;
        }

        if (!plugin.getQuestManager()
                .resetQuest(
                    target,
                    quest.getId()
                )) {

            send(
                sender,
                "admin_command.questreset.error",
                "{prefix}§cImpossible de reset la quete §e{quest_id}§c.",
                "{player}",
                target.getName(),
                "{quest}",
                quest.getDisplayName(),
                "{quest_id}",
                quest.getId()
            );

            return;
        }

        send(
            sender,
            "admin_command.questreset.done",
            "{prefix}§aQuete §f{quest} §7reset pour §e{player}§7.",
            "{player}",
            target.getName(),
            "{quest}",
            quest.getDisplayName(),
            "{quest_id}",
            quest.getId()
        );
    }

    private Player requirePlayer(
            CommandSender sender,
            String name) {

        Player target =
            Bukkit.getPlayerExact(name);

        if (target == null) {

            send(
                sender,
                "commands.player_not_found",
                "{prefix}§cJoueur introuvable ou hors ligne: §e{player}",
                "{player}",
                name
            );

            return null;
        }

        return target;
    }

    private JobDefinition requireJob(
            CommandSender sender,
            String jobId) {

        JobDefinition def =
            plugin.getJobRegistry()
                .getJob(jobId);

        if (def == null) {

            send(
                sender,
                "commands.job_not_found",
                "{prefix}§cJob '{job}' introuvable. Jobs disponibles : {list}",
                "{job}",
                jobId,
                "{job_id}",
                jobId,
                "{list}",
                joinJobIds()
            );

            return null;
        }

        return def;
    }

    private QuestDefinition requireQuest(
            CommandSender sender,
            String questId) {

        QuestDefinition quest =
            plugin.getQuestManager() == null
                ? null
                : plugin.getQuestManager()
                    .getQuest(questId);

        if (quest == null) {

            send(
                sender,
                "admin_command.quest.unknown",
                "{prefix}§cQuete '§e{quest_id}§c' introuvable. Quetes disponibles : §7{list}",
                "{quest_id}",
                questId,
                "{list}",
                joinQuestIds()
            );

            return null;
        }

        return quest;
    }

    private boolean isQuestSystemReady(
            CommandSender sender) {

        if (plugin.getQuestManager() == null
                || !plugin.getQuestManager()
                    .isEnabled()) {

            send(
                sender,
                "admin_command.quest.disabled",
                "{prefix}§cLe systeme de quetes est desactive ou non initialise."
            );

            return false;
        }

        return true;
    }

    private PlayerData requireData(
            CommandSender sender,
            Player target) {

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(target);

        if (data == null) {

            send(
                sender,
                "admin_command.player_data_not_loaded",
                "{prefix}§cDonnees joueur non chargees pour §e{player}§c.",
                "{player}",
                target.getName()
            );

            return null;
        }

        return data;
    }

    private Integer parseInt(
            CommandSender sender,
            String raw,
            String key,
            String fallback) {

        try {
            return Integer.valueOf(
                Integer.parseInt(raw)
            );

        } catch (NumberFormatException failure) {

            send(
                sender,
                key,
                fallback,
                "{value}",
                raw
            );

            return null;
        }
    }

    private Double parseDouble(
            CommandSender sender,
            String raw,
            String key,
            String fallback) {

        try {
            return Double.valueOf(
                Double.parseDouble(raw)
            );

        } catch (NumberFormatException failure) {

            send(
                sender,
                key,
                fallback,
                "{value}",
                raw
            );

            return null;
        }
    }

    private String joinJobIds() {

        StringBuilder builder =
            new StringBuilder();

        for (String jobId
                : plugin.getJobRegistry()
                    .getJobIds()) {

            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(jobId);
        }

        return builder.toString();
    }

    private String joinQuestIds() {

        if (plugin.getQuestManager()
                == null) {

            return "";
        }

        StringBuilder builder =
            new StringBuilder();

        for (String questId
                : plugin.getQuestManager()
                    .getQuestIds()) {

            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(questId);
        }

        return builder.toString();
    }

    private String yn(
            boolean value) {

        return value
            ? "§aON"
            : "§cOFF";
    }

    private void send(
            CommandSender sender,
            String key,
            String fallback,
            String... replacements) {

        String msg =
            message(
                key,
                fallback,
                replacements
            );

        if (!msg.isEmpty()) {
            sender.sendMessage(msg);
        }
    }

    private String message(
            String key,
            String fallback,
            String... replacements) {

        String msg =
            plugin.getConfigManager()
                .getMessage(
                    key,
                    fallback
                );

        return format(
            msg,
            replacements
        );
    }

    private String format(
            String raw,
            String... replacements) {

        String msg =
            raw == null
                ? ""
                : raw.replace(
                    "{prefix}",
                    plugin.getConfigManager()
                        .getPrefix()
                );

        for (int i = 0;
                i + 1 < replacements.length;
                i += 2) {

            msg =
                msg.replace(
                    replacements[i],
                    replacements[i + 1]
                        == null
                        ? ""
                        : replacements[i + 1]
                );
        }

        return msg.replace(
            "&",
            "§"
        );
    }
}
