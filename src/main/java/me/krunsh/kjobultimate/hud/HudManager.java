package me.krunsh.kjobultimate.hud;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Achievement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.util.KjobLogger;
import me.krunsh.kjobultimate.util.LevelUtil;

/**
 * HUD V3.16.2.
 *
 * Performance :
 * - NMS/reflexion mis en cache dans HudNmsAdapter ;
 * - scheduler limite aux joueurs avec un affichage actuellement actif ;
 * - ActionBar rafraichie a faible frequence au lieu de 10 fois/seconde ;
 * - BossBar conserve son intervalle configurable.
 *
 * Wither 1.8 :
 * le client continue de produire des particules meme si le faux Wither est
 * invisible. En mode particle-safe, le HUD normal place donc le faux Wither
 * sous le joueur. /kjobs testhud respecte la position demandee et reste un
 * vrai diagnostic.
 */
public final class HudManager {

    private static final AtomicInteger FAKE_ENTITY_COUNTER =
        new AtomicInteger(800_000);

    private final KjobUltimate plugin;
    private final HudNmsAdapter nmsAdapter;

    private final Map<UUID, PlayerHudState> states =
        new ConcurrentHashMap<UUID, PlayerHudState>();

    private final ConcurrentHashMap<UUID, Boolean> active =
        new ConcurrentHashMap<UUID, Boolean>();

    private BukkitTask updateTask;

    // BossBar.
    private boolean bossEnabled;
    private long bossResetMs;
    private long bossUpdateMs;
    private double bossOffsetY;
    private double bossForwardOffset;
    private double bossFrontFarDistance;
    private double bossDragonDistance;
    private double bossDragonVerticalOffset;
    private double bossAutoDistance;
    private double bossAutoVerticalOffset;
    private double bossArmoredDistance;
    private double bossArmoredVerticalOffset;
    private double bossArmoredThreshold;
    private String bossPositionMode;
    private boolean bossFollowPlayer;
    private boolean bossInvisibleEntity;
    private double bossMinProgress;
    private String bossEntityType;
    private float bossEntityMaxHealth;
    private long bossTestDurationMs;
    private String bossTitleFormat;
    private String bossTitleFormatMax;

    // Correctif visuel Wither.
    private boolean hideWitherParticles;

    // ActionBar.
    private boolean abEnabled;
    private String abFormat;
    private String abAutoSellSuffix;
    private String abAutoSellOnlyFormat;
    private int abCurrencyDecimals;
    private long abDisplayMs;
    private long abAccumulationWindowMs;
    private long abRefreshMs;

    // Scheduler.
    private int schedulerIntervalTicks;

    // Metriques.
    private final AtomicLong xpSignals = new AtomicLong();
    private final AtomicLong levelPopupSignals = new AtomicLong();
    private final AtomicLong schedulerTicks = new AtomicLong();
    private final AtomicLong schedulerPlayerVisits = new AtomicLong();
    private final AtomicLong schedulerTotalNanos = new AtomicLong();
    private final AtomicLong schedulerMaxNanos = new AtomicLong();
    private final AtomicLong bossParticleSafePlacements = new AtomicLong();

