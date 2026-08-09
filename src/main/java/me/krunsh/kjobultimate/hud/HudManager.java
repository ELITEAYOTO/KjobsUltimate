package me.krunsh.kjobultimate.hud;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.util.KjobLogger;
import me.krunsh.kjobultimate.util.LevelUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gère le HUD en jeu :
 * - actionbar d'XP accumulée ;
 * - bossbar de progression ;
 * - popup de passage de niveau.
 *
 * Toutes les opérations NMS restent isolées dans cette classe et sont faites
 * par réflexion pour éviter de lier directement le projet aux classes NMS.
 *
 * Convention XP :
 * - PlayerData stocke le niveau actuel ;
 * - JobDefinition#getXpRequiredForNextLevel(int) fournit le palier suivant ;
 * - LevelUtil centralise les valeurs et pourcentages affichés.
 *
 * Cycle de vie :
 * - onXpGain() est appelé après un gain d'XP réellement appliqué ;
 * - onLevelUp() est appelé par XpManager.handleLevelUp() ;
 * - removePlayer() est appelé à la déconnexion ;
 * - shutdown() est appelé lors de l'arrêt du plugin.
 */
public final class HudManager {

    private static final AtomicInteger FAKE_ENTITY_COUNTER =
        new AtomicInteger(800_000);

    private final KjobUltimate plugin;

    /** Version NMS détectée au démarrage, par exemple v1_8_R3. */
    private final String NMS;

    private final Map<UUID, PlayerHudState> states =
        new ConcurrentHashMap<UUID, PlayerHudState>();

    private BukkitTask updateTask;

    // Valeurs de configuration mises en cache.
    private boolean bossEnabled;
    private long bossResetMs;
    private long bossUpdateMs;
    private double bossOffsetY;
    private double bossForwardOffset;
    private String bossPositionMode;
    private boolean bossFollowPlayer;
    private boolean bossInvisibleEntity;
    private double bossMinProgress;
    private String bossEntityType;
    private float bossEntityMaxHealth;
    private long bossTestDurationMs;

    private boolean abEnabled;
    private String abFormat;
    private long abDisplayMs;
    private long hudWindowMs;

    public HudManager(KjobUltimate plugin) {
        this.plugin = Objects.requireNonNull(
            plugin,
            "KjobUltimate ne peut pas être null.");

        String pkg =
            Bukkit.getServer().getClass().getPackage().getName();

        this.NMS =
            pkg.substring(pkg.lastIndexOf('.') + 1);

        reloadHudConfig();
        startUpdateTask();

        KjobLogger.success(
            "HudManager actif (" + NMS
                + ") - actionbar + bossbar + level-up popup.");
    }

