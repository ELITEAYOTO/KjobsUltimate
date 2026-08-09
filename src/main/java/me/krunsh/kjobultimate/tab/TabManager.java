package me.krunsh.kjobultimate.tab;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.hooks.VaultHook;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;
import me.krunsh.kjobultimate.util.LevelUtil;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Gestion du header/footer, des noms de joueurs et des placeholders natifs
 * de la tablist Minecraft 1.8.
 *
 * Les calculs de progression de métier sont centralisés dans LevelUtil afin
 * que la tablist, le HUD, les GUI et PlaceholderAPI affichent exactement les
 * mêmes valeurs.
 */
public final class TabManager {

    private static final Set<String> KNOWN_SECTION_CONDITIONS =
        Collections.unmodifiableSet(
            new HashSet<String>(
                Arrays.asList(
                    "always",
                    "has_jobs",
                    "no_jobs",
                    "staff_online",
                    "no_staff_online",
                    "vault")));

    private final KjobUltimate plugin;
    private final String nms;
    private final VirtualTabManager virtualTabManager;
    private final DecimalFormat moneyFormat =
        new DecimalFormat("#,##0.##");

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

    private List<TabSection> headerSections =
        Collections.emptyList();

    private List<TabSection> footerSections =
        Collections.emptyList();

    private List<TabStaffGroup> staffGroups =
        Collections.emptyList();

    public TabManager(KjobUltimate plugin) {
        this.plugin = Objects.requireNonNull(
            plugin,
            "KjobUltimate ne peut pas être null.");

        String packageName =
            Bukkit.getServer()
                .getClass()
                .getPackage()
                .getName();

        this.nms =
            packageName.substring(
                packageName.lastIndexOf('.') + 1);

        this.virtualTabManager =
            new VirtualTabManager(
                plugin,
                this,
                nms);

        reload();
    }

    /**
     * Recharge tab.yml et redémarre les tâches associées.
     */
    public void reload() {
        ConfigurationSection config =
            plugin.getConfigManager()
                .getTabConfig();

        boolean wasPlayerListNameEnabled =
            playerListNameEnabled;

        enabled =
            config.getBoolean("enabled", true)
                && canOwnTab();

        intervalTicks =
            Math.max(
                20L,
                config.getLong(
                    "update_interval_ticks",
                    40L));

        header =
            nonNull(
                config.getString(
                    "header",
                    ""),
                "");

        footer =
            nonNull(
                config.getString(
                    "footer",
                    ""),
                "");

        sectionsEnabled =
            config.getBoolean(
                "sections.enabled",
                false);

        applyPapiPerPlayer =
            config.getBoolean(
                "placeholderapi_per_player",
                true);

        playerListNameEnabled =
            config.getBoolean(
                "player_list_name.enabled",
                false);

        playerListNameTruncate =
            config.getBoolean(
                "player_list_name.truncate_to_legacy_limit",
                true);

        playerListNameMaxLength =
            Math.max(
                1,
                config.getInt(
                    "player_list_name.max_length",
                    16));

        playerListNameFormat =
            nonNull(
                config.getString(
                    "player_list_name.format",
                    "&7%player_name%"),
                "&7%player_name%");

        playerListNameStaffFormat =
            nonNull(
                config.getString(
                    "player_list_name.staff_format",
                    "&b%player_name%"),
                "&b%player_name%");

        playerListNameStaffPermission =
            nonNull(
                config.getString(
                    "player_list_name.staff_permission",
                    "kjobsultimate.staff"),
                "kjobsultimate.staff");

        staffGroups =
            loadStaffGroups();

        headerSections =
            loadSections(
                "sections.header");

        footerSections =
            loadSections(
                "sections.footer");

        if (wasPlayerListNameEnabled
                && !playerListNameEnabled) {

            resetPlayerListNames();
        }

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
            KjobLogger.info(
                "TabManager inactif - tab.yml désactivé "
                    + "ou Kchat garde le header/footer.");
            return;
        }

