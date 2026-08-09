package me.krunsh.kjobultimate.gui;

import de.tr7zw.changeme.nbtapi.NBTItem;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.QuestData;
import me.krunsh.kjobultimate.data.RankingEntry;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.quests.QuestChainDefinition;
import me.krunsh.kjobultimate.quests.QuestChainPolicy;
import me.krunsh.kjobultimate.quests.QuestDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;
import me.krunsh.kjobultimate.util.LevelUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;

/**
 * GUI interne V1, volontairement simple et autonome.
 *
 * Objectif: remplacer les menus texte pour les joueurs sans dependre de Kgui.
 * Les formats YAML sont gardes lisibles pour pouvoir les enrichir ensuite.
 */
public final class GuiManager implements Listener {

    private final KjobUltimate plugin;

    private FileConfiguration homeConfig;
    private FileConfiguration jobsConfig;
    private FileConfiguration questsConfig;
    private final Map<UUID, Map<String, Long>> clickCooldowns = new HashMap<UUID, Map<String, Long>>();

    public GuiManager(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        homeConfig = loadOrCreate("gui/home.yml");
        jobsConfig = loadOrCreate("gui/jobs.yml");
        questsConfig = loadOrCreate("gui/quests.yml");
        validateGuiConfig("gui/home.yml", homeConfig);
        validateGuiConfig("gui/jobs.yml", jobsConfig);
        validateGuiConfig("gui/quests.yml", questsConfig);
    }

    public boolean isEnabled() {
        return homeConfig == null || homeConfig.getBoolean("enabled", true);
    }

    public void openDefault(Player player) {
        if (!isEnabled()) {
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null || plugin.getSlotManager().getActiveJobs(data).isEmpty()) {
            openJobs(player);
            return;
        }
        openHome(player);
    }

    public void openHome(Player player) {
        if (!isEnabled()) return;
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            send("player_command.data_not_loaded", "{prefix}\u00A7cDonnees non chargees, reessaie dans un instant.", player);
            return;
        }

        String favorite = data.getDisplayJob();
        if (favorite == null || !plugin.getSlotManager().isJobActive(data, favorite)) {
            List<String> active = plugin.getSlotManager().getActiveJobs(data);
            favorite = active.isEmpty() ? null : active.get(0);
            if (favorite != null) data.setDisplayJob(favorite);
        }

        JobDefinition def = favorite == null ? null : plugin.getJobRegistry().getJob(favorite);
        Inventory inv = Bukkit.createInventory(new JobsHolder(GuiType.HOME, favorite),
            normalizeSize(homeConfig.getInt("size", 27)),
            color(homeConfig.getString("title", "&6Jobs")));
        fill(inv, homeConfig);

        setConfiguredItem(inv, homeConfig, "items.jobs_list",
            "{unlocked}", String.valueOf(plugin.getSlotManager().getActiveJobs(data).size()),
            "{slots}", String.valueOf(data.getUnlockedSlots()),
            "{global_level}", String.valueOf(plugin.getSlotManager().getGlobalLevel(data)));

        setConfiguredItem(inv, homeConfig, "items.hud_toggle",
            hudPlaceholders(data));
        setConfiguredItem(inv, homeConfig, "items.top");
        setConfiguredItem(inv, homeConfig, "items.quests",
            "{claimable_quests}", String.valueOf(plugin.getQuestManager() == null ? 0 : plugin.getQuestManager().countClaimable(data)));

        if (def != null) {
            setConfiguredJobItem(inv, homeConfig, "items.favorite", def,
                placeholders(player, data, def));
            setConfiguredItem(inv, homeConfig, "items.leave",
                placeholders(player, data, def));
        } else {
            setConfiguredItem(inv, homeConfig, "items.favorite_empty");
        }

