package me.krunsh.kjobultimate.commands;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.RankingEntry;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.slots.SlotManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Commande joueur /jobs.
 *
 * Objectif V1 : separer clairement les commandes joueur des commandes admin /kjobs.
 * Les GUI internes arriveront dans une phase suivante ; ce fallback texte doit rester
 * fiable pour tester toute la logique metier sans dependance externe.
 */
public final class KjobCommand implements CommandExecutor, TabCompleter {

    private final KjobUltimate plugin;
    private final Object topCacheLock = new Object();
    private final Map<String, TopCacheEntry> topCache = new HashMap<String, TopCacheEntry>();

    public KjobCommand(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(message("player_command.player_only", "Cette commande est reservee aux joueurs."));
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            openMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("menu".equals(sub) || "gui".equals(sub)) {
            if (plugin.getGuiManager() != null && plugin.getGuiManager().isEnabled()) {
                plugin.getGuiManager().openDefault(player);
            } else {
                openMainMenu(player);
            }
            return true;
        }
        if ("text".equals(sub)) {
            openTextMenu(player);
            return true;
        }
        if ("help".equals(sub) || "aide".equals(sub)) {
            sendHelp(player);
            return true;
        }
        if ("join".equals(sub) || "prendre".equals(sub) || "rejoindre".equals(sub)) {
            handleJoin(player, args.length > 1 ? args[1] : null);
            return true;
        }
        if ("select".equals(sub) || "favori".equals(sub) || "favorite".equals(sub) || "fav".equals(sub)) {
            handleFavorite(player, args.length > 1 ? args[1] : null);
            return true;
        }
        if ("leave".equals(sub) || "quitter".equals(sub)) {
            handleLeave(player, args.length > 1 ? args[1] : null);
            return true;
        }
        if ("confirm".equals(sub) || "confirmer".equals(sub)) {
            handleConfirm(player);
            return true;
        }
        if ("cancel".equals(sub) || "annuler".equals(sub)) {
            handleCancel(player);
            return true;
        }
        if ("info".equals(sub) || "infos".equals(sub) || "stats".equals(sub)) {
            handleInfo(player, args.length > 1 ? args[1] : null);
            return true;
        }
        if ("top".equals(sub) || "classement".equals(sub)) {
            handleTop(player, args.length > 1 ? args[1] : null);
            return true;
        }
        if ("quests".equals(sub) || "quest".equals(sub) || "quetes".equals(sub) || "quete".equals(sub)) {
            if (plugin.getGuiManager() != null && plugin.getGuiManager().isEnabled()) {
                plugin.getGuiManager().openQuests(player);
            } else {
                send(player, "quest.gui_required", "{prefix}\u00A7cLes quetes se recuperent via le GUI jobs.");
            }
            return true;
        }
        if ("hud".equals(sub)) {
            handleHudToggle(player, args.length > 1 ? args[1] : null);
            return true;
        }

        sendHelp(player);
        return true;
    }

    private void openMainMenu(Player player) {
        if (plugin.getGuiManager() != null && plugin.getGuiManager().isEnabled()) {
            plugin.getGuiManager().openDefault(player);
            return;
        }
        openTextMenu(player);
    }

    private void openTextMenu(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            send(player, "player_command.data_not_loaded", "{prefix}\u00A7cDonnees non chargees, reessaie dans un instant.");
            return;
        }

        SlotManager sm = plugin.getSlotManager();
        List<String> unlocked = sm.getActiveJobs(data);
        String favorite = data.getDisplayJob();
        int maxSlots = plugin.getConfigManager().getMaxSlots();

        send(player, "player_command.menu.header", "\u00A76\u00A7l====== KjobsUltimate ======");
        send(player, "player_command.menu.summary",
            "\u00A77Jobs debloques: \u00A7e{unlocked}\u00A77/\u00A7e{slots} \u00A78(max {max_slots}) \u00A77| Global: \u00A7e{global_level}",
            "{unlocked}", String.valueOf(unlocked.size()),
            "{slots}", String.valueOf(data.getUnlockedSlots()),
            "{max_slots}", String.valueOf(maxSlots),
            "{global_level}", String.valueOf(sm.getGlobalLevel(data)));
        send(player, "player_command.menu.favorite",
            "\u00A77Favori: \u00A7e{job}",
            "{job}", favorite == null ? message("player_command.menu.none", "aucun") : favorite);