        task =
            Bukkit.getScheduler().runTaskTimer(
                plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        tick();
                    }
                },
                20L,
                intervalTicks);

        KjobLogger.success(
            "TabManager actif ("
                + nms
                + ") - interval="
                + intervalTicks
                + " ticks.");
    }

    private boolean canOwnTab() {
        ConfigurationSection main =
            plugin.getConfigManager()
                .getMainConfig();

        if (!main.getBoolean(
                "hooks.kchat.enabled",
                true)) {

            return true;
        }

        Plugin kchat =
            Bukkit.getPluginManager()
                .getPlugin("Kchat");

        if (kchat == null
                || !kchat.isEnabled()) {

            return true;
        }

        return main.getBoolean(
            "hooks.kchat.disable_tab_header_footer",
            true);
    }

    private void tick() {
        if (!enabled) {
            return;
        }

        TabSnapshot snapshot =
            TabSnapshot.capture(
                staffGroups);

        for (Player player
                : Bukkit.getOnlinePlayers()) {

            if (player == null
                    || !player.isOnline()) {
                continue;
            }

            String renderedHeader =
                sectionsEnabled
                    ? renderSections(
                        player,
                        snapshot,
                        headerSections)
                    : render(
                        player,
                        snapshot,
                        header);

            String renderedFooter =
                sectionsEnabled
                    ? renderSections(
                        player,
                        snapshot,
                        footerSections)
                    : render(
                        player,
                        snapshot,
                        footer);

            send(
                player,
                renderedHeader,
                renderedFooter);

            updatePlayerListName(
                player,
                snapshot);
        }
    }

    private String render(
            Player player,
            TabSnapshot snapshot,
            String input) {

        String output =
            nonNull(input, "");

        /*
         * Trois passes sont conservées afin qu'un format injecté par un
         * placeholder puisse lui-même contenir un placeholder natif.
         */
        for (int pass = 0;
                pass < 3;
                pass++) {

            output =
                replaceNative(
                    player,
                    snapshot,
                    output);
        }

        if (applyPapiPerPlayer) {
            output =
                applyPapi(
                    player,
                    output);
        }

        return color(output);
    }

    private String renderSections(
            Player player,
            TabSnapshot snapshot,
            List<TabSection> sections) {

        List<String> lines =
            new ArrayList<String>();

        if (sections != null) {
            for (TabSection section : sections) {
                if (section == null
                        || !section.shouldShow(
                            plugin,
                            player,
                            snapshot)) {
                    continue;
                }

                lines.addAll(section.lines);
            }
        }

        return render(
            player,
            snapshot,
            joinLines(lines));
    }

    /**
     * Appelé par VirtualTabManager pour rendre ses cellules.
     */
    List<String> renderVirtualLines(
            Player player,
            List<String> lines) {

        TabSnapshot snapshot =
            TabSnapshot.capture(
                staffGroups);

        List<String> rendered =
            new ArrayList<String>();

        if (lines == null) {
            return rendered;
        }

        for (String line : lines) {
            rendered.add(
                render(
                    player,
                    snapshot,
                    nonNull(line, "")));
        }

        return rendered;
    }

    private String replaceNative(
            Player player,
            TabSnapshot snapshot,
            String text) {

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(player);

        String displayJob =
            data == null
                ? null
                : data.getDisplayJob();

        JobDefinition displayDefinition =
            displayJob == null
                ? null
                : plugin.getJobRegistry()
                    .getJob(displayJob);

        double balance =
            getBalance(player);

        String output =
            nonNull(text, "")
                .replace(
                    "%server_online%",
                    String.valueOf(
                        Bukkit.getOnlinePlayers()
                            .size()))
                .replace(
                    "%server_max_players%",
                    String.valueOf(
                        Bukkit.getMaxPlayers()))
                .replace(
                    "%player_name%",
                    player.getName())
                .replace(
                    "%vault_balance%",
                    moneyFormat.format(balance))
                .replace(
                    "%vault_balance_raw%",
                    String.valueOf(balance))
                .replace(
                    "%rank_name%",
                    getRankName(player))
                .replace(
                    "%staff_online%",
                    snapshot.staffOnline)
                .replace(
                    "%staff_count%",
                    String.valueOf(
                        snapshot.staffCount))
                .replace(
                    "%staff_groups_inline%",
                    snapshot.staffGroupsInline)
                .replace(
                    "%staff_groups_lines%",
                    snapshot.staffGroupsLines)
                .replace(
                    "%kjob_display_job%",
                    displayJob == null
                        ? ""
                        : displayJob)
                .replace(
                    "%kjob_display_job_name%",
                    displayDefinition == null
                        ? ""
                        : displayDefinition
                            .getDisplayName())
                .replace(
                    "%kjob_active_jobs_inline%",
                    data == null
                        ? ""
                        : activeJobsInline(data))
                .replace(
                    "%kjob_active_jobs_lines%",
                    data == null
                        ? ""
                        : activeJobsLines(data))
                .replace(
                    "%kjob_slots%",
                    data == null
                        ? "0"
                        : String.valueOf(
                            Math.max(
                                0,
                                data.getUnlockedSlots())))
                .replace(
                    "%kjob_unlocked_jobs%",
                    data == null
                        ? "0"
                        : String.valueOf(
                            countUnlockedJobs(data)))
                .replace(
                    "%kjob_global_level%",
                    data == null
                        ? "0"
                        : String.valueOf(
                            globalLevel(data)))
                .replace(
                    "%kfaction_name%",
                    getFactionName(player))
                .replace(
                    "%kfaction_role%",
                    getFactionRole(player))
                .replace(
                    "%kfaction_members%",
                    getFactionMembers(player))
                .replace(
                    "%kfaction_members_lines%",
                    getFactionMembersLines(player));

        for (Map.Entry<String, String> entry
                : snapshot.staffGroupValues
                    .entrySet()) {

            output =
                output.replace(
                    "%staff_"
                        + entry.getKey()
                        + "_online%",
                    entry.getValue());
        }

        for (Map.Entry<String, Integer> entry
                : snapshot.staffGroupCounts
                    .entrySet()) {

            output =
                output.replace(
                    "%staff_"
                        + entry.getKey()
                        + "_count%",
                    String.valueOf(
                        entry.getValue()
                            .intValue()));
        }

        if (data == null) {
            return output;
        }

        for (String jobId
                : plugin.getJobRegistry()
                    .getExpectedJobIds()) {

            JobDefinition job =
                plugin.getJobRegistry()
                    .getJob(jobId);

            if (job == null) {
                output =
                    replaceUnknownJobPlaceholders(
                        output,
                        data,
                        jobId);
                continue;
            }

            int level =
                sanitizeLevel(
                    data.getLevel(jobId),
                    job);

            int xp =
                level >= job.getMaxLevel()
                    ? 0
                    : LevelUtil.getCurrentLevelXp(
                        data,
                        job);

            int xpNext =
                LevelUtil.getRequiredXpForNextLevel(
                    data,
                    job);

            int percent =
                LevelUtil.getProgressPercentage(
                    data,
                    job);

            output =
                output
                    .replace(
                        "%kjob_level_"
                            + jobId
                            + "%",
                        String.valueOf(level))
                    .replace(
                        "%kjob_xp_"
                            + jobId
                            + "%",
                        String.valueOf(xp))
                    .replace(
                        "%kjob_xp_next_"
                            + jobId
                            + "%",
                        String.valueOf(
                            xpNext))
                    .replace(
                        "%kjob_percent_"
                            + jobId
                            + "%",
                        String.valueOf(
                            percent))
                    .replace(
                        "%kjob_max_level_"
                            + jobId
                            + "%",
                        String.valueOf(
                            job.getMaxLevel()));
        }

        return output;
    }

    private String replaceUnknownJobPlaceholders(
            String text,
            PlayerData data,
            String jobId) {

        int level =
            Math.max(
                0,
                data.getLevel(jobId));

        int xp =
            Math.max(
                0,
                data.getXP(jobId));

        return text
            .replace(
                "%kjob_level_"
                    + jobId
                    + "%",
                String.valueOf(level))
            .replace(
                "%kjob_xp_"
                    + jobId
                    + "%",
                String.valueOf(xp))
            .replace(
                "%kjob_xp_next_"
                    + jobId
                    + "%",
                "0")
            .replace(
                "%kjob_percent_"
                    + jobId
                    + "%",
                "0")
            .replace(
                "%kjob_max_level_"
                    + jobId
                    + "%",
                "0");
    }

    private List<TabSection> loadSections(
            String path) {

        ConfigurationSection parent =
            plugin.getConfigManager()
                .getTabConfig()
                .getConfigurationSection(path);

        if (parent == null) {
            return Collections.emptyList();
        }

        List<TabSection> sections =
            new ArrayList<TabSection>();

        for (String id
                : parent.getKeys(false)) {

            ConfigurationSection section =
                parent.getConfigurationSection(id);

            if (section == null) {
                continue;
            }

            TabSection tabSection =
                TabSection.fromConfig(
                    id,
                    section);

            if (tabSection.lines.isEmpty()) {
                KjobLogger.warn(
                    "[TAB] Section "
                        + path
                        + "."
                        + id
                        + " sans lignes.");
            }

            if (!KNOWN_SECTION_CONDITIONS
                    .contains(
                        tabSection.condition)
                    && !tabSection.condition
                        .startsWith(
                            "staff_group_online:")) {

                KjobLogger.warn(
                    "[TAB] Section "
                        + path
                        + "."
                        + id
                        + " condition inconnue : "
                        + tabSection.condition);
            }

            sections.add(tabSection);
        }

        return Collections.unmodifiableList(
            sections);
    }

    private List<TabStaffGroup> loadStaffGroups() {
        ConfigurationSection parent =
            plugin.getConfigManager()
                .getTabConfig()
                .getConfigurationSection(
                    "staff_groups");

        if (parent == null) {
            return Collections.singletonList(
                defaultStaffGroup());
        }

        List<TabStaffGroup> groups =
            new ArrayList<TabStaffGroup>();

        for (String id
                : parent.getKeys(false)) {

            ConfigurationSection section =
                parent.getConfigurationSection(id);

            if (section == null
                    || !section.getBoolean(
                        "enabled",
                        true)) {
                continue;
            }

            String permission =
                section.getString(
                    "permission",
                    "");

            if (isBlank(permission)) {
                KjobLogger.warn(
                    "[TAB] staff_groups."
                        + id
                        + ".permission vide, groupe ignoré.");
                continue;
            }

            groups.add(
                new TabStaffGroup(
                    id.toLowerCase(
                        Locale.ROOT),
                    permission,
                    nonNull(
                        section.getString(
                            "format",
                            "&b{player}"),
                        "&b{player}"),
                    nonNull(
                        section.getString(
                            "separator",
                            ", "),
                        ", "),
                    nonNull(
                        section.getString(
                            "empty",
                            "Aucun"),
                        "Aucun"),
                    nonNull(
                        section.getString(
                            "line_format",
                            "&b"
                                + id
                                + "&8: &f{players}"),
                        "&b"
                            + id
                            + "&8: &f{players}")));
        }

        if (groups.isEmpty()) {
            groups.add(
                defaultStaffGroup());
        }

        return Collections.unmodifiableList(
            groups);
    }

    private static TabStaffGroup defaultStaffGroup() {
        return new TabStaffGroup(
            "staff",
            "kjobsultimate.staff",
            "&b{player}",
            ", ",
            "Aucun",
            "&bStaff&8: &f{players}");
    }

    private String activeJobsInline(
            PlayerData data) {

        ConfigurationSection config =
            plugin.getConfigManager()
                .getTabConfig();

        String format =
            nonNull(
                config.getString(
                    "formats.active_job_inline",
                    "&e{job}&7 Lv.&a{level}"),
                "&e{job}&7 Lv.&a{level}");

        String separator =
            nonNull(
                config.getString(
                    "formats.active_job_separator",
                    " &8| "),
                " &8| ");

        List<String> parts =
            buildActiveJobLines(
                data,
                format);

        if (parts.isEmpty()) {
            return nonNull(
                config.getString(
                    "formats.no_active_jobs",
                    "&7Aucun job"),
                "&7Aucun job");
        }

        return join(
            parts,
            separator);
    }

    private String activeJobsLines(
            PlayerData data) {

        ConfigurationSection config =
            plugin.getConfigManager()
                .getTabConfig();

        String format =
            nonNull(
                config.getString(
                    "formats.active_job_line",
                    "&8- &e{job}&7 Lv.&a{level} "
                        + "&8(&f{percent}%&8)"),
                "&8- &e{job}&7 Lv.&a{level} "
                    + "&8(&f{percent}%&8)");

        List<String> parts =
            buildActiveJobLines(
                data,
                format);

        if (parts.isEmpty()) {
            return nonNull(
                config.getString(
                    "formats.no_active_jobs",
                    "&7Aucun job"),
                "&7Aucun job");
        }

        return joinLines(parts);
    }

    private List<String> buildActiveJobLines(
            PlayerData data,
            String format) {

        List<String> parts =
            new ArrayList<String>();

        /*
         * Évite un doublon d'affichage si une donnée ancienne contient le même
         * métier dans plusieurs slots.
         */
        Set<String> seen =
            new LinkedHashSet<String>();

        for (String rawJobId
                : data.getSlotJobs()
                    .values()) {

            if (isBlank(rawJobId)) {
                continue;
            }

            String jobId =
                rawJobId.trim()
                    .toLowerCase(
                        Locale.ROOT);

            if (!seen.add(jobId)) {
                continue;
            }

            JobDefinition job =
                plugin.getJobRegistry()
                    .getJob(jobId);

            if (job == null) {
                continue;
            }

            parts.add(
                formatJobLine(
                    format,
                    data,
                    job));
        }

        return parts;
    }

    private String formatJobLine(
            String format,
            PlayerData data,
            JobDefinition job) {

        String jobId =
            job.getId();

        int level =
            sanitizeLevel(
                data.getLevel(jobId),
                job);

        int xp =
            level >= job.getMaxLevel()
                ? 0
                : LevelUtil.getCurrentLevelXp(
                    data,
                    job);

        int xpNext =
            LevelUtil.getRequiredXpForNextLevel(
                data,
                job);

        int percent =
            LevelUtil.getProgressPercentage(
                data,
                job);

        return nonNull(format, "")
            .replace(
                "{job}",
                job.getDisplayName())
            .replace(
                "{job_id}",
                jobId)
            .replace(
                "{level}",
                String.valueOf(level))
            .replace(
                "{max_level}",
                String.valueOf(
                    job.getMaxLevel()))
            .replace(
                "{xp}",
                String.valueOf(xp))
            .replace(
                "{xp_next}",
                String.valueOf(
                    xpNext))
            .replace(
                "{percent}",
                String.valueOf(
                    percent));
    }

    private double getBalance(Player player) {
        if (plugin.getHookManager() == null
                || plugin.getHookManager()
                    .getVaultHook() == null) {

            return 0.0D;
        }

        VaultHook vault =
            plugin.getHookManager()
                .getVaultHook();

        try {
            double balance =
                vault.getBalance(
                    player.getName());

            if (Double.isNaN(balance)
                    || Double.isInfinite(balance)) {
                return 0.0D;
            }

            return balance;
        } catch (RuntimeException failure) {
            return 0.0D;
        }
    }

    private String getRankName(Player player) {
        if (player == null) {
            return "Joueur";
        }

        if (player.hasPermission(
                "kjobsultimate.admin")) {
            return "Gérant";
        }

        if (player.hasPermission(
                "kjobsultimate.staff.modo")) {
            return "Modo";
        }

        if (player.hasPermission(
                "kjobsultimate.staff.helper")) {
            return "Helper";
        }

        return "Joueur";
    }

    private String getFactionName(Player player) {
        if (!isKfactionAvailable()) {
            return "Aucune";
        }

        return plugin.getHookManager()
            .getKfactionHook()
            .getFactionName(
                player,
                "Aucune");
    }

    private String getFactionRole(Player player) {
        if (!isKfactionAvailable()) {
            return "-";
        }

        return plugin.getHookManager()
            .getKfactionHook()
            .getFactionRole(
                player,
                "-");
    }

    private String getFactionMembers(Player player) {
        if (!isKfactionAvailable()) {
            return "0";
        }

        return String.valueOf(
            Math.max(
                0,
                plugin.getHookManager()
                    .getKfactionHook()
                    .getFactionMembers(
                        player)));
    }

    private String getFactionMembersLines(
            Player player) {

        int limit =
            Math.max(
                1,
                plugin.getConfigManager()
                    .getTabConfig()
                    .getInt(
                        "virtual_layout.faction_members_limit",
                        8));

        if (!isKfactionAvailable()) {
            return "Aucune faction";
        }

        return plugin.getHookManager()
            .getKfactionHook()
            .getFactionMembersLines(
                player,
                limit,
                "Aucun membre");
    }

    private boolean isKfactionAvailable() {
        return plugin.getHookManager() != null
            && plugin.getHookManager()
                .isKfactionEnabled()
            && plugin.getHookManager()
                .getKfactionHook() != null;
    }

    private void updatePlayerListName(
            Player player,
            TabSnapshot snapshot) {

        if (!playerListNameEnabled) {
            return;
        }

        String format =
            player.hasPermission(
                playerListNameStaffPermission)
                ? playerListNameStaffFormat
                : playerListNameFormat;

        String value =
            render(
                player,
                snapshot,
                format);

        if (playerListNameTruncate
                && value.length()
                    > playerListNameMaxLength) {

            value =
                truncateLegacy(
                    value,
                    playerListNameMaxLength);
        }

        try {
            player.setPlayerListName(value);
        } catch (IllegalArgumentException failure) {
            KjobLogger.warn(
                "[TAB] PlayerListName invalide pour "
                    + player.getName()
                    + " : "
                    + failure.getMessage());

            try {
                player.setPlayerListName(
                    truncateLegacy(
                        player.getName(),
                        playerListNameMaxLength));
            } catch (IllegalArgumentException ignored) {
                // Le fork refuse également le nom vanilla.
            }
        }
    }

    /**
     * Coupe une chaîne legacy sans laisser un caractère § orphelin.
     */
    private String truncateLegacy(
            String value,
            int maxLength) {

        if (value == null
                || value.length() <= maxLength) {
            return value;
        }

        int safeMax =
            Math.max(
                1,
                maxLength);

        String cut =
            value.substring(
                0,
                Math.min(
                    safeMax,
                    value.length()));

        if (cut.endsWith(
                String.valueOf(
                    ChatColor.COLOR_CHAR))) {

            cut =
                cut.substring(
                    0,
                    cut.length() - 1);
        }

        return cut;
    }

    private void resetPlayerListNames() {
        for (Player player
                : Bukkit.getOnlinePlayers()) {

            try {
                player.setPlayerListName(
                    player.getName());
            } catch (IllegalArgumentException ignored) {
                // Nettoyage best-effort.
            }
        }
    }

    private int countUnlockedJobs(
            PlayerData data) {

        Set<String> jobs =
            new HashSet<String>();

        for (String rawJobId
                : data.getSlotJobs()
                    .values()) {

            if (!isBlank(rawJobId)) {
                jobs.add(
                    rawJobId.trim()
                        .toLowerCase(
                            Locale.ROOT));
            }
        }

        return jobs.size();
    }

    private int globalLevel(
            PlayerData data) {

        long total = 0L;
        Set<String> jobs =
            new HashSet<String>();

        for (String rawJobId
                : data.getSlotJobs()
                    .values()) {

            if (isBlank(rawJobId)) {
                continue;
            }

            String jobId =
                rawJobId.trim()
                    .toLowerCase(
                        Locale.ROOT);

            if (!jobs.add(jobId)) {
                continue;
            }

            JobDefinition job =
                plugin.getJobRegistry()
                    .getJob(jobId);

            int level =
                job == null
                    ? Math.max(
                        0,
                        data.getLevel(jobId))
                    : sanitizeLevel(
                        data.getLevel(jobId),
                        job);

            total += level;

            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }

        return (int) total;
    }

    private String applyPapi(
            Player player,
            String text) {

        Plugin papi =
            Bukkit.getPluginManager()
                .getPlugin("PlaceholderAPI");

        if (papi == null
                || !papi.isEnabled()) {
            return text;
        }

        try {
            Class<?> papiClass =
                Class.forName(
                    "me.clip.placeholderapi.PlaceholderAPI");

            Method setPlaceholders =
                papiClass.getMethod(
                    "setPlaceholders",
                    Player.class,
                    String.class);

            Object result =
                setPlaceholders.invoke(
                    null,
                    player,
                    text);

            return result instanceof String
                ? (String) result
                : text;
        } catch (Throwable failure) {
            return text;
        }
    }

    private void send(
            Player player,
            String headerText,
            String footerText) {

        if (player == null
                || !player.isOnline()) {
            return;
        }

        try {
            Class<?> craftPlayerClass =
                Class.forName(
                    "org.bukkit.craftbukkit."
                        + nms
                        + ".entity.CraftPlayer");

            Class<?> packetClass =
                Class.forName(
                    "net.minecraft.server."
                        + nms
                        + ".Packet");

            Class<?> headerFooterClass =
                Class.forName(
                    "net.minecraft.server."
                        + nms
                        + ".PacketPlayOutPlayerListHeaderFooter");

            Class<?> chatBaseClass =
                Class.forName(
                    "net.minecraft.server."
                        + nms
                        + ".IChatBaseComponent");

            Class<?> chatSerializerClass =
                Class.forName(
                    "net.minecraft.server."
                        + nms
                        + ".IChatBaseComponent$ChatSerializer");

            Object headerComponent =
                chatSerializerClass
                    .getMethod(
                        "a",
                        String.class)
                    .invoke(
                        null,
                        "{\"text\":\""
                            + escapeJson(
                                nonNull(
                                    headerText,
                                    ""))
                            + "\"}");

            Object footerComponent =
                chatSerializerClass
                    .getMethod(
                        "a",
                        String.class)
                    .invoke(
                        null,
                        "{\"text\":\""
                            + escapeJson(
                                nonNull(
                                    footerText,
                                    ""))
                            + "\"}");

            Object packet;

            try {
                packet =
                    headerFooterClass
                        .getConstructor(
                            chatBaseClass)
                        .newInstance(
                            headerComponent);
            } catch (NoSuchMethodException ignored) {
                packet =
                    headerFooterClass
                        .getConstructor()
                        .newInstance();
            }

            setComponentField(
                headerFooterClass,
                packet,
                "a",
                headerComponent);

            setComponentField(
                headerFooterClass,
                packet,
                "b",
                footerComponent);

            Object handle =
                craftPlayerClass
                    .getMethod("getHandle")
                    .invoke(player);

            Object connection =
                handle.getClass()
                    .getField(
                        "playerConnection")
                    .get(handle);

            connection.getClass()
                .getMethod(
                    "sendPacket",
                    packetClass)
                .invoke(
                    connection,
                    packet);
        } catch (Exception failure) {
            Throwable cause =
                unwrap(failure);

            KjobLogger.warn(
                "[TAB] NMS "
                    + cause.getClass()
                        .getSimpleName()
                    + " : "
                    + cause.getMessage());
        }
    }

    private void setComponentField(
            Class<?> packetClass,
            Object packet,
            String fieldName,
            Object value)
            throws Exception {

        Field field =
            packetClass.getDeclaredField(
                fieldName);

        field.setAccessible(true);
        field.set(packet, value);
    }

    private String escapeJson(
            String text) {

        String value =
            nonNull(text, "");

        StringBuilder escaped =
            new StringBuilder(
                value.length() + 16);

        for (int index = 0;
                index < value.length();
                index++) {

            char character =
                value.charAt(index);

            switch (character) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        escaped.append(
                            String.format(
                                Locale.ROOT,
                                "\\u%04x",
                                Integer.valueOf(
                                    character)));
                    } else {
                        escaped.append(
                            character);
                    }
                    break;
            }
        }

        return escaped.toString();
    }

    private String color(String text) {
        return nonNull(text, "")
            .replace(
                '&',
                ChatColor.COLOR_CHAR);
    }

    private String joinLines(
            List<String> lines) {

        return join(
            lines,
            "\n");
    }

    private String join(
            List<String> values,
            String separator) {

        StringBuilder builder =
            new StringBuilder();

        if (values == null) {
            return "";
        }

        String safeSeparator =
            nonNull(separator, "");

        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(
                    safeSeparator);
            }

            builder.append(
                nonNull(value, ""));
        }

        return builder.toString();
    }

    private static int sanitizeLevel(
            int level,
            JobDefinition job) {

        return Math.max(
            0,
            Math.min(
                job.getMaxLevel(),
                level));
    }

    private static Throwable unwrap(
            Exception failure) {

        if (failure
                instanceof InvocationTargetException
                && failure.getCause() != null) {

            return failure.getCause();
        }

        return failure;
    }

    private static String nonNull(
            String value,
            String fallback) {

        return value == null
            ? fallback
            : value;
    }

    private static boolean isBlank(
            String value) {

        return value == null
            || value.trim().isEmpty();
    }

    private static final class TabSection {

        @SuppressWarnings("unused")
        private final String id;

        private final boolean enabled;
        private final String condition;
        private final String permission;
        private final List<String> lines;

        private TabSection(
                String id,
                boolean enabled,
                String condition,
                String permission,
                List<String> lines) {

            this.id = id;
            this.enabled = enabled;
            this.condition = condition;
            this.permission = permission;
            this.lines =
                Collections.unmodifiableList(
                    new ArrayList<String>(
                        lines));
        }

        private static TabSection fromConfig(
                String id,
                ConfigurationSection section) {

            List<String> lines =
                new ArrayList<String>(
                    section.getStringList(
                        "lines"));

            if (lines.isEmpty()
                    && section.contains("line")) {

                lines.add(
                    nonNull(
                        section.getString(
                            "line",
                            ""),
                        ""));
            }

            return new TabSection(
                id,
                section.getBoolean(
                    "enabled",
                    true),
                nonNull(
                    section.getString(
                        "condition",
                        "always"),
                    "always")
                    .trim()
                    .toLowerCase(
                        Locale.ROOT),
                nonNull(
                    section.getString(
                        "permission",
                        ""),
                    ""),
                lines);
        }

        private boolean shouldShow(
                KjobUltimate plugin,
                Player player,
                TabSnapshot snapshot) {

            if (!enabled) {
                return false;
            }

            if (!isBlank(permission)
                    && !player.hasPermission(
                        permission)) {

                return false;
            }

            if ("always".equals(condition)) {
                return true;
            }

            if ("staff_online".equals(condition)) {
                return snapshot.staffCount > 0;
            }

            if ("no_staff_online".equals(condition)) {
                return snapshot.staffCount == 0;
            }

            if (condition.startsWith(
                    "staff_group_online:")) {

                String group =
                    condition.substring(
                        "staff_group_online:"
                            .length())
                        .trim()
                        .toLowerCase(
                            Locale.ROOT);

                Integer count =
                    snapshot.staffGroupCounts
                        .get(group);

                return count != null
                    && count.intValue() > 0;
            }

            if ("vault".equals(condition)) {
                return plugin.getHookManager() != null
                    && plugin.getHookManager()
                        .getVaultHook() != null;
            }

            if ("has_jobs".equals(condition)) {
                PlayerData data =
                    plugin.getPlayerDataManager()
                        .get(player);

                return data != null
                    && !plugin.getSlotManager()
                        .getActiveJobs(data)
                        .isEmpty();
            }

            if ("no_jobs".equals(condition)) {
                PlayerData data =
                    plugin.getPlayerDataManager()
                        .get(player);

                return data == null
                    || plugin.getSlotManager()
                        .getActiveJobs(data)
                        .isEmpty();
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

        private TabStaffGroup(
                String id,
                String permission,
                String format,
                String separator,
                String empty,
                String lineFormat) {

            this.id = id;
            this.permission = permission;
            this.format = format;
            this.separator = separator;
            this.empty = empty;
            this.lineFormat = lineFormat;
        }

        private String renderName(
                Player player) {

            return format
                .replace(
                    "{player}",
                    player.getName())
                .replace(
                    "%player_name%",
                    player.getName());
        }

        private String renderLine(
                String players,
                int count) {

            return lineFormat
                .replace(
                    "{players}",
                    players)
                .replace(
                    "{count}",
                    String.valueOf(count));
        }
    }

    private static final class TabSnapshot {

        private final String staffOnline;
        private final int staffCount;
        private final Map<String, String> staffGroupValues;
        private final Map<String, Integer> staffGroupCounts;
        private final String staffGroupsInline;
        private final String staffGroupsLines;

        private TabSnapshot(
                String staffOnline,
                int staffCount,
                Map<String, String> staffGroupValues,
                Map<String, Integer> staffGroupCounts,
                String staffGroupsInline,
                String staffGroupsLines) {

            this.staffOnline = staffOnline;
            this.staffCount = staffCount;
            this.staffGroupValues =
                Collections.unmodifiableMap(
                    new LinkedHashMap<String, String>(
                        staffGroupValues));

            this.staffGroupCounts =
                Collections.unmodifiableMap(
                    new LinkedHashMap<String, Integer>(
                        staffGroupCounts));

            this.staffGroupsInline =
                staffGroupsInline;

            this.staffGroupsLines =
                staffGroupsLines;
        }

        private static TabSnapshot capture(
                List<TabStaffGroup> configuredGroups) {

            List<TabStaffGroup> groups =
                configuredGroups == null
                        || configuredGroups.isEmpty()
                    ? Collections.singletonList(
                        defaultStaffGroup())
                    : configuredGroups;

            Map<String, String> groupValues =
                new LinkedHashMap<String, String>();

            Map<String, Integer> groupCounts =
                new LinkedHashMap<String, Integer>();

            List<String> allNames =
                new ArrayList<String>();

            List<String> lines =
                new ArrayList<String>();

            Set<String> assigned =
                new HashSet<String>();

            for (TabStaffGroup group : groups) {
                List<String> names =
                    new ArrayList<String>();

                for (Player online
                        : Bukkit.getOnlinePlayers()) {

                    String uniqueKey =
                        online.getUniqueId()
                            .toString();

                    if (assigned.contains(
                            uniqueKey)) {
                        continue;
                    }

                    if (online.hasPermission(
                            group.permission)) {

                        names.add(
                            group.renderName(
                                online));

                        assigned.add(
                            uniqueKey);

                        allNames.add(
                            online.getName());
                    }
                }

                String players =
                    names.isEmpty()
                        ? group.empty
                        : joinStatic(
                            names,
                            group.separator);

                groupValues.put(
                    group.id,
                    players);

                groupCounts.put(
                    group.id,
                    Integer.valueOf(
                        names.size()));

                lines.add(
                    group.renderLine(
                        players,
                        names.size()));
            }

            String staffOnline =
                allNames.isEmpty()
                    ? "Aucun"
                    : joinStatic(
                        allNames,
                        ", ");

            return new TabSnapshot(
                staffOnline,
                allNames.size(),
                groupValues,
                groupCounts,
                joinStatic(
                    lines,
                    " &8| "),
                joinStatic(
                    lines,
                    "\n"));
        }

        private static String joinStatic(
                List<String> values,
                String separator) {

            StringBuilder builder =
                new StringBuilder();

            for (String value : values) {
                if (builder.length() > 0) {
                    builder.append(separator);
                }

                builder.append(
                    value == null
                        ? ""
                        : value);
            }

            return builder.toString();
        }
    }
}