        player.openInventory(inv);
    }

    public void openJobs(Player player) {
        if (!isEnabled()) return;
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            send("player_command.data_not_loaded", "{prefix}\u00A7cDonnees non chargees, reessaie dans un instant.", player);
            return;
        }

        Inventory inv = Bukkit.createInventory(new JobsHolder(GuiType.JOBS, null),
            normalizeSize(jobsConfig.getInt("size", 54)),
            color(jobsConfig.getString("title", "&6Choix des jobs")));
        fill(inv, jobsConfig);

        List<Integer> slots = jobsConfig.getIntegerList("job_slots");
        if (slots.isEmpty()) slots = Arrays.asList(10, 11, 12, 13, 14, 15);

        int index = 0;
        for (JobDefinition def : plugin.getJobRegistry().getAllJobs()) {
            if (index >= slots.size()) break;
            inv.setItem(slots.get(index), buildJobItem(player, data, def));
            index++;
        }

        setConfiguredItem(inv, jobsConfig, "items.back");
        player.openInventory(inv);
    }

    public void openConfirmLeave(Player player, String jobId) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        if (data == null || def == null) return;

        Inventory inv = Bukkit.createInventory(new JobsHolder(GuiType.CONFIRM_LEAVE, jobId),
            normalizeSize(homeConfig.getInt("confirm_leave.size", 27)),
            format(homeConfig.getString("confirm_leave.title", "&cConfirmer"), placeholders(player, data, def)));
        fill(inv, homeConfig.getConfigurationSection("confirm_leave"));
        setConfiguredJobItem(inv, homeConfig, "confirm_leave.items.job", def,
            placeholders(player, data, def));
        setConfiguredItem(inv, homeConfig, "confirm_leave.items.confirm", placeholders(player, data, def));
        setConfiguredItem(inv, homeConfig, "confirm_leave.items.cancel", placeholders(player, data, def));
        player.openInventory(inv);
    }

    public void openJobDetail(Player player, String jobId) {
        if (!isEnabled()) return;
        PlayerData data = plugin.getPlayerDataManager().get(player);
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        if (data == null || def == null) return;

        boolean active = plugin.getSlotManager().isJobActive(data, jobId);
        boolean favorite = jobId.equals(data.getDisplayJob());
        String state = favorite ? "favorite" : (active ? "unlocked" : "locked");

        Inventory inv = Bukkit.createInventory(new JobsHolder(GuiType.DETAIL, jobId),
            normalizeSize(jobsConfig.getInt("detail.size", 27)),
            format(jobsConfig.getString("detail.title", "&6{job}"), placeholders(player, data, def)));
        fill(inv, jobsConfig.getConfigurationSection("detail"));
        setConfiguredJobItem(inv, jobsConfig, "detail.items.info", def,
            placeholders(player, data, def, "{status}", stateText(state)));
        setConfiguredItem(inv, jobsConfig, "detail.items.action." + state,
            placeholders(player, data, def, "{status}", stateText(state)));
        if (active) {
            setConfiguredItem(inv, jobsConfig, "detail.items.leave",
                placeholders(player, data, def, "{status}", stateText(state)));
        }
        setConfiguredItem(inv, jobsConfig, "detail.items.back");
        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;
        Inventory inv = Bukkit.createInventory(new JobsHolder(GuiType.SETTINGS, null),
            normalizeSize(homeConfig.getInt("settings.size", 27)),
            color(homeConfig.getString("settings.title", "&6Parametres Jobs")));
        fill(inv, homeConfig.getConfigurationSection("settings"));
        setConfiguredItem(inv, homeConfig, "settings.items.hud_toggle",
            hudPlaceholders(data));
        setConfiguredItem(inv, homeConfig, "settings.items.bossbar_toggle",
            hudPlaceholders(data));
        setConfiguredItem(inv, homeConfig, "settings.items.actionbar_toggle",
            hudPlaceholders(data));
        setConfiguredItem(inv, homeConfig, "settings.items.back");
        player.openInventory(inv);
    }

    public void openTop(Player player) {
        Inventory inv = Bukkit.createInventory(new JobsHolder(GuiType.TOP, null),
            normalizeSize(homeConfig.getInt("top.size", 27)),
            color(homeConfig.getString("top.selector_title", homeConfig.getString("top.title", "&6Classements Jobs"))));
        fill(inv, homeConfig.getConfigurationSection("top"));
        setConfiguredItem(inv, homeConfig, "top.items.global", topPlaceholders(player, null, 0, 0, 0, 0));
        setTopJobSelectorItems(inv, player);
        if (homeConfig.getConfigurationSection("top.items.global") == null
            && homeConfig.getConfigurationSection("top.items.job") == null) {
            setConfiguredItem(inv, homeConfig, "top.items.pending");
        }
        setConfiguredItem(inv, homeConfig, "top.items.back");
        player.openInventory(inv);
    }

    public void openQuests(Player player) {
        openQuests(player, "all", 0);
    }

    public void openQuests(Player player, String target, int page) {
        if (!isEnabled() || plugin.getQuestManager() == null) return;
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            send("player_command.data_not_loaded", "{prefix}\u00A7cDonnees non chargees, reessaie dans un instant.", player);
            return;
        }

        String filter = normalizeQuestTarget(target);
        int safePage = Math.max(0, page);
        List<QuestDefinition> quests = questsFor(filter);
        List<Integer> entrySlots = readQuestEntrySlots();
        int perPage = Math.max(1, entrySlots.size());
        int maxPage = quests.isEmpty() ? 0 : (quests.size() - 1) / perPage;
        if (safePage > maxPage) {
            openQuests(player, filter, maxPage);
            return;
        }

        Inventory inv = Bukkit.createInventory(new JobsHolder(GuiType.QUESTS, filter, safePage),
            normalizeSize(questsConfig.getInt("quests.size", 54)),
            format(questsConfig.getString("quests.title", "&6Quetes &8- Page {page}"),
                questListPlaceholders(player, filter, safePage, quests.size(), 0)));
        fill(inv, questsConfig.getConfigurationSection("quests"));

        int start = safePage * perPage;
        int displayed = 0;
        for (int i = 0; i < entrySlots.size(); i++) {
            int index = start + i;
            if (index >= quests.size()) break;
            int slot = entrySlots.get(i);
            if (slot < 0 || slot >= inv.getSize()) continue;
            QuestDefinition quest = quests.get(index);
            inv.setItem(slot, buildQuestItem(player, data, quest, filter, safePage, quests.size()));
            displayed++;
        }

        if (displayed == 0) {
            setConfiguredItem(inv, questsConfig, "quests.items.empty",
                questListPlaceholders(player, filter, safePage, quests.size(), displayed));
        }

        setQuestStaticItems(inv, player, filter, safePage, quests.size(), displayed);
        player.openInventory(inv);
    }

    public void openTop(Player player, String target, int page) {
        String normalized = normalizeTopTarget(target);
        if (normalized == null) {
            send("player_command.top.invalid_filter", "{prefix}\u00A7cClassement inconnu: \u00A7e{job_id}", player,
                "{job_id}", target == null ? "" : target);
            openTop(player);
            return;
        }

        int safePage = Math.max(0, page);
        Inventory inv = Bukkit.createInventory(new JobsHolder(GuiType.TOP, normalized, safePage),
            normalizeSize(homeConfig.getInt("top.ranking.size", 54)),
            format(homeConfig.getString("top.ranking.title", "&6Top {target} &8- Page {page}"),
                topPlaceholders(player, normalized, safePage, 0, 0, 0)));
        fill(inv, homeConfig.getConfigurationSection("top.ranking"));
        setConfiguredItem(inv, homeConfig, "top.ranking.items.loading",
            topPlaceholders(player, normalized, safePage, 0, 0, 0));
        setTopStaticRankingItems(inv, player, normalized, safePage, -1, 0, 0);
        player.openInventory(inv);

        final String finalTarget = normalized;
        final int finalPage = safePage;
        final int limit = Math.max(1, Math.min(50, plugin.getConfigManager().getMainConfig().getInt("top.gui_limit", 50)));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                List<RankingEntry> entries;
                int rank;
                try {
                    entries = plugin.getDatabaseManager().getTop(toDatabaseTopTarget(finalTarget), limit);
                    rank = plugin.getDatabaseManager().getRank(player.getUniqueId(), toDatabaseTopTarget(finalTarget));
                } catch (Exception e) {
                    KjobLogger.warn("[GUI] Erreur classement " + finalTarget + ": " + e.getMessage());
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (player.isOnline()) {
                                send("player_command.top.error", "{prefix}\u00A7cImpossible de charger le classement pour le moment.", player);
                            }
                        }
                    });
                    return;
                }

                final List<RankingEntry> finalEntries = entries;
                final int finalRank = rank;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (player.isOnline() && isSameTopView(player, finalTarget, finalPage)) {
                            renderTopRanking(player, finalTarget, finalPage, finalEntries, finalRank);
                        }
                    }
                });
            }
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof JobsHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClickedInventory() == null || event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;

        Player player = (Player) event.getWhoClicked();
        JobsHolder holder = (JobsHolder) event.getInventory().getHolder();

        if (executeConfiguredActions(player, holder, event.getRawSlot(), event.getClick())) {
            return;
        }

        if (holder.type == GuiType.HOME) {
            handleHomeClick(player, event.getRawSlot(), holder.jobId);
        } else if (holder.type == GuiType.JOBS) {
            handleJobsClick(player, event.getRawSlot(), event.getClick());
        } else if (holder.type == GuiType.DETAIL) {
            handleDetailClick(player, event.getRawSlot(), holder.jobId);
        } else if (holder.type == GuiType.SETTINGS) {
            handleSettingsClick(player, event.getRawSlot());
        } else if (holder.type == GuiType.TOP) {
            handleTopClick(player, event.getRawSlot(), holder);
        } else if (holder.type == GuiType.QUESTS) {
            handleQuestClick(player, event.getRawSlot(), holder);
        } else if (holder.type == GuiType.CONFIRM_LEAVE) {
            handleConfirmLeaveClick(player, event.getRawSlot(), holder.jobId);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof JobsHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clickCooldowns.remove(event.getPlayer().getUniqueId());
    }

    private boolean executeConfiguredActions(Player player, JobsHolder holder, int slot, ClickType click) {
        ClickContext context = contextFor(player, holder, slot);
        if (context == null || context.section == null) return false;

        List<String> actions = actionsForClick(context.section, click);
        if (actions.isEmpty()) return false;

        if (isOnClickCooldown(player, context)) {
            executeDenyActions(player, holder, context.jobId, context.section, "cooldown_deny_actions");
            return true;
        }

        if (!passesClickRequirements(player, context)) {
            executeDenyActions(player, holder, context.jobId, context.section, "deny_actions");
            return true;
        }

        markClickCooldown(player, context);
        for (String action : actions) {
            executeAction(player, holder, context.jobId, action);
        }
        return true;
    }

    private ClickContext contextFor(Player player, JobsHolder holder, int slot) {
        if (holder.type == GuiType.JOBS) {
            String clickedJob = jobIdAtSlot(slot);
            if (clickedJob != null) {
                PlayerData data = plugin.getPlayerDataManager().get(player);
                String state = stateFor(data, clickedJob);
                return new ClickContext(jobsConfig.getConfigurationSection("job_item." + state), clickedJob, "jobs.job_item." + state + "." + clickedJob);
            }
            ConfigurationSection section = findItemSection(jobsConfig.getConfigurationSection("items"), slot);
            return new ClickContext(section, null, pathOf("jobs", section));
        }
        if (holder.type == GuiType.DETAIL) {
            ConfigurationSection actionSection = detailActionSection(player, holder.jobId, slot);
            if (actionSection != null) {
                return new ClickContext(actionSection, holder.jobId, pathOf("jobs", actionSection));
            }
            ConfigurationSection section = findItemSection(jobsConfig.getConfigurationSection("detail.items"), slot);
            return new ClickContext(section, holder.jobId, pathOf("jobs", section));
        }
        if (holder.type == GuiType.HOME) {
            ConfigurationSection section = findItemSection(homeConfig.getConfigurationSection("items"), slot);
            return new ClickContext(section, holder.jobId, pathOf("home", section));
        }
        if (holder.type == GuiType.SETTINGS) {
            ConfigurationSection section = findItemSection(homeConfig.getConfigurationSection("settings.items"), slot);
            return new ClickContext(section, holder.jobId, pathOf("home", section));
        }
        if (holder.type == GuiType.TOP) {
            if (holder.jobId == null) {
                String selectorJob = topJobIdAtSlot(slot);
                if (selectorJob != null) {
                    ConfigurationSection section = homeConfig.getConfigurationSection("top.items.job");
                    return new ClickContext(section, selectorJob, "home.top.items.job." + selectorJob);
                }
                ConfigurationSection section = findItemSection(homeConfig.getConfigurationSection("top.items"), slot);
                return new ClickContext(section, holder.jobId, pathOf("home", section));
            }
            ConfigurationSection section = findItemSection(homeConfig.getConfigurationSection("top.ranking.items"), slot);
            return new ClickContext(section, holder.jobId, pathOf("home", section) + "." + holder.page);
        }
        if (holder.type == GuiType.QUESTS) {
            String filterJob = questFilterJobIdAtSlot(slot);
            if (filterJob != null) {
                ConfigurationSection section = "all".equals(holder.jobId) || !filterJob.equalsIgnoreCase(holder.jobId)
                    ? questsConfig.getConfigurationSection("quests.items.filter_job")
                    : questsConfig.getConfigurationSection("quests.items.filter_job_selected");
                if (section == null) section = questsConfig.getConfigurationSection("quests.items.filter_job");
                return new ClickContext(section, filterJob, "home.quests.items.filter_job." + filterJob);
            }
            String questId = questIdAtSlot(slot, holder.jobId, holder.page);
            if (questId != null) {
                PlayerData data = plugin.getPlayerDataManager().get(player);
                QuestDefinition quest = plugin.getQuestManager() == null ? null : plugin.getQuestManager().getQuest(questId);
                String state = quest == null ? "in_progress" : questState(data, quest);
                ConfigurationSection section = questsConfig.getConfigurationSection("quests.items.quest." + state);
                if (section == null) section = questsConfig.getConfigurationSection("quests.items.quest.in_progress");
                return new ClickContext(section, questId, "home.quests.items.quest." + state + "." + questId);
            }
            ConfigurationSection section = findItemSection(questsConfig.getConfigurationSection("quests.items"), slot);
            return new ClickContext(section, holder.jobId, pathOf("home", section) + "." + holder.page);
        }
        if (holder.type == GuiType.CONFIRM_LEAVE) {
            ConfigurationSection section = findItemSection(homeConfig.getConfigurationSection("confirm_leave.items"), slot);
            return new ClickContext(section, holder.jobId, pathOf("home", section));
        }
        return null;
    }

    private String pathOf(String file, ConfigurationSection section) {
        return file + "." + (section == null ? "unknown" : section.getCurrentPath());
    }

    private ConfigurationSection findItemSection(ConfigurationSection parent, int slot) {
        if (parent == null) return null;
        for (String key : parent.getKeys(false)) {
            ConfigurationSection child = parent.getConfigurationSection(key);
            if (child == null) continue;
            if (readSlots(child).contains(slot)) return child;
            ConfigurationSection nested = findItemSection(child, slot);
            if (nested != null) return nested;
        }
        return null;
    }

    private ConfigurationSection detailActionSection(Player player, String jobId, int slot) {
        if (jobId == null) return null;
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return null;
        String state = stateFor(data, jobId);
        ConfigurationSection section = jobsConfig.getConfigurationSection("detail.items.action." + state);
        if (section != null && readSlots(section).contains(slot)) return section;
        return null;
    }

    private List<String> actionsForClick(ConfigurationSection section, ClickType click) {
        if (section == null) return Collections.emptyList();
        String specific = null;
        if (click == ClickType.MIDDLE) {
            specific = "middle_click_actions";
        } else if (click == ClickType.SHIFT_LEFT) {
            specific = section.isList("shift_left_click_actions") ? "shift_left_click_actions" : "shift_click_actions";
        } else if (click == ClickType.SHIFT_RIGHT) {
            specific = section.isList("shift_right_click_actions") ? "shift_right_click_actions" : "shift_click_actions";
        } else if (click.isRightClick()) {
            specific = "right_click_actions";
        } else if (click.isLeftClick()) {
            specific = "left_click_actions";
        }
        if (specific != null && section.isList(specific)) return section.getStringList(specific);
        return section.getStringList("click_actions");
    }

    private boolean passesClickRequirements(Player player, ClickContext context) {
        List<String> requirements = context.section.getStringList("click_requirements");
        if (requirements.isEmpty()) return true;
        for (String requirement : requirements) {
            if (!passesRequirement(player, context.jobId, requirement)) return false;
        }
        return true;
    }

    private boolean passesRequirement(Player player, String contextJobId, String rawRequirement) {
        if (rawRequirement == null || rawRequirement.trim().isEmpty()) return true;
        String requirement = formatActionValue(player, contextJobId, rawRequirement).trim();
        String type = requirement;
        String value = "";
        int colon = requirement.indexOf(':');
        if (colon >= 0) {
            type = requirement.substring(0, colon).trim();
            value = requirement.substring(colon + 1).trim();
        }
        type = type.toLowerCase();

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if ("permission".equals(type) || "perm".equals(type)) {
            return !value.isEmpty() && player.hasPermission(value);
        }
        if ("no_permission".equals(type) || "!permission".equals(type)) {
            return value.isEmpty() || !player.hasPermission(value);
        }
        if ("has_jobs".equals(type)) {
            return data != null && !plugin.getSlotManager().getActiveJobs(data).isEmpty();
        }
        if ("no_jobs".equals(type)) {
            return data == null || plugin.getSlotManager().getActiveJobs(data).isEmpty();
        }
        if ("job_unlocked".equals(type) || "job_active".equals(type)) {
            return data != null && plugin.getSlotManager().isJobActive(data, resolveJobId(contextJobId, value));
        }
        if ("job_locked".equals(type)) {
            return data == null || !plugin.getSlotManager().isJobActive(data, resolveJobId(contextJobId, value));
        }
        if ("favorite_job".equals(type) || "job_favorite".equals(type)) {
            return data != null && resolveJobId(contextJobId, value).equals(data.getDisplayJob());
        }
        if ("global_level_min".equals(type)) {
            Integer min = parseInt(value);
            return data != null && min != null && plugin.getSlotManager().getGlobalLevel(data) >= min.intValue();
        }
        if ("slots_min".equals(type)) {
            Integer min = parseInt(value);
            return data != null && min != null && data.getUnlockedSlots() >= min.intValue();
        }
        if ("level_min".equals(type)) {
            return passesLevelRequirement(data, contextJobId, value, true);
        }
        if ("level_max".equals(type)) {
            return passesLevelRequirement(data, contextJobId, value, false);
        }

        KjobLogger.warn("[GUI] Requirement inconnu ignore: " + rawRequirement);
        return true;
    }

    private boolean passesLevelRequirement(PlayerData data, String contextJobId, String rawValue, boolean minCheck) {
        if (data == null) return false;
        String[] parts = rawValue.trim().split("\\s+");
        String jobId = contextJobId;
        String levelRaw = rawValue.trim();
        if (parts.length >= 2) {
            jobId = resolveJobId(contextJobId, parts[0]);
            levelRaw = parts[1];
        } else if (rawValue.contains("=")) {
            String[] eq = rawValue.split("=", 2);
            jobId = resolveJobId(contextJobId, eq[0].trim());
            levelRaw = eq[1].trim();
        }
        Integer level = parseInt(levelRaw);
        if (jobId == null || jobId.isEmpty() || level == null) return false;
        int current = data.getLevel(jobId);
        return minCheck ? current >= level.intValue() : current <= level.intValue();
    }

    private boolean isOnClickCooldown(Player player, ClickContext context) {
        int seconds = context.section.getInt("cooldown", 0);
        if (seconds <= 0) return false;
        Map<String, Long> playerCooldowns = clickCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return false;
        Long expires = playerCooldowns.get(context.path);
        long now = System.currentTimeMillis();
        if (expires == null) return false;
        if (expires.longValue() <= now) {
            playerCooldowns.remove(context.path);
            return false;
        }
        return true;
    }

    private void markClickCooldown(Player player, ClickContext context) {
        int seconds = context.section.getInt("cooldown", 0);
        if (seconds <= 0) return;
        Map<String, Long> playerCooldowns = clickCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            playerCooldowns = new HashMap<String, Long>();
            clickCooldowns.put(player.getUniqueId(), playerCooldowns);
        }
        playerCooldowns.put(context.path, System.currentTimeMillis() + seconds * 1000L);
    }

    private void executeDenyActions(Player player, JobsHolder holder, String contextJobId, ConfigurationSection section, String key) {
        List<String> actions = section.getStringList(key);
        if (actions.isEmpty() && !"deny_actions".equals(key)) actions = section.getStringList("deny_actions");
        for (String action : actions) {
            executeAction(player, holder, contextJobId, action);
        }
    }

    private void executeAction(Player player, JobsHolder holder, String contextJobId, String rawAction) {
        if (rawAction == null) return;
        String action = rawAction.trim();
        if (action.isEmpty()) return;

        String type = "player";
        String value = action;
        if (action.startsWith("[") && action.contains("]")) {
            int end = action.indexOf(']');
            type = action.substring(1, end).trim().toLowerCase();
            value = action.substring(end + 1).trim();
        }
        value = formatActionValue(player, contextJobId, value);

        if ("open".equals(type)) {
            openTarget(player, holder, contextJobId, value);
        } else if ("back".equals(type)) {
            openBack(player, holder);
        } else if ("close".equals(type)) {
            player.closeInventory();
        } else if ("message".equals(type)) {
            if (!value.isEmpty()) player.sendMessage(color(value));
        } else if ("player".equals(type) || "joueur".equals(type)) {
            if (!value.isEmpty()) player.performCommand(stripSlash(value));
        } else if ("console".equals(type) || "command".equals(type)) {
            if (!value.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(value));
        } else if ("sound".equals(type)) {
            playConfiguredSound(player, value);
        } else if ("refresh".equals(type)) {
            refresh(player, holder, contextJobId);
        } else if ("favorite".equals(type) || "favourite".equals(type)) {
            setFavoriteFromAction(player, contextJobId, value);
        } else if ("unlock".equals(type) || "join".equals(type)) {
            unlockFromAction(player, contextJobId, value);
        } else if ("leave_confirm".equals(type)) {
            openConfirmLeave(player, resolveJobId(contextJobId, value));
        } else if ("leave_confirmed".equals(type)) {
            confirmLeaveFromAction(player, contextJobId, value);
        } else if ("toggle_hud".equals(type) || "hud_toggle".equals(type)) {
            toggleHud(player);
        } else if ("toggle_bossbar".equals(type) || "bossbar_toggle".equals(type)) {
            toggleBossBar(player);
        } else if ("toggle_actionbar".equals(type) || "actionbar_toggle".equals(type)) {
            toggleActionBar(player);
        } else if ("top".equals(type)) {
            openTopFromAction(player, holder, contextJobId, value);
        } else if ("quests".equals(type) || "quest".equals(type)) {
            openQuestsFromAction(player, holder, contextJobId, value);
        } else if ("quest_claim".equals(type) || "claim_quest".equals(type)) {
            claimQuestFromAction(player, holder, contextJobId, value);
        } else {
            KjobLogger.warn("[GUI] Action inconnue ignoree: " + rawAction);
        }
    }

    private void openTarget(Player player, JobsHolder holder, String contextJobId, String rawTarget) {
        String target = rawTarget == null ? "" : rawTarget.trim();
        String lower = target.toLowerCase();
        if (lower.isEmpty() || "home".equals(lower) || "main".equals(lower) || "kjobs_home".equals(lower)) {
            openHome(player);
        } else if ("jobs".equals(lower) || "list".equals(lower) || "kjobs_jobs".equals(lower)) {
            openJobs(player);
        } else if ("settings".equals(lower) || "parametres".equals(lower) || "kjobs_settings".equals(lower)) {
            openSettings(player);
        } else if ("top".equals(lower) || "classement".equals(lower) || "classements".equals(lower)) {
            openTop(player);
        } else if ("quests".equals(lower) || "quetes".equals(lower) || "quest".equals(lower) || "quete".equals(lower)) {
            openQuests(player);
        } else if (lower.startsWith("quests:") || lower.startsWith("quetes:")) {
            openQuests(player, target.substring(target.indexOf(':') + 1), 0);
        } else if (lower.startsWith("top:") || lower.startsWith("classement:")) {
            openTop(player, target.substring(target.indexOf(':') + 1), 0);
        } else if (lower.startsWith("detail:") || lower.startsWith("job:")) {
            openJobDetail(player, resolveJobId(contextJobId, target.substring(target.indexOf(':') + 1)));
        } else if (lower.startsWith("confirm_leave:")) {
            openConfirmLeave(player, resolveJobId(contextJobId, target.substring(target.indexOf(':') + 1)));
        } else if (lower.startsWith("kjobs_detail_")) {
            openJobDetail(player, resolveJobId(contextJobId, target.substring("kjobs_detail_".length())));
        } else if ("back".equals(lower)) {
            openBack(player, holder);
        } else {
            KjobLogger.warn("[GUI] Target [open] inconnu: " + rawTarget);
        }
    }

    private void openBack(Player player, JobsHolder holder) {
        if (holder.type == GuiType.DETAIL) {
            openJobs(player);
        } else {
            openHome(player);
        }
    }

    private void refresh(Player player, JobsHolder holder, String contextJobId) {
        if (holder.type == GuiType.HOME) openHome(player);
        else if (holder.type == GuiType.JOBS) openJobs(player);
        else if (holder.type == GuiType.DETAIL) openJobDetail(player, resolveJobId(holder.jobId, contextJobId));
        else if (holder.type == GuiType.SETTINGS) openSettings(player);
        else if (holder.type == GuiType.TOP) {
            if (holder.jobId == null) openTop(player);
            else openTop(player, holder.jobId, holder.page);
        }
        else if (holder.type == GuiType.QUESTS) openQuests(player, holder.jobId, holder.page);
        else if (holder.type == GuiType.CONFIRM_LEAVE) openConfirmLeave(player, resolveJobId(holder.jobId, contextJobId));
    }

    private void openTopFromAction(Player player, JobsHolder holder, String contextJobId, String value) {
        String target = value == null ? "" : value.trim();
        String lower = target.toLowerCase();
        if (lower.isEmpty() || "selector".equals(lower) || "select".equals(lower) || "list".equals(lower)) {
            openTop(player);
            return;
        }
        if ("previous".equals(lower) || "prev".equals(lower)) {
            openTop(player, holder.jobId == null ? "global" : holder.jobId, Math.max(0, holder.page - 1));
            return;
        }
        if ("next".equals(lower)) {
            openTop(player, holder.jobId == null ? "global" : holder.jobId, holder.page + 1);
            return;
        }
        if ("refresh".equals(lower)) {
            if (holder.jobId == null) openTop(player);
            else openTop(player, holder.jobId, holder.page);
            return;
        }
        openTop(player, resolveTopActionTarget(contextJobId, target), 0);
    }

    private void openQuestsFromAction(Player player, JobsHolder holder, String contextId, String value) {
        String target = value == null ? "" : value.trim();
        String lower = target.toLowerCase();
        if (lower.isEmpty() || "list".equals(lower) || "all".equals(lower) || "selector".equals(lower)) {
            openQuests(player, "all", 0);
            return;
        }
        if ("previous".equals(lower) || "prev".equals(lower)) {
            openQuests(player, holder.jobId == null ? "all" : holder.jobId, Math.max(0, holder.page - 1));
            return;
        }
        if ("next".equals(lower)) {
            openQuests(player, holder.jobId == null ? "all" : holder.jobId, holder.page + 1);
            return;
        }
        if ("refresh".equals(lower)) {
            openQuests(player, holder.jobId == null ? "all" : holder.jobId, holder.page);
            return;
        }
        openQuests(player, resolveQuestActionTarget(contextId, target), 0);
    }

    private void claimQuestFromAction(Player player, JobsHolder holder, String contextQuestId, String value) {
        if (plugin.getQuestManager() == null) return;
        String questId = resolveQuestId(contextQuestId, value);
        if (questId == null || questId.isEmpty()) return;
        if (plugin.getQuestManager().claimReward(player, questId)) {
            openQuests(player, holder.jobId == null ? "all" : holder.jobId, holder.page);
        }
    }

    private void setFavoriteFromAction(Player player, String contextJobId, String value) {
        String jobId = resolveJobId(contextJobId, value);
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null || jobId == null || jobId.isEmpty()) return;
        if (plugin.getSlotManager().setFavoriteJob(player, data, jobId)) {
            openHome(player);
        }
    }

    private void unlockFromAction(Player player, String contextJobId, String value) {
        String jobId = resolveJobId(contextJobId, value);
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (def == null || data == null) return;
        if (plugin.getSlotManager().isJobActive(data, jobId)) {
            plugin.getSlotManager().setFavoriteJob(player, data, jobId);
            openHome(player);
            return;
        }
        if (!plugin.getSlotManager().assignJobToFreeSlot(player, data, jobId)) {
            send("player_command.join.no_free_slot",
                "{prefix}\u00A7cAucun emplacement libre. Monte ton niveau global pour debloquer un nouveau job.",
                player);
            return;
        }
        plugin.getSlotManager().setFavoriteJob(player, data, jobId);
        send("player_command.join.unlocked", "{prefix}\u00A7aJob debloque: \u00A7e{job}", player,
            "{job}", def.getDisplayName(), "{job_id}", jobId);
        openHome(player);
    }

    private void confirmLeaveFromAction(Player player, String contextJobId, String value) {
        String jobId = resolveJobId(contextJobId, value);
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null || jobId == null || jobId.isEmpty()) return;
        if (plugin.getSlotManager().requestLeaveJob(player, data, jobId)) {
            plugin.getSlotManager().confirmChange(player, data);
        }
        player.closeInventory();
    }

    private void toggleHud(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;
        data.setHudEnabled(!data.isHudEnabled());
        if (!data.isHudEnabled() && plugin.getHudManager() != null) {
            plugin.getHudManager().clearActionBar(player);
            plugin.getHudManager().removePlayer(player);
        }
        if (data.isHudEnabled() && !data.isActionBarHudEnabled() && plugin.getHudManager() != null) plugin.getHudManager().clearActionBar(player);
        openSettings(player);
    }

    private void toggleBossBar(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;
        data.setBossBarHudEnabled(!data.isBossBarHudEnabled());
        if (!data.isBossBarHudEnabled() && plugin.getHudManager() != null) plugin.getHudManager().removePlayer(player);
        openSettings(player);
    }

    private void toggleActionBar(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;
        data.setActionBarHudEnabled(!data.isActionBarHudEnabled());
        if (!data.isActionBarHudEnabled() && plugin.getHudManager() != null) plugin.getHudManager().clearActionBar(player);
        openSettings(player);
    }

    private void playConfiguredSound(Player player, String value) {
        String[] parts = value.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0F;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0F;
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception ex) {
            KjobLogger.warn("[GUI] Son invalide ignore: " + value);
        }
    }

    private String formatActionValue(Player player, String jobId, String value) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        JobDefinition def = jobId == null ? null : plugin.getJobRegistry().getJob(jobId);
        if (data != null && def != null) return format(value, placeholders(player, data, def));
        return format(value, "{player}", player.getName(), "{job_id}", jobId == null ? "" : jobId);
    }

    private String resolveJobId(String contextJobId, String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty() || "{job_id}".equals(raw)) return contextJobId;
        return raw.toLowerCase();
    }

    private String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private String stateFor(PlayerData data, String jobId) {
        if (data == null) return "locked";
        if (jobId.equals(data.getDisplayJob())) return "favorite";
        return plugin.getSlotManager().isJobActive(data, jobId) ? "unlocked" : "locked";
    }

    private void setTopJobSelectorItems(Inventory inv, Player player) {
        ConfigurationSection section = homeConfig.getConfigurationSection("top.items.job");
        if (section == null) return;
        List<Integer> slots = homeConfig.getIntegerList("top.job_slots");
        if (slots.isEmpty()) slots = Arrays.asList(10, 11, 12, 13, 14, 15);

        int index = 0;
        for (JobDefinition def : plugin.getJobRegistry().getAllJobs()) {
            if (index >= slots.size()) break;
            int slot = slots.get(index);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, itemForJob(section, def, topJobPlaceholders(player, def)));
            }
            index++;
        }
    }

    private void renderTopRanking(Player player, String target, int page, List<RankingEntry> entries, int rank) {
        ConfigurationSection entrySection = homeConfig.getConfigurationSection("top.ranking.items.entry");
        List<Integer> entrySlots = readSlots(entrySection);
        if (entrySlots.isEmpty()) entrySlots = defaultTopEntrySlots();

        int perPage = Math.max(1, entrySlots.size());
        int maxPage = entries.isEmpty() ? 0 : (entries.size() - 1) / perPage;
        if (page > maxPage) {
            openTop(player, target, maxPage);
            return;
        }

        Inventory inv = Bukkit.createInventory(new JobsHolder(GuiType.TOP, target, page),
            normalizeSize(homeConfig.getInt("top.ranking.size", 54)),
            format(homeConfig.getString("top.ranking.title", "&6Top {target} &8- Page {page}"),
                topPlaceholders(player, target, page, rank, entries.size(), entries.size())));
        fill(inv, homeConfig.getConfigurationSection("top.ranking"));

        int start = page * perPage;
        int displayed = 0;
        if (!entries.isEmpty() && entrySection != null) {
            for (int i = 0; i < entrySlots.size(); i++) {
                int index = start + i;
                if (index >= entries.size()) break;
                int slot = entrySlots.get(i);
                if (slot < 0 || slot >= inv.getSize()) continue;
                RankingEntry entry = entries.get(index);
                inv.setItem(slot, item(entrySection, topEntryPlaceholders(player, target, page, entry, index + 1, rank, entries.size())));
                displayed++;
            }
        }

        if (displayed == 0) {
            setConfiguredItem(inv, homeConfig, "top.ranking.items.empty",
                topPlaceholders(player, target, page, rank, entries.size(), entries.size()));
        }

        setTopStaticRankingItems(inv, player, target, page, rank, entries.size(), displayed);
        player.openInventory(inv);
    }

    private void setTopStaticRankingItems(Inventory inv, Player player, String target, int page, int rank, int total, int displayed) {
        String[] placeholders = topPlaceholders(player, target, page, rank, total, displayed);
        setConfiguredItem(inv, homeConfig, "top.ranking.items.own_rank", placeholders);
        setConfiguredItem(inv, homeConfig, "top.ranking.items.refresh", placeholders);
        setConfiguredItem(inv, homeConfig, "top.ranking.items.selector", placeholders);
        setConfiguredItem(inv, homeConfig, "top.ranking.items.back", placeholders);
        if (page > 0) {
            setConfiguredItem(inv, homeConfig, "top.ranking.items.previous", placeholders);
        }
        if (total > 0 && (page + 1) * Math.max(1, readTopEntrySlots().size()) < total) {
            setConfiguredItem(inv, homeConfig, "top.ranking.items.next", placeholders);
        }
    }

    private List<Integer> readTopEntrySlots() {
        ConfigurationSection entrySection = homeConfig.getConfigurationSection("top.ranking.items.entry");
        List<Integer> slots = readSlots(entrySection);
        return slots.isEmpty() ? defaultTopEntrySlots() : slots;
    }

    private List<Integer> defaultTopEntrySlots() {
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i <= 44; i++) slots.add(i);
        return slots;
    }

    private ItemStack buildQuestItem(Player player, PlayerData data, QuestDefinition quest, String filter, int page, int total) {
        String state = questState(data, quest);
        ConfigurationSection section = questsConfig.getConfigurationSection("quests.items.quest." + state);
        if (section == null) section = questsConfig.getConfigurationSection("quests.items.quest.in_progress");
        return item(section, questPlaceholders(player, data, quest, filter, page, total));
    }

    private void setQuestStaticItems(Inventory inv, Player player, String filter, int page, int total, int displayed) {
        String[] placeholders = questListPlaceholders(player, filter, page, total, displayed);
        setQuestFilterItems(inv, player, filter, page, total, displayed);
        setConfiguredItem(inv, questsConfig, "quests.items.refresh", placeholders);
        setConfiguredItem(inv, questsConfig, "quests.items.back", placeholders);
        if (page > 0) {
            setConfiguredItem(inv, questsConfig, "quests.items.previous", placeholders);
        }
        if (total > 0 && (page + 1) * Math.max(1, readQuestEntrySlots().size()) < total) {
            setConfiguredItem(inv, questsConfig, "quests.items.next", placeholders);
        }
    }

    private void setQuestFilterItems(Inventory inv, Player player, String filter, int page, int total, int displayed) {
        String normalized = normalizeQuestTarget(filter);
        String allPath = "all".equals(normalized)
            && questsConfig.getConfigurationSection("quests.items.filter_all_selected") != null
                ? "quests.items.filter_all_selected"
                : "quests.items.filter_all";
        setConfiguredItem(inv, questsConfig, allPath,
            questFilterPlaceholders(player, null, normalized, page, total, displayed));

        ConfigurationSection normal = questsConfig.getConfigurationSection("quests.items.filter_job");
        if (normal == null) return;

        List<Integer> slots = questsConfig.getIntegerList("quests.filter_job_slots");
        if (slots.isEmpty()) slots = Arrays.asList(1, 2, 3, 4, 5, 6);

        int index = 0;
        for (JobDefinition def : plugin.getJobRegistry().getAllJobs()) {
            if (index >= slots.size()) break;
            int slot = slots.get(index);
            if (slot >= 0 && slot < inv.getSize()) {
                boolean selected = def.getId().equalsIgnoreCase(normalized);
                ConfigurationSection section = selected
                    && questsConfig.getConfigurationSection("quests.items.filter_job_selected") != null
                        ? questsConfig.getConfigurationSection("quests.items.filter_job_selected")
                        : normal;
                inv.setItem(slot, itemForJob(section, def,
                    questFilterPlaceholders(player, def, normalized, page, total, displayed)));
            }
            index++;
        }
    }

    private String questFilterJobIdAtSlot(int slot) {
        List<Integer> slots = questsConfig.getIntegerList("quests.filter_job_slots");
        if (slots.isEmpty()) slots = Arrays.asList(1, 2, 3, 4, 5, 6);
        int index = slots.indexOf(slot);
        if (index < 0) return null;

        int current = 0;
        for (JobDefinition def : plugin.getJobRegistry().getAllJobs()) {
            if (current == index) return def.getId();
            current++;
        }
        return null;
    }

    private String questIdAtSlot(int slot, String filter, int page) {
        List<Integer> slots = readQuestEntrySlots();
        int indexInPage = slots.indexOf(slot);
        if (indexInPage < 0) return null;
        List<QuestDefinition> quests = questsFor(filter);
        int index = Math.max(0, page) * Math.max(1, slots.size()) + indexInPage;
        return index >= 0 && index < quests.size() ? quests.get(index).getId() : null;
    }

    private List<Integer> readQuestEntrySlots() {
        ConfigurationSection section = questsConfig.getConfigurationSection("quests.items.quest.in_progress");
        List<Integer> slots = readSlots(section);
        if (!slots.isEmpty()) return slots;
        slots = new ArrayList<Integer>();
        for (int i = 10; i <= 16; i++) slots.add(i);
        for (int i = 19; i <= 25; i++) slots.add(i);
        for (int i = 28; i <= 34; i++) slots.add(i);
        return slots;
    }

    private List<QuestDefinition> questsFor(String filter) {
        if (plugin.getQuestManager() == null) return Collections.emptyList();
        String normalized = normalizeQuestTarget(filter);
        List<QuestDefinition> quests = "all".equals(normalized)
            ? new ArrayList<QuestDefinition>(plugin.getQuestManager().getQuests())
            : new ArrayList<QuestDefinition>(plugin.getQuestManager().getQuestsForJob(normalized));
        Collections.sort(quests, new Comparator<QuestDefinition>() {
            @Override
            public int compare(QuestDefinition a, QuestDefinition b) {
                int job = a.getJobId().compareToIgnoreCase(b.getJobId());
                if (job != 0) return job;
                int chain = a.getChainId().compareToIgnoreCase(b.getChainId());
                if (chain != 0) return chain;
                int stage = Integer.compare(a.getChainStage(), b.getChainStage());
                return stage != 0 ? stage
                        : a.getId().compareToIgnoreCase(b.getId());
            }
        });
        return quests;
    }

    private String normalizeQuestTarget(String target) {
        if (target == null || target.trim().isEmpty()) return "all";
        String lower = target.trim().toLowerCase();
        if ("all".equals(lower) || "global".equals(lower) || "toutes".equals(lower) || "tout".equals(lower)) return "all";
        return plugin.getJobRegistry().getJob(lower) == null ? "all" : lower;
    }

    private String questState(PlayerData data, QuestDefinition quest) {
        if (plugin.getQuestManager() == null) return QuestChainPolicy.LOCKED_CHAIN;
        return plugin.getQuestManager().getQuestState(data, quest);
    }

    private String questStatusText(String state) {
        if (QuestChainPolicy.CLAIMED.equals(state)) return color("&8Deja recuperee");
        if (QuestChainPolicy.CLAIMABLE.equals(state)) return color("&aA recuperer");
        if (QuestChainPolicy.PAUSED_JOB.equals(state)) return color("&6En pause: metier inactif");
        if (QuestChainPolicy.LOCKED_LEVEL.equals(state)) return color("&cNiveau de metier insuffisant");
        if (QuestChainPolicy.LOCKED_CHAIN.equals(state)) return color("&8Etape precedente requise");
        return color("&eEn cours");
    }

    private boolean isSameTopView(Player player, String target, int page) {
        if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) return false;
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (!(holder instanceof JobsHolder)) return false;
        JobsHolder jobsHolder = (JobsHolder) holder;
        return jobsHolder.type == GuiType.TOP
            && page == jobsHolder.page
            && target != null
            && target.equals(jobsHolder.jobId);
    }

    private void handleHomeClick(Player player, int slot, String favorite) {
        if (slot == homeConfig.getInt("items.jobs_list.slot", 11)) {
            openJobs(player);
            return;
        }
        if (slot == homeConfig.getInt("items.hud_toggle.slot", 15)) {
            openSettings(player);
            return;
        }
        if (slot == homeConfig.getInt("items.top.slot", 16)) {
            openTop(player);
            return;
        }
        if (slot == homeConfig.getInt("items.quests.slot", -1)) {
            openQuests(player);
            return;
        }
        if (favorite != null && slot == homeConfig.getInt("items.leave.slot", 22)) {
            openConfirmLeave(player, favorite);
        }
    }

    private void handleJobsClick(Player player, int slot, ClickType click) {
        String jobId = jobIdAtSlot(slot);
        if (jobId == null) {
            if (slot == jobsConfig.getInt("items.back.slot", 49)) openHome(player);
            return;
        }

        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (def == null || data == null) return;

        openJobDetail(player, jobId);
    }

    private void handleDetailClick(Player player, int slot, String jobId) {
        if (jobId == null) return;
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (def == null || data == null) return;

        if (slot == jobsConfig.getInt("detail.items.back.slot", 22)) {
            openJobs(player);
            return;
        }
        if (slot == jobsConfig.getInt("detail.items.leave.slot", 15)
            && plugin.getSlotManager().isJobActive(data, jobId)) {
            openConfirmLeave(player, jobId);
            return;
        }
        if (slot != detailActionSlot(data, jobId)) return;

        if (plugin.getSlotManager().isJobActive(data, jobId)) {
            plugin.getSlotManager().setFavoriteJob(player, data, jobId);
            openHome(player);
            return;
        }

        if (!plugin.getSlotManager().assignJobToFreeSlot(player, data, jobId)) {
            send("player_command.join.no_free_slot",
                "{prefix}\u00A7cAucun emplacement libre. Monte ton niveau global pour debloquer un nouveau job.",
                player);
            return;
        }

        plugin.getSlotManager().setFavoriteJob(player, data, jobId);
        send("player_command.join.unlocked", "{prefix}\u00A7aJob debloque: \u00A7e{job}", player,
            "{job}", def.getDisplayName(), "{job_id}", jobId);
        openHome(player);
    }

    private void handleSettingsClick(Player player, int slot) {
        if (slot == homeConfig.getInt("settings.items.back.slot", 22)) {
            openHome(player);
            return;
        }
        if (slot == homeConfig.getInt("settings.items.hud_toggle.slot", 13)) {
            PlayerData data = plugin.getPlayerDataManager().get(player);
            if (data == null) return;
            data.setHudEnabled(!data.isHudEnabled());
            if (!data.isHudEnabled() && plugin.getHudManager() != null) {
                plugin.getHudManager().clearActionBar(player);
                plugin.getHudManager().removePlayer(player);
            }
            if (data.isHudEnabled() && !data.isActionBarHudEnabled() && plugin.getHudManager() != null) plugin.getHudManager().clearActionBar(player);
            openSettings(player);
            return;
        }
        if (slot == homeConfig.getInt("settings.items.bossbar_toggle.slot", -1)) {
            toggleBossBar(player);
            return;
        }
        if (slot == homeConfig.getInt("settings.items.actionbar_toggle.slot", -1)) {
            toggleActionBar(player);
        }
    }

    private void handleTopClick(Player player, int slot, JobsHolder holder) {
        if (holder.jobId == null) {
            String jobId = topJobIdAtSlot(slot);
            if (jobId != null) {
                openTop(player, jobId, 0);
                return;
            }
            if (slot == homeConfig.getInt("top.items.global.slot", -1)) {
                openTop(player, "global", 0);
                return;
            }
            if (slot == homeConfig.getInt("top.items.back.slot", 22)) openHome(player);
            return;
        }

        if (slot == homeConfig.getInt("top.ranking.items.previous.slot", -1) && holder.page > 0) {
            openTop(player, holder.jobId, holder.page - 1);
            return;
        }
        if (slot == homeConfig.getInt("top.ranking.items.next.slot", -1)) {
            openTop(player, holder.jobId, holder.page + 1);
            return;
        }
        if (slot == homeConfig.getInt("top.ranking.items.selector.slot", -1)) {
            openTop(player);
            return;
        }
        if (slot == homeConfig.getInt("top.ranking.items.refresh.slot", -1)) {
            openTop(player, holder.jobId, holder.page);
            return;
        }
        if (slot == homeConfig.getInt("top.ranking.items.back.slot", 49)) openHome(player);
    }

    private void handleQuestClick(Player player, int slot, JobsHolder holder) {
        String questId = questIdAtSlot(slot, holder.jobId, holder.page);
        if (questId != null) {
            PlayerData data = plugin.getPlayerDataManager().get(player);
            QuestDefinition quest = plugin.getQuestManager() == null ? null : plugin.getQuestManager().getQuest(questId);
            if (data != null && quest != null && "claimable".equals(questState(data, quest))) {
                if (plugin.getQuestManager().claimReward(player, questId)) {
                    openQuests(player, holder.jobId, holder.page);
                }
            }
            return;
        }

        if (slot == questsConfig.getInt("quests.items.previous.slot", -1) && holder.page > 0) {
            openQuests(player, holder.jobId, holder.page - 1);
            return;
        }
        if (slot == questsConfig.getInt("quests.items.next.slot", -1)) {
            openQuests(player, holder.jobId, holder.page + 1);
            return;
        }
        if (slot == questsConfig.getInt("quests.items.refresh.slot", -1)) {
            openQuests(player, holder.jobId, holder.page);
            return;
        }
        if (slot == questsConfig.getInt("quests.items.back.slot", 49)) openHome(player);
    }

    private void handleConfirmLeaveClick(Player player, int slot, String jobId) {
        if (slot == homeConfig.getInt("confirm_leave.items.cancel.slot", 15)) {
            openHome(player);
            return;
        }
        if (slot != homeConfig.getInt("confirm_leave.items.confirm.slot", 11)) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null || jobId == null) return;
        if (plugin.getSlotManager().requestLeaveJob(player, data, jobId)) {
            plugin.getSlotManager().confirmChange(player, data);
        }
        player.closeInventory();
    }

    private String jobIdAtSlot(int slot) {
        List<Integer> slots = jobsConfig.getIntegerList("job_slots");
        if (slots.isEmpty()) slots = Arrays.asList(10, 11, 12, 13, 14, 15);
        int index = slots.indexOf(slot);
        if (index < 0) return null;

        int i = 0;
        for (JobDefinition def : plugin.getJobRegistry().getAllJobs()) {
            if (i == index) return def.getId();
            i++;
        }
        return null;
    }

    private String topJobIdAtSlot(int slot) {
        List<Integer> slots = homeConfig.getIntegerList("top.job_slots");
        if (slots.isEmpty()) slots = Arrays.asList(10, 11, 12, 13, 14, 15);
        int index = slots.indexOf(slot);
        if (index < 0) return null;

        int i = 0;
        for (JobDefinition def : plugin.getJobRegistry().getAllJobs()) {
            if (i == index) return def.getId();
            i++;
        }
        return null;
    }

    private int detailActionSlot(PlayerData data, String jobId) {
        boolean active = plugin.getSlotManager().isJobActive(data, jobId);
        boolean favorite = jobId.equals(data.getDisplayJob());
        String state = favorite ? "favorite" : (active ? "unlocked" : "locked");
        return jobsConfig.getInt("detail.items.action." + state + ".slot", 11);
    }

    private ItemStack buildJobItem(Player player, PlayerData data, JobDefinition def) {
        String jobId = def.getId();
        boolean active = plugin.getSlotManager().isJobActive(data, jobId);
        boolean favorite = jobId.equals(data.getDisplayJob());
        String state = favorite ? "favorite" : (active ? "unlocked" : "locked");

        ConfigurationSection section = jobsConfig.getConfigurationSection("job_item." + state);
        if (section == null) section = jobsConfig.getConfigurationSection("job_item.locked");
        ItemStack item = itemForJob(section, def,
            placeholders(player, data, def,
                "{status}", stateText(state),
                "{click_action}", active ? color("&eClic: definir favori") : color("&aClic: debloquer")));
        return item;
    }

    private void setConfiguredJobItem(Inventory inv, FileConfiguration cfg, String path,
                                      JobDefinition job, String... replacements) {
        ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section == null) return;
        ItemStack built = itemForJob(section, job, replacements);
        for (Integer slot : readSlots(section)) {
            if (slot == null || slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, built);
        }
    }

    private void setConfiguredItem(Inventory inv, FileConfiguration cfg, String path, String... replacements) {
        ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section == null) return;
        ItemStack built = item(section, replacements);
        for (Integer slot : readSlots(section)) {
            if (slot == null || slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, built);
        }
    }

    private void fill(Inventory inv, ConfigurationSection menuSection) {
        for (ConfigurationSection section
                : MenuFillerResolver.resolveAll(menuSection)) {

            if (section == null
                    || !section.getBoolean("enabled", true)) {
                continue;
            }

            java.util.Set<Integer> excluded =
                MenuFillerResolver.excludedSlots(section);

            List<Integer> slots = readSlots(section);

            boolean clear =
                section.getBoolean("clear", false)
                    || section.getBoolean("remove", false);

            ItemStack filler =
                clear ? null : item(section);

            if (slots.isEmpty()) {
                for (int slot = 0;
                        slot < inv.getSize();
                        slot++) {

                    if (excluded.contains(
                            Integer.valueOf(slot))) {
                        continue;
                    }

                    inv.setItem(slot, filler);
                }

                continue;
            }

            for (Integer slot : slots) {
                if (slot == null
                        || slot < 0
                        || slot >= inv.getSize()) {
                    continue;
                }

                if (excluded.contains(slot)) {
                    continue;
                }

                inv.setItem(slot, filler);
            }
        }

        for (Integer slot
                : MenuFillerResolver.clearSlots(menuSection)) {

            if (slot == null
                    || slot < 0
                    || slot >= inv.getSize()) {
                continue;
            }

            inv.setItem(slot, null);
        }
    }

    private ItemStack item(ConfigurationSection section, String... replacements) {
        String materialName = section == null ? "STONE" : section.getString("material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.STONE;
        int amount = Math.max(1, section == null ? 1 : section.getInt("amount", 1));
        short data = (short) (section == null ? 0 : section.getInt("data", 0));

        ItemStack item = GuiItemFeatures.createBaseItem(
            plugin,
            section,
            material,
            amount,
            data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && section != null) {
            meta.setDisplayName(format(section.getString("name", section.getString("display_name", material.name())), replacements));
            List<String> lore = new ArrayList<String>();
            for (String line : section.getStringList("lore")) lore.add(format(line, replacements));
            meta.setLore(lore);
            if (section.getBoolean("hide_attributes", false)) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            item.setItemMeta(meta);
        }
        item = applyCustomNbt(item, section, replacements);
        item = GuiItemFeatures.applyGlow(item, section);
        return item;
    }

    /**
     * Applique l'icone centrale du metier apres le template du menu. Le template
     * garde le nom/lore/actions d'etat; seul le visuel material/data/CIT vient du
     * metier. Un menu peut mettre use_job_icon: false ou surcharger job_icon.*.
     */
    private ItemStack itemForJob(ConfigurationSection section, JobDefinition job, String... replacements) {
        ItemStack built = item(section, replacements);
        if (section == null || job == null || !section.getBoolean("use_job_icon", true)
                || !job.getIcon().isConfigured()) {
            return built;
        }

        String materialName = section.getString("job_icon.material", job.getIcon().getMaterial());
        Material material = materialName == null ? null : Material.matchMaterial(materialName.trim());
        if (material != null) {
            built.setType(material);
        }
        built.setDurability((short) section.getInt("job_icon.data", job.getIcon().getData()));

        boolean localCit = section.contains("job_icon.cit") || section.contains("job_icon.nbt_cit");
        String cit = section.contains("job_icon.nbt_cit")
            ? section.getString("job_icon.nbt_cit", "")
            : section.getString("job_icon.cit", job.getIcon().getCit());
        if (cit == null) cit = "";

        try {
            NBTItem nbtItem = new NBTItem(built);
            if (localCit && cit.trim().isEmpty()) {
                nbtItem.removeKey("sparrowmc-item");
            } else if (!cit.trim().isEmpty()) {
                // Valeur volontairement exacte: aucune normalisation de '_' ou '-'.
                nbtItem.setString("sparrowmc-item", formatPlain(cit, replacements));
            }
            built = nbtItem.getItem();
        } catch (Throwable ex) {
            KjobLogger.warn("[GUI] Impossible d'appliquer l'icone du job " + job.getId() + ": " + ex.getMessage());
        }
        return built;
    }

    private ItemStack applyCustomNbt(ItemStack item, ConfigurationSection section, String... replacements) {
        if (item == null || section == null) return item;

        try {
            NBTItem nbtItem = null;
            ConfigurationSection nbtSection = section.getConfigurationSection("nbt");
            if (nbtSection != null) {
                nbtItem = new NBTItem(item);
                for (String key : nbtSection.getKeys(false)) {
                    Object value = nbtSection.get(key);
                    applyNbtValue(nbtItem, key, value, replacements);
                }
                item = nbtItem.getItem();
            }

            String cit = section.getString("cit", section.getString("cit_key", ""));
            if (cit != null && !cit.trim().isEmpty()) {
                nbtItem = new NBTItem(item);
                nbtItem.setString("sparrowmc-item", formatPlain(cit, replacements));
                item = nbtItem.getItem();
            }
        } catch (Throwable ex) {
            KjobLogger.warn("[GUI] Impossible d'appliquer le NBT/CIT sur un item: " + ex.getMessage());
        }
        return item;
    }

    private void applyNbtValue(NBTItem nbtItem, String key, Object value, String... replacements) {
        if (value instanceof Boolean) {
            nbtItem.setBoolean(key, ((Boolean) value).booleanValue());
        } else if (value instanceof Integer) {
            nbtItem.setInteger(key, ((Integer) value).intValue());
        } else if (value instanceof Long) {
            nbtItem.setLong(key, ((Long) value).longValue());
        } else if (value instanceof Float) {
            nbtItem.setDouble(key, ((Float) value).doubleValue());
        } else if (value instanceof Double) {
            nbtItem.setDouble(key, ((Double) value).doubleValue());
        } else {
            nbtItem.setString(key, formatPlain(String.valueOf(value), replacements));
        }
    }

    private List<Integer> readSlots(ConfigurationSection section) {
        if (section == null) return Collections.emptyList();
        List<Integer> slots = new ArrayList<Integer>();
        if (section.contains("slots")) {
            Object raw = section.get("slots");
            appendSlots(slots, raw);
        }
        if (slots.isEmpty() && section.contains("slot")) {
            slots.add(section.getInt("slot", -1));
        }
        return slots;
    }

    private void appendSlots(List<Integer> target, Object raw) {
        if (raw == null) return;
        if (raw instanceof Number) {
            target.add(((Number) raw).intValue());
            return;
        }
        if (raw instanceof List) {
            for (Object part : (List<?>) raw) appendSlots(target, part);
            return;
        }

        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) return;
        String[] parts = value.split(",");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            int dash = token.indexOf('-');
            if (dash > 0) {
                Integer start = parseInt(token.substring(0, dash).trim());
                Integer end = parseInt(token.substring(dash + 1).trim());
                if (start == null || end == null) continue;
                int step = start <= end ? 1 : -1;
                for (int slot = start; slot != end + step; slot += step) target.add(slot);
            } else {
                Integer slot = parseInt(token);
                if (slot != null) target.add(slot);
            }
        }
    }

    private Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String[] placeholders(Player player, PlayerData data, JobDefinition def, String... extra) {
        String jobId = def.getId();
        int level = Math.max(0, Math.min(def.getMaxLevel(), data.getLevel(jobId)));
        int xp = level >= def.getMaxLevel()
            ? 0
            : LevelUtil.getCurrentLevelXp(data, def);
        int xpNext = LevelUtil.getRequiredXpForNextLevel(data, def);
        int percent = LevelUtil.getProgressPercentage(data, def);
        List<String> values = new ArrayList<String>();
        values.add("{player}"); values.add(player.getName());
        values.add("{job}"); values.add(def.getDisplayName());
        values.add("{job_id}"); values.add(jobId);
        values.add("{level}"); values.add(String.valueOf(level));
        values.add("{max_level}"); values.add(String.valueOf(def.getMaxLevel()));
        values.add("{xp}"); values.add(String.valueOf(xp));
        values.add("{xp_next}"); values.add(String.valueOf(xpNext));
        values.add("{percent}"); values.add(String.valueOf(percent));
        values.add("{global_level}"); values.add(String.valueOf(plugin.getSlotManager().getGlobalLevel(data)));
        values.add("{unlocked}"); values.add(String.valueOf(plugin.getSlotManager().getActiveJobs(data).size()));
        values.add("{slots}"); values.add(String.valueOf(data.getUnlockedSlots()));
        values.add("{max_slots}"); values.add(String.valueOf(plugin.getConfigManager().getMaxSlots()));
        values.addAll(Arrays.asList(extra));
        return values.toArray(new String[values.size()]);
    }

    private String[] hudPlaceholders(PlayerData data) {
        boolean hud = data != null && data.isHudEnabled();
        boolean bossbar = data != null && data.isBossBarHudEnabled();
        boolean actionbar = data != null && data.isActionBarHudEnabled();
        return new String[] {
            "{status}", hud ? color("&aON") : color("&cOFF"),
            "{hud_status}", hud ? color("&aON") : color("&cOFF"),
            "{bossbar_status}", bossbar ? color("&aON") : color("&cOFF"),
            "{actionbar_status}", actionbar ? color("&aON") : color("&cOFF")
        };
    }

    private String[] topJobPlaceholders(Player player, JobDefinition def) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        String jobId = def.getId();
        int level = data == null ? 0 : data.getLevel(jobId);
        int xp = data == null ? 0 : data.getXP(jobId);
        boolean active = data != null && plugin.getSlotManager().isJobActive(data, jobId);
        boolean favorite = data != null && jobId.equals(data.getDisplayJob());
        List<String> values = new ArrayList<String>();
        values.add("{player}"); values.add(player.getName());
        values.add("{job}"); values.add(def.getDisplayName());
        values.add("{job_id}"); values.add(jobId);
        values.add("{level}"); values.add(String.valueOf(level));
        values.add("{xp}"); values.add(String.valueOf(xp));
        values.add("{status}"); values.add(favorite ? color("&6Favori") : (active ? color("&aDebloque") : color("&8Bloque")));
        values.add("{target}"); values.add(def.getDisplayName());
        values.add("{target_id}"); values.add(jobId);
        return values.toArray(new String[values.size()]);
    }

    private String[] topEntryPlaceholders(Player player, String target, int page, RankingEntry entry, int position, int rank, int total) {
        List<String> values = new ArrayList<String>(Arrays.asList(topPlaceholders(player, target, page, rank, total, total)));
        values.add("{position}"); values.add(String.valueOf(position));
        values.add("{name}"); values.add(resolveName(entry));
        values.add("{uuid}"); values.add(entry.getUuid().toString());
        values.add("{job_id}"); values.add(entry.getJobId());
        values.add("{level}"); values.add(String.valueOf(entry.getLevel()));
        values.add("{xp}"); values.add(String.valueOf(entry.getXp()));
        return values.toArray(new String[values.size()]);
    }

    private String[] topPlaceholders(Player player, String target, int page, int rank, int total, int displayed) {
        String normalized = target == null ? "global" : target;
        List<String> values = new ArrayList<String>();
        values.add("{player}"); values.add(player.getName());
        values.add("{target}"); values.add(topTargetDisplay(normalized));
        values.add("{target_id}"); values.add(normalized);
        values.add("{page}"); values.add(String.valueOf(page + 1));
        values.add("{page_index}"); values.add(String.valueOf(page));
        values.add("{previous_page}"); values.add(String.valueOf(Math.max(1, page)));
        values.add("{next_page}"); values.add(String.valueOf(page + 2));
        values.add("{rank}"); values.add(rankLabel(rank));
        values.add("{total}"); values.add(String.valueOf(total));
        values.add("{count}"); values.add(String.valueOf(displayed));
        return values.toArray(new String[values.size()]);
    }

    private String[] questPlaceholders(Player player, PlayerData data, QuestDefinition quest, String filter, int page, int total) {
        QuestData questData = data == null ? null : data.getQuestProgress().get(quest.getId());
        int progress = questData == null ? 0 : questData.getProgress();
        int amount = Math.max(1, quest.getAmount());
        int percent = Math.min(100, (int) ((double) progress / amount * 100D));
        String state = questState(data, quest);
        JobDefinition job = plugin.getJobRegistry().getJob(quest.getJobId());
        QuestChainDefinition chain = plugin.getQuestManager() == null
                ? null : plugin.getQuestManager().getChain(quest.getChainId());
        QuestDefinition active = plugin.getQuestManager() == null
                ? null : plugin.getQuestManager().getActiveQuest(
                        data, quest.getChainId());

        List<String> values = new ArrayList<String>(Arrays.asList(questListPlaceholders(player, filter, page, total, total)));
        values.add("{quest}"); values.add(quest.getDisplayName());
        values.add("{quest_id}"); values.add(quest.getId());
        values.add("{quest_status}"); values.add(questStatusText(state));
        values.add("{quest_state}"); values.add(state);
        values.add("{job}"); values.add(job == null ? quest.getJobId() : job.getDisplayName());
        values.add("{job_id}"); values.add(quest.getJobId());
        values.add("{type}"); values.add(quest.getType());
        values.add("{target}"); values.add(quest.getTarget());
        values.add("{progress}"); values.add(String.valueOf(progress));
        values.add("{amount}"); values.add(String.valueOf(amount));
        values.add("{percent}"); values.add(String.valueOf(percent));
        values.add("{min_level}"); values.add(String.valueOf(quest.getMinLevel()));
        values.add("{reward_xp}"); values.add(String.valueOf(quest.getRewardXp()));
        values.add("{completed_at}"); values.add(String.valueOf(questData == null ? 0L : questData.getCompletedAt()));
        values.add("{chain}"); values.add(chain == null
                ? quest.getChainId() : chain.getDisplayName());
        values.add("{chain_id}"); values.add(quest.getChainId());
        values.add("{stage}"); values.add(String.valueOf(quest.getChainStage()));
        values.add("{stage_total}"); values.add(String.valueOf(
                chain == null ? 1 : chain.getStages().size()));
        values.add("{active_quest}"); values.add(active == null
                ? "" : active.getDisplayName());
        values.add("{active_quest_id}"); values.add(active == null
                ? "" : active.getId());
        return values.toArray(new String[values.size()]);
    }

    private String[] questListPlaceholders(Player player, String filter, int page, int total, int displayed) {
        List<String> values = new ArrayList<String>();
        values.add("{player}"); values.add(player.getName());
        values.add("{target}"); values.add(questTargetDisplay(filter));
        values.add("{target_id}"); values.add(filter == null ? "all" : filter);
        values.add("{page}"); values.add(String.valueOf(page + 1));
        values.add("{page_index}"); values.add(String.valueOf(page));
        values.add("{previous_page}"); values.add(String.valueOf(Math.max(1, page)));
        values.add("{next_page}"); values.add(String.valueOf(page + 2));
        values.add("{total}"); values.add(String.valueOf(total));
        values.add("{count}"); values.add(String.valueOf(displayed));
        return values.toArray(new String[values.size()]);
    }

    private String[] questFilterPlaceholders(Player player, JobDefinition def, String filter, int page, int total, int displayed) {
        List<String> values = new ArrayList<String>(Arrays.asList(questListPlaceholders(player, filter, page, total, displayed)));
        String jobId = def == null ? "all" : def.getId();
        boolean selected = jobId.equalsIgnoreCase(filter);
        values.add("{job}"); values.add(def == null ? questTargetDisplay("all") : def.getDisplayName());
        values.add("{job_id}"); values.add(jobId);
        values.add("{filter_status}"); values.add(selected ? color("&aSelectionne") : color("&7Disponible"));
        values.add("{selected}"); values.add(selected ? color("&aON") : "");
        return values.toArray(new String[values.size()]);
    }

    private String questTargetDisplay(String filter) {
        if (filter == null || "all".equals(filter)) return plugin.getConfigManager().getMessage("quest.all_name", "toutes");
        JobDefinition def = plugin.getJobRegistry().getJob(filter);
        return def == null ? filter : def.getDisplayName();
    }

    private String normalizeTopTarget(String target) {
        if (target == null || target.trim().isEmpty()) return "global";
        String lower = target.trim().toLowerCase();
        if ("global".equals(lower) || "all".equals(lower) || "general".equals(lower)) return "global";
        return plugin.getJobRegistry().getJob(lower) == null ? null : lower;
    }

    private String resolveTopActionTarget(String contextJobId, String target) {
        String raw = target == null ? "" : target.trim();
        if (raw.isEmpty() || "{job_id}".equals(raw)) return contextJobId == null ? "global" : contextJobId;
        return raw;
    }

    private String resolveQuestActionTarget(String contextJobId, String target) {
        String raw = target == null ? "" : target.trim();
        if (raw.isEmpty() || "{job_id}".equals(raw)) return contextJobId == null ? "all" : contextJobId;
        return raw;
    }

    private String resolveQuestId(String contextQuestId, String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty() || "{quest_id}".equals(raw)) return contextQuestId;
        return raw;
    }

    private String toDatabaseTopTarget(String target) {
        return target == null || "global".equals(target) ? null : target;
    }

    private String topTargetDisplay(String target) {
        if (target == null || "global".equals(target)) {
            return plugin.getConfigManager().getMessage("player_command.top.global_name", "global");
        }
        JobDefinition def = plugin.getJobRegistry().getJob(target);
        return def == null ? target : def.getDisplayName();
    }

    private String rankLabel(int rank) {
        if (rank <= 0) return plugin.getConfigManager().getMessage("player_command.top.unranked", "non classe");
        return "#" + rank;
    }

    private String resolveName(RankingEntry entry) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getUuid());
        String name = offline == null ? null : offline.getName();
        return name == null || name.trim().isEmpty() ? entry.getUuid().toString().substring(0, 8) : name;
    }

    private String stateText(String state) {
        if ("favorite".equals(state)) return color("&6Favori");
        if ("unlocked".equals(state)) return color("&aDebloque");
        return color("&8Bloque");
    }

    private String format(String raw, String... replacements) {
        String msg = (raw == null ? "" : raw).replace("{prefix}", plugin.getConfigManager().getPrefix());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return color(msg);
    }

    private String formatPlain(String raw, String... replacements) {
        String msg = raw == null ? "" : raw.replace("{prefix}", plugin.getConfigManager().getPrefix());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return msg;
    }

    private String color(String raw) {
        return raw == null ? "" : raw.replace("&", "\u00A7");
    }

    private int normalizeSize(int size) {
        if (size < 9) return 9;
        if (size > 54) return 54;
        return ((size + 8) / 9) * 9;
    }

    private FileConfiguration loadOrCreate(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        InputStream defaultsStream = plugin.getResource(resourcePath);
        if (defaultsStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultsStream, StandardCharsets.UTF_8));
            cfg.setDefaults(defaults);
            cfg.options().copyDefaults(true);
        }
        return cfg;
    }

    private void validateGuiConfig(String name, FileConfiguration cfg) {
        if (cfg == null) {
            KjobLogger.warn("[GUI] " + name + " non charge.");
            return;
        }
        validateSize(name + ".size", cfg.getInt("size", 27));
        validateSection(name, cfg);
        if (cfg.contains("detail.size")) validateSize(name + ".detail.size", cfg.getInt("detail.size", 27));
        if (cfg.contains("settings.size")) validateSize(name + ".settings.size", cfg.getInt("settings.size", 27));
        if (cfg.contains("top.size")) validateSize(name + ".top.size", cfg.getInt("top.size", 27));
        if (cfg.contains("top.ranking.size")) validateSize(name + ".top.ranking.size", cfg.getInt("top.ranking.size", 54));
        if (cfg.contains("confirm_leave.size")) validateSize(name + ".confirm_leave.size", cfg.getInt("confirm_leave.size", 27));

        for (Integer slot : cfg.getIntegerList("job_slots")) {
            if (slot == null || slot < 0 || slot >= normalizeSize(cfg.getInt("size", 54))) {
                KjobLogger.warn("[GUI] " + name + ".job_slots contient un slot invalide: " + slot);
            }
        }
    }

    private void validateSection(String path, ConfigurationSection section) {
        if (section == null) return;
        if (section.contains("material")) {
            String materialName = section.getString("material", "");
            if (Material.matchMaterial(materialName) == null) {
                KjobLogger.warn("[GUI] Material inconnu dans " + path + ": " + materialName);
            }
        }
        if (section.contains("slot")) {
            int slot = section.getInt("slot", -1);
            if (slot < 0 || slot >= 54) {
                KjobLogger.warn("[GUI] Slot hors limites dans " + path + ": " + slot);
            }
        }
        if (section.contains("slots")) {
            for (Integer slot : readSlots(section)) {
                if (slot == null || slot < 0 || slot >= 54) {
                    KjobLogger.warn("[GUI] Slot hors limites dans " + path + ".slots: " + slot);
                }
            }
        }
        validateActions(path, section, "click_actions");
        validateActions(path, section, "left_click_actions");
        validateActions(path, section, "right_click_actions");
        validateActions(path, section, "shift_click_actions");
        validateActions(path, section, "shift_left_click_actions");
        validateActions(path, section, "shift_right_click_actions");
        validateActions(path, section, "middle_click_actions");
        validateActions(path, section, "deny_actions");
        validateActions(path, section, "cooldown_deny_actions");
        validateRequirements(path, section);
        if (section.contains("cooldown") && section.getInt("cooldown", 0) < 0) {
            KjobLogger.warn("[GUI] cooldown negatif dans " + path + ": " + section.getInt("cooldown"));
        }
        validateItemNbt(path, section);
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) validateSection(path + "." + key, child);
        }
    }

    private void validateItemNbt(String path, ConfigurationSection section) {
        if (section.contains("cit") && section.getString("cit", "").trim().isEmpty()) {
            KjobLogger.warn("[GUI] cit vide dans " + path);
        }
        if (section.contains("cit_key") && section.getString("cit_key", "").trim().isEmpty()) {
            KjobLogger.warn("[GUI] cit_key vide dans " + path);
        }
        ConfigurationSection nbt = section.getConfigurationSection("nbt");
        if (nbt == null) return;
        for (String key : nbt.getKeys(false)) {
            if (nbt.getConfigurationSection(key) != null) {
                KjobLogger.warn("[GUI] NBT imbrique non supporte dans " + path + ".nbt." + key);
            }
        }
    }

    private void validateActions(String path, ConfigurationSection section, String key) {
        if (!section.isList(key)) return;
        for (String action : section.getStringList(key)) {
            String type = actionType(action);
            if (!isKnownActionType(type)) {
                KjobLogger.warn("[GUI] Action inconnue dans " + path + "." + key + ": " + action);
            }
        }
    }

    private void validateRequirements(String path, ConfigurationSection section) {
        if (!section.isList("click_requirements")) return;
        for (String requirement : section.getStringList("click_requirements")) {
            String type = requirementType(requirement);
            if (!isKnownRequirementType(type)) {
                KjobLogger.warn("[GUI] Requirement inconnu dans " + path + ".click_requirements: " + requirement);
            }
        }
    }

    private String requirementType(String requirement) {
        if (requirement == null) return "";
        String trimmed = requirement.trim();
        int colon = trimmed.indexOf(':');
        return (colon >= 0 ? trimmed.substring(0, colon) : trimmed).trim().toLowerCase();
    }

    private boolean isKnownRequirementType(String type) {
        return "permission".equals(type)
            || "perm".equals(type)
            || "no_permission".equals(type)
            || "!permission".equals(type)
            || "has_jobs".equals(type)
            || "no_jobs".equals(type)
            || "job_unlocked".equals(type)
            || "job_active".equals(type)
            || "job_locked".equals(type)
            || "favorite_job".equals(type)
            || "job_favorite".equals(type)
            || "global_level_min".equals(type)
            || "slots_min".equals(type)
            || "level_min".equals(type)
            || "level_max".equals(type);
    }

    private String actionType(String action) {
        if (action == null) return "";
        String trimmed = action.trim();
        if (!trimmed.startsWith("[") || !trimmed.contains("]")) return "player";
        return trimmed.substring(1, trimmed.indexOf(']')).trim().toLowerCase();
    }

    private boolean isKnownActionType(String type) {
        return "open".equals(type)
            || "back".equals(type)
            || "close".equals(type)
            || "message".equals(type)
            || "player".equals(type)
            || "joueur".equals(type)
            || "console".equals(type)
            || "command".equals(type)
            || "sound".equals(type)
            || "refresh".equals(type)
            || "favorite".equals(type)
            || "favourite".equals(type)
            || "unlock".equals(type)
            || "join".equals(type)
            || "leave_confirm".equals(type)
            || "leave_confirmed".equals(type)
            || "toggle_hud".equals(type)
            || "hud_toggle".equals(type)
            || "toggle_bossbar".equals(type)
            || "bossbar_toggle".equals(type)
            || "toggle_actionbar".equals(type)
            || "actionbar_toggle".equals(type)
            || "top".equals(type)
            || "quests".equals(type)
            || "quest".equals(type)
            || "quest_claim".equals(type)
            || "claim_quest".equals(type);
    }

    private void validateSize(String path, int size) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            KjobLogger.warn("[GUI] Taille inventaire invalide dans " + path + ": " + size + " (normalisee au runtime).");
        }
    }

    private void send(String key, String fallback, Player player, String... replacements) {
        String msg = format(plugin.getConfigManager().getMessage(key, fallback), replacements);
        if (!msg.isEmpty()) player.sendMessage(msg);
    }

    private enum GuiType {
        HOME,
        JOBS,
        DETAIL,
        SETTINGS,
        TOP,
        QUESTS,
        CONFIRM_LEAVE
    }

    private static final class ClickContext {
        private final ConfigurationSection section;
        private final String jobId;
        private final String path;

        private ClickContext(ConfigurationSection section, String jobId, String path) {
            this.section = section;
            this.jobId = jobId;
            this.path = path == null ? "unknown" : path;
        }
    }

    private static final class JobsHolder implements InventoryHolder {
        private final GuiType type;
        private final String jobId;
        private final int page;

        private JobsHolder(GuiType type, String jobId) {
            this(type, jobId, 0);
        }

        private JobsHolder(GuiType type, String jobId, int page) {
            this.type = type;
            this.jobId = jobId;
            this.page = Math.max(0, page);
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