        for (JobDefinition def : plugin.getJobRegistry().getAllJobs()) {
            String jobId = def.getId();
            boolean active = sm.isJobActive(data, jobId);
            boolean fav = jobId.equals(favorite);
            int level = data.getLevel(jobId);
            int xp = data.getXP(jobId);
            int xpNext = def.getXpForLevel(Math.max(1, level));
            String status = active
                ? message("player_command.menu.status_unlocked", "\u00A7a[debloque]")
                : message("player_command.menu.status_locked", "\u00A78[bloque]");
            if (fav) status = message("player_command.menu.status_favorite", "\u00A76[favori]");

            send(player, "player_command.menu.job_line",
                "\u00A7e{job} {status} \u00A77Niv \u00A7f{level} \u00A78- \u00A7a{xp}\u00A77/\u00A7a{xp_next} XP",
                "{job}", def.getDisplayName(),
                "{job_id}", jobId,
                "{status}", status,
                "{level}", String.valueOf(level),
                "{xp}", String.valueOf(xp),
                "{xp_next}", String.valueOf(xpNext));
        }
        send(player, "player_command.menu.commands", "\u00A78Commandes: \u00A7e/jobs join <job> \u00A78| \u00A7e/jobs select <job> \u00A78| \u00A7e/jobs leave <job>");
    }

    private void handleJoin(Player player, String jobId) {
        if (jobId == null) {
            send(player, "player_command.join.usage", "{prefix}\u00A77Usage: \u00A7e/jobs join <job>");
            return;
        }
        jobId = jobId.toLowerCase();

        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        if (def == null) {
            send(player, "player_command.job_unknown", "{prefix}\u00A7cJob inconnu: \u00A7e{job_id}", "{job_id}", jobId);
            return;
        }

        PlayerData data = getData(player);
        if (data == null) return;

        SlotManager sm = plugin.getSlotManager();
        if (sm.isJobActive(data, jobId)) {
            sm.setFavoriteJob(player, data, jobId);
            send(player, "player_command.join.already_unlocked", "{prefix}\u00A77Ce job etait deja debloque.");
            return;
        }

        if (!sm.assignJobToFreeSlot(player, data, jobId)) {
            send(player, "player_command.join.no_free_slot", "{prefix}\u00A7cAucun emplacement libre. Monte ton niveau global pour debloquer un nouveau job.");
            send(player, "player_command.join.global_level", "{prefix}\u00A77Niveau global actuel: \u00A7e{global_level}", "{global_level}", String.valueOf(sm.getGlobalLevel(data)));
            return;
        }

        sm.setFavoriteJob(player, data, jobId);
        send(player, "player_command.join.unlocked", "{prefix}\u00A7aJob debloque: \u00A7e{job}", "{job}", def.getDisplayName(), "{job_id}", jobId);
    }

    private void handleFavorite(Player player, String jobId) {
        if (jobId == null) {
            send(player, "player_command.favorite.usage", "{prefix}\u00A77Usage: \u00A7e/jobs select <job>");
            return;
        }
        jobId = jobId.toLowerCase();

        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        if (def == null) {
            send(player, "player_command.job_unknown", "{prefix}\u00A7cJob inconnu: \u00A7e{job_id}", "{job_id}", jobId);
            return;
        }

        PlayerData data = getData(player);
        if (data == null) return;

        if (!plugin.getSlotManager().setFavoriteJob(player, data, jobId)) {
            send(player, "player_command.favorite.must_unlock", "{prefix}\u00A7cTu dois d'abord debloquer ce job avec \u00A7e/jobs join {job_id}\u00A7c.", "{job_id}", jobId, "{job}", def.getDisplayName());
        }
    }

    private void handleLeave(Player player, String jobId) {
        if (jobId == null) {
            send(player, "player_command.leave.usage", "{prefix}\u00A77Usage: \u00A7e/jobs leave <job>");
            return;
        }
        jobId = jobId.toLowerCase();

        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        if (def == null) {
            send(player, "player_command.job_unknown", "{prefix}\u00A7cJob inconnu: \u00A7e{job_id}", "{job_id}", jobId);
            return;
        }

        PlayerData data = getData(player);
        if (data == null) return;
        plugin.getSlotManager().requestLeaveJob(player, data, jobId);
    }

    private void handleInfo(Player player, String jobId) {
        PlayerData data = getData(player);
        if (data == null) return;

        if (jobId == null) {
            send(player, "player_command.info.header", "\u00A76\u00A7l====== Tes jobs ======");
            for (JobDefinition def : plugin.getJobRegistry().getAllJobs()) {
                sendJobLine(player, data, def);
            }
            return;
        }

        jobId = jobId.toLowerCase();
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        if (def == null) {
            send(player, "player_command.job_unknown", "{prefix}\u00A7cJob inconnu: \u00A7e{job_id}", "{job_id}", jobId);
            return;
        }

        int level = data.getLevel(jobId);
        int xp = data.getXP(jobId);
        int xpNext = def.getXpForLevel(Math.max(1, level));
        int pct = xpNext > 0 ? Math.min(100, (int) ((double) xp / xpNext * 100)) : 100;
        boolean active = plugin.getSlotManager().isJobActive(data, jobId);

        send(player, "player_command.info.detail_header", "\u00A76\u00A7l{job}", "{job}", def.getDisplayName(), "{job_id}", jobId);
        send(player, "player_command.info.status", "\u00A77Statut: {status}", "{status}", active ? message("player_command.info.status_unlocked", "\u00A7adebloque") : message("player_command.info.status_locked", "\u00A78bloque"));
        send(player, "player_command.info.level", "\u00A77Niveau: \u00A7e{level}\u00A77/\u00A7e{max_level}", "{level}", String.valueOf(level), "{max_level}", String.valueOf(def.getMaxLevel()));
        send(player, "player_command.info.xp", "\u00A77XP: \u00A7a{xp}\u00A77/\u00A7a{xp_next} \u00A78({percent}%)", "{xp}", String.valueOf(xp), "{xp_next}", String.valueOf(xpNext), "{percent}", String.valueOf(pct));
    }

    private void handleTop(Player player, String jobId) {
        String filter = normalizeTopFilter(jobId);
        if ("__invalid__".equals(filter)) {
            send(player, "player_command.top.invalid_filter", "{prefix}\u00A7cClassement inconnu: \u00A7e{job_id}",
                "{job_id}", jobId == null ? "" : jobId.toLowerCase());
            return;
        }

        final String topFilter = filter;
        final String cacheKey = topFilter == null ? "global" : topFilter;
        final int limit = Math.max(1, Math.min(50, plugin.getConfigManager().getMainConfig().getInt("top.chat_limit", 10)));

        send(player, "player_command.top.loading", "{prefix}\u00A77Chargement du classement \u00A7e{target}\u00A77...",
            "{target}", getTopTargetDisplay(topFilter));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                List<RankingEntry> entries = getCachedTop(cacheKey);
                boolean cached = entries != null;
                int rank;
                try {
                    if (entries == null) {
                        entries = plugin.getDatabaseManager().getTop(topFilter, limit);
                        putCachedTop(cacheKey, entries);
                    }
                    rank = plugin.getDatabaseManager().getRank(player.getUniqueId(), topFilter);
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur classement " + cacheKey + ": " + e.getMessage());
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (player.isOnline()) {
                                send(player, "player_command.top.error", "{prefix}\u00A7cImpossible de charger le classement pour le moment.");
                            }
                        }
                    });
                    return;
                }

                final List<RankingEntry> finalEntries = entries;
                final int finalRank = rank;
                final boolean finalCached = cached;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) sendTop(player, topFilter, finalEntries, finalRank, finalCached);
                    }
                });
            }
        });
    }

    private void handleConfirm(Player player) {
        PlayerData data = getData(player);
        if (data == null) return;
        plugin.getSlotManager().confirmChange(player, data);
    }

    private void handleCancel(Player player) {
        plugin.getSlotManager().cancelChange(player);
    }

    private void handleHudToggle(Player player, String target) {
        PlayerData data = getData(player);
        if (data == null) return;

        String normalized = target == null ? "all" : target.trim().toLowerCase();
        if ("bossbar".equals(normalized) || "boss".equals(normalized) || "bar".equals(normalized)) {
            data.setBossBarHudEnabled(!data.isBossBarHudEnabled());
            if (!data.isBossBarHudEnabled() && plugin.getHudManager() != null) plugin.getHudManager().removePlayer(player);
            send(player, "hud_toggle.bossbar", "{prefix}\u00A77BossBar jobs: {state}",
                "{state}", data.isBossBarHudEnabled() ? "\u00A7aON" : "\u00A7cOFF");
            return;
        }

        if ("actionbar".equals(normalized) || "action".equals(normalized) || "message".equals(normalized)) {
            data.setActionBarHudEnabled(!data.isActionBarHudEnabled());
            if (!data.isActionBarHudEnabled() && plugin.getHudManager() != null) plugin.getHudManager().clearActionBar(player);
            send(player, "hud_toggle.actionbar", "{prefix}\u00A77ActionBar jobs: {state}",
                "{state}", data.isActionBarHudEnabled() ? "\u00A7aON" : "\u00A7cOFF");
            return;
        }

        if ("on".equals(normalized) || "enable".equals(normalized) || "activer".equals(normalized)) {
            data.setHudEnabled(true);
            data.setBossBarHudEnabled(true);
            data.setActionBarHudEnabled(true);
        } else if ("off".equals(normalized) || "disable".equals(normalized) || "desactiver".equals(normalized)) {
            data.setHudEnabled(false);
            data.setBossBarHudEnabled(false);
            data.setActionBarHudEnabled(false);
        } else {
            data.setHudEnabled(!data.isHudEnabled());
        }

        if (data.isHudEnabled()) {
            send(player, "hud_toggle.enabled", "{prefix}\u00A7aHUD active. \u00A77BossBar={bossbar} \u00A77ActionBar={actionbar}",
                "{bossbar}", data.isBossBarHudEnabled() ? "\u00A7aON" : "\u00A7cOFF",
                "{actionbar}", data.isActionBarHudEnabled() ? "\u00A7aON" : "\u00A7cOFF");
        } else {
            send(player, "hud_toggle.disabled", "{prefix}\u00A7cHUD desactive.");
        }

        if (!data.isHudEnabled() && plugin.getHudManager() != null) {
            plugin.getHudManager().clearActionBar(player);
            plugin.getHudManager().removePlayer(player);
        } else if (!data.isActionBarHudEnabled() && plugin.getHudManager() != null) {
            plugin.getHudManager().clearActionBar(player);
        }
    }

    private void sendHelp(Player player) {
        List<String> lines = plugin.getConfigManager().getMessagesConfig().getStringList("player_command.help.lines");
        if (lines == null || lines.isEmpty()) {
            lines = Arrays.asList(
                "\u00A76\u00A7l====== Commandes Jobs ======",
                "\u00A7e/jobs \u00A78- \u00A77resume de tes jobs",
                "\u00A7e/jobs join <job> \u00A78- \u00A77debloque un job si tu as une place",
                "\u00A7e/jobs select <job> \u00A78- \u00A77definit le job favori",
                "\u00A7e/jobs leave <job> \u00A78- \u00A77quitte un job avec confirmation",
                "\u00A7e/jobs info [job] \u00A78- \u00A77affiche les infos",
                "\u00A7e/jobs hud [bossbar|actionbar|on|off] \u00A78- \u00A77regle l'affichage jobs"
            );
        }
        for (String line : lines) {
            player.sendMessage(format(line));
        }
    }

    private void sendJobLine(Player player, PlayerData data, JobDefinition def) {
        String jobId = def.getId();
        int level = data.getLevel(jobId);
        int xp = data.getXP(jobId);
        int xpNext = def.getXpForLevel(Math.max(1, level));
        boolean active = plugin.getSlotManager().isJobActive(data, jobId);
        send(player, "player_command.info.job_line",
            "\u00A7e{job} \u00A77Niv \u00A7f{level} \u00A78- \u00A7a{xp}\u00A77/\u00A7a{xp_next} \u00A78- {status}",
            "{job}", def.getDisplayName(),
            "{job_id}", jobId,
            "{level}", String.valueOf(level),
            "{xp}", String.valueOf(xp),
            "{xp_next}", String.valueOf(xpNext),
            "{status}", active ? message("player_command.info.status_unlocked", "\u00A7adebloque") : message("player_command.info.status_locked", "\u00A78bloque"));
    }

    private PlayerData getData(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            send(player, "player_command.data_not_loaded", "{prefix}\u00A7cDonnees non chargees, reessaie dans un instant.");
        }
        return data;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(args[0], Arrays.asList(
                "help", "menu", "gui", "text", "join", "select", "favori", "leave", "confirm", "cancel",
                "info", "infos", "stats", "top", "classement", "quests", "quetes", "hud"
            ));
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("join".equals(sub) || "prendre".equals(sub) || "rejoindre".equals(sub)) {
                return complete(args[1], plugin.getJobRegistry().getJobIds());
            }
            if ("select".equals(sub) || "favori".equals(sub) || "favorite".equals(sub)
                || "fav".equals(sub) || "leave".equals(sub) || "quitter".equals(sub)) {
                if (sender instanceof Player) {
                    PlayerData data = plugin.getPlayerDataManager().get((Player) sender);
                    if (data != null) return complete(args[1], plugin.getSlotManager().getActiveJobs(data));
                }
                return complete(args[1], plugin.getJobRegistry().getJobIds());
            }
            if ("info".equals(sub) || "infos".equals(sub) || "stats".equals(sub)) {
                return complete(args[1], plugin.getJobRegistry().getJobIds());
            }
            if ("top".equals(sub) || "classement".equals(sub)) {
                List<String> values = new ArrayList<String>();
                values.add("global");
                values.addAll(plugin.getJobRegistry().getJobIds());
                return complete(args[1], values);
            }
            if ("hud".equals(sub)) {
                return complete(args[1], Arrays.asList("bossbar", "actionbar", "on", "off"));
            }
        }
        return Collections.emptyList();
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

    private void send(Player player, String key, String fallback, String... replacements) {
        String msg = message(key, fallback, replacements);
        if (!msg.isEmpty()) player.sendMessage(msg);
    }

    private String message(String key, String fallback, String... replacements) {
        return format(plugin.getConfigManager().getMessage(key, fallback), replacements);
    }

    private String format(String raw, String... replacements) {
        String msg = raw == null ? "" : raw.replace("{prefix}", prefix());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return msg.replace("&", "\u00A7");
    }

    private String prefix() {
        return plugin.getConfigManager().getPrefix();
    }

    private String normalizeTopFilter(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return null;
        String lower = jobId.trim().toLowerCase();
        if ("global".equals(lower) || "all".equals(lower) || "general".equals(lower)) return null;
        return plugin.getJobRegistry().getJob(lower) == null ? "__invalid__" : lower;
    }

    private void sendTop(Player player, String jobId, List<RankingEntry> entries, int rank, boolean cached) {
        String target = getTopTargetDisplay(jobId);
        send(player, "player_command.top.header",
            "\u00A76\u00A7l====== Top {target} ====== \u00A78({count})",
            "{target}", target,
            "{target_id}", getTopTargetId(jobId),
            "{count}", String.valueOf(entries.size()),
            "{cached}", String.valueOf(cached));

        if (entries.isEmpty()) {
            send(player, "player_command.top.empty", "{prefix}\u00A77Aucune donnee de classement pour le moment.");
        } else {
            for (int i = 0; i < entries.size(); i++) {
                RankingEntry entry = entries.get(i);
                send(player, "player_command.top.line",
                    "\u00A7e#{position} \u00A7f{name} \u00A78- \u00A77Niv \u00A7a{level} \u00A78- \u00A77XP \u00A7b{xp}",
                    "{position}", String.valueOf(i + 1),
                    "{name}", resolveName(entry),
                    "{uuid}", entry.getUuid().toString(),
                    "{job_id}", entry.getJobId(),
                    "{level}", String.valueOf(entry.getLevel()),
                    "{xp}", String.valueOf(entry.getXp()));
            }
        }

        send(player, "player_command.top.own_rank",
            "{prefix}\u00A77Ton classement: \u00A7e{rank}",
            "{rank}", rank <= 0 ? message("player_command.top.unranked", "non classe") : "#" + rank,
            "{target}", target,
            "{target_id}", getTopTargetId(jobId));
    }

    private String getTopTargetDisplay(String jobId) {
        if (jobId == null) return message("player_command.top.global_name", "global");
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        return def == null ? jobId : def.getDisplayName();
    }

    private String getTopTargetId(String jobId) {
        return jobId == null ? "global" : jobId;
    }

    private String resolveName(RankingEntry entry) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getUuid());
        String name = offline == null ? null : offline.getName();
        return name == null || name.trim().isEmpty() ? entry.getUuid().toString().substring(0, 8) : name;
    }

    private List<RankingEntry> getCachedTop(String key) {
        int cacheSeconds = Math.max(0, plugin.getConfigManager().getMainConfig().getInt("top.cache_seconds", 30));
        if (cacheSeconds <= 0) return null;
        synchronized (topCacheLock) {
            TopCacheEntry entry = topCache.get(key);
            if (entry == null) return null;
            if (System.currentTimeMillis() - entry.createdAt > cacheSeconds * 1000L) {
                topCache.remove(key);
                return null;
            }
            return entry.entries;
        }
    }

    private void putCachedTop(String key, List<RankingEntry> entries) {
        synchronized (topCacheLock) {
            topCache.put(key, new TopCacheEntry(new ArrayList<RankingEntry>(entries)));
        }
    }

    private static final class TopCacheEntry {
        private final long createdAt = System.currentTimeMillis();
        private final List<RankingEntry> entries;

        private TopCacheEntry(List<RankingEntry> entries) {
            this.entries = entries;
        }
    }
}
