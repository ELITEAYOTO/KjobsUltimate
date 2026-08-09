package me.krunsh.kjobultimate.commands;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gestionnaire des commandes staff : /kjobs /kjob /kjobadmin /kjobadm.
 */
public final class KjobAdminCommand implements CommandExecutor, TabCompleter {

    private final KjobUltimate plugin;

    public KjobAdminCommand(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasAdminPermission(sender)) {
            send(sender, "commands.no_permission", "{prefix}\u00A7cTu n'as pas la permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
            case "statut":
                handleStatus(sender);
                return true;
            case "reload":
                handleReload(sender);
                return true;
            case "xp":
            case "addxp":
                handleXp(sender, args);
                return true;
            case "resetxp":
                handleResetXp(sender, args);
                return true;
            case "level":
            case "setlvl":
                handleLevel(sender, args);
                return true;
            case "reset":
            case "resetjob":
                handleReset(sender, args);
                return true;
            case "setdisplay":
                handleSetDisplay(sender, args);
                return true;
            case "forcejoin":
            case "adminjoin":
                handleForceJoin(sender, args);
                return true;
            case "forceleave":
            case "adminleave":
                handleForceLeave(sender, args);
                return true;
            case "clearcooldown":
            case "resetcooldown":
                handleClearCooldown(sender, args);
                return true;
            case "event":
                handleEvent(sender, args);
                return true;
            case "bonus":
                handleBonus(sender, args);
                return true;
            case "questcomplete":
            case "completequest":
            case "questgive":
                handleQuestComplete(sender, args);
                return true;
            case "resetquest":
            case "questreset":
                handleQuestReset(sender, args);
                return true;
            case "testhud":
                handleTestHud(sender, args);
                return true;
            case "tabdebug":
                handleTabDebug(sender, args);
                return true;
            case "tabrender":
                handleTabRender(sender, args);
                return true;
            case "tabclear":
                handleTabClear(sender, args);
                return true;
            case "tabclearall":
                handleTabClearAll(sender);
                return true;
            case "help":
            default:
                sendHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAdminPermission(sender)) return Collections.emptyList();

        if (args.length == 1) {
            return complete(args[0], Arrays.asList(
                "help", "status", "statut", "reload", "addxp", "xp", "resetxp", "setlvl", "level",
                "reset", "resetjob", "setdisplay", "forcejoin", "forceleave",
                "clearcooldown", "event", "bonus", "questcomplete", "questgive", "questreset", "resetquest", "testhud",
                "tabdebug", "tabrender", "tabclear", "tabclearall"
            ));
        }

        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if ("testhud".equals(sub)) return complete(args[1], Arrays.asList(
                "wither", "dragon", "ender_dragon", "below", "above", "front", "eye_front", "player", "visible", "invisible"
            ));
            if ("tabdebug".equals(sub) || "tabrender".equals(sub) || "tabclear".equals(sub)) return complete(args[1], onlinePlayerNames());
            if (needsPlayer(sub)) return complete(args[1], onlinePlayerNames());
            if ("event".equals(sub)) return complete(args[1], Arrays.asList("0.5", "1.0", "1.5", "2.0", "3.0"));
            return Collections.emptyList();
        }

        if (args.length == 3) {
            if ("testhud".equals(sub)) return complete(args[2], Arrays.asList(
                "below", "above", "front", "eye_front", "player", "visible", "invisible"
            ));
            if (isQuestCompleteCommand(sub)) return complete(args[2], questIds(false));
            if (isQuestResetCommand(sub)) return complete(args[2], questIds(true));
            if (needsJob(sub)) {
                List<String> jobs = new ArrayList<String>(plugin.getJobRegistry().getJobIds());
                if ("bonus".equals(sub)) jobs.add("all");
                return complete(args[2], jobs);
            }
            return Collections.emptyList();
        }

        if (args.length == 4) {
            if ("testhud".equals(sub)) return complete(args[3], Arrays.asList("visible", "invisible"));
            if ("setlvl".equals(sub) || "level".equals(sub)) return complete(args[3], levelSamples());
            if ("forcejoin".equals(sub) || "adminjoin".equals(sub)) return complete(args[3], slotSamples());
            if ("addxp".equals(sub) || "xp".equals(sub)) return complete(args[3], Arrays.asList("100", "500", "1000", "-100"));
            if ("bonus".equals(sub)) return complete(args[3], Arrays.asList("1.0", "1.25", "1.5", "2.0", "0.0"));
        }