    public HudManager(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                "plugin ne peut pas etre null."
            );
        }

        this.plugin = plugin;
        this.nmsAdapter =
            new HudNmsAdapter(plugin);

        reloadHudConfig();
        startUpdateTask();

        KjobLogger.success(
            "HudManager V3.16.2 actif ("
                + nmsAdapter.getNms()
                + ") - NMS cache="
                + (nmsAdapter.isAvailable() ? "ON" : "OFF")
                + ", active-only=ON"
                + ", bossbar="
                + bossEntityType
                + "@packet"
                + ("ENDER_DRAGON".equals(bossEntityType)
                    ? "(" + bossDragonDistance + "m/y" + bossDragonVerticalOffset + ")"
                    : "")
                + ", wither-particles="
                + (hideWitherParticles ? "SAFE" : "LEGACY")
                + "."
        );
    }

    public String getNMS() {
        return nmsAdapter.getNms();
    }

    public boolean isActionBarEnabled() {
        return abEnabled;
    }

    public boolean isBossBarEnabled() {
        return bossEnabled;
    }

    public long getBossUpdateMs() {
        return bossUpdateMs;
    }

    public int getTrackedPlayers() {
        return states.size();
    }

    public int getActivePlayers() {
        return active.size();
    }

    public boolean isNmsCacheReady() {
        return nmsAdapter.isAvailable();
    }

    public long getXpSignals() {
        return xpSignals.get();
    }

    public long getLevelPopupSignals() {
        return levelPopupSignals.get();
    }

    public long getSchedulerTicks() {
        return schedulerTicks.get();
    }

    public long getSchedulerPlayerVisits() {
        return schedulerPlayerVisits.get();
    }

    public double getAverageTickMillis() {
        long ticks = schedulerTicks.get();

        if (ticks <= 0L) {
            return 0D;
        }

        return (schedulerTotalNanos.get() / 1_000_000D) / ticks;
    }

    public double getMaxTickMillis() {
        return schedulerMaxNanos.get() / 1_000_000D;
    }

    public long getBossParticleSafePlacements() {
        return bossParticleSafePlacements.get();
    }

    public long getPacketCount() {
        return nmsAdapter.getPacketCount();
    }

    public long getActionBarPackets() {
        return nmsAdapter.getActionBarPackets();
    }

    public long getTitlePackets() {
        return nmsAdapter.getTitlePackets();
    }

    public long getBossSpawnPackets() {
        return nmsAdapter.getBossSpawnPackets();
    }

    public long getBossMetadataPackets() {
        return nmsAdapter.getBossMetadataPackets();
    }

    public long getBossTeleportPackets() {
        return nmsAdapter.getBossTeleportPackets();
    }

    public long getBossDestroyPackets() {
        return nmsAdapter.getBossDestroyPackets();
    }

    public long getStatisticPackets() {
        return nmsAdapter.getStatisticPackets();
    }

    public long getNmsFailureCount() {
        return nmsAdapter.getFailureCount();
    }

    public long getNmsReflectionResolutions() {
        return nmsAdapter.getReflectionResolutions();
    }

    /**
     * Recharge uniquement des valeurs deja parsees.
     * Les classes NMS restent resolues une seule fois pour toute la vie du jar.
     */
    public void reloadHudConfig() {
        FileConfiguration cfg =
            plugin.getConfigManager()
                .getHudConfig();

        bossEnabled =
            cfg.getBoolean(
                "bossbar.enabled",
                true
            );

        bossResetMs =
            secondsToMillis(
                cfg.getLong(
                    "bossbar.bossbar_timing_reset",
                    8L
                )
            );

        bossUpdateMs =
            Math.max(
                50L,
                (long) Math.max(
                    1,
                    cfg.getInt(
                        "bossbar.update_interval_ticks",
                        40
                    )
                ) * 50L
            );

        bossOffsetY =
            cfg.getDouble(
                "bossbar.entity_offset_y",
                -30.0D
            );

        bossForwardOffset =
            clamp(
                cfg.getDouble(
                    "bossbar.placement.front_distance",
                    24.0D
                ),
                4.0D,
                60.0D
            );

        bossFrontFarDistance =
            clamp(
                cfg.getDouble(
                    "bossbar.placement.front_far_distance",
                    40.0D
                ),
                8.0D,
                60.0D
            );

        /*
         * Le Dragon 1.8 est une entite uniquement envoyee par paquets. Meme
         * invisible, son immense modele peut apparaitre une frame si le
         * client recoit le spawn avant la metadata. Le garder loin et sous le
         * joueur rend ce cas imperceptible sans perdre la bossbar.
         */
        bossDragonDistance =
            clamp(
                cfg.getDouble(
                    "bossbar.placement.dragon_distance",
                    30.0D
                ),
                8.0D,
                60.0D
            );

        bossDragonVerticalOffset =
            clamp(
                cfg.getDouble(
                    "bossbar.placement.dragon_vertical_offset",
                    -100.0D
                ),
                -160.0D,
                -32.0D
            );

        bossAutoDistance =
            clamp(
                cfg.getDouble(
                    "bossbar.placement.auto_distance",
                    36.0D
                ),
                8.0D,
                60.0D
            );

        bossAutoVerticalOffset =
            clamp(
                cfg.getDouble(
                    "bossbar.placement.auto_vertical_offset",
                    -7.0D
                ),
                -20.0D,
                20.0D
            );

        /*
         * Sous 50 % de vie, le client 1.8 met le Wither en état armored et
         * génère ses particules blanches. On garde donc le Wither DEVANT le
         * joueur (bossbar fiable) mais avec un placement plus bas.
         */
        bossArmoredDistance =
            clamp(
                cfg.getDouble(
                    "bossbar.placement.armored_distance",
                    28.0D
                ),
                8.0D,
                60.0D
            );

        bossArmoredVerticalOffset =
            clamp(
                cfg.getDouble(
                    "bossbar.placement.armored_vertical_offset",
                    -18.0D
                ),
                -30.0D,
                10.0D
            );

        bossArmoredThreshold =
            clamp(
                cfg.getDouble(
                    "bossbar.placement.armored_threshold",
                    0.50D
                ),
                0.01D,
                0.99D
            );

        bossPositionMode =
            normalizePositionMode(
                cfg.getString(
                    "bossbar.placement.profile",
                    "AUTO"
                )
            );

        bossFollowPlayer =
            cfg.getBoolean(
                "bossbar.follow_player",
                true
            );

        bossInvisibleEntity =
            cfg.getBoolean(
                "bossbar.invisible_entity",
                true
            );

        bossMinProgress =
            clamp(
                cfg.getDouble(
                    "bossbar.minimum_progress",
                    0.05D
                ),
                0.01D,
                1.0D
            );

        bossEntityType =
            normalizeEntityType(
                cfg.getString(
                    "bossbar.entity_type",
                    "ENDER_DRAGON"
                )
            );

        bossEntityMaxHealth =
            (float) Math.max(
                1.0D,
                cfg.getDouble(
                    "bossbar.max_health",
                    defaultMaxHealth(bossEntityType)
                )
            );

        bossTestDurationMs =
            secondsToMillis(
                Math.max(
                    1L,
                    cfg.getLong(
                        "bossbar.test_duration_seconds",
                        8L
                    )
                )
            );

        bossTitleFormat =
            value(
                cfg.getString(
                    "bossbar.title_format",
                    "&a{job} &7Lv.&e{level} &8| "
                        + "&a{xp}&8/&a{xp_next} &7XP"
                )
            );

        bossTitleFormatMax =
            value(
                cfg.getString(
                    "bossbar.title_format_max_level",
                    "&a{job} &7Lv.&e{level} &8| &6&lMAX"
                )
            );

        hideWitherParticles =
            cfg.getBoolean(
                "bossbar.hide_wither_particles",
                true
            );

        abEnabled =
            cfg.getBoolean(
                "actionbar.enabled",
                true
            );

        abFormat =
            value(
                cfg.getString(
                    "actionbar.format",
                    "&a{job} &7Lv.&e{level} &8| "
                        + "&a+{xp_gained} XP "
                        + "&8(&7{xp}&8/&7{xp_next}&8)"
                        + "{autosell_suffix}"
                )
            );

        abAutoSellSuffix =
            value(
                cfg.getString(
                    "actionbar.autosell_suffix",
                    " &8| &6+{autosell_value}$ "
                        + "&7({autosell_items} objets)"
                )
            );

        abAutoSellOnlyFormat =
            value(
                cfg.getString(
                    "actionbar.autosell_only_format",
                    "&6AutoSell &8| &a+{autosell_value}$ "
                        + "&7({autosell_items} objets)"
                )
            );

        abCurrencyDecimals =
            Math.max(
                0,
                Math.min(
                    4,
                    cfg.getInt(
                        "actionbar.currency_decimals",
                        2
                    )
                )
            );

        abDisplayMs =
            secondsToMillis(
                Math.max(
                    0L,
                    cfg.getLong(
                        "actionbar.display_duration",
                        3L
                    )
                )
            );

        abAccumulationWindowMs =
            Math.max(
                50L,
                cfg.getLong(
                    "actionbar.accumulation_window_ms",
                    800L
                )
            );

        abRefreshMs =
            (long) Math.max(
                1,
                cfg.getInt(
                    "performance.actionbar_refresh_interval_ticks",
                    40
                )
            ) * 50L;

        schedulerIntervalTicks =
            Math.max(
                1,
                Math.min(
                    20,
                    cfg.getInt(
                        "performance.scheduler_interval_ticks",
                        2
                    )
                )
            );

        /*
         * Une config bossbar changee a chaud ne doit pas garder une ancienne
         * fake entity avec l'ancien type/placement.
         */
        for (Map.Entry<UUID, PlayerHudState> entry
                : states.entrySet()) {

            PlayerHudState state = entry.getValue();

            if (state == null
                    || state.bossHandle == null) {

                continue;
            }

            Player player =
                Bukkit.getPlayer(entry.getKey());

            if (player != null
                    && player.isOnline()) {

                nmsAdapter.destroyBoss(
                    player,
                    state.bossHandle
                );
            }

            state.bossHandle = null;
            state.lastBossRefreshMs = 0L;
        }

        if (updateTask != null) {
            startUpdateTask();
        }

        if ("WITHER".equals(bossEntityType)) {
            KjobLogger.info(
                "[HUD] BossBar placement="
                    + bossPositionMode
                    + ", front="
                    + bossForwardOffset
                    + ", far="
                    + bossFrontFarDistance
                    + ", auto="
                    + bossAutoDistance
                    + "/Y"
                    + (bossAutoVerticalOffset >= 0D ? "+" : "")
                    + bossAutoVerticalOffset
                    + ", armored="
                    + bossArmoredDistance
                    + "/Y"
                    + (bossArmoredVerticalOffset >= 0D ? "+" : "")
                    + bossArmoredVerticalOffset
                    + "@"
                    + Math.round(bossArmoredThreshold * 100D)
                    + "%"
                    + ", particle-safe="
                    + (hideWitherParticles ? "ON" : "OFF")
                    + "."
            );
        }
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask =
            Bukkit.getScheduler()
                .runTaskTimer(
                    plugin,
                    new Runnable() {
                        @Override
                        public void run() {
                            tick();
                        }
                    },
                    schedulerIntervalTicks,
                    schedulerIntervalTicks
                );
    }

    /**
     * Gain XP reel deja applique.
     */
    public void onXpGain(
            Player player,
            PlayerData data,
            String jobId,
            int xpGained,
            LevelUpResult result) {

        if (player == null
                || data == null
                || jobId == null
                || jobId.trim().isEmpty()
                || !player.isOnline()
                || !data.isHudEnabled()) {

            return;
        }

        if (!data.isActionBarHudEnabled()
                && !data.isBossBarHudEnabled()) {

            return;
        }

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(jobId);

        if (job == null) {
            return;
        }

        int actualXp =
            result == null
                ? Math.max(0, xpGained)
                : Math.max(0, result.getXpActual());

        if (actualXp <= 0) {
            return;
        }

        xpSignals.incrementAndGet();

        UUID uuid =
            player.getUniqueId();

        PlayerHudState state =
            getOrCreateState(uuid);

        long now =
            System.currentTimeMillis();

        if (abEnabled
                && data.isActionBarHudEnabled()) {

            boolean jobChanged =
                state.accumulatingJobId != null
                    && !job.getId()
                        .equals(state.accumulatingJobId);

            boolean windowExpired =
                state.windowStartMs > 0L
                    && now - state.windowStartMs
                        > abAccumulationWindowMs;

            boolean newWindow =
                state.windowStartMs <= 0L
                    || !hasPendingActionBar(state);

            if (jobChanged
                    || windowExpired
                    || newWindow) {

                if (hasPendingActionBar(state)) {

                    flushActionBar(
                        player,
                        data,
                        state
                    );
                }

                clearPendingActionBar(state);
                state.accumulatingJobId =
                    job.getId();
                state.windowStartMs = now;
            }

            if (state.accumulatingJobId == null) {
                state.accumulatingJobId =
                    job.getId();
            }

            state.accumulatedXp =
                saturatingAdd(
                    state.accumulatedXp,
                    actualXp
                );

        } else {
            clearActionBarState(state);
        }

        state.lastXpMs = now;
        state.testBossUntilMs = 0L;

        updateSnapshot(
            data,
            job,
            state
        );

        activate(uuid);
    }

    /**
     * Gain AutoSell déjà déposé et agrégé par action logique. Cette méthode
     * ne touche jamais à l'économie et ne produit aucune seconde ActionBar.
     */
    public void onAutoSellGain(
            Player player,
            long soldItems,
            double soldValue) {

        if (player == null
                || !player.isOnline()
                || soldItems <= 0L
                || soldValue <= 0D
                || Double.isNaN(soldValue)
                || Double.isInfinite(soldValue)
                || !abEnabled) {

            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(player);

        if (data == null
                || !data.isHudEnabled()
                || !data.isActionBarHudEnabled()) {

            return;
        }

        UUID uuid = player.getUniqueId();
        PlayerHudState state = getOrCreateState(uuid);
        long now = System.currentTimeMillis();

        boolean windowExpired =
            state.windowStartMs > 0L
                && now - state.windowStartMs
                    > abAccumulationWindowMs;

        if (windowExpired && hasPendingActionBar(state)) {
            flushActionBar(player, data, state);
            clearPendingActionBar(state);
        }

        if (state.windowStartMs <= 0L) {
            state.windowStartMs = now;
        }

        state.accumulatedSoldItems =
            saturatingAdd(
                state.accumulatedSoldItems,
                soldItems
            );

        state.accumulatedSoldValue =
            saturatingAddMoney(
                state.accumulatedSoldValue,
                soldValue
            );

        activate(uuid);
    }

    /**
     * Popup level-up. API conservee.
     */
    public void onLevelUp(
            Player player,
            PlayerData data,
            String jobId,
            int newLevel) {

        if (player == null
                || data == null
                || jobId == null
                || !player.isOnline()) {

            return;
        }

        FileConfiguration cfg =
            plugin.getConfigManager()
                .getHudConfig();

        if (!cfg.getBoolean(
                "achievement.enabled",
                true
            )) {

            return;
        }

        if (cfg.getBoolean(
                "achievement.respect_hud_toggle",
                false
            )
                && !data.isHudEnabled()) {

            return;
        }

        PlayerHudState state =
            getOrCreateState(
                player.getUniqueId()
            );

        long now =
            System.currentTimeMillis();

        long cooldown =
            Math.max(
                0L,
                cfg.getLong(
                    "achievement.popup_cooldown_ms",
                    2000L
                )
            );

        if (now - state.lastPopupMs
                < cooldown) {

            return;
        }

        state.lastPopupMs = now;
        levelPopupSignals.incrementAndGet();

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(jobId);

        String jobName =
            job == null
                ? jobId
                : job.getDisplayName();

        int safeLevel =
            job == null
                ? Math.max(0, newLevel)
                : Math.max(
                    0,
                    Math.min(
                        job.getMaxLevel(),
                        newLevel
                    )
                );

        String title =
            color(
                value(
                    cfg.getString(
                        "achievement.title",
                        "&6&lNIVEAU {level}"
                    )
                )
                    .replace("{job}", jobName)
                    .replace(
                        "{level}",
                        String.valueOf(safeLevel)
                    )
            );

        String subtitle =
            color(
                value(
                    cfg.getString(
                        "achievement.subtitle",
                        "&b{job} &7atteint !"
                    )
                )
                    .replace("{job}", jobName)
                    .replace(
                        "{level}",
                        String.valueOf(safeLevel)
                    )
            );

        String mode =
            value(
                cfg.getString(
                    "achievement.mode",
                    "TITLE_AND_CHAT"
                )
            )
                .trim()
                .toUpperCase()
                .replace('-', '_');

        if (mode.contains("TITLE")) {
            nmsAdapter.sendTitle(
                player,
                title,
                subtitle,
                Math.max(
                    0,
                    cfg.getInt(
                        "achievement.fade_in",
                        10
                    )
                ),
                Math.max(
                    0,
                    cfg.getInt(
                        "achievement.stay",
                        50
                    )
                ),
                Math.max(
                    0,
                    cfg.getInt(
                        "achievement.fade_out",
                        15
                    )
                ),
                cfg.getBoolean(
                    "achievement.reset_before_send",
                    true
                )
            );
        }

        if (mode.contains("ACTIONBAR")
                && data.isActionBarHudEnabled()) {

            String actionbar =
                color(
                    value(
                        cfg.getString(
                            "achievement.actionbar",
                            "&6&lNIVEAU {level} &8- &b{job}"
                        )
                    )
                        .replace("{job}", jobName)
                        .replace(
                            "{level}",
                            String.valueOf(safeLevel)
                        )
                );

            nmsAdapter.sendActionBar(
                player,
                actionbar
            );
        }

        boolean chatSent = false;

        if (mode.contains("CHAT")) {
            sendLevelChat(
                player,
                cfg,
                jobName,
                safeLevel
            );
            chatSent = true;
        }

        if (!chatSent
                && cfg.getBoolean(
                    "achievement.force_chat_fallback",
                    true
                )) {

            sendLevelChat(
                player,
                cfg,
                jobName,
                safeLevel
            );
        }

        sendVanillaToast(
            player,
            cfg,
            jobId
        );
    }

    private void sendLevelChat(
            Player player,
            FileConfiguration cfg,
            String jobName,
            int level) {

        String message =
            color(
                value(
                    cfg.getString(
                        "achievement.chat",
                        "&6&lNIVEAU {level} &8- &b{job}"
                    )
                )
                    .replace("{job}", jobName)
                    .replace(
                        "{level}",
                        String.valueOf(level)
                    )
            );

        if (!message.isEmpty()) {
            player.sendMessage(message);
        }
    }

    private void sendVanillaToast(
            final Player player,
            final FileConfiguration cfg,
            String jobId) {

        if (!cfg.getBoolean(
                "achievement.vanilla_toast.enabled",
                true
            )) {

            return;
        }

        String raw =
            cfg.getString(
                "achievement.vanilla_toast.mapping." + jobId,
                cfg.getString(
                    "achievement.vanilla_toast.achievement",
                    "OPEN_INVENTORY"
                )
            );

        final Achievement achievement;

        try {
            achievement =
                Achievement.valueOf(
                    value(raw)
                        .trim()
                        .toUpperCase()
                        .replace('-', '_')
                );
        } catch (Exception failure) {
            KjobLogger.warn(
                "[HUD] Achievement vanilla inconnu : " + raw
            );
            return;
        }

        String method =
            value(
                cfg.getString(
                    "achievement.vanilla_toast.method",
                    "BUKKIT"
                )
            )
                .trim()
                .toUpperCase()
                .replace('-', '_');

        if ("PACKET".equals(method)) {
            nmsAdapter.sendAchievementStatistic(
                player,
                achievement
            );
            return;
        }

        if ("PACKET_THEN_BUKKIT".equals(method)) {
            nmsAdapter.sendAchievementStatistic(
                player,
                achievement
            );

            int delay =
                Math.max(
                    1,
                    cfg.getInt(
                        "achievement.vanilla_toast."
                            + "bukkit_after_packet_ticks",
                        2
                    )
                );

            Bukkit.getScheduler()
                .runTaskLater(
                    plugin,
                    new Runnable() {
                        @Override
                        public void run() {
                            if (player.isOnline()) {
                                sendBukkitToast(
                                    player,
                                    plugin.getConfigManager()
                                        .getHudConfig(),
                                    achievement
                                );
                            }
                        }
                    },
                    delay
                );

            return;
        }

        sendBukkitToast(
            player,
            cfg,
            achievement
        );
    }

    private void sendBukkitToast(
            final Player player,
            final FileConfiguration cfg,
            final Achievement achievement) {

        final boolean hadBefore;

        try {
            hadBefore =
                player.hasAchievement(achievement);

            if (hadBefore
                    || cfg.getBoolean(
                        "achievement.vanilla_toast.force_reaward",
                        true
                    )) {

                player.removeAchievement(achievement);
            }
        } catch (Exception failure) {
            return;
        }

        final boolean restore =
            cfg.getBoolean(
                "achievement.vanilla_toast."
                    + "restore_if_not_previously_awarded",
                true
            );

        final int restoreTicks =
            Math.max(
                5,
                cfg.getInt(
                    "achievement.vanilla_toast.restore_after_ticks",
                    60
                )
            );

        Bukkit.getScheduler()
            .runTaskLater(
                plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) {
                            return;
                        }

                        try {
                            player.awardAchievement(achievement);
                        } catch (Exception ignored) {
                            return;
                        }

                        if (!hadBefore && restore) {
                            Bukkit.getScheduler()
                                .runTaskLater(
                                    plugin,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!player.isOnline()) {
                                                return;
                                            }

                                            try {
                                                player.removeAchievement(
                                                    achievement
                                                );
                                            } catch (Exception ignored) {
                                                // Cosmetique uniquement.
                                            }
                                        }
                                    },
                                    restoreTicks
                                );
                        }
                    }
                },
                1L
            );
    }

    public void testBossBar(Player player) {
        testBossBar(
            player,
            null,
            null,
            null
        );
    }

    public void testBossBar(
            Player player,
            String entityTypeOverride,
            String positionModeOverride,
            Boolean invisibleOverride) {

        if (player == null
                || !player.isOnline()) {

            return;
        }

        PlayerHudState state =
            getOrCreateState(
                player.getUniqueId()
            );

        if (state.bossHandle != null) {
            nmsAdapter.destroyBoss(
                player,
                state.bossHandle
            );
            state.bossHandle = null;
        }

        state.testEntityType =
            entityTypeOverride == null
                ? bossEntityType
                : normalizeEntityType(entityTypeOverride);

        state.testPositionMode =
            positionModeOverride == null
                ? bossPositionMode
                : normalizePositionMode(positionModeOverride);

        state.testInvisible =
            invisibleOverride == null
                ? bossInvisibleEntity
                : invisibleOverride.booleanValue();

        state.testBossUntilMs =
            System.currentTimeMillis()
                + bossTestDurationMs;

        showBossBar(
            player,
            state,
            0.75F,
            "§bTest BossBar §a75% §7- KjobsUltimate",
            true
        );

        activate(
            player.getUniqueId()
        );
    }

    private void tick() {
        long start =
            System.nanoTime();

        schedulerTicks.incrementAndGet();

        try {
            if (active.isEmpty()) {
                return;
            }

            long now =
                System.currentTimeMillis();

            for (UUID uuid : active.keySet()) {
                PlayerHudState state =
                    states.get(uuid);

                if (state == null) {
                    active.remove(uuid);
                    continue;
                }

                schedulerPlayerVisits.incrementAndGet();

                Player player =
                    Bukkit.getPlayer(uuid);

                if (player == null
                        || !player.isOnline()) {

                    active.remove(uuid);
                    states.remove(uuid);
                    continue;
                }

                boolean keepActive = false;

                if (state.testBossUntilMs > 0L) {
                    if (now >= state.testBossUntilMs) {
                        hideBossBar(
                            player,
                            state
                        );
                        clearTestState(state);
                    } else {
                        keepActive = true;
                    }
                }

                PlayerData data =
                    plugin.getPlayerDataManager()
                        .get(player);

                if (data == null
                        || !data.isHudEnabled()) {

                    if (state.bossHandle != null) {
                        hideBossBar(
                            player,
                            state
                        );
                    }

                    clearActionBarState(state);

                    if (!keepActive) {
                        active.remove(uuid);
                    }

                    continue;
                }

                if (abEnabled
                        && data.isActionBarHudEnabled()) {

                    if (hasPendingActionBar(state)
                            && now - state.windowStartMs
                                >= abAccumulationWindowMs) {

                        flushActionBar(
                            player,
                            data,
                            state
                        );

                        clearPendingActionBar(state);
                    }

                    if (state.cachedActionBarMsg != null) {
                        if (now >= state.displayUntilMs) {
                            nmsAdapter.sendActionBar(
                                player,
                                ""
                            );

                            state.cachedActionBarMsg = null;
                            state.displayUntilMs = 0L;

                        } else {
                            keepActive = true;

                            if (now - state.lastActionBarSendMs
                                    >= abRefreshMs) {

                                nmsAdapter.sendActionBar(
                                    player,
                                    state.cachedActionBarMsg
                                );

                                state.lastActionBarSendMs = now;
                            }
                        }
                    }

                    if (hasPendingActionBar(state)) {
                        keepActive = true;
                    }
                } else {
                    if (state.cachedActionBarMsg != null) {
                        nmsAdapter.sendActionBar(
                            player,
                            ""
                        );
                    }

                    clearActionBarState(
                        state
                    );
                }

                if (state.testBossUntilMs <= 0L) {
                    if (bossEnabled
                            && data.isBossBarHudEnabled()
                            && state.snapshotJobId != null) {

                        boolean visible =
                            bossResetMs <= 0L
                                || now - state.lastXpMs
                                    < bossResetMs;

                        if (visible) {
                            keepActive = true;

                            if (state.bossHandle == null
                                    || now - state.lastBossRefreshMs
                                        >= bossUpdateMs) {

                                refreshBossBar(
                                    player,
                                    data,
                                    state
                                );

                                state.lastBossRefreshMs = now;
                            }

                        } else if (state.bossHandle != null) {
                            hideBossBar(
                                player,
                                state
                            );
                        }

                    } else if (state.bossHandle != null) {
                        hideBossBar(
                            player,
                            state
                        );
                    }
                }

                if (!keepActive
                        && state.testBossUntilMs <= 0L
                        && !hasPendingActionBar(state)
                        && state.cachedActionBarMsg == null
                        && state.bossHandle == null) {

                    active.remove(uuid);
                }
            }

        } finally {
            long elapsed =
                Math.max(
                    0L,
                    System.nanoTime() - start
                );

            schedulerTotalNanos.addAndGet(elapsed);
            updateMax(
                schedulerMaxNanos,
                elapsed
            );
        }
    }

    private void flushActionBar(
            Player player,
            PlayerData data,
            PlayerHudState state) {

        if (player == null
                || !player.isOnline()
                || state == null
                || !hasPendingActionBar(state)) {

            return;
        }

        String saleValue =
            formatMoney(state.accumulatedSoldValue);
        String saleItems =
            String.valueOf(
                Math.max(0L, state.accumulatedSoldItems)
            );

        String saleSuffix =
            state.accumulatedSoldItems <= 0L
                || state.accumulatedSoldValue <= 0D
                    ? ""
                    : abAutoSellSuffix
                        .replace(
                            "{autosell_value}",
                            saleValue
                        )
                        .replace(
                            "{autosell_items}",
                            saleItems
                        );

        JobDefinition job =
            state.accumulatingJobId == null
                ? null
                : plugin.getJobRegistry()
                    .getJob(state.accumulatingJobId);

        String rawMessage;

        if (job == null) {
            if (saleSuffix.isEmpty()) {
                return;
            }

            rawMessage =
                abAutoSellOnlyFormat
                    .replace(
                        "{autosell_value}",
                        saleValue
                    )
                    .replace(
                        "{autosell_items}",
                        saleItems
                    );

        } else {
            if (!job.getId()
                    .equals(state.snapshotJobId)) {

                updateSnapshot(
                    data,
                    job,
                    state
                );
            }

            int percentage =
                percent(
                    state.snapshotLevel,
                    job.getMaxLevel(),
                    state.snapshotXp,
                    state.snapshotXpNext
                );

            rawMessage =
                abFormat
                    .replace(
                        "{job}",
                        job.getDisplayName()
                    )
                    .replace(
                        "{level}",
                        String.valueOf(
                            state.snapshotLevel
                        )
                    )
                    .replace(
                        "{xp}",
                        String.valueOf(
                            state.snapshotXp
                        )
                    )
                    .replace(
                        "{xp_next}",
                        String.valueOf(
                            state.snapshotXpNext
                        )
                    )
                    .replace(
                        "{xp_gained}",
                        String.valueOf(
                            Math.max(
                                0,
                                state.accumulatedXp
                            )
                        )
                    )
                    .replace(
                        "{percent}",
                        String.valueOf(percentage)
                    )
                    .replace(
                        "{autosell_suffix}",
                        saleSuffix
                    )
                    .replace(
                        "{autosell_value}",
                        saleValue
                    )
                    .replace(
                        "{autosell_items}",
                        saleItems
                    );
        }

        String message = color(rawMessage);

        long now =
            System.currentTimeMillis();

        state.cachedActionBarMsg = message;
        state.displayUntilMs = now + abDisplayMs;
        state.lastActionBarSendMs = now;

        nmsAdapter.sendActionBar(
            player,
            message
        );
    }

    private void refreshBossBar(
            Player player,
            PlayerData data,
            PlayerHudState state) {

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(state.snapshotJobId);

        if (job == null) {
            hideBossBar(
                player,
                state
            );
            return;
        }

        updateSnapshot(
            data,
            job,
            state
        );

        float progress =
            LevelUtil.getProgressPercent(
                data,
                job
            );

        int percentage =
            LevelUtil.getProgressPercentage(
                data,
                job
            );

        String format =
            state.snapshotLevel >= job.getMaxLevel()
                ? bossTitleFormatMax
                : bossTitleFormat;

        String title =
            color(
                format
                    .replace(
                        "{job}",
                        job.getDisplayName()
                    )
                    .replace(
                        "{level}",
                        String.valueOf(
                            state.snapshotLevel
                        )
                    )
                    .replace(
                        "{xp}",
                        String.valueOf(
                            state.snapshotXp
                        )
                    )
                    .replace(
                        "{xp_next}",
                        String.valueOf(
                            state.snapshotXpNext
                        )
                    )
                    .replace(
                        "{percent}",
                        String.valueOf(percentage)
                    )
            );

        showBossBar(
            player,
            state,
            progress,
            title,
            false
        );
    }

    private void showBossBar(
            Player player,
            PlayerHudState state,
            float progress,
            String title,
            boolean test) {

        String entityType =
            test
                ? normalizeEntityType(state.testEntityType)
                : bossEntityType;

        String positionMode =
            test
                ? normalizePositionMode(state.testPositionMode)
                : bossPositionMode;

        boolean invisible =
            test
                ? state.testInvisible
                : bossInvisibleEntity;

        float maxHealth =
            test
                ? defaultMaxHealth(entityType)
                : bossEntityMaxHealth;

        float safeProgress =
            (float) clamp(
                progress,
                0D,
                1D
            );

        float health =
            Math.max(
                1.0F,
                Math.min(
                    maxHealth,
                    (float) Math.max(
                        bossMinProgress,
                        safeProgress
                    ) * maxHealth
                )
            );

        double displayedRatio =
            maxHealth <= 0.0F
                ? 1.0D
                : clamp(
                    (double) health / (double) maxHealth,
                    0.0D,
                    1.0D
                );

        BossBarLocation location =
            computeBossLocation(
                player,
                positionMode,
                entityType,
                invisible,
                test,
                displayedRatio
            );

        if (state.bossHandle != null
                && !entityType.equals(
                    state.bossHandle.getEntityType()
                )) {

            nmsAdapter.destroyBoss(
                player,
                state.bossHandle
            );

            state.bossHandle = null;
        }

        if (state.bossHandle == null) {
            state.bossHandle =
                nmsAdapter.spawnBoss(
                    player,
                    entityType,
                    nextEntityId(),
                    location.x,
                    location.y,
                    location.z,
                    health,
                    title,
                    invisible
                );

            return;
        }

        boolean updated =
            nmsAdapter.updateBoss(
                player,
                state.bossHandle,
                location.x,
                location.y,
                location.z,
                bossFollowPlayer,
                health,
                title,
                invisible
            );

        if (!updated) {
            state.bossHandle = null;
        }
    }

    private BossBarLocation computeBossLocation(
            Player player,
            String requestedMode,
            String entityType,
            boolean invisible,
            boolean test,
            double displayedRatio) {

        Location location =
            player.getLocation();

        String mode =
            normalizePositionMode(
                requestedMode
            );

        if ("AUTO".equals(mode)) {

            if (invisible
                    && "ENDER_DRAGON".equals(entityType)) {

                return frontLocation(
                    location,
                    bossDragonDistance,
                    bossDragonVerticalOffset
                );
            }

            if (!test
                    && hideWitherParticles
                    && invisible
                    && "WITHER".equals(entityType)) {

                bossParticleSafePlacements.incrementAndGet();

                /*
                 * Différence cruciale V3.16.2 :
                 *
                 * > armored_threshold :
                 *   fumée normale seulement -> placement AUTO classique.
                 *
                 * <= armored_threshold :
                 *   le client active isArmored() et crée les particules
                 *   blanches. L'entité reste devant pour conserver la bossbar,
                 *   mais descend nettement sous la ligne de vue.
                 *
                 * La longueur de la bossbar reste exacte : on ne modifie
                 * jamais la vie du Wither pour contourner l'état armored.
                 */
                if (displayedRatio <= bossArmoredThreshold) {
                    return frontLocation(
                        location,
                        bossArmoredDistance,
                        bossArmoredVerticalOffset
                    );
                }

                return frontLocation(
                    location,
                    bossAutoDistance,
                    bossAutoVerticalOffset
                );
            }

            return frontLocation(
                location,
                bossForwardOffset,
                0.0D
            );
        }

        if ("FRONT".equals(mode)) {
            return frontLocation(
                location,
                bossForwardOffset,
                0.0D
            );
        }

        if ("FRONT_FAR".equals(mode)) {
            if (!test
                    && hideWitherParticles
                    && invisible
                    && "WITHER".equals(entityType)) {

                bossParticleSafePlacements.incrementAndGet();
            }

            return frontLocation(
                location,
                bossFrontFarDistance,
                0.0D
            );
        }

        if ("EYE_FRONT".equals(mode)) {
            Location eye =
                player.getEyeLocation();

            Vector direction =
                eye.getDirection();

            if (direction.lengthSquared() < 0.001D) {
                direction =
                    new Vector(0D, 0D, 1D);
            }

            direction.normalize();

            return new BossBarLocation(
                eye.getX() + direction.getX() * bossForwardOffset,
                eye.getY() + direction.getY() * bossForwardOffset,
                eye.getZ() + direction.getZ() * bossForwardOffset
            );
        }

        if ("BELOW".equals(mode)) {
            return new BossBarLocation(
                location.getX(),
                location.getY()
                    - Math.abs(
                        bossOffsetY == 0D ? 12.0D : bossOffsetY
                    ),
                location.getZ()
            );
        }

        if ("ABOVE".equals(mode)) {
            return new BossBarLocation(
                location.getX(),
                location.getY()
                    + Math.abs(
                        bossOffsetY == 0D ? 12.0D : bossOffsetY
                    ),
                location.getZ()
            );
        }

        return new BossBarLocation(
            location.getX(),
            location.getY() + 1.5D,
            location.getZ()
        );
    }

    /** Stable frontal placement: ignores camera pitch. */
    private static BossBarLocation frontLocation(
            Location origin,
            double distance,
            double verticalOffset) {

        Vector direction =
            origin.getDirection();

        direction.setY(0D);

        if (direction.lengthSquared() < 0.001D) {
            direction =
                new Vector(0D, 0D, 1D);
        }

        direction.normalize();

        double safeDistance =
            Math.max(
                4.0D,
                Math.min(60.0D, distance)
            );

        return new BossBarLocation(
            origin.getX() + direction.getX() * safeDistance,
            origin.getY() + 1.5D + verticalOffset,
            origin.getZ() + direction.getZ() * safeDistance
        );
    }

    private void updateSnapshot(
            PlayerData data,
            JobDefinition job,
            PlayerHudState state) {

        int level =
            Math.max(
                0,
                Math.min(
                    job.getMaxLevel(),
                    data.getLevel(job.getId())
                )
            );

        state.snapshotJobId = job.getId();
        state.snapshotLevel = level;

        if (level >= job.getMaxLevel()) {
            state.snapshotXp = 0;
            state.snapshotXpNext = 0;
            return;
        }

        state.snapshotXp =
            LevelUtil.getCurrentLevelXp(
                data,
                job
            );

        state.snapshotXpNext =
            LevelUtil.getRequiredXpForNextLevel(
                data,
                job
            );
    }

    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid =
            player.getUniqueId();

        active.remove(uuid);

        PlayerHudState state =
            states.remove(uuid);

        if (state != null
                && state.bossHandle != null) {

            nmsAdapter.destroyBoss(
                player,
                state.bossHandle
            );
        }
    }

    public void clearActionBar(Player player) {
        if (player == null
                || !player.isOnline()) {

            return;
        }

        PlayerHudState state =
            states.get(
                player.getUniqueId()
            );

        if (state != null) {
            clearActionBarState(state);
        }

        nmsAdapter.sendActionBar(
            player,
            ""
        );
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        for (Map.Entry<UUID, PlayerHudState> entry
                : states.entrySet()) {

            Player player =
                Bukkit.getPlayer(entry.getKey());

            PlayerHudState state =
                entry.getValue();

            if (player != null
                    && player.isOnline()
                    && state != null
                    && state.bossHandle != null) {

                nmsAdapter.destroyBoss(
                    player,
                    state.bossHandle
                );
            }
        }

        active.clear();
        states.clear();
    }

    private void hideBossBar(
            Player player,
            PlayerHudState state) {

        if (state == null
                || state.bossHandle == null) {

            return;
        }

        nmsAdapter.destroyBoss(
            player,
            state.bossHandle
        );

        state.bossHandle = null;
        state.lastBossRefreshMs = 0L;
    }

    private void clearActionBarState(
            PlayerHudState state) {

        clearPendingActionBar(state);
        state.cachedActionBarMsg = null;
        state.displayUntilMs = 0L;
        state.lastActionBarSendMs = 0L;
    }

    private void clearPendingActionBar(
            PlayerHudState state) {

        if (state == null) {
            return;
        }

        state.accumulatingJobId = null;
        state.accumulatedXp = 0;
        state.accumulatedSoldItems = 0L;
        state.accumulatedSoldValue = 0D;
        state.windowStartMs = 0L;
    }

    private static boolean hasPendingActionBar(
            PlayerHudState state) {

        return state != null
            && (state.accumulatedXp > 0
                || (state.accumulatedSoldItems > 0L
                    && state.accumulatedSoldValue > 0D));
    }

    private void clearTestState(
            PlayerHudState state) {

        state.testBossUntilMs = 0L;
        state.testEntityType = null;
        state.testPositionMode = null;
        state.testInvisible = false;
    }

    private PlayerHudState getOrCreateState(
            UUID uuid) {

        PlayerHudState state =
            states.get(uuid);

        if (state != null) {
            return state;
        }

        PlayerHudState created =
            new PlayerHudState();

        PlayerHudState existing =
            states.putIfAbsent(
                uuid,
                created
            );

        return existing == null
            ? created
            : existing;
    }

    private void activate(UUID uuid) {
        if (uuid != null) {
            active.put(
                uuid,
                Boolean.TRUE
            );
        }
    }

    private static String normalizeEntityType(
            String raw) {

        String value =
            raw == null
                ? "ENDER_DRAGON"
                : raw.trim()
                    .toUpperCase()
                    .replace('-', '_');

        if ("DRAGON".equals(value)) {
            value = "ENDER_DRAGON";
        }

        return "WITHER".equals(value)
            ? "WITHER"
            : "ENDER_DRAGON";
    }

    private static String normalizePositionMode(
            String raw) {

        String value =
            raw == null
                ? "AUTO"
                : raw.trim()
                    .toUpperCase()
                    .replace('-', '_');

        if ("EYEFRONT".equals(value)) {
            value = "EYE_FRONT";
        }

        if ("FRONTFAR".equals(value)
                || "FAR".equals(value)) {

            value = "FRONT_FAR";
        }

        if ("UNDER".equals(value)
                || "DOWN".equals(value)) {

            value = "BELOW";
        }

        if ("UP".equals(value)) {
            value = "ABOVE";
        }

        if (!"AUTO".equals(value)
                && !"FRONT".equals(value)
                && !"FRONT_FAR".equals(value)
                && !"BELOW".equals(value)
                && !"ABOVE".equals(value)
                && !"EYE_FRONT".equals(value)
                && !"PLAYER".equals(value)) {

            return "AUTO";
        }

        return value;
    }

    private static float defaultMaxHealth(
            String entityType) {

        return "ENDER_DRAGON".equals(
                normalizeEntityType(entityType)
            )
            ? 200.0F
            : 300.0F;
    }

    private static int nextEntityId() {
        int id =
            FAKE_ENTITY_COUNTER.getAndIncrement();

        if (id <= 0
                || id >= Integer.MAX_VALUE - 1000) {

            FAKE_ENTITY_COUNTER.set(800_001);
            return 800_000;
        }

        return id;
    }

    private static int saturatingAdd(
            int first,
            int second) {

        long result =
            (long) first + (long) second;

        if (result >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        if (result <= 0L) {
            return 0;
        }

        return (int) result;
    }

    private static long saturatingAdd(
            long first,
            long second) {

        if (second > 0L
                && first > Long.MAX_VALUE - second) {

            return Long.MAX_VALUE;
        }

        return first + second;
    }

    private static double saturatingAddMoney(
            double first,
            double second) {

        double result = first + second;

        return Double.isNaN(result)
                || Double.isInfinite(result)
            ? Double.MAX_VALUE
            : result;
    }

    private String formatMoney(double amount) {
        double safe =
            Double.isNaN(amount)
                    || Double.isInfinite(amount)
                    || amount < 0D
                ? 0D
                : amount;

        return String.format(
            Locale.US,
            "%." + abCurrencyDecimals + "f",
            safe
        );
    }

    private static int percent(
            int level,
            int maxLevel,
            int xp,
            int required) {

        if (level >= maxLevel) {
            return 100;
        }

        if (required <= 0) {
            return 0;
        }

        return Math.max(
            0,
            Math.min(
                100,
                (int) Math.floor(
                    ((double) Math.max(0, xp)
                        / (double) required)
                        * 100D
                )
            )
        );
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum) {

        if (Double.isNaN(value)) {
            return minimum;
        }

        return Math.max(
            minimum,
            Math.min(
                maximum,
                value
            )
        );
    }

    private static long secondsToMillis(
            long seconds) {

        long safe =
            Math.max(0L, seconds);

        if (safe > Long.MAX_VALUE / 1000L) {
            return Long.MAX_VALUE;
        }

        return safe * 1000L;
    }

    private static String color(String text) {
        return value(text)
            .replace('&', '§');
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private static void updateMax(
            AtomicLong target,
            long value) {

        while (true) {
            long previous =
                target.get();

            if (value <= previous) {
                return;
            }

            if (target.compareAndSet(
                    previous,
                    value
                )) {

                return;
            }
        }
    }

    private static final class BossBarLocation {

        private final double x;
        private final double y;
        private final double z;

        private BossBarLocation(
                double x,
                double y,
                double z) {

            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class PlayerHudState {

        // ActionBar.
        private String accumulatingJobId;
        private int accumulatedXp;
        private long accumulatedSoldItems;
        private double accumulatedSoldValue;
        private long windowStartMs;
        private String cachedActionBarMsg;
        private long displayUntilMs;
        private long lastActionBarSendMs;

        // Snapshot.
        private String snapshotJobId;
        private int snapshotLevel;
        private int snapshotXp;
        private int snapshotXpNext;
        private long lastXpMs;

        // BossBar.
        private HudNmsAdapter.BossHandle bossHandle;
        private long lastBossRefreshMs;

        // Test.
        private long testBossUntilMs;
        private String testEntityType;
        private String testPositionMode;
        private boolean testInvisible;

        // Popup.
        private long lastPopupMs;
    }
}
