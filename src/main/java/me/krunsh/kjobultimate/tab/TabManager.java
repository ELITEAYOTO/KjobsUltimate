package me.krunsh.kjobultimate.tab;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.hooks.VaultHook;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * Header/footer tab list 1.8 via PacketPlayOutPlayerListHeaderFooter.
 */
public final class TabManager {

    private final KjobUltimate plugin;
    private final String nms;
    private BukkitTask task;
    private boolean enabled;
    private long intervalTicks;
    private String header;
    private String footer;
    private boolean sectionsEnabled;
    private boolean applyPapiPerPlayer;
    private boolean playerListNameEnabled;
    private boolean playerListNameTruncate;
    private int playerListNameMaxLength;
    private String playerListNameFormat;
    private String playerListNameStaffFormat;
    private String playerListNameStaffPermission;
    private List<TabSection> headerSections = Collections.emptyList();
    private List<TabSection> footerSections = Collections.emptyList();
    private List<TabStaffGroup> staffGroups = Collections.emptyList();
    private final VirtualTabManager virtualTabManager;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.##");
    private static final Set<String> KNOWN_SECTION_CONDITIONS = new HashSet<String>(Arrays.asList(
        "always", "has_jobs", "no_jobs", "staff_online", "no_staff_online", "vault"
    ));

    public TabManager(KjobUltimate plugin) {
        this.plugin = plugin;
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        this.nms = pkg.substring(pkg.lastIndexOf('.') + 1);
        this.virtualTabManager = new VirtualTabManager(plugin, this, nms);
        reload();
    }

    public void reload() {
        boolean wasPlayerListNameEnabled = playerListNameEnabled;
        enabled = plugin.getConfigManager().getTabConfig().getBoolean("enabled", true) && canOwnTab();
        intervalTicks = Math.max(20L, plugin.getConfigManager().getTabConfig().getLong("update_interval_ticks", 40L));
        header = plugin.getConfigManager().getTabConfig().getString("header", "");
        footer = plugin.getConfigManager().getTabConfig().getString("footer", "");
        sectionsEnabled = plugin.getConfigManager().getTabConfig().getBoolean("sections.enabled", false);
        applyPapiPerPlayer = plugin.getConfigManager().getTabConfig().getBoolean("placeholderapi_per_player", true);
        playerListNameEnabled = plugin.getConfigManager().getTabConfig().getBoolean("player_list_name.enabled", false);
        playerListNameTruncate = plugin.getConfigManager().getTabConfig().getBoolean("player_list_name.truncate_to_legacy_limit", true);
        playerListNameMaxLength = Math.max(1, plugin.getConfigManager().getTabConfig().getInt("player_list_name.max_length", 16));
        playerListNameFormat = plugin.getConfigManager().getTabConfig().getString("player_list_name.format", "&7%player_name%");
        playerListNameStaffFormat = plugin.getConfigManager().getTabConfig().getString("player_list_name.staff_format", "&b%player_name%");
        playerListNameStaffPermission = plugin.getConfigManager().getTabConfig().getString("player_list_name.staff_permission", "kjobsultimate.staff");
        staffGroups = loadStaffGroups();
        headerSections = loadSections("sections.header");
        footerSections = loadSections("sections.footer");
        if (wasPlayerListNameEnabled && !playerListNameEnabled) resetPlayerListNames();
        start();
        virtualTabManager.reload();
    }