        return Collections.emptyList();
    }

    private boolean needsPlayer(String sub) {
        return "addxp".equals(sub) || "xp".equals(sub) || "resetxp".equals(sub)
            || "setlvl".equals(sub) || "level".equals(sub) || "reset".equals(sub)
            || "resetjob".equals(sub) || "setdisplay".equals(sub) || "forcejoin".equals(sub)
            || "adminjoin".equals(sub) || "forceleave".equals(sub) || "adminleave".equals(sub)
            || "clearcooldown".equals(sub) || "resetcooldown".equals(sub) || "bonus".equals(sub)
            || isQuestCompleteCommand(sub) || isQuestResetCommand(sub);
    }

    private boolean needsJob(String sub) {
        return "addxp".equals(sub) || "xp".equals(sub) || "resetxp".equals(sub)
            || "setlvl".equals(sub) || "level".equals(sub) || "setdisplay".equals(sub)
            || "forcejoin".equals(sub) || "adminjoin".equals(sub) || "forceleave".equals(sub)
            || "adminleave".equals(sub) || "bonus".equals(sub);
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<String>();
        for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
        return names;
    }

    private List<String> levelSamples() {
        int max = 50;
        for (JobDefinition def : plugin.getJobRegistry().getAllJobs()) {
            max = Math.max(max, def.getMaxLevel());
        }
        return Arrays.asList("0", "1", "5", "10", "25", String.valueOf(max));
    }

    private List<String> slotSamples() {
        List<String> slots = new ArrayList<String>();
        int max = plugin.getConfigManager().getMaxSlots();
        for (int i = 1; i <= max; i++) slots.add(String.valueOf(i));
        return slots;
    }

    private List<String> questIds(boolean includeAll) {
        List<String> ids = new ArrayList<String>();
        if (includeAll) ids.add("all");
        if (plugin.getQuestManager() != null) ids.addAll(plugin.getQuestManager().getQuestIds());
        return ids;
    }

    private boolean isQuestCompleteCommand(String sub) {
        return "questcomplete".equals(sub) || "completequest".equals(sub) || "questgive".equals(sub);
    }

    private boolean isQuestResetCommand(String sub) {
        return "questreset".equals(sub) || "resetquest".equals(sub);
    }

    private List<String> complete(String token, Iterable<String> values) {
        String lower = token == null ? "" : token.toLowerCase();
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value != null && value.toLowerCase().startsWith(lower)) result.add(value);
        }
        Collections.sort(result);
        return result;
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission("kjobsultimate.admin")
            || sender.hasPermission("kjobsultimate.admin.*")
            || sender.hasPermission("kjob.admin")
            || sender.isOp();
    }

    private void sendHelp(CommandSender sender) {
        List<String> lines = plugin.getConfigManager().getMessagesConfig().getStringList("admin_command.help.lines");
        if (lines == null || lines.isEmpty()) {
            lines = Arrays.asList(
                "\u00A78----------------------------------------",
                "\u00A76\u00A7l KjobsUltimate \u00A77- Commandes Staff",
                "\u00A78----------------------------------------",
                "\u00A7e/kjobs status \u00A77- diagnostic plugin",
                "\u00A7e/kjobs reload",
                "\u00A7e/kjobs addxp \u00A7f<joueur> <jobId> <+/-montant>",
                "\u00A7e/kjobs resetxp \u00A7f<joueur> [jobId]",
                "\u00A7e/kjobs setlvl \u00A7f<joueur> <jobId> <niveau>",
                "\u00A7e/kjobs resetjob \u00A7f<joueur>",
                "\u00A7e/kjobs setdisplay \u00A7f<joueur> <jobId>",
                "\u00A7e/kjobs forcejoin \u00A7f<joueur> <jobId> [slot]",
                "\u00A7e/kjobs forceleave \u00A7f<joueur> <jobId>",
                "\u00A7e/kjobs clearcooldown \u00A7f<joueur>",
                "\u00A7e/kjobs event \u00A7f<multiplicateur>",
                "\u00A7e/kjobs bonus \u00A7f<joueur> <jobId|all> <multiplicateur>",
                "\u00A7e/kjobs questcomplete \u00A7f<joueur> <questId> \u00A77- rend la quete claimable",
                "\u00A7e/kjobs questreset \u00A7f<joueur> <questId|all>",
                "\u00A7e/kjobs testhud \u00A7f[wither|dragon] [below|above|front|eye_front|player] [visible|invisible]",
                "\u00A7e/kjobs tabdebug \u00A77[joueur] - diagnostic tab virtuel",
                "\u00A7e/kjobs tabrender \u00A77[joueur] - preview lignes tab virtuel",
                "\u00A7e/kjobs tabclear \u00A77[joueur] - retire les lignes fake du joueur",
                "\u00A78----------------------------------------",
                "\u00A77Aliases : /kjob /kjobadmin /kjobadm"
            );
        }
        for (String line : lines) sender.sendMessage(format(line));
    }

    private void handleStatus(CommandSender sender) {
        List<String> lines = plugin.getConfigManager().getMessagesConfig().getStringList("admin_command.status.lines");
        if (lines == null || lines.isEmpty()) {
            lines = Arrays.asList(
                "&8----------------------------------------",
                "&6&l KjobsUltimate &7- Status",
                "&8----------------------------------------",
                "&7Version: &f{version} &8| &7Online: &f{online}/{max_online}",
                "&7Storage: {storage_status} &f{storage_type} &8- &7{storage_path}",
                "&7MySQL pool: &f{pool_status}",
                "&7Jobs: &f{jobs_loaded}/{jobs_expected} &8- &7{jobs}",
                "&7Players cache: &f{cache_size}",
                "&7Hooks: &f{hooks}",
                "&7GUI: {gui_status} &8| &7HUD: {hud_status} &8| &7Tab: {tab_status}",
                "&7Tab virtuel: {virtual_tab_status}",
                "&7XP: event x{event_multiplier} &8| &7debug={debug} xp={debug_xp} hud={debug_hud}",
                "&8----------------------------------------"
            );
        }

        String hooks = "Vault=" + yn(plugin.getHookManager() != null && plugin.getHookManager().isVaultEnabled())
            + ", PAPI=" + yn(plugin.getHookManager() != null && plugin.getHookManager().isPAPIEnabled())
            + ", Kcraft=" + yn(plugin.getHookManager() != null && plugin.getHookManager().isKcraftEnabled())
            + ", Kfaction=" + yn(plugin.getHookManager() != null && plugin.getHookManager().isKfactionEnabled())
            + ", KStacker=" + yn(plugin.getHookManager() != null && plugin.getHookManager().isKstackerEnabled());

        String guiStatus = plugin.getGuiManager() != null && plugin.getGuiManager().isEnabled() ? "&aON" : "&cOFF";
        String hudStatus = plugin.getHudManager() != null
            ? "&aON &7(ab=" + yn(plugin.getHudManager().isActionBarEnabled())
                + ", boss=" + yn(plugin.getHudManager().isBossBarEnabled())
                + ", tracked=" + plugin.getHudManager().getTrackedPlayers()
                + ", nms=" + plugin.getHudManager().getNMS() + ")"
            : "&cOFF";
        String tabStatus = plugin.getTabManager() != null
            ? (plugin.getTabManager().isEnabled() ? "&aON" : "&cOFF")
                + " &7(interval=" + plugin.getTabManager().getIntervalTicks() + "t"
                + ", sections=" + yn(plugin.getTabManager().isSectionsEnabled())
                + ", names=" + yn(plugin.getTabManager().isPlayerListNameEnabled())
                + ", papi=" + yn(plugin.getTabManager().isPlaceholderApiPerPlayer()) + "&7)"
            : "&cOFF";
        String virtualTabStatus = plugin.getTabManager() != null
            ? "&7" + plugin.getTabManager().getVirtualStatus(sender instanceof Player ? (Player) sender : null)
            : "&cOFF";

        for (String line : lines) {
            sender.sendMessage(format(line,
                "{version}", plugin.getDescription().getVersion(),
                "{online}", String.valueOf(Bukkit.getOnlinePlayers().size()),
                "{max_online}", String.valueOf(Bukkit.getMaxPlayers()),
                "{storage_status}", plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isOpen() ? "&aOK" : "&cKO",
                "{storage_type}", plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getStorageTypeName() : "UNKNOWN",
                "{storage_path}", plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getDbPath() : "n/a",
                "{pool_status}", plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getPoolStatus() : "n/a",
                "{jobs_loaded}", String.valueOf(plugin.getJobRegistry().getJobCount()),
                "{jobs_expected}", String.valueOf(plugin.getJobRegistry().getExpectedJobIds().size()),
                "{jobs}", joinJobIds(),
                "{cache_size}", plugin.getPlayerDataManager() != null ? String.valueOf(plugin.getPlayerDataManager().getCacheSize()) : "0",
                "{hooks}", hooks,
                "{gui_status}", guiStatus,
                "{hud_status}", hudStatus,
                "{tab_status}", tabStatus,
                "{virtual_tab_status}", virtualTabStatus,
                "{event_multiplier}", String.valueOf(plugin.getXpManager().getEventMultiplier()),
                "{debug}", yn(plugin.getConfigManager().isDebug()),
                "{debug_xp}", yn(plugin.getConfigManager().isDebugXp()),
                "{debug_hud}", yn(plugin.getConfigManager().isDebugHud())
            ));
        }
    }

    private void handleTabDebug(CommandSender sender, String[] args) {
        if (plugin.getTabManager() == null) {
            send(sender, "admin_command.tabdebug.no_tab", "{prefix}\u00A7cTabManager non initialise.");
            return;
        }
        Player viewer = resolveOptionalPlayer(sender, args, 1);
        sender.sendMessage(format("&8----------------------------------------"));
        sender.sendMessage(format("&6&l KjobsUltimate &7- Tab Debug"));
        sender.sendMessage(format("&7Header/Footer: {status} &8| &7sections={sections} names={names} papi={papi}",
            "{status}", plugin.getTabManager().isEnabled() ? "&aON" : "&cOFF",
            "{sections}", yn(plugin.getTabManager().isSectionsEnabled()),
            "{names}", yn(plugin.getTabManager().isPlayerListNameEnabled()),
            "{papi}", yn(plugin.getTabManager().isPlaceholderApiPerPlayer())));
        sender.sendMessage(format("&7Virtuel: &f{status}",
            "{status}", plugin.getTabManager().getVirtualStatus(viewer)));
        if (viewer != null) {
            sender.sendMessage(format("&7Viewer: &f{player}", "{player}", viewer.getName()));
        }
        sender.sendMessage(format("&8----------------------------------------"));
    }

    private void handleTabRender(CommandSender sender, String[] args) {
        if (plugin.getTabManager() == null) {
            send(sender, "admin_command.tabdebug.no_tab", "{prefix}\u00A7cTabManager non initialise.");
            return;
        }
        Player viewer = resolveOptionalPlayer(sender, args, 1);
        if (viewer == null) {
            send(sender, "admin_command.tabrender.usage", "{prefix}\u00A7cUsage console: /kjobs tabrender <joueur>");
            return;
        }
        List<String> lines = plugin.getTabManager().previewVirtualLayout(viewer);
        sender.sendMessage(format("&8----------------------------------------"));
        sender.sendMessage(format("&6&l KjobsUltimate &7- Preview Tab Virtuel &8(&f{count}&8)", "{count}", String.valueOf(lines.size())));
        if (lines.isEmpty()) {
            sender.sendMessage(format("&7Aucune ligne rendue. Verifie virtual_layout.enabled/columns/bottom_lines."));
        } else {
            int index = 1;
            for (String line : lines) {
                sender.sendMessage(format("&8{index}. &r{line}", "{index}", String.valueOf(index++), "{line}", line));
            }
        }
        sender.sendMessage(format("&8----------------------------------------"));
    }

    private void handleTabClear(CommandSender sender, String[] args) {
        if (plugin.getTabManager() == null) {
            send(sender, "admin_command.tabdebug.no_tab", "{prefix}\u00A7cTabManager non initialise.");
            return;
        }
        Player viewer = resolveOptionalPlayer(sender, args, 1);
        if (viewer == null) {
            send(sender, "admin_command.tabclear.usage", "{prefix}\u00A7cUsage console: /kjobs tabclear <joueur>");
            return;
        }
        plugin.getTabManager().clearVirtualLayout(viewer);
        send(sender, "admin_command.tabclear.done", "{prefix}\u00A7aLignes fake du tab virtuel retirees pour \u00A7e{player}\u00A7a.",
            "{player}", viewer.getName());
    }

    private void handleTabClearAll(CommandSender sender) {
        if (plugin.getTabManager() == null) {
            send(sender, "admin_command.tabdebug.no_tab", "{prefix}\u00A7cTabManager non initialise.");
            return;
        }
        plugin.getTabManager().clearAllVirtualLayouts();
        send(sender, "admin_command.tabclearall.done", "{prefix}\u00A7aToutes les lignes fake du tab virtuel ont ete retirees.");
    }

    private Player resolveOptionalPlayer(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            return requirePlayer(sender, args[index]);
        }
        return sender instanceof Player ? (Player) sender : null;
    }

    private void handleTestHud(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            send(sender, "admin_command.testhud.player_only", "{prefix}\u00A7c/kjobs testhud reserve aux joueurs en jeu.");
            return;
        }
        Player player = (Player) sender;

        if (plugin.getHudManager() == null) {
            send(sender, "admin_command.testhud.hud_null", "\u00A7c[TESTHUD] HudManager est NULL - le HUD n'a pas ete initialise !");
            return;
        }
        send(sender, "admin_command.testhud.hud_ok", "\u00A7a[TESTHUD] HudManager OK - NMS version: {nms}",
            "{nms}", plugin.getHudManager().getNMS());

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            send(sender, "admin_command.testhud.data_null", "\u00A7c[TESTHUD] PlayerData NULL - donnees non chargees !");
            return;
        }
        send(sender, "admin_command.testhud.data_ok", "\u00A7a[TESTHUD] PlayerData OK | HUD active: {enabled}",
            "{enabled}", String.valueOf(data.isHudEnabled()));

        String entityOverride = null;
        String positionOverride = null;
        Boolean invisibleOverride = null;
        for (int i = 1; i < args.length; i++) {
            String token = args[i].toLowerCase().replace('-', '_');
            if ("wither".equals(token)) {
                entityOverride = "WITHER";
            } else if ("dragon".equals(token) || "ender_dragon".equals(token)) {
                entityOverride = "ENDER_DRAGON";
            } else if ("below".equals(token) || "above".equals(token) || "front".equals(token)
                    || "eye_front".equals(token) || "eyefront".equals(token) || "player".equals(token)) {
                positionOverride = token;
            } else if ("visible".equals(token)) {
                invisibleOverride = Boolean.FALSE;
            } else if ("invisible".equals(token)) {
                invisibleOverride = Boolean.TRUE;
            } else {
                send(sender, "admin_command.testhud.invalid_arg",
                    "\u00A7c[TESTHUD] Argument ignore: {arg} \u00A77(types: wither/dragon, positions: below/above/front/eye_front/player, visible/invisible)",
                    "{arg}", args[i]);
            }
        }

        try {
            plugin.getHudManager().testBossBar(player, entityOverride, positionOverride, invisibleOverride);
            send(sender, "admin_command.testhud.bossbar_ok",
                "\u00A7a[TESTHUD] BossBar envoyee a 75% \u00A77(type={type}, position={position}, visible={visible}) \u00A78- \u00A77auto-hide selon hud.yml",
                "{type}", entityOverride != null ? entityOverride : "config",
                "{position}", positionOverride != null ? positionOverride : "config",
                "{visible}", invisibleOverride == null ? "config" : String.valueOf(!invisibleOverride.booleanValue()));
        } catch (Exception e) {
            send(sender, "admin_command.testhud.bossbar_error", "\u00A7c[TESTHUD] testBossBar ERREUR: {error}", "{error}", e.getMessage());
            KjobLogger.warn("[TESTHUD] testBossBar: " + e);
        }

        try {
            plugin.getHudManager().onLevelUp(player, data, "mineur", 99);
            send(sender, "admin_command.testhud.levelup_ok", "\u00A7a[TESTHUD] onLevelUp OK - popup achievement devrait s'afficher");
        } catch (Exception e) {
            send(sender, "admin_command.testhud.levelup_error", "\u00A7c[TESTHUD] onLevelUp ERREUR: {error}", "{error}", e.getMessage());
            KjobLogger.warn("[TESTHUD] onLevelUp: " + e);
        }

        send(sender, "admin_command.testhud.console_hint", "\u00A77Consulte la console pour les erreurs [HUD] si rien n'apparait.");
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.getConfigManager().loadAll();
            plugin.getJobRegistry().loadAll();
            if (plugin.getQuestManager() != null) plugin.getQuestManager().loadAll();
            new me.krunsh.kjobultimate.validation.ConfigValidator(plugin).validateOrThrow();
            if (plugin.getGuiManager() != null) plugin.getGuiManager().loadAll();
            if (plugin.getHudManager() != null) plugin.getHudManager().reloadHudConfig();
            if (plugin.getTabManager() != null) plugin.getTabManager().reload();
            send(sender, "commands.reload_success", "{prefix}\u00A7aConfigs et jobs recharges.");
            KjobLogger.reload("Rechargement admin par " + sender.getName());
        } catch (Exception e) {
            send(sender, "admin_command.reload.error", "{prefix}\u00A7cErreur lors du rechargement: {error}", "{error}", e.getMessage());
            KjobLogger.error("Erreur rechargement admin", e);
        }
    }

    private void handleXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "admin_command.usage.addxp", "\u00A7c\u00A77Usage: /kjobs addxp <joueur> <jobId> <+/-montant>");
            return;
        }
        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;

        String jobId = args[2].toLowerCase();
        JobDefinition def = requireJob(sender, jobId);
        if (def == null) return;

        Integer amount = parseInt(sender, args[3], "admin_command.invalid.amount", "{prefix}\u00A7cMontant invalide: \u00A7e{value}");
        if (amount == null) return;

        PlayerData data = requireData(sender, target);
        if (data == null) return;

        LevelUpResult result = plugin.getXpManager().adminAddXp(target, data, jobId, amount.intValue());
        if (result.isLeveledUp()) plugin.getXpManager().handleLevelUp(target, data, jobId, result);

        send(sender, "admin_command.xp.modified",
            "{prefix}\u00A7aXP de \u00A7e{player} \u00A77en \u00A7e{job_id} \u00A77modifie - niveau \u00A7f{level} \u00A77- \u00A7f{xp} \u00A77XP",
            "{player}", target.getName(), "{job}", def.getDisplayName(), "{job_id}", jobId,
            "{level}", String.valueOf(result.getNewLevel()), "{xp}", String.valueOf(result.getRemainingXP()),
            "{amount}", String.valueOf(amount));
    }

    private void handleLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "admin_command.usage.setlvl", "\u00A7c\u00A77Usage: /kjobs setlvl <joueur> <jobId> <niveau>");
            return;
        }
        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;

        String jobId = args[2].toLowerCase();
        JobDefinition def = requireJob(sender, jobId);
        if (def == null) return;

        Integer level = parseInt(sender, args[3], "admin_command.invalid.level", "{prefix}\u00A7cNiveau invalide: \u00A7e{value}");
        if (level == null) return;
        if (level.intValue() < 0 || level.intValue() > def.getMaxLevel()) {
            send(sender, "admin_command.invalid.level_range", "{prefix}\u00A7cNiveau hors limites (0-{max_level}).", "{max_level}", String.valueOf(def.getMaxLevel()));
            return;
        }

        PlayerData data = requireData(sender, target);
        if (data == null) return;
        data.setLevel(jobId, level.intValue());
        data.setXP(jobId, 0);
        plugin.notifyJobsUiChanged(target.getUniqueId(), "kjobs:admin-level");
        send(sender, "admin_command.level.set", "{prefix}\u00A7aNiveau de \u00A7e{player} \u00A77en \u00A7e{job_id} \u00A77defini a \u00A7f{level}",
            "{player}", target.getName(), "{job}", def.getDisplayName(), "{job_id}", jobId, "{level}", String.valueOf(level));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin_command.usage.resetjob", "\u00A7c\u00A77Usage: /kjobs resetjob <joueur>");
            return;
        }
        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;
        PlayerData data = requireData(sender, target);
        if (data == null) return;

        for (String jobId : plugin.getJobRegistry().getJobIds()) {
            data.setLevel(jobId, 0);
            data.setXP(jobId, 0);
        }
        data.setDisplayJob(null);
        data.markDirty();
        plugin.notifyJobsUiChanged(target.getUniqueId(), "kjobs:admin-reset");
        send(sender, "admin_command.resetjob.done", "{prefix}\u00A7aJobs de \u00A7e{player} \u00A77remis a zero.", "{player}", target.getName());
    }

    private void handleSetDisplay(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "admin_command.usage.setdisplay", "\u00A7c\u00A77Usage: /kjobs setdisplay <joueur> <jobId>");
            return;
        }
        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;

        String jobId = args[2].toLowerCase();
        JobDefinition def = requireJob(sender, jobId);
        if (def == null) return;
        PlayerData data = requireData(sender, target);
        if (data == null) return;

        data.setDisplayJob(jobId);
        data.markDirty();
        plugin.notifyJobsUiChanged(target.getUniqueId(), "kjobs:admin-display",
            "kjobs_main", "kjobs_detail");
        send(sender, "admin_command.display.set", "{prefix}\u00A7aJob actif de \u00A7e{player} \u00A77defini a \u00A7e{job}",
            "{player}", target.getName(), "{job}", def.getDisplayName(), "{job_id}", jobId);
    }

    private void handleEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            double current = plugin.getConfigManager().getMainConfig().getDouble("xp_multipliers.event_multiplier", 1.0);
            send(sender, "admin_command.event.current", "{prefix}\u00A7aMultiplicateur XP event actuel: \u00A7ex{multiplier}",
                "{multiplier}", String.valueOf(current));
            return;
        }
        Double multiplier = parseDouble(sender, args[1], "admin_command.invalid.value", "{prefix}\u00A7cValeur invalide: \u00A7e{value}");
        if (multiplier == null) return;
        if (multiplier.doubleValue() < 0D) {
            send(sender, "admin_command.invalid.negative_multiplier", "{prefix}\u00A7cLe multiplicateur ne peut pas etre negatif.");
            return;
        }
        plugin.getConfigManager().getMainConfig().set("xp_multipliers.event_multiplier", multiplier.doubleValue());
        send(sender, "admin_command.event.set", "{prefix}\u00A7aMultiplicateur XP event defini a \u00A7ex{multiplier}\u00A77 (tous les joueurs, tous les jobs).",
            "{multiplier}", String.valueOf(multiplier));
        KjobLogger.info("[Admin] Multiplicateur event -> x" + multiplier + " par " + sender.getName());
    }

    private void handleBonus(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "admin_command.usage.bonus", "\u00A7c\u00A77Usage: /kjobs bonus <joueur> <jobId|all> <valeur>");
            return;
        }
        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;
        String jobId = args[2].toLowerCase();
        Double multiplier = parseDouble(sender, args[3], "admin_command.invalid.value", "{prefix}\u00A7cValeur invalide: \u00A7e{value}");
        if (multiplier == null) return;
        if (multiplier.doubleValue() < 0D) {
            send(sender, "admin_command.invalid.negative_multiplier", "{prefix}\u00A7cLe multiplicateur ne peut pas etre negatif.");
            return;
        }
        PlayerData data = requireData(sender, target);
        if (data == null) return;
        data.setBonusMultiplier(jobId, multiplier.doubleValue());
        final String finalJobId = jobId;
        final String setBy = sender.getName();
        final double finalMultiplier = multiplier.doubleValue();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.getDatabaseManager().saveBonusMultiplier(target.getUniqueId(), finalJobId, finalMultiplier, setBy);
                } catch (Exception e) {
                    KjobLogger.error("Erreur sauvegarde bonus multiplier", e);
                }
            }
        });
        send(sender, "admin_command.bonus.set", "{prefix}\u00A7aBonus multiplier de \u00A7e{player}\u00A77 pour \u00A7e{job_id}\u00A77 -> \u00A7fx{multiplier}",
            "{player}", target.getName(), "{job_id}", jobId, "{multiplier}", String.valueOf(multiplier));
        KjobLogger.info("[Admin] Bonus multiplier " + target.getName() + " " + jobId + " -> x" + multiplier + " par " + sender.getName());
    }

    private void handleForceJoin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "admin_command.usage.forcejoin", "\u00A7c\u00A77Usage: /kjobs forcejoin <joueur> <jobId> [slot]");
            return;
        }
        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;

        String jobId = args[2].toLowerCase();
        JobDefinition def = requireJob(sender, jobId);
        if (def == null) return;
        PlayerData data = requireData(sender, target);
        if (data == null) return;

        if (plugin.getSlotManager().isJobActive(data, jobId)) {
            plugin.getSlotManager().setFavoriteJob(target, data, jobId);
            send(sender, "admin_command.forcejoin.already", "{prefix}\u00A7a{player} avait deja ce job. Favori mis a jour.",
                "{player}", target.getName(), "{job}", def.getDisplayName(), "{job_id}", jobId);
            return;
        }

        if (args.length >= 4) {
            Integer slotValue = parseInt(sender, args[3], "admin_command.invalid.slot", "{prefix}\u00A7cSlot invalide: \u00A7e{value}");
            if (slotValue == null) return;
            int slot = slotValue.intValue();
            int maxSlots = plugin.getConfigManager().getMaxSlots();
            if (slot < 1 || slot > maxSlots) {
                send(sender, "admin_command.invalid.slot_range", "{prefix}\u00A7cSlot hors limites (1-{max_slots}).", "{max_slots}", String.valueOf(maxSlots));
                return;
            }
            if (slot > data.getUnlockedSlots()) data.setUnlockedSlots(slot);
            String oldJob = data.getJobInSlot(slot);
            if (oldJob != null) plugin.getSlotManager().forceLeaveJob(target, data, oldJob, true);
            plugin.getSlotManager().assignJobToSlot(target, data, slot, jobId);
        } else if (!plugin.getSlotManager().assignJobToFreeSlot(target, data, jobId)) {
            if (data.getUnlockedSlots() < plugin.getConfigManager().getMaxSlots()) {
                data.setUnlockedSlots(data.getUnlockedSlots() + 1);
                plugin.getSlotManager().assignJobToFreeSlot(target, data, jobId);
            } else {
                send(sender, "admin_command.forcejoin.no_slot", "{prefix}\u00A7cAucun slot libre. Utilise /kjobs forcejoin <joueur> <jobId> <slot> pour remplacer.");
                return;
            }
        }

        plugin.getSlotManager().setFavoriteJob(target, data, jobId);
        send(sender, "admin_command.forcejoin.done", "{prefix}\u00A7aJob \u00A7e{job} \u00A77force pour \u00A7e{player}\u00A77.",
            "{player}", target.getName(), "{job}", def.getDisplayName(), "{job_id}", jobId);
    }

    private void handleForceLeave(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "admin_command.usage.forceleave", "\u00A7c\u00A77Usage: /kjobs forceleave <joueur> <jobId>");
            return;
        }
        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;

        String jobId = args[2].toLowerCase();
        JobDefinition def = requireJob(sender, jobId);
        if (def == null) return;
        PlayerData data = requireData(sender, target);
        if (data == null) return;

        if (!plugin.getSlotManager().forceLeaveJob(target, data, jobId, true)) {
            send(sender, "admin_command.forceleave.not_unlocked", "{prefix}\u00A7cCe joueur n'a pas debloque ce job.",
                "{player}", target.getName(), "{job}", def.getDisplayName(), "{job_id}", jobId);
            return;
        }

        send(sender, "admin_command.forceleave.done", "{prefix}\u00A7aJob \u00A7e{job} \u00A77retire de \u00A7e{player}\u00A77, progression reset.",
            "{player}", target.getName(), "{job}", def.getDisplayName(), "{job_id}", jobId);
    }

    private void handleClearCooldown(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin_command.usage.clearcooldown", "\u00A7c\u00A77Usage: /kjobs clearcooldown <joueur>");
            return;
        }
        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;
        PlayerData data = requireData(sender, target);
        if (data == null) return;

        plugin.getSlotManager().clearJobChangeCooldown(data);
        send(sender, "admin_command.cooldown.cleared", "{prefix}\u00A7aCooldown jobs retire pour \u00A7e{player}\u00A77.", "{player}", target.getName());
    }

    private void handleResetXp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin_command.usage.resetxp", "\u00A7c\u00A77Usage: /kjobs resetxp <joueur> [jobId]");
            return;
        }
        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;
        PlayerData data = requireData(sender, target);
        if (data == null) return;

        if (args.length >= 3) {
            String jobId = args[2].toLowerCase();
            JobDefinition def = requireJob(sender, jobId);
            if (def == null) return;
            data.setXP(jobId, 0);
            data.markDirty();
            send(sender, "admin_command.resetxp.job", "{prefix}\u00A7aXP de \u00A7e{player} \u00A77en \u00A7e{job_id} \u00A77remis a \u00A7f0\u00A77.",
                "{player}", target.getName(), "{job}", def.getDisplayName(), "{job_id}", jobId);
        } else {
            for (String jobId : plugin.getJobRegistry().getJobIds()) {
                data.setXP(jobId, 0);
            }
            data.markDirty();
            send(sender, "admin_command.resetxp.all", "{prefix}\u00A7aXP de \u00A7e{player} \u00A77remis a \u00A7f0 \u00A77pour tous les jobs.",
                "{player}", target.getName());
        }
        plugin.notifyJobsUiChanged(target.getUniqueId(), "kjobs:admin-reset-xp");
    }

    private void handleQuestComplete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "admin_command.usage.questcomplete", "\u00A7c\u00A77Usage: /kjobs questcomplete <joueur> <questId>");
            return;
        }
        if (!isQuestSystemReady(sender)) return;

        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;
        QuestDefinition quest = requireQuest(sender, args[2]);
        if (quest == null) return;

        if (!plugin.getQuestManager().forceComplete(target, quest.getId())) {
            send(sender, "admin_command.questcomplete.error", "{prefix}\u00A7cImpossible de completer la quete \u00A7e{quest_id}\u00A7c pour \u00A7e{player}\u00A7c.",
                "{player}", target.getName(), "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
            return;
        }

        send(sender, "admin_command.questcomplete.done",
            "{prefix}\u00A7aQuete \u00A7f{quest} \u00A77rendue claimable pour \u00A7e{player}\u00A77.",
            "{player}", target.getName(), "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
        KjobLogger.info("[Admin] Quete " + quest.getId() + " force-complete pour " + target.getName() + " par " + sender.getName());
    }

    private void handleQuestReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "admin_command.usage.questreset", "\u00A7c\u00A77Usage: /kjobs questreset <joueur> <questId|all>");
            return;
        }
        if (!isQuestSystemReady(sender)) return;

        Player target = requirePlayer(sender, args[1]);
        if (target == null) return;

        String questId = args[2].toLowerCase();
        if ("all".equals(questId) || "*".equals(questId)) {
            int count = plugin.getQuestManager().resetAllQuests(target);
            send(sender, "admin_command.questreset.all",
                "{prefix}\u00A7a{count} quete(s) reset pour \u00A7e{player}\u00A77.",
                "{player}", target.getName(), "{count}", String.valueOf(count));
            KjobLogger.info("[Admin] Reset ALL quetes pour " + target.getName() + " (" + count + ") par " + sender.getName());
            return;
        }

        QuestDefinition quest = requireQuest(sender, questId);
        if (quest == null) return;
        if (!plugin.getQuestManager().resetQuest(target, quest.getId())) {
            send(sender, "admin_command.questreset.error", "{prefix}\u00A7cImpossible de reset la quete \u00A7e{quest_id}\u00A7c pour \u00A7e{player}\u00A7c.",
                "{player}", target.getName(), "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
            return;
        }

        send(sender, "admin_command.questreset.done",
            "{prefix}\u00A7aQuete \u00A7f{quest} \u00A77reset pour \u00A7e{player}\u00A77.",
            "{player}", target.getName(), "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
        KjobLogger.info("[Admin] Reset quete " + quest.getId() + " pour " + target.getName() + " par " + sender.getName());
    }

    private Player requirePlayer(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            send(sender, "commands.player_not_found", "{prefix}\u00A7cJoueur introuvable ou hors ligne: \u00A7e{player}", "{player}", name);
            return null;
        }
        return target;
    }

    private JobDefinition requireJob(CommandSender sender, String jobId) {
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        if (def == null) {
            send(sender, "commands.job_not_found", "{prefix}\u00A7cJob '{job}' introuvable. Jobs disponibles : {list}",
                "{job}", jobId, "{job_id}", jobId, "{list}", joinJobIds());
            return null;
        }
        return def;
    }

    private QuestDefinition requireQuest(CommandSender sender, String questId) {
        QuestDefinition quest = plugin.getQuestManager() == null ? null : plugin.getQuestManager().getQuest(questId);
        if (quest == null) {
            send(sender, "admin_command.quest.unknown",
                "{prefix}\u00A7cQuete '\u00A7e{quest_id}\u00A7c' introuvable. Quetes disponibles : \u00A77{list}",
                "{quest_id}", questId, "{list}", joinQuestIds());
            return null;
        }
        return quest;
    }

    private boolean isQuestSystemReady(CommandSender sender) {
        if (plugin.getQuestManager() == null || !plugin.getQuestManager().isEnabled()) {
            send(sender, "admin_command.quest.disabled", "{prefix}\u00A7cLe systeme de quetes est desactive ou non initialise.");
            return false;
        }
        return true;
    }

    private PlayerData requireData(CommandSender sender, Player target) {
        PlayerData data = plugin.getPlayerDataManager().get(target);
        if (data == null) {
            send(sender, "admin_command.player_data_not_loaded", "{prefix}\u00A7cDonnees joueur non chargees pour \u00A7e{player}\u00A7c.", "{player}", target.getName());
            return null;
        }
        return data;
    }

    private Integer parseInt(CommandSender sender, String raw, String key, String fallback) {
        try {
            return Integer.valueOf(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            send(sender, key, fallback, "{value}", raw);
            return null;
        }
    }

    private Double parseDouble(CommandSender sender, String raw, String key, String fallback) {
        try {
            return Double.valueOf(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            send(sender, key, fallback, "{value}", raw);
            return null;
        }
    }

    private String joinJobIds() {
        StringBuilder builder = new StringBuilder();
        for (String jobId : plugin.getJobRegistry().getJobIds()) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(jobId);
        }
        return builder.toString();
    }

    private String joinQuestIds() {
        if (plugin.getQuestManager() == null) return "";
        StringBuilder builder = new StringBuilder();
        for (String questId : plugin.getQuestManager().getQuestIds()) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(questId);
        }
        return builder.toString();
    }

    private String yn(boolean value) {
        return value ? "\u00A7aON" : "\u00A7cOFF";
    }

    private void send(CommandSender sender, String key, String fallback, String... replacements) {
        String msg = message(key, fallback, replacements);
        if (!msg.isEmpty()) sender.sendMessage(msg);
    }

    private String message(String key, String fallback, String... replacements) {
        String msg = plugin.getConfigManager().getMessage(key, fallback);
        return format(msg, replacements);
    }

    private String format(String raw, String... replacements) {
        String msg = raw == null ? "" : raw.replace("{prefix}", plugin.getConfigManager().getPrefix());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return msg.replace("&", "\u00A7");
    }
}