    public String getNMS() {
        return NMS;
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

    /**
     * Envoie directement une bossbar de test à 75 %.
     */
    public void testBossBar(Player player) {
        testBossBar(player, null, null, null);
    }

    /**
     * Variante de diagnostic permettant de tester plusieurs stratégies sans
     * modifier hud.yml entre chaque essai.
     */
    public void testBossBar(
            Player player,
            String entityTypeOverride,
            String positionModeOverride,
            Boolean invisibleOverride) {

        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        PlayerHudState state =
            getOrCreateState(uuid);

        String previousEntityType = bossEntityType;
        String previousPositionMode = bossPositionMode;
        boolean previousInvisible = bossInvisibleEntity;
        float previousMaxHealth = bossEntityMaxHealth;

        if (entityTypeOverride != null
                && !entityTypeOverride.trim().isEmpty()) {

            bossEntityType =
                normalizeBossEntityType(entityTypeOverride);

            bossEntityMaxHealth =
                defaultMaxHealthFor(bossEntityType);
        }

        if (positionModeOverride != null
                && !positionModeOverride.trim().isEmpty()) {

            bossPositionMode =
                normalizeBossPositionMode(positionModeOverride);
        }

        if (invisibleOverride != null) {
            bossInvisibleEntity =
                invisibleOverride.booleanValue();
        }

        debugHud("[HUD-DEBUG] TestHud options: type="
            + bossEntityType
            + " maxHealth=" + bossEntityMaxHealth
            + " positionMode=" + bossPositionMode
            + " offsetY=" + bossOffsetY
            + " forward=" + bossForwardOffset
            + " invisible=" + bossInvisibleEntity
            + " follow=" + bossFollowPlayer);

        if (state.bossBarEntityId != -1) {
            hideBossBar(player, state);
        }

        try {
            sendBossBar(
                player,
                0.75F,
                "§bTest BossBar §a75% §7- KjobsUltimate",
                state);

            state.testBossBarUntilMs =
                System.currentTimeMillis()
                    + bossTestDurationMs;

            debugHud("[HUD-DEBUG] TestHud auto-hide dans "
                + bossTestDurationMs
                + "ms pour "
                + player.getName());
        } finally {
            bossEntityType = previousEntityType;
            bossPositionMode = previousPositionMode;
            bossInvisibleEntity = previousInvisible;
            bossEntityMaxHealth = previousMaxHealth;
        }
    }

    /**
     * Recharge les valeurs mises en cache depuis hud.yml.
     */
    public void reloadHudConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg =
            plugin.getConfigManager().getHudConfig();

        bossEnabled =
            cfg.getBoolean("bossbar.enabled", true);

        bossResetMs =
            secondsToMillis(
                cfg.getLong(
                    "bossbar.bossbar_timing_reset",
                    8L));

        long updateTicks =
            Math.max(
                1L,
                cfg.getLong(
                    "bossbar.update_interval_ticks",
                    40L));

        bossUpdateMs =
            safeMultiply(updateTicks, 50L);

        bossOffsetY =
            cfg.getDouble(
                "bossbar.entity_offset_y",
                -30.0D);

        bossForwardOffset =
            cfg.getDouble(
                "bossbar.entity_forward_offset",
                0.0D);

        bossPositionMode =
            normalizeBossPositionMode(
                cfg.getString(
                    "bossbar.position_mode",
                    "FRONT"));

        bossFollowPlayer =
            cfg.getBoolean(
                "bossbar.follow_player",
                true);

        bossInvisibleEntity =
            cfg.getBoolean(
                "bossbar.invisible_entity",
                true);

        bossMinProgress =
            clamp(
                cfg.getDouble(
                    "bossbar.minimum_progress",
                    0.05D),
                0.01D,
                1.0D);

        bossEntityType =
            normalizeBossEntityType(
                cfg.getString(
                    "bossbar.entity_type",
                    "WITHER"));

        double defaultMaxHealth =
            "ENDER_DRAGON".equals(bossEntityType)
                ? 200.0D
                : 300.0D;

        double configuredMaxHealth =
            cfg.getDouble(
                "bossbar.max_health",
                defaultMaxHealth);

        bossEntityMaxHealth =
            (float) Math.max(
                1.0D,
                configuredMaxHealth);

        bossTestDurationMs =
            secondsToMillis(
                Math.max(
                    1L,
                    cfg.getLong(
                        "bossbar.test_duration_seconds",
                        8L)));

        if ("ENDER_DRAGON".equals(bossEntityType)
                && Math.abs(
                    bossEntityMaxHealth - 200.0F)
                    > 0.001F) {

            KjobLogger.warn(
                "[HUD] bossbar.entity_type=ENDER_DRAGON "
                    + "utilise normalement max_health=200.0 "
                    + "en Minecraft 1.8.");
        }

        abEnabled =
            cfg.getBoolean(
                "actionbar.enabled",
                true);

        abFormat =
            nonNull(
                cfg.getString(
                    "actionbar.format",
                    "&b{job} Lv.&e{level} &8| "
                        + "&a+{xp_gained} XP "
                        + "&8(&7{xp}&8/&7{xp_next}&8)"),
                "");

        abDisplayMs =
            secondsToMillis(
                Math.max(
                    0L,
                    cfg.getLong(
                        "actionbar.display_duration",
                        3L)));

        hudWindowMs =
            Math.max(
                50L,
                cfg.getLong(
                    "actionbar.accumulation_window_ms",
                    800L));

        debugHud("[HUD-DEBUG] Config bossbar: enabled="
            + bossEnabled
            + " type=" + bossEntityType
            + " maxHealth=" + bossEntityMaxHealth
            + " positionMode=" + bossPositionMode
            + " offsetY=" + bossOffsetY
            + " forward=" + bossForwardOffset
            + " invisible=" + bossInvisibleEntity
            + " follow=" + bossFollowPlayer
            + " resetMs=" + bossResetMs
            + " testMs=" + bossTestDurationMs
            + " updateMs=" + bossUpdateMs);
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask =
            Bukkit.getScheduler().runTaskTimer(
                plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        tick();
                    }
                },
                2L,
                2L);
    }

    /**
     * Enregistre un gain d'XP pour l'affichage.
     *
     * Le paramètre xpGained est conservé pour l'API publique existante.
     * Lorsque LevelUpResult est disponible, getXpActual() reste la source de
     * vérité afin de ne jamais afficher l'XP brute avant multiplicateurs/caps.
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
                || jobId.trim().isEmpty()) {
            return;
        }

        if (!player.isOnline()
                || !data.isHudEnabled()
                || (!data.isActionBarHudEnabled()
                    && !data.isBossBarHudEnabled())) {
            return;
        }

        JobDefinition job =
            plugin.getJobRegistry().getJob(jobId);

        if (job == null) {
            return;
        }

        int actualXp =
            result == null
                ? Math.max(0, xpGained)
                : Math.max(0, result.getXpActual());

        /*
         * Aucun affichage pour un gain refusé, par exemple niveau maximum,
         * plafond quotidien ou multiplicateur à zéro.
         */
        if (actualXp <= 0) {
            return;
        }

        UUID uuid = player.getUniqueId();
        PlayerHudState state =
            getOrCreateState(uuid);

        long now = System.currentTimeMillis();

        if (data.isActionBarHudEnabled()) {
            boolean jobChanged =
                !job.getId().equals(
                    state.accumulatingJobId);

            boolean windowExpired =
                state.windowStartMs > 0L
                    && now - state.windowStartMs
                        > hudWindowMs;

            boolean newWindow =
                state.windowStartMs <= 0L
                    || state.accumulatedXp <= 0;

            if (jobChanged || windowExpired || newWindow) {
                if (state.accumulatedXp > 0
                        && state.accumulatingJobId != null) {

                    flushActionBar(
                        player,
                        data,
                        state);
                }

                state.accumulatedXp = 0;
                state.accumulatingJobId =
                    job.getId();
                state.windowStartMs = now;
            }

            state.accumulatedXp =
                saturatingAdd(
                    state.accumulatedXp,
                    actualXp);
        } else {
            clearActionBarState(state);
        }

        state.lastXpMs = now;
        state.testBossBarUntilMs = 0L;

        updateSnapshot(data, job, state);
    }

    /**
     * Affiche le popup de passage de niveau.
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

        org.bukkit.configuration.file.FileConfiguration hudConfig =
            plugin.getConfigManager().getHudConfig();

        boolean respectHudToggle =
            hudConfig.getBoolean(
                "achievement.respect_hud_toggle",
                false);

        if (respectHudToggle
                && !data.isHudEnabled()) {

            debugHud(
                "[HUD-DEBUG] onLevelUp annulé : HUD désactivé pour "
                    + player.getName());

            return;
        }

        if (!hudConfig.getBoolean(
                "achievement.enabled",
                true)) {

            debugHud(
                "[HUD-DEBUG] onLevelUp annulé : "
                    + "achievement.enabled=false.");

            return;
        }

        UUID uuid = player.getUniqueId();
        PlayerHudState state =
            getOrCreateState(uuid);

        long now = System.currentTimeMillis();
        long cooldown =
            Math.max(
                0L,
                hudConfig.getLong(
                    "achievement.popup_cooldown_ms",
                    2000L));

        if (now - state.lastPopupMs < cooldown) {
            debugHud(
                "[HUD-DEBUG] onLevelUp annulé : cooldown actif - "
                    + (cooldown
                        - (now - state.lastPopupMs))
                    + "ms restant pour "
                    + player.getName());

            return;
        }

        state.lastPopupMs = now;

        JobDefinition job =
            plugin.getJobRegistry().getJob(jobId);

        String jobName =
            job == null
                ? jobId
                : job.getDisplayName();

        int safeNewLevel =
            job == null
                ? Math.max(0, newLevel)
                : Math.max(
                    0,
                    Math.min(
                        job.getMaxLevel(),
                        newLevel));

        String rawTitle =
            nonNull(
                hudConfig.getString(
                    "achievement.title",
                    "§6§lNIVEAU {level}"),
                "");

        String rawSubtitle =
            nonNull(
                hudConfig.getString(
                    "achievement.subtitle",
                    "§b{job} §7atteint !"),
                "");

        int fadeIn =
            Math.max(
                0,
                hudConfig.getInt(
                    "achievement.fade_in",
                    10));

        int stay =
            Math.max(
                0,
                hudConfig.getInt(
                    "achievement.stay",
                    50));

        int fadeOut =
            Math.max(
                0,
                hudConfig.getInt(
                    "achievement.fade_out",
                    15));

        String title =
            colorize(
                rawTitle
                    .replace("{job}", jobName)
                    .replace(
                        "{level}",
                        String.valueOf(
                            safeNewLevel)));

        String subtitle =
            colorize(
                rawSubtitle
                    .replace("{job}", jobName)
                    .replace(
                        "{level}",
                        String.valueOf(
                            safeNewLevel)));

        debugHud(
            "[HUD-DEBUG] Achievement popup -> joueur="
                + player.getName()
                + " titre=\"" + title + "\""
                + " sous-titre=\"" + subtitle + "\""
                + " fadeIn=" + fadeIn
                + " stay=" + stay
                + " fadeOut=" + fadeOut);

        String mode =
            nonNull(
                hudConfig.getString(
                    "achievement.mode",
                    "TITLE_AND_CHAT"),
                "TITLE_AND_CHAT")
                .trim()
                .toUpperCase();

        if (mode.contains("TITLE")) {
            sendTitle(
                player,
                title,
                subtitle,
                fadeIn,
                stay,
                fadeOut);
        }

        if (mode.contains("ACTIONBAR")
                && data.isActionBarHudEnabled()) {

            String actionbar =
                nonNull(
                    hudConfig.getString(
                        "achievement.actionbar",
                        "&6&lNIVEAU {level} &8- &b{job}"),
                    "");

            sendActionBar(
                player,
                colorize(
                    actionbar
                        .replace("{job}", jobName)
                        .replace(
                            "{level}",
                            String.valueOf(
                                safeNewLevel))));

            debugHud(
                "[HUD-DEBUG] Achievement actionbar envoyé à "
                    + player.getName());
        }

        boolean sentChat = false;

        if (mode.contains("CHAT")) {
            sendAchievementChat(
                player,
                hudConfig,
                jobName,
                safeNewLevel,
                "mode");

            sentChat = true;
        }

        if (!sentChat
                && hudConfig.getBoolean(
                    "achievement.force_chat_fallback",
                    true)) {

            sendAchievementChat(
                player,
                hudConfig,
                jobName,
                safeNewLevel,
                "force_chat_fallback");
        }

        sendVanillaAchievementToast(
            player,
            hudConfig,
            jobId);
    }

    private void sendAchievementChat(
            Player player,
            org.bukkit.configuration.file.FileConfiguration hudConfig,
            String jobName,
            int newLevel,
            String source) {

        String chat =
            nonNull(
                hudConfig.getString(
                    "achievement.chat",
                    "&6&lNIVEAU {level} &8- &b{job}"),
                "");

        String message =
            colorize(
                chat
                    .replace("{job}", jobName)
                    .replace(
                        "{level}",
                        String.valueOf(newLevel)));

        if (!message.isEmpty()) {
            player.sendMessage(message);
        }

        debugHud(
            "[HUD-DEBUG] Achievement chat envoyé à "
                + player.getName()
                + " source=" + source
                + " message=\"" + message + "\"");
    }

    private void sendVanillaAchievementToast(
            final Player player,
            org.bukkit.configuration.file.FileConfiguration hudConfig,
            String jobId) {

        if (!hudConfig.getBoolean(
                "achievement.vanilla_toast.enabled",
                false)) {
            return;
        }

        String key =
            hudConfig.getString(
                "achievement.vanilla_toast.mapping."
                    + jobId,
                hudConfig.getString(
                    "achievement.vanilla_toast.achievement",
                    "OPEN_INVENTORY"));

        final org.bukkit.Achievement achievement;

        try {
            achievement =
                org.bukkit.Achievement.valueOf(
                    key.trim()
                        .toUpperCase()
                        .replace('-', '_'));
        } catch (Exception failure) {
            KjobLogger.warn(
                "[HUD] Achievement vanilla inconnu dans hud.yml : "
                    + key);
            return;
        }

        String method =
            nonNull(
                hudConfig.getString(
                    "achievement.vanilla_toast.method",
                    "BUKKIT"),
                "BUKKIT")
                .trim()
                .toUpperCase()
                .replace('-', '_');

        if ("VANILLA".equals(method)
                || "AWARD".equals(method)) {
            method = "BUKKIT";
        }

        if ("STATISTIC".equals(method)
                || "NMS".equals(method)) {
            method = "PACKET";
        }

        if ("PACKET".equals(method)) {
            sendAchievementStatisticPacket(
                player,
                achievement);
            return;
        }

        if ("PACKET_THEN_BUKKIT".equals(method)) {
            sendAchievementStatisticPacket(
                player,
                achievement);

            int delay =
                Math.max(
                    1,
                    hudConfig.getInt(
                        "achievement.vanilla_toast."
                            + "bukkit_after_packet_ticks",
                        2));

            Bukkit.getScheduler().runTaskLater(
                plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            sendBukkitAchievementToast(
                                player,
                                plugin.getConfigManager()
                                    .getHudConfig(),
                                achievement);
                        }
                    }
                },
                delay);

            return;
        }

        if (!"BUKKIT".equals(method)) {
            KjobLogger.warn(
                "[HUD] achievement.vanilla_toast.method inconnu : "
                    + method
                    + " - fallback BUKKIT");
        }

        sendBukkitAchievementToast(
            player,
            hudConfig,
            achievement);
    }

    private boolean sendAchievementStatisticPacket(
            Player player,
            org.bukkit.Achievement achievement) {

        try {
            Class<?> craftPlayerClass =
                Class.forName(
                    "org.bukkit.craftbukkit."
                        + NMS
                        + ".entity.CraftPlayer");

            Class<?> packetClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".Packet");

            Class<?> statisticPacketClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".PacketPlayOutStatistic");

            Class<?> achievementListClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".AchievementList");

            String fieldName =
                achievementListField(achievement);

            if (fieldName == null) {
                debugHud(
                    "[HUD-DEBUG] Achievement packet mapping absent pour "
                        + achievement.name());
                return false;
            }

            Object nmsAchievement =
                achievementListClass
                    .getField(fieldName)
                    .get(null);

            java.util.Map<Object, Integer> stats =
                new java.util.HashMap<Object, Integer>();

            stats.put(
                nmsAchievement,
                Integer.valueOf(1));

            Object packet =
                statisticPacketClass
                    .getConstructor(java.util.Map.class)
                    .newInstance(stats);

            sendPacketTo(
                player,
                packet,
                craftPlayerClass,
                packetClass);

            debugHud(
                "[HUD-DEBUG] Achievement packet toast envoyé à "
                    + player.getName()
                    + " achievement="
                    + achievement.name()
                    + " field=" + fieldName);

            return true;
        } catch (Exception failure) {
            Throwable cause =
                unwrap(failure);

            debugHud(
                "[HUD-DEBUG] Achievement packet toast impossible : "
                    + cause.getClass().getSimpleName()
                    + " "
                    + cause.getMessage());

            return false;
        }
    }

    private void sendBukkitAchievementToast(
            final Player player,
            org.bukkit.configuration.file.FileConfiguration hudConfig,
            final org.bukkit.Achievement achievement) {

        final boolean hadBefore;

        try {
            hadBefore =
                player.hasAchievement(achievement);

            if (hadBefore
                    || hudConfig.getBoolean(
                        "achievement.vanilla_toast.force_reaward",
                        true)) {

                player.removeAchievement(achievement);
            }
        } catch (Exception failure) {
            debugHud(
                "[HUD-DEBUG] Achievement vanilla remove impossible : "
                    + failure.getClass().getSimpleName()
                    + " "
                    + failure.getMessage());
            return;
        }

        final boolean restoreIfNew =
            hudConfig.getBoolean(
                "achievement.vanilla_toast."
                    + "restore_if_not_previously_awarded",
                true);

        final int restoreTicks =
            Math.max(
                5,
                hudConfig.getInt(
                    "achievement.vanilla_toast.restore_after_ticks",
                    60));

        Bukkit.getScheduler().runTaskLater(
            plugin,
            new Runnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        return;
                    }

                    try {
                        player.awardAchievement(achievement);

                        debugHud(
                            "[HUD-DEBUG] Achievement vanilla toast envoyé à "
                                + player.getName()
                                + " achievement="
                                + achievement.name()
                                + " hadBefore="
                                + hadBefore);
                    } catch (Exception failure) {
                        debugHud(
                            "[HUD-DEBUG] Achievement vanilla award impossible : "
                                + failure.getClass()
                                    .getSimpleName()
                                + " "
                                + failure.getMessage());

                        org.bukkit.Achievement fallback =
                            getFallbackAchievement(
                                achievement);

                        if (fallback != null) {
                            debugHud(
                                "[HUD-DEBUG] Achievement vanilla fallback vers "
                                    + fallback.name()
                                    + " pour "
                                    + player.getName());

                            sendBukkitAchievementToast(
                                player,
                                plugin.getConfigManager()
                                    .getHudConfig(),
                                fallback);
                        }

                        return;
                    }

                    if (!hadBefore && restoreIfNew) {
                        Bukkit.getScheduler().runTaskLater(
                            plugin,
                            new Runnable() {
                                @Override
                                public void run() {
                                    if (!player.isOnline()) {
                                        return;
                                    }

                                    try {
                                        player.removeAchievement(
                                            achievement);

                                        debugHud(
                                            "[HUD-DEBUG] Achievement vanilla "
                                                + "restauré/retiré pour "
                                                + player.getName()
                                                + " achievement="
                                                + achievement.name());
                                    } catch (Exception failure) {
                                        debugHud(
                                            "[HUD-DEBUG] Achievement vanilla "
                                                + "restore impossible : "
                                                + failure.getClass()
                                                    .getSimpleName()
                                                + " "
                                                + failure.getMessage());
                                    }
                                }
                            },
                            restoreTicks);
                    }
                }
            },
            1L);
    }

    private org.bukkit.Achievement getFallbackAchievement(
            org.bukkit.Achievement current) {

        String key =
            plugin.getConfigManager()
                .getHudConfig()
                .getString(
                    "achievement.vanilla_toast."
                        + "fallback_achievement",
                    "OPEN_INVENTORY");

        try {
            org.bukkit.Achievement fallback =
                org.bukkit.Achievement.valueOf(
                    key.trim()
                        .toUpperCase()
                        .replace('-', '_'));

            return fallback == current
                ? null
                : fallback;
        } catch (Exception failure) {
            debugHud(
                "[HUD-DEBUG] Achievement fallback invalide : "
                    + key);

            return current
                    == org.bukkit.Achievement.OPEN_INVENTORY
                ? null
                : org.bukkit.Achievement.OPEN_INVENTORY;
        }
    }

    private String achievementListField(
            org.bukkit.Achievement achievement) {

        switch (achievement) {
            case OPEN_INVENTORY:
                return "f";
            case MINE_WOOD:
                return "g";
            case BUILD_WORKBENCH:
                return "h";
            case BUILD_PICKAXE:
                return "i";
            case BUILD_FURNACE:
                return "j";
            case ACQUIRE_IRON:
                return "k";
            case BUILD_HOE:
                return "l";
            case MAKE_BREAD:
                return "m";
            case BAKE_CAKE:
                return "n";
            case BUILD_BETTER_PICKAXE:
                return "o";
            case COOK_FISH:
                return "p";
            case ON_A_RAIL:
                return "q";
            case BUILD_SWORD:
                return "r";
            case KILL_ENEMY:
                return "s";
            case KILL_COW:
                return "t";
            case FLY_PIG:
                return "u";
            case SNIPE_SKELETON:
                return "v";
            case GET_DIAMONDS:
                return "w";
            case DIAMONDS_TO_YOU:
                return "x";
            case NETHER_PORTAL:
                return "y";
            case GHAST_RETURN:
                return "z";
            case GET_BLAZE_ROD:
                return "A";
            case BREW_POTION:
                return "B";
            case END_PORTAL:
                return "C";
            case THE_END:
                return "D";
            case ENCHANTMENTS:
                return "E";
            case OVERKILL:
                return "F";
            case BOOKCASE:
                return "G";
            case BREED_COW:
                return "H";
            case SPAWN_WITHER:
                return "I";
            case KILL_WITHER:
                return "J";
            case FULL_BEACON:
                return "K";
            case EXPLORE_ALL_BIOMES:
                return "L";
            case OVERPOWERED:
                return "M";
            default:
                return null;
        }
    }

    /**
     * Supprime l'état HUD d'un joueur et masque sa bossbar.
     */
    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }

        PlayerHudState state =
            states.remove(player.getUniqueId());

        if (state != null) {
            hideBossBar(player, state);
        }
    }

    public void clearActionBar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PlayerHudState state =
            states.get(player.getUniqueId());

        if (state != null) {
            clearActionBarState(state);
        }

        sendActionBar(player, "");
    }

    /**
     * Arrête le scheduler et nettoie toutes les bossbars actives.
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        for (Map.Entry<UUID, PlayerHudState> entry
                : states.entrySet()) {

            Player player =
                Bukkit.getPlayer(entry.getKey());

            if (player != null) {
                hideBossBar(
                    player,
                    entry.getValue());
            } else {
                resetBossBarState(
                    entry.getValue());
            }
        }

        states.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, PlayerHudState> entry
                : states.entrySet()) {

            UUID uuid = entry.getKey();
            PlayerHudState state = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                if (states.remove(uuid, state)) {
                    resetBossBarState(state);
                }
                continue;
            }

            if (state.testBossBarUntilMs > 0L) {
                if (now >= state.testBossBarUntilMs) {
                    hideBossBar(player, state);
                    state.testBossBarUntilMs = 0L;
                }
                continue;
            }

            PlayerData data =
                plugin.getPlayerDataManager().get(player);

            if (data == null || !data.isHudEnabled()) {
                if (state.bossBarEntityId != -1) {
                    hideBossBar(player, state);
                }
                continue;
            }

            if (abEnabled
                    && data.isActionBarHudEnabled()
                    && state.accumulatedXp > 0
                    && state.accumulatingJobId != null
                    && now - state.windowStartMs
                        > hudWindowMs) {

                flushActionBar(
                    player,
                    data,
                    state);

                state.accumulatedXp = 0;
                state.windowStartMs = 0L;
            }

            if (abEnabled
                    && data.isActionBarHudEnabled()
                    && state.cachedActionBarMsg != null
                    && now < state.displayUntilMs) {

                sendActionBar(
                    player,
                    state.cachedActionBarMsg);
            } else if (state.cachedActionBarMsg != null
                    && now >= state.displayUntilMs) {

                state.cachedActionBarMsg = null;
                state.displayUntilMs = 0L;
            }

            if (bossEnabled
                    && data.isBossBarHudEnabled()
                    && state.snapshotJobId != null) {

                boolean shouldStayVisible =
                    bossResetMs <= 0L
                        || now - state.lastXpMs
                            < bossResetMs;

                if (shouldStayVisible) {
                    if (now
                            - state.lastBossBarRefreshMs
                            >= bossUpdateMs) {

                        refreshBossBar(
                            player,
                            data,
                            state);

                        state.lastBossBarRefreshMs =
                            now;
                    }
                } else if (state.bossBarEntityId != -1) {
                    hideBossBar(player, state);
                }
            } else if (state.bossBarEntityId != -1) {
                hideBossBar(player, state);
            }
        }
    }

    private void flushActionBar(
            Player player,
            PlayerData data,
            PlayerHudState state) {

        if (player == null
                || !player.isOnline()
                || state.accumulatingJobId == null) {
            return;
        }

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(state.accumulatingJobId);

        if (job == null) {
            clearActionBarState(state);
            return;
        }

        /*
         * Si le snapshot correspond au même métier, on l'utilise afin de
         * conserver l'état exact associé à la fenêtre d'accumulation.
         * Sinon, on recalcule depuis PlayerData.
         */
        if (!job.getId().equals(state.snapshotJobId)) {
            updateSnapshot(data, job, state);
        }

        int percent =
            calculatePercent(
                state.snapshotLevel,
                job.getMaxLevel(),
                state.snapshotXp,
                state.snapshotXpNext);

        String message =
            colorize(
                abFormat
                    .replace(
                        "{job}",
                        job.getDisplayName())
                    .replace(
                        "{level}",
                        String.valueOf(
                            state.snapshotLevel))
                    .replace(
                        "{xp}",
                        String.valueOf(
                            state.snapshotXp))
                    .replace(
                        "{xp_next}",
                        String.valueOf(
                            state.snapshotXpNext))
                    .replace(
                        "{xp_gained}",
                        String.valueOf(
                            Math.max(
                                0,
                                state.accumulatedXp)))
                    .replace(
                        "{percent}",
                        String.valueOf(percent)));

        state.cachedActionBarMsg = message;
        state.displayUntilMs =
            System.currentTimeMillis()
                + abDisplayMs;

        sendActionBar(player, message);
    }

    private void clearActionBarState(
            PlayerHudState state) {

        state.accumulatedXp = 0;
        state.accumulatingJobId = null;
        state.windowStartMs = 0L;
        state.cachedActionBarMsg = null;
        state.displayUntilMs = 0L;
    }

    private void refreshBossBar(
            Player player,
            PlayerData data,
            PlayerHudState state) {

        String jobId = state.snapshotJobId;

        JobDefinition job =
            plugin.getJobRegistry().getJob(jobId);

        if (job == null) {
            hideBossBar(player, state);
            return;
        }

        updateSnapshot(data, job, state);

        int level = state.snapshotLevel;
        int xp = state.snapshotXp;
        int xpNext = state.snapshotXpNext;

        float progress =
            LevelUtil.getProgressPercent(
                data,
                job);

        String format =
            level >= job.getMaxLevel()
                ? plugin.getConfigManager()
                    .getHudConfig()
                    .getString(
                        "bossbar.title_format_max_level",
                        "&b{job} Lv.&e{level} &8| &6MAX")
                : plugin.getConfigManager()
                    .getHudConfig()
                    .getString(
                        "bossbar.title_format",
                        "&b{job} Lv.&e{level} "
                            + "&8| &a{xp}&8/&a{xp_next} XP");

        int percent =
            LevelUtil.getProgressPercentage(
                data,
                job);

        String title =
            colorize(
                nonNull(format, "")
                    .replace(
                        "{job}",
                        job.getDisplayName())
                    .replace(
                        "{level}",
                        String.valueOf(level))
                    .replace(
                        "{xp}",
                        String.valueOf(xp))
                    .replace(
                        "{xp_next}",
                        String.valueOf(xpNext))
                    .replace(
                        "{percent}",
                        String.valueOf(percent)));

        sendBossBar(
            player,
            progress,
            title,
            state);
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
                    data.getLevel(job.getId())));

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
                job);

        state.snapshotXpNext =
            LevelUtil.getRequiredXpForNextLevel(
                data,
                job);
    }

    private void sendActionBar(
            Player player,
            String message) {

        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            Class<?> craftPlayerClass =
                Class.forName(
                    "org.bukkit.craftbukkit."
                        + NMS
                        + ".entity.CraftPlayer");

            Class<?> packetClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".PacketPlayOutChat");

            Class<?> chatBaseClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".IChatBaseComponent");

            Class<?> chatSerializerClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".IChatBaseComponent$ChatSerializer");

            Object chatComponent =
                chatSerializerClass
                    .getMethod("a", String.class)
                    .invoke(
                        null,
                        "{\"text\":\""
                            + escapeJson(
                                nonNull(message, ""))
                            + "\"}");

            Object packet =
                packetClass
                    .getConstructor(
                        chatBaseClass,
                        byte.class)
                    .newInstance(
                        chatComponent,
                        (byte) 2);

            Object handle =
                craftPlayerClass
                    .getMethod("getHandle")
                    .invoke(player);

            Object connection =
                handle.getClass()
                    .getField("playerConnection")
                    .get(handle);

            connection.getClass()
                .getMethod(
                    "sendPacket",
                    Class.forName(
                        "net.minecraft.server."
                            + NMS
                            + ".Packet"))
                .invoke(connection, packet);
        } catch (Exception failure) {
            Throwable cause =
                unwrap(failure);

            KjobLogger.warn(
                "[HUD] ActionBar NMS "
                    + cause.getClass().getSimpleName()
                    + " : "
                    + cause.getMessage());

            if (plugin.getConfigManager().isDebugHud()) {
                cause.printStackTrace();
            }
        }
    }

    private void sendTitle(
            Player player,
            String title,
            String subtitle,
            int fadeIn,
            int stay,
            int fadeOut) {

        if (player == null || !player.isOnline()) {
            return;
        }

        debugHud(
            "[HUD-DEBUG] sendTitle -> "
                + player.getName()
                + " titre=\"" + title + "\""
                + " sous-titre=\"" + subtitle + "\"");

        try {
            Class<?> craftPlayerClass =
                Class.forName(
                    "org.bukkit.craftbukkit."
                        + NMS
                        + ".entity.CraftPlayer");

            Class<?> craftChatMessageClass =
                Class.forName(
                    "org.bukkit.craftbukkit."
                        + NMS
                        + ".util.CraftChatMessage");

            Class<?> packetTitleClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".PacketPlayOutTitle");

            Class<?> enumActionClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".PacketPlayOutTitle$EnumTitleAction");

            Class<?> chatBaseClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".IChatBaseComponent");

            Class<?> packetClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".Packet");

            Object handle =
                craftPlayerClass
                    .getMethod("getHandle")
                    .invoke(player);

            Object connection =
                handle.getClass()
                    .getField("playerConnection")
                    .get(handle);

            Method sendPacket =
                connection.getClass()
                    .getMethod(
                        "sendPacket",
                        packetClass);

            if (plugin.getConfigManager()
                    .getHudConfig()
                    .getBoolean(
                        "achievement.reset_before_send",
                        true)) {

                Object resetAction =
                    enumActionClass
                        .getField("RESET")
                        .get(null);

                Object resetPacket =
                    packetTitleClass
                        .getConstructor(
                            enumActionClass,
                            chatBaseClass)
                        .newInstance(
                            resetAction,
                            null);

                sendPacket.invoke(
                    connection,
                    resetPacket);
            }

            Object timesAction =
                enumActionClass
                    .getField("TIMES")
                    .get(null);

            Object timesPacket =
                packetTitleClass
                    .getConstructor(
                        enumActionClass,
                        chatBaseClass,
                        int.class,
                        int.class,
                        int.class)
                    .newInstance(
                        timesAction,
                        null,
                        fadeIn,
                        stay,
                        fadeOut);

            sendPacket.invoke(
                connection,
                timesPacket);

            Object[] titleComponents =
                (Object[]) craftChatMessageClass
                    .getMethod(
                        "fromString",
                        String.class)
                    .invoke(
                        null,
                        nonNull(title, ""));

            if (titleComponents.length > 0) {
                Object titleAction =
                    enumActionClass
                        .getField("TITLE")
                        .get(null);

                Object titlePacket =
                    packetTitleClass
                        .getConstructor(
                            enumActionClass,
                            chatBaseClass,
                            int.class,
                            int.class,
                            int.class)
                        .newInstance(
                            titleAction,
                            titleComponents[0],
                            fadeIn,
                            stay,
                            fadeOut);

                sendPacket.invoke(
                    connection,
                    titlePacket);
            }

            if (subtitle != null
                    && !subtitle.isEmpty()) {

                Object[] subtitleComponents =
                    (Object[]) craftChatMessageClass
                        .getMethod(
                            "fromString",
                            String.class)
                        .invoke(
                            null,
                            subtitle);

                if (subtitleComponents.length > 0) {
                    Object subtitleAction =
                        enumActionClass
                            .getField("SUBTITLE")
                            .get(null);

                    Object subtitlePacket =
                        packetTitleClass
                            .getConstructor(
                                enumActionClass,
                                chatBaseClass,
                                int.class,
                                int.class,
                                int.class)
                            .newInstance(
                                subtitleAction,
                                subtitleComponents[0],
                                fadeIn,
                                stay,
                                fadeOut);

                    sendPacket.invoke(
                        connection,
                        subtitlePacket);
                }
            }
        } catch (Exception failure) {
            Throwable cause =
                unwrap(failure);

            KjobLogger.warn(
                "[HUD] Title NMS "
                    + cause.getClass().getSimpleName()
                    + " : "
                    + cause.getMessage());

            if (plugin.getConfigManager().isDebugHud()) {
                cause.printStackTrace();
            }
        }
    }

    /**
     * Bossbar 1.8 basée sur une fausse entité envoyée uniquement au client.
     */
    private void sendBossBar(
            Player player,
            float progress,
            String title,
            PlayerHudState state) {

        if (player == null
                || !player.isOnline()
                || state == null) {
            return;
        }

        float safeProgress =
            (float) clamp(progress, 0.0D, 1.0D);

        try {
            Class<?> craftWorldClass =
                Class.forName(
                    "org.bukkit.craftbukkit."
                        + NMS
                        + ".CraftWorld");

            Class<?> worldServerClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".WorldServer");

            String entityTypeForPacket =
                state.nmsWither != null
                        && state.bossEntityType != null
                    ? state.bossEntityType
                    : bossEntityType;

            float maxHealthForPacket =
                state.nmsWither != null
                        && state.bossEntityMaxHealth > 0.0F
                    ? state.bossEntityMaxHealth
                    : bossEntityMaxHealth;

            Class<?> entityWitherClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + "."
                        + bossEntityClassName(
                            entityTypeForPacket));

            Class<?> entityLivingClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".EntityLiving");

            Class<?> entityClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".Entity");

            Class<?> spawnPacketClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".PacketPlayOutSpawnEntityLiving");

            Class<?> metadataPacketClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".PacketPlayOutEntityMetadata");

            Class<?> teleportPacketClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".PacketPlayOutEntityTeleport");

            Class<?> dataWatcherClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".DataWatcher");

            Class<?> craftPlayerClass =
                Class.forName(
                    "org.bukkit.craftbukkit."
                        + NMS
                        + ".entity.CraftPlayer");

            Class<?> packetClass =
                Class.forName(
                    "net.minecraft.server."
                        + NMS
                        + ".Packet");

            Object nmsWorld =
                craftWorldClass
                    .getMethod("getHandle")
                    .invoke(player.getWorld());

            float minimumHealth =
                (float) (
                    bossMinProgress
                        * maxHealthForPacket);

            float health =
                Math.max(
                    minimumHealth,
                    safeProgress
                        * maxHealthForPacket);

            health =
                Math.max(
                    1.0F,
                    Math.min(
                        maxHealthForPacket,
                        health));

            BossBarLocation bossLocation =
                computeBossBarLocation(player);

            if (state.nmsWither == null) {
                Class<?> worldClass =
                    worldServerClass.getSuperclass();

                Object wither =
                    entityWitherClass
                        .getConstructor(worldClass)
                        .newInstance(nmsWorld);

                int customId =
                    nextFakeEntityId();

                entityWitherClass
                    .getMethod("d", int.class)
                    .invoke(wither, customId);

                entityWitherClass
                    .getMethod(
                        "setPosition",
                        double.class,
                        double.class,
                        double.class)
                    .invoke(
                        wither,
                        bossLocation.x,
                        bossLocation.y,
                        bossLocation.z);

                applyBossBarVisibility(wither);

                entityWitherClass
                    .getMethod(
                        "setCustomName",
                        String.class)
                    .invoke(
                        wither,
                        nonNull(title, ""));

                entityWitherClass
                    .getMethod(
                        "setHealth",
                        float.class)
                    .invoke(
                        wither,
                        health);

                state.nmsWither = wither;
                state.bossBarEntityId = customId;
                state.bossEntityType =
                    entityTypeForPacket;
                state.bossEntityMaxHealth =
                    maxHealthForPacket;

                Object spawnPacket =
                    spawnPacketClass
                        .getConstructor(
                            entityLivingClass)
                        .newInstance(wither);

                debugHud(
                    "[HUD-DEBUG] BossBar spawn pour "
                        + player.getName()
                        + " entityId=" + customId
                        + " health=" + health
                        + "/" + maxHealthForPacket
                        + " pos=" + bossLocation
                        + " mode=" + bossPositionMode
                        + " titre=\"" + title + "\"");

                sendPacketTo(
                    player,
                    spawnPacket,
                    craftPlayerClass,
                    packetClass);

                Method getDataWatcher =
                    findMethod(
                        wither.getClass(),
                        "getDataWatcher");

                if (getDataWatcher != null) {
                    Object dataWatcher =
                        getDataWatcher.invoke(wither);

                    Object metadataPacket =
                        metadataPacketClass
                            .getConstructor(
                                int.class,
                                dataWatcherClass,
                                boolean.class)
                            .newInstance(
                                customId,
                                dataWatcher,
                                true);

                    sendPacketTo(
                        player,
                        metadataPacket,
                        craftPlayerClass,
                        packetClass);
                }
            } else {
                if (bossFollowPlayer) {
                    entityWitherClass
                        .getMethod(
                            "setPosition",
                            double.class,
                            double.class,
                            double.class)
                        .invoke(
                            state.nmsWither,
                            bossLocation.x,
                            bossLocation.y,
                            bossLocation.z);

                    Object teleportPacket =
                        teleportPacketClass
                            .getConstructor(
                                entityClass)
                            .newInstance(
                                state.nmsWither);

                    sendPacketTo(
                        player,
                        teleportPacket,
                        craftPlayerClass,
                        packetClass);
                }

                entityWitherClass
                    .getMethod(
                        "setHealth",
                        float.class)
                    .invoke(
                        state.nmsWither,
                        health);

                entityWitherClass
                    .getMethod(
                        "setCustomName",
                        String.class)
                    .invoke(
                        state.nmsWither,
                        nonNull(title, ""));

                applyBossBarVisibility(
                    state.nmsWither);

                Method getDataWatcher =
                    findMethod(
                        state.nmsWither.getClass(),
                        "getDataWatcher");

                if (getDataWatcher == null) {
                    return;
                }

                Object dataWatcher =
                    getDataWatcher.invoke(
                        state.nmsWither);

                Object metadataPacket =
                    metadataPacketClass
                        .getConstructor(
                            int.class,
                            dataWatcherClass,
                            boolean.class)
                        .newInstance(
                            state.bossBarEntityId,
                            dataWatcher,
                            true);

                sendPacketTo(
                    player,
                    metadataPacket,
                    craftPlayerClass,
                    packetClass);
            }
        } catch (Exception failure) {
            Throwable cause =
                unwrap(failure);

            KjobLogger.warn(
                "[HUD] BossBar NMS "
                    + cause.getClass().getSimpleName()
                    + " : "
                    + cause.getMessage());

            if (plugin.getConfigManager().isDebugHud()) {
                cause.printStackTrace();
            }
        }
    }

    private void hideBossBar(
            Player player,
            PlayerHudState state) {

        if (state == null
                || state.bossBarEntityId == -1) {
            return;
        }

        try {
            if (player != null
                    && player.isOnline()) {

                Class<?> destroyPacketClass =
                    Class.forName(
                        "net.minecraft.server."
                            + NMS
                            + ".PacketPlayOutEntityDestroy");

                Class<?> craftPlayerClass =
                    Class.forName(
                        "org.bukkit.craftbukkit."
                            + NMS
                            + ".entity.CraftPlayer");

                Class<?> packetClass =
                    Class.forName(
                        "net.minecraft.server."
                            + NMS
                            + ".Packet");

                Object destroyPacket =
                    destroyPacketClass
                        .getConstructor(int[].class)
                        .newInstance(
                            (Object) new int[] {
                                state.bossBarEntityId
                            });

                sendPacketTo(
                    player,
                    destroyPacket,
                    craftPlayerClass,
                    packetClass);
            }
        } catch (Exception failure) {
            KjobLogger.warn(
                "[HUD] BossBar destroy NMS "
                    + failure.getClass().getSimpleName()
                    + " : "
                    + failure.getMessage());
        } finally {
            resetBossBarState(state);
        }
    }

    private void resetBossBarState(
            PlayerHudState state) {

        state.bossBarEntityId = -1;
        state.nmsWither = null;
        state.bossEntityType = null;
        state.bossEntityMaxHealth = 0.0F;
        state.testBossBarUntilMs = 0L;
        state.lastBossBarRefreshMs = 0L;
    }

    private void sendPacketTo(
            Player player,
            Object packet,
            Class<?> craftPlayerClass,
            Class<?> packetClass)
            throws Exception {

        Object handle =
            craftPlayerClass
                .getMethod("getHandle")
                .invoke(player);

        Object connection =
            handle.getClass()
                .getField("playerConnection")
                .get(handle);

        connection.getClass()
            .getMethod(
                "sendPacket",
                packetClass)
            .invoke(connection, packet);
    }

    private BossBarLocation computeBossBarLocation(
            Player player) {

        Location location =
            player.getLocation();

        String mode =
            normalizeBossPositionMode(
                bossPositionMode);

        if ("FRONT".equals(mode)) {
            double distance =
                bossForwardOffset != 0.0D
                    ? bossForwardOffset
                    : 24.0D;

            Vector direction =
                location.getDirection();

            direction.setY(0.0D);

            if (direction.lengthSquared() < 0.001D) {
                direction =
                    new Vector(0.0D, 0.0D, 1.0D);
            }

            direction.normalize();

            return new BossBarLocation(
                location.getX()
                    + direction.getX()
                    * distance,
                location.getY() + 1.5D,
                location.getZ()
                    + direction.getZ()
                    * distance);
        }

        if ("EYE_FRONT".equals(mode)) {
            Location eye =
                player.getEyeLocation();

            double distance =
                bossForwardOffset != 0.0D
                    ? bossForwardOffset
                    : 24.0D;

            Vector direction =
                eye.getDirection();

            if (direction.lengthSquared() < 0.001D) {
                direction =
                    new Vector(0.0D, 0.0D, 1.0D);
            }

            direction.normalize();

            return new BossBarLocation(
                eye.getX()
                    + direction.getX()
                    * distance,
                eye.getY()
                    + direction.getY()
                    * distance,
                eye.getZ()
                    + direction.getZ()
                    * distance);
        }

        double x = location.getX();
        double y;
        double z = location.getZ();

        if ("PLAYER".equals(mode)) {
            y = location.getY();
        } else if ("ABOVE".equals(mode)) {
            double offset =
                bossOffsetY == 0.0D
                    ? 30.0D
                    : Math.abs(bossOffsetY);

            y = location.getY() + offset;
        } else {
            y = location.getY() + bossOffsetY;
        }

        if (bossForwardOffset != 0.0D) {
            Vector direction =
                location.getDirection();

            x += direction.getX()
                * bossForwardOffset;

            y += direction.getY()
                * bossForwardOffset;

            z += direction.getZ()
                * bossForwardOffset;
        }

        return new BossBarLocation(x, y, z);
    }

    private void applyBossBarVisibility(
            Object wither) {

        try {
            wither.getClass()
                .getMethod(
                    "setInvisible",
                    boolean.class)
                .invoke(
                    wither,
                    bossInvisibleEntity);
        } catch (Exception failure) {
            if (bossInvisibleEntity) {
                debugHud(
                    "[HUD-DEBUG] setInvisible indisponible : "
                        + failure.getClass()
                            .getSimpleName()
                        + " "
                        + failure.getMessage());
            }
        }
    }

    private String bossEntityClassName(
            String entityType) {

        return "ENDER_DRAGON".equals(entityType)
            ? "EntityEnderDragon"
            : "EntityWither";
    }

    private String normalizeBossEntityType(
            String value) {

        String normalized =
            value == null
                ? "WITHER"
                : value.trim()
                    .toUpperCase()
                    .replace('-', '_');

        if ("DRAGON".equals(normalized)) {
            normalized = "ENDER_DRAGON";
        }

        if (!"WITHER".equals(normalized)
                && !"ENDER_DRAGON".equals(
                    normalized)) {

            KjobLogger.warn(
                "[HUD] bossbar.entity_type invalide : "
                    + value
                    + " - fallback WITHER");

            return "WITHER";
        }

        return normalized;
    }

    private String normalizeBossPositionMode(
            String value) {

        String normalized =
            value == null
                ? "FRONT"
                : value.trim()
                    .toUpperCase()
                    .replace('-', '_');

        if ("EYEFRONT".equals(normalized)) {
            normalized = "EYE_FRONT";
        }

        if ("UNDER".equals(normalized)
                || "DOWN".equals(normalized)) {
            normalized = "BELOW";
        }

        if ("UP".equals(normalized)) {
            normalized = "ABOVE";
        }

        if (!"BELOW".equals(normalized)
                && !"ABOVE".equals(normalized)
                && !"FRONT".equals(normalized)
                && !"EYE_FRONT".equals(normalized)
                && !"PLAYER".equals(normalized)) {

            KjobLogger.warn(
                "[HUD] bossbar.position_mode invalide : "
                    + value
                    + " - fallback FRONT");

            return "FRONT";
        }

        return normalized;
    }

    private float defaultMaxHealthFor(
            String entityType) {

        return "ENDER_DRAGON".equals(
                normalizeBossEntityType(entityType))
            ? 200.0F
            : 300.0F;
    }

    private PlayerHudState getOrCreateState(
            UUID uuid) {

        PlayerHudState existing =
            states.get(uuid);

        if (existing != null) {
            return existing;
        }

        PlayerHudState created =
            new PlayerHudState();

        PlayerHudState concurrent =
            states.putIfAbsent(
                uuid,
                created);

        return concurrent == null
            ? created
            : concurrent;
    }

    private static int calculatePercent(
            int currentLevel,
            int maxLevel,
            int currentXp,
            int requiredXp) {

        if (currentLevel >= maxLevel) {
            return 100;
        }

        if (requiredXp <= 0) {
            return 0;
        }

        double ratio =
            (double) Math.max(0, currentXp)
                / (double) requiredXp;

        if (Double.isNaN(ratio)
                || Double.isInfinite(ratio)) {
            return 0;
        }

        return Math.max(
            0,
            Math.min(
                100,
                (int) Math.floor(
                    ratio * 100.0D)));
    }

    private static int saturatingAdd(
            int first,
            int second) {

        long total =
            (long) first + (long) second;

        if (total >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        if (total <= 0L) {
            return 0;
        }

        return (int) total;
    }

    private static int nextFakeEntityId() {
        int id =
            FAKE_ENTITY_COUNTER.getAndIncrement();

        /*
         * Protection théorique après une très longue durée de vie du serveur.
         * La plage repart à une valeur élevée positive.
         */
        if (id <= 0
                || id >= Integer.MAX_VALUE - 1000) {

            FAKE_ENTITY_COUNTER.compareAndSet(
                id + 1,
                800_000);

            return 800_000;
        }

        return id;
    }

    private static long secondsToMillis(
            long seconds) {

        return safeMultiply(
            Math.max(0L, seconds),
            1000L);
    }

    private static long safeMultiply(
            long value,
            long multiplier) {

        if (value <= 0L || multiplier <= 0L) {
            return 0L;
        }

        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }

        return value * multiplier;
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
            Math.min(maximum, value));
    }

    private static String escapeJson(
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
                                "\\u%04x",
                                (int) character));
                    } else {
                        escaped.append(character);
                    }
                    break;
            }
        }

        return escaped.toString();
    }

    private static String colorize(
            String text) {

        return nonNull(text, "")
            .replace('&', '§');
    }

    private void debugHud(
            String message) {

        if (plugin.getConfigManager()
                .isDebugHud()) {

            KjobLogger.info(message);
        }
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

    /**
     * Cherche une méthode sans paramètre en remontant la hiérarchie.
     */
    private static Method findMethod(
            Class<?> start,
            String name) {

        Class<?> type = start;

        while (type != null) {
            for (Method method
                    : type.getDeclaredMethods()) {

                if (method.getName().equals(name)
                        && method.getParameterTypes().length
                            == 0) {

                    method.setAccessible(true);
                    return method;
                }
            }

            type = type.getSuperclass();
        }

        return null;
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

        @Override
        public String toString() {
            return "x=" + round(x)
                + ",y=" + round(y)
                + ",z=" + round(z);
        }

        private static double round(
                double value) {

            return Math.round(
                value * 10.0D)
                / 10.0D;
        }
    }

    private static final class PlayerHudState {

        // Accumulation actionbar.
        private String accumulatingJobId;
        private int accumulatedXp;
        private long windowStartMs;
        private long lastXpMs;

        // Affichage actionbar.
        private String cachedActionBarMsg;
        private long displayUntilMs;

        // Snapshot de progression.
        private String snapshotJobId;
        private int snapshotLevel;
        private int snapshotXp;
        private int snapshotXpNext;

        // Bossbar.
        private Object nmsWither;
        private int bossBarEntityId = -1;
        private String bossEntityType;
        private float bossEntityMaxHealth;
        private long lastBossBarRefreshMs;
        private long testBossBarUntilMs;

        // Popup de niveau.
        private long lastPopupMs;
    }
}