    public void shutdown() {
        virtualTabManager.shutdown();
        stopTask();
        resetPlayerListNames();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getIntervalTicks() {
        return intervalTicks;
    }

    public boolean isSectionsEnabled() {
        return sectionsEnabled;
    }

    public boolean isPlayerListNameEnabled() {
        return playerListNameEnabled;
    }

    public boolean isPlaceholderApiPerPlayer() {
        return applyPapiPerPlayer;
    }

    public boolean isVirtualLayoutEnabled() {
        return virtualTabManager.isEnabled();
    }

    public String getVirtualStatus(Player viewer) {
        return virtualTabManager.statusLine(viewer);
    }

    public List<String> previewVirtualLayout(Player viewer) {
        return virtualTabManager.preview(viewer);
    }

    public void clearVirtualLayout(Player viewer) {
        virtualTabManager.clear(viewer);
    }

    public void clearAllVirtualLayouts() {
        virtualTabManager.clearAll();
    }

    public void refreshVirtualRealPlayerVisibility() {
        virtualTabManager.refreshRealPlayerVisibility();
    }

    private void start() {
        stopTask();
        if (!enabled) {
            KjobLogger.info("TabManager inactif - tab.yml disabled ou Kchat garde le header/footer.");
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, intervalTicks);
        KjobLogger.success("TabManager actif (" + nms + ") - interval=" + intervalTicks + " ticks.");
    }

    private boolean canOwnTab() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("hooks.kchat.enabled", true)) return true;
        Plugin kchat = Bukkit.getPluginManager().getPlugin("Kchat");
        if (kchat == null) return true;
        return plugin.getConfigManager().getMainConfig().getBoolean("hooks.kchat.disable_tab_header_footer", true);
    }

    private void tick() {
        if (!enabled) return;
        TabSnapshot snapshot = TabSnapshot.capture(staffGroups);
        for (Player player : Bukkit.getOnlinePlayers()) {
            String renderedHeader = sectionsEnabled ? renderSections(player, snapshot, headerSections) : render(player, snapshot, header);
            String renderedFooter = sectionsEnabled ? renderSections(player, snapshot, footerSections) : render(player, snapshot, footer);
            send(player, renderedHeader, renderedFooter);
            updatePlayerListName(player, snapshot);
        }
    }

    private String render(Player player, TabSnapshot snapshot, String input) {
        String out = input == null ? "" : input;
        for (int i = 0; i < 3; i++) {
            out = replaceNative(player, snapshot, out);
        }
        if (applyPapiPerPlayer) out = applyPapi(player, out);
        return color(out);
    }

    private String renderSections(Player player, TabSnapshot snapshot, List<TabSection> sections) {
        List<String> lines = new ArrayList<String>();
        for (TabSection section : sections) {
            if (!section.shouldShow(plugin, player, snapshot)) continue;
            for (String line : section.lines) {
                lines.add(line);
            }
        }
        return render(player, snapshot, joinLines(lines));
    }

    List<String> renderVirtualLines(Player player, List<String> lines) {
        TabSnapshot snapshot = TabSnapshot.capture(staffGroups);
        List<String> rendered = new ArrayList<String>();
        if (lines == null) return rendered;
        for (String line : lines) {
            rendered.add(render(player, snapshot, line == null ? "" : line));
        }
        return rendered;
    }

    private String replaceNative(Player player, TabSnapshot snapshot, String text) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        String displayJob = data != null ? data.getDisplayJob() : null;
        JobDefinition displayDef = displayJob != null ? plugin.getJobRegistry().getJob(displayJob) : null;

        double balance = getBalance(player);
        String out = text
            .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .replace("%server_max_players%", String.valueOf(Bukkit.getMaxPlayers()))
            .replace("%player_name%", player.getName())
            .replace("%vault_balance%", moneyFormat.format(balance))
            .replace("%vault_balance_raw%", String.valueOf(balance))
            .replace("%rank_name%", getRankName(player))
            .replace("%staff_online%", snapshot.staffOnline)
            .replace("%staff_count%", String.valueOf(snapshot.staffCount))
            .replace("%staff_groups_inline%", snapshot.staffGroupsInline)
            .replace("%staff_groups_lines%", snapshot.staffGroupsLines)
            .replace("%kjob_display_job%", displayJob != null ? displayJob : "")
            .replace("%kjob_display_job_name%", displayDef != null ? displayDef.getDisplayName() : "")
            .replace("%kjob_active_jobs_inline%", data != null ? activeJobsInline(data) : "")
            .replace("%kjob_active_jobs_lines%", data != null ? activeJobsLines(data) : "")
            .replace("%kjob_slots%", data != null ? String.valueOf(data.getUnlockedSlots()) : "0")
            .replace("%kjob_unlocked_jobs%", data != null ? String.valueOf(countUnlockedJobs(data)) : "0")
            .replace("%kjob_global_level%", data != null ? String.valueOf(globalLevel(data)) : "0")
            .replace("%kfaction_name%", getFactionName(player))
            .replace("%kfaction_role%", getFactionRole(player))
            .replace("%kfaction_members%", getFactionMembers(player))
            .replace("%kfaction_members_lines%", getFactionMembersLines(player));

        for (Map.Entry<String, String> entry : snapshot.staffGroupValues.entrySet()) {
            out = out.replace("%staff_" + entry.getKey() + "_online%", entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : snapshot.staffGroupCounts.entrySet()) {
            out = out.replace("%staff_" + entry.getKey() + "_count%", String.valueOf(entry.getValue().intValue()));
        }

        if (data == null) return out;
        for (String jobId : plugin.getJobRegistry().getExpectedJobIds()) {
            JobDefinition def = plugin.getJobRegistry().getJob(jobId);
            int level = data.getLevel(jobId);
            int xp = data.getXP(jobId);
            int xpNext = def != null ? def.getXpForLevel(level) : 0;
            int percent = xpNext > 0 ? Math.min(100, (int) ((double) xp / xpNext * 100)) : 100;

            out = out
                .replace("%kjob_level_" + jobId + "%", String.valueOf(level))
                .replace("%kjob_xp_" + jobId + "%", String.valueOf(xp))
                .replace("%kjob_xp_next_" + jobId + "%", String.valueOf(xpNext))
                .replace("%kjob_percent_" + jobId + "%", String.valueOf(percent))
                .replace("%kjob_max_level_" + jobId + "%", def != null ? String.valueOf(def.getMaxLevel()) : "0");
        }
        return out;
    }

    private List<TabSection> loadSections(String path) {
        ConfigurationSection parent = plugin.getConfigManager().getTabConfig().getConfigurationSection(path);
        if (parent == null) return Collections.emptyList();
        List<TabSection> sections = new ArrayList<TabSection>();
        for (String id : parent.getKeys(false)) {
            ConfigurationSection section = parent.getConfigurationSection(id);
            if (section == null) continue;
            TabSection tabSection = TabSection.fromConfig(id, section);
            if (tabSection.lines.isEmpty()) {
                KjobLogger.warn("[TAB] Section " + path + "." + id + " sans lignes.");
            }
            if (!KNOWN_SECTION_CONDITIONS.contains(tabSection.condition) && !tabSection.condition.startsWith("staff_group_online:")) {
                KjobLogger.warn("[TAB] Section " + path + "." + id + " condition inconnue: " + tabSection.condition);
            }
            sections.add(tabSection);
        }
        return sections;
    }

    private List<TabStaffGroup> loadStaffGroups() {
        ConfigurationSection parent = plugin.getConfigManager().getTabConfig().getConfigurationSection("staff_groups");
        if (parent == null) {
            return Collections.singletonList(new TabStaffGroup("staff", "kjobsultimate.staff", "&b{player}", ", ", "Aucun", "&bStaff&8: &f{players}"));
        }

        List<TabStaffGroup> groups = new ArrayList<TabStaffGroup>();
        for (String id : parent.getKeys(false)) {
            ConfigurationSection section = parent.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            String permission = section.getString("permission", "");
            if (permission == null || permission.trim().isEmpty()) {
                KjobLogger.warn("[TAB] staff_groups." + id + ".permission vide, groupe ignore.");
                continue;
            }
            groups.add(new TabStaffGroup(
                id.toLowerCase(),
                permission,
                section.getString("format", "&b{player}"),
                section.getString("separator", ", "),
                section.getString("empty", "Aucun"),
                section.getString("line_format", "&b" + id + "&8: &f{players}")
            ));
        }
        return groups;
    }

    private String activeJobsInline(PlayerData data) {
        String format = plugin.getConfigManager().getTabConfig().getString("formats.active_job_inline", "&e{job}&7 Lv.&a{level}");
        String separator = plugin.getConfigManager().getTabConfig().getString("formats.active_job_separator", " &8| ");
        List<String> parts = new ArrayList<String>();
        for (String jobId : data.getSlotJobs().values()) {
            if (jobId == null || jobId.trim().isEmpty()) continue;
            JobDefinition def = plugin.getJobRegistry().getJob(jobId);
            if (def == null) continue;
            parts.add(formatJobLine(format, data, def));
        }
        if (parts.isEmpty()) return plugin.getConfigManager().getTabConfig().getString("formats.no_active_jobs", "&7Aucun job");
        return join(parts, separator);
    }

    private String activeJobsLines(PlayerData data) {
        String format = plugin.getConfigManager().getTabConfig().getString("formats.active_job_line", "&8- &e{job}&7 Lv.&a{level} &8(&f{percent}%&8)");
        List<String> parts = new ArrayList<String>();
        for (String jobId : data.getSlotJobs().values()) {
            if (jobId == null || jobId.trim().isEmpty()) continue;
            JobDefinition def = plugin.getJobRegistry().getJob(jobId);
            if (def == null) continue;
            parts.add(formatJobLine(format, data, def));
        }
        if (parts.isEmpty()) return plugin.getConfigManager().getTabConfig().getString("formats.no_active_jobs", "&7Aucun job");
        return joinLines(parts);
    }

    private String formatJobLine(String format, PlayerData data, JobDefinition def) {
        String jobId = def.getId();
        int level = data.getLevel(jobId);
        int xp = data.getXP(jobId);
        int xpNext = def.getXpForLevel(level);
        int percent = xpNext > 0 ? Math.min(100, (int) ((double) xp / xpNext * 100)) : 100;
        return format
            .replace("{job}", def.getDisplayName())
            .replace("{job_id}", jobId)
            .replace("{level}", String.valueOf(level))
            .replace("{max_level}", String.valueOf(def.getMaxLevel()))
            .replace("{xp}", String.valueOf(xp))
            .replace("{xp_next}", String.valueOf(xpNext))
            .replace("{percent}", String.valueOf(percent));
    }

    private double getBalance(Player player) {
        if (plugin.getHookManager() == null || plugin.getHookManager().getVaultHook() == null) return 0D;
        VaultHook vault = plugin.getHookManager().getVaultHook();
        return vault.getBalance(player.getName());
    }

    private String getRankName(Player player) {
        if (player == null) return "Joueur";
        if (player.hasPermission("kjobsultimate.admin")) return "Gerant";
        if (player.hasPermission("kjobsultimate.staff.modo")) return "Modo";
        if (player.hasPermission("kjobsultimate.staff.helper")) return "Helper";
        return "Joueur";
    }

    private String getFactionName(Player player) {
        if (plugin.getHookManager() == null || !plugin.getHookManager().isKfactionEnabled()) return "Aucune";
        return plugin.getHookManager().getKfactionHook().getFactionName(player, "Aucune");
    }

    private String getFactionRole(Player player) {
        if (plugin.getHookManager() == null || !plugin.getHookManager().isKfactionEnabled()) return "-";
        return plugin.getHookManager().getKfactionHook().getFactionRole(player, "-");
    }

    private String getFactionMembers(Player player) {
        if (plugin.getHookManager() == null || !plugin.getHookManager().isKfactionEnabled()) return "0";
        return String.valueOf(plugin.getHookManager().getKfactionHook().getFactionMembers(player));
    }

    private String getFactionMembersLines(Player player) {
        int limit = Math.max(1, plugin.getConfigManager().getTabConfig().getInt("virtual_layout.faction_members_limit", 8));
        if (plugin.getHookManager() == null || !plugin.getHookManager().isKfactionEnabled()) return "Aucune faction";
        return plugin.getHookManager().getKfactionHook().getFactionMembersLines(player, limit, "Aucun membre");
    }

    private void updatePlayerListName(Player player, TabSnapshot snapshot) {
        if (!playerListNameEnabled) return;
        String format = player.hasPermission(playerListNameStaffPermission)
            ? playerListNameStaffFormat
            : playerListNameFormat;
        String value = render(player, snapshot, format);
        if (playerListNameTruncate && value.length() > playerListNameMaxLength) {
            value = truncateLegacy(value, playerListNameMaxLength);
        }
        try {
            player.setPlayerListName(value);
        } catch (IllegalArgumentException ex) {
            KjobLogger.warn("[TAB] PlayerListName invalide pour " + player.getName() + ": " + ex.getMessage());
            try {
                player.setPlayerListName(player.getName());
            } catch (IllegalArgumentException ignored) {
                // Name vanilla valide en principe; ignore si le fork refuse tout de meme.
            }
        }
    }

    private String truncateLegacy(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        String cut = value.substring(0, maxLength);
        if (cut.endsWith("\u00A7")) cut = cut.substring(0, cut.length() - 1);
        String lastColors = ChatColor.getLastColors(cut);
        if (lastColors.length() > 0 && cut.length() + lastColors.length() <= maxLength) {
            cut = lastColors + cut;
        }
        return cut;
    }

    private void resetPlayerListNames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.setPlayerListName(player.getName());
            } catch (IllegalArgumentException ignored) {
                // Ignore on shutdown/reload cleanup.
            }
        }
    }

    private int countUnlockedJobs(PlayerData data) {
        int count = 0;
        for (String jobId : data.getSlotJobs().values()) {
            if (jobId != null && !jobId.trim().isEmpty()) count++;
        }
        return count;
    }

    private int globalLevel(PlayerData data) {
        int total = 0;
        for (String jobId : data.getSlotJobs().values()) {
            if (jobId != null) total += data.getLevel(jobId);
        }
        return total;
    }

    private String applyPapi(Player player, String text) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return text;
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders = papiClass.getMethod("setPlaceholders", Player.class, String.class);
            Object result = setPlaceholders.invoke(null, player, text);
            return result instanceof String ? (String) result : text;
        } catch (Throwable ignored) {
            return text;
        }
    }

    private void send(Player player, String headerText, String footerText) {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nms + ".entity.CraftPlayer");
            Class<?> packetClass = Class.forName("net.minecraft.server." + nms + ".Packet");
            Class<?> headerFooterClass = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutPlayerListHeaderFooter");
            Class<?> chatBaseClass = Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent");
            Class<?> chatSerClass = Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent$ChatSerializer");

            Object headerComp = chatSerClass.getMethod("a", String.class)
                .invoke(null, "{\"text\":\"" + escapeJson(headerText) + "\"}");
            Object footerComp = chatSerClass.getMethod("a", String.class)
                .invoke(null, "{\"text\":\"" + escapeJson(footerText) + "\"}");

            Object packet;
            try {
                packet = headerFooterClass.getConstructor(chatBaseClass).newInstance(headerComp);
            } catch (NoSuchMethodException ignored) {
                packet = headerFooterClass.getConstructor().newInstance();
            }

            setComponentField(headerFooterClass, packet, "a", headerComp);
            setComponentField(headerFooterClass, packet, "b", footerComp);

            Object handle = craftPlayerClass.getMethod("getHandle").invoke(player);
            Object conn = handle.getClass().getField("playerConnection").get(handle);
            conn.getClass().getMethod("sendPacket", packetClass).invoke(conn, packet);
        } catch (Exception e) {
            Throwable cause = (e instanceof InvocationTargetException && e.getCause() != null) ? e.getCause() : e;
            KjobLogger.warn("[TAB] NMS " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    private void setComponentField(Class<?> packetClass, Object packet, String fieldName, Object value) throws Exception {
        Field field = packetClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(packet, value);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String color(String text) {
        return text == null ? "" : text.replace("&", "\u00A7");
    }

    private String joinLines(List<String> lines) {
        return join(lines, "\n");
    }

    private String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(separator);
            builder.append(value == null ? "" : value);
        }
        return builder.toString();
    }

    private static final class TabSection {
        private final String id;
        private final boolean enabled;
        private final String condition;
        private final String permission;
        private final List<String> lines;

        private TabSection(String id, boolean enabled, String condition, String permission, List<String> lines) {
            this.id = id;
            this.enabled = enabled;
            this.condition = condition;
            this.permission = permission;
            this.lines = lines;
        }

        private static TabSection fromConfig(String id, ConfigurationSection section) {
            List<String> lines = section.getStringList("lines");
            if (lines.isEmpty() && section.contains("line")) lines = Collections.singletonList(section.getString("line", ""));
            return new TabSection(
                id,
                section.getBoolean("enabled", true),
                section.getString("condition", "always").toLowerCase(),
                section.getString("permission", ""),
                lines
            );
        }

        private boolean shouldShow(KjobUltimate plugin, Player player, TabSnapshot snapshot) {
            if (!enabled) return false;
            if (permission != null && !permission.trim().isEmpty() && !player.hasPermission(permission)) return false;
            if ("always".equals(condition)) return true;
            if ("staff_online".equals(condition)) return snapshot.staffCount > 0;
            if ("no_staff_online".equals(condition)) return snapshot.staffCount == 0;
            if (condition.startsWith("staff_group_online:")) {
                String group = condition.substring("staff_group_online:".length()).trim().toLowerCase();
                Integer count = snapshot.staffGroupCounts.get(group);
                return count != null && count.intValue() > 0;
            }
            if ("vault".equals(condition)) return plugin.getHookManager() != null && plugin.getHookManager().getVaultHook() != null;
            if ("has_jobs".equals(condition)) {
                PlayerData data = plugin.getPlayerDataManager().get(player);
                return data != null && !plugin.getSlotManager().getActiveJobs(data).isEmpty();
            }
            if ("no_jobs".equals(condition)) {
                PlayerData data = plugin.getPlayerDataManager().get(player);
                return data == null || plugin.getSlotManager().getActiveJobs(data).isEmpty();
            }
            return true;
        }
    }

    private static final class TabStaffGroup {
        private final String id;
        private final String permission;
        private final String format;
        private final String separator;
        private final String empty;
        private final String lineFormat;

        private TabStaffGroup(String id, String permission, String format, String separator, String empty, String lineFormat) {
            this.id = id;
            this.permission = permission;
            this.format = format;
            this.separator = separator;
            this.empty = empty;
            this.lineFormat = lineFormat;
        }

        private String renderName(Player player) {
            return format
                .replace("{player}", player.getName())
                .replace("%player_name%", player.getName());
        }

        private String renderLine(String players, int count) {
            return lineFormat
                .replace("{players}", players)
                .replace("{count}", String.valueOf(count));
        }
    }

    private static final class TabSnapshot {
        private final String staffOnline;
        private final int staffCount;
        private final Map<String, String> staffGroupValues;
        private final Map<String, Integer> staffGroupCounts;
        private final String staffGroupsInline;
        private final String staffGroupsLines;

        private TabSnapshot(String staffOnline, int staffCount, Map<String, String> staffGroupValues,
                            Map<String, Integer> staffGroupCounts, String staffGroupsInline, String staffGroupsLines) {
            this.staffOnline = staffOnline;
            this.staffCount = staffCount;
            this.staffGroupValues = staffGroupValues;
            this.staffGroupCounts = staffGroupCounts;
            this.staffGroupsInline = staffGroupsInline;
            this.staffGroupsLines = staffGroupsLines;
        }

        private static TabSnapshot capture(List<TabStaffGroup> groups) {
            Map<String, String> groupValues = new LinkedHashMap<String, String>();
            Map<String, Integer> groupCounts = new LinkedHashMap<String, Integer>();
            List<String> allNames = new ArrayList<String>();
            List<String> lines = new ArrayList<String>();
            Set<String> assigned = new HashSet<String>();

            if (groups == null || groups.isEmpty()) {
                groups = Collections.singletonList(new TabStaffGroup("staff", "kjobsultimate.staff", "&b{player}", ", ", "Aucun", "&bStaff&8: &f{players}"));
            }

            for (TabStaffGroup group : groups) {
                List<String> names = new ArrayList<String>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (assigned.contains(online.getName())) continue;
                    if (online.hasPermission(group.permission)) {
                        names.add(group.renderName(online));
                        assigned.add(online.getName());
                        if (!allNames.contains(online.getName())) allNames.add(online.getName());
                    }
                }
                String players = names.isEmpty() ? group.empty : joinStatic(names, group.separator);
                groupValues.put(group.id, players);
                groupCounts.put(group.id, Integer.valueOf(names.size()));
                lines.add(group.renderLine(players, names.size()));
            }

            String staffOnline = allNames.isEmpty() ? "Aucun" : joinStatic(allNames, ", ");
            return new TabSnapshot(staffOnline, allNames.size(), groupValues, groupCounts,
                joinStatic(lines, " &8| "), joinStatic(lines, "\n"));
        }

        private static String joinStatic(List<String> values, String separator) {
            StringBuilder builder = new StringBuilder();
            for (String value : values) {
                if (builder.length() > 0) builder.append(separator);
                builder.append(value == null ? "" : value);
            }
            return builder.toString();
        }
    }
}
