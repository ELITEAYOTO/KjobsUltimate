package me.krunsh.kjobultimate.hud;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère le HUD en jeu : actionbar (XP accumulé), bossbar (progression) et
 * title popup (level-up). Toutes les opérations NMS se font par réflexion.
 *
 * Cycle de vie :
 *   onXpGain()  ← appelé par chaque listener après attribution XP
 *   onLevelUp() ← appelé par XpManager.handleLevelUp()
 *   shutdown()  ← appelé par KjobUltimate.onDisable()
 */
public final class HudManager {

    private final KjobUltimate plugin;
    /** Version NMS détectée au démarrage (ex: "v1_8_R3"). */
    private final String NMS;

    private final Map<UUID, PlayerHudState> states = new ConcurrentHashMap<>();
    /** IDs d'entités fictifs (bossbar) dans une plage haute pour éviter tout conflit avec les entités serveur réelles. */
    private static final java.util.concurrent.atomic.AtomicInteger FAKE_ENTITY_COUNTER =
        new java.util.concurrent.atomic.AtomicInteger(800_000);
    private BukkitTask updateTask;

    // ─── Valeurs config cachées (rafraîchies par reloadHudConfig) ──────────────────────
    private boolean bossEnabled;
    private long    bossResetMs;
    private long    bossUpdateMs;
    private double  bossOffsetY;
    private double  bossForwardOffset;
    private String  bossPositionMode;
    private boolean bossFollowPlayer;
    private boolean bossInvisibleEntity;
    private double  bossMinProgress;
    private String  bossEntityType;
    private float   bossEntityMaxHealth;
    private long    bossTestDurationMs;
    private boolean abEnabled;
    private String  abFormat;
    private long    abDisplayMs;
    private long    hudWindowMs;

    public HudManager(KjobUltimate plugin) {
        this.plugin = plugin;
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        this.NMS = pkg.substring(pkg.lastIndexOf('.') + 1);
        reloadHudConfig();
        startUpdateTask();
        KjobLogger.success("HudManager actif (" + NMS + ") — actionbar + bossbar + level-up popup.");
    }

    /** Retourne la version NMS détectée (ex: "v1_8_R3"). Utile pour le debug. */
    public String getNMS() { return NMS; }

    public boolean isActionBarEnabled() { return abEnabled; }
    public boolean isBossBarEnabled() { return bossEnabled; }
    public long getBossUpdateMs() { return bossUpdateMs; }
    public int getTrackedPlayers() { return states.size(); }

    /**
     * Envoie directement une bossbar de test à 75% pour valider la chaîne NMS.
     * Indépendant des données joueur (XP = 0 ne fausse pas le résultat).
     */
    public void testBossBar(Player player) {
        testBossBar(player, null, null, null);
    }

    /**
     * Variante debug utilisable par /kjobs testhud pour tester plusieurs strategies
     * sans modifier hud.yml entre chaque essai.
     */
    public void testBossBar(Player player, String entityTypeOverride, String positionModeOverride, Boolean invisibleOverride) {
        UUID uuid = player.getUniqueId();
        PlayerHudState state = states.computeIfAbsent(uuid, k -> new PlayerHudState());

        String previousEntityType = bossEntityType;
        String previousPositionMode = bossPositionMode;
        boolean previousInvisible = bossInvisibleEntity;
        float previousMaxHealth = bossEntityMaxHealth;

        if (entityTypeOverride != null && !entityTypeOverride.trim().isEmpty()) {
            bossEntityType = normalizeBossEntityType(entityTypeOverride);
            bossEntityMaxHealth = defaultMaxHealthFor(bossEntityType);
        }
        if (positionModeOverride != null && !positionModeOverride.trim().isEmpty()) {
            bossPositionMode = normalizeBossPositionMode(positionModeOverride);
        }
        if (invisibleOverride != null) {
            bossInvisibleEntity = invisibleOverride.booleanValue();
        }

        debugHud("[HUD-DEBUG] TestHud options: type=" + bossEntityType
            + " maxHealth=" + bossEntityMaxHealth
            + " positionMode=" + bossPositionMode
            + " offsetY=" + bossOffsetY
            + " forward=" + bossForwardOffset
            + " invisible=" + bossInvisibleEntity
            + " follow=" + bossFollowPlayer);

        // Détruire l'éventuelle bossbar précédente pour forcer un nouveau spawn
        if (state.bossBarEntityId != -1) hideBossBar(player, state);
        try {
            sendBossBar(player, 0.75f, "§bTest BossBar §a75% §7— KjobUltimate", state);
            state.testBossBarUntilMs = System.currentTimeMillis() + bossTestDurationMs;
            debugHud("[HUD-DEBUG] TestHud auto-hide dans " + bossTestDurationMs + "ms pour " + player.getName());
        } finally {
            bossEntityType = previousEntityType;
            bossPositionMode = previousPositionMode;
            bossInvisibleEntity = previousInvisible;
            bossEntityMaxHealth = previousMaxHealth;
        }
    }

    /**
     * Recharge les valeurs cachées depuis hud.yml.
     * À appeler depuis KjobAdminCommand.handleReload() après configManager.loadAll().
     */
    public void reloadHudConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfigManager().getHudConfig();
        bossEnabled  = cfg.getBoolean("bossbar.enabled", true);
        bossResetMs  = cfg.getLong("bossbar.bossbar_timing_reset", 8L) * 1000L;
        bossUpdateMs = cfg.getLong("bossbar.update_interval_ticks", 40L) * 50L;
        bossOffsetY = cfg.getDouble("bossbar.entity_offset_y", -30.0D);
        bossForwardOffset = cfg.getDouble("bossbar.entity_forward_offset", 0.0D);
        bossPositionMode = normalizeBossPositionMode(cfg.getString("bossbar.position_mode", "FRONT"));
        bossFollowPlayer = cfg.getBoolean("bossbar.follow_player", true);
        bossInvisibleEntity = cfg.getBoolean("bossbar.invisible_entity", true);
        bossMinProgress = Math.max(0.01D, Math.min(1.0D, cfg.getDouble("bossbar.minimum_progress", 0.05D)));
        bossEntityType = normalizeBossEntityType(cfg.getString("bossbar.entity_type", "WITHER"));
        double configuredMaxHealth = cfg.getDouble("bossbar.max_health", "ENDER_DRAGON".equals(bossEntityType) ? 200.0D : 300.0D);
        bossEntityMaxHealth = (float) Math.max(1.0D, configuredMaxHealth);
        bossTestDurationMs = Math.max(1L, cfg.getLong("bossbar.test_duration_seconds", 8L)) * 1000L;
        if ("ENDER_DRAGON".equals(bossEntityType) && Math.abs(bossEntityMaxHealth - 200.0F) > 0.001F) {
            KjobLogger.warn("[HUD] bossbar.entity_type=ENDER_DRAGON utilise normalement max_health=200.0 en 1.8.");
        }
        abEnabled    = cfg.getBoolean("actionbar.enabled", true);
        abFormat     = cfg.getString("actionbar.format",
            "&b{job} Lv.&e{level} &8| &a+{xp_gained} XP &8(&7{xp}&8/&7{xp_next}&8)");
        abDisplayMs  = cfg.getLong("actionbar.display_duration", 3L) * 1000L;
        hudWindowMs  = cfg.getLong("actionbar.accumulation_window_ms", 800L);

        debugHud("[HUD-DEBUG] Config bossbar: enabled=" + bossEnabled
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
        // 2 ticks : l'actionbar s'efface automatiquement après ~2 secondes sans ré-envoi.
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    // ─── API publique ────────────────────────────────────────────────────────

    /**
     * À appeler depuis chaque listener XP après attribution de l'XP.
     *
     * @param player    Joueur concerné
     * @param data      Données RAM du joueur
     * @param jobId     Identifiant du job
     * @param xpGained  XP brut accordé (avant multiplicateurs)
     * @param result    Résultat du calcul XP (level up inclus)
     */
    public void onXpGain(Player player, PlayerData data, String jobId, int xpGained, LevelUpResult result) {
        if (!data.isHudEnabled() || (!data.isActionBarHudEnabled() && !data.isBossBarHudEnabled())) return;

        UUID uuid = player.getUniqueId();
        PlayerHudState state = states.computeIfAbsent(uuid, k -> new PlayerHudState());

        long now = System.currentTimeMillis();
        long windowMs = hudWindowMs;

        if (data.isActionBarHudEnabled()) {
            // Si changement de job ou fenêtre expirée, on purge d'abord l'accumulateur courant
            if (!jobId.equals(state.accumulatingJobId) || now - state.windowStartMs > windowMs) {
                if (state.accumulatedXp > 0 && state.accumulatingJobId != null) {
                    flushActionBar(player, data, state);
                }
                state.accumulatedXp = 0;
                state.accumulatingJobId = jobId;
                state.windowStartMs = now;
            }
            state.accumulatedXp += xpGained;
        } else {
            clearActionBarState(state);
        }

        state.lastXpMs = now;
        state.testBossBarUntilMs = 0L;

        // Snapshot des données d'affichage (niveau après attribution)
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        state.snapshotJobId  = jobId;
        state.snapshotLevel  = data.getLevel(jobId);
        state.snapshotXp     = data.getXP(jobId);
        state.snapshotXpNext = def != null ? def.getXpForLevel(data.getLevel(jobId)) : 0;
    }

    /**
     * À appeler depuis XpManager.handleLevelUp() pour afficher le title de level-up.
     *
     * @param player    Joueur concerné
     * @param data      Données RAM du joueur
     * @param jobId     Identifiant du job
     * @param newLevel  Nouveau niveau atteint
     */
    public void onLevelUp(Player player, PlayerData data, String jobId, int newLevel) {
        org.bukkit.configuration.file.FileConfiguration hudConfig = plugin.getConfigManager().getHudConfig();
        boolean respectHudToggle = hudConfig.getBoolean("achievement.respect_hud_toggle", false);
        if (respectHudToggle && !data.isHudEnabled()) {
            debugHud("[HUD-DEBUG] onLevelUp annulé: HUD désactivé pour " + player.getName());
            return;
        }
        if (!hudConfig.getBoolean("achievement.enabled", true)) {
            debugHud("[HUD-DEBUG] onLevelUp annulé: achievement.enabled=false dans hud.yml");
            return;
        }

        UUID uuid = player.getUniqueId();
        PlayerHudState state = states.computeIfAbsent(uuid, k -> new PlayerHudState());

        long now = System.currentTimeMillis();
        long cooldown = hudConfig.getLong("achievement.popup_cooldown_ms", 2000L);
        if (now - state.lastPopupMs < cooldown) {
            debugHud("[HUD-DEBUG] onLevelUp annulé: cooldown actif — "
                + (cooldown - (now - state.lastPopupMs)) + "ms restant pour " + player.getName());
            return;
        }
        state.lastPopupMs = now;

        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        String jobName = def != null ? def.getDisplayName() : jobId;

        String rawTitle    = hudConfig.getString("achievement.title",    "§6§lNIVEAU {level}");
        String rawSubtitle = hudConfig.getString("achievement.subtitle", "§b{job} §7atteint !");
        int fadeIn  = hudConfig.getInt("achievement.fade_in", 10);
        int stay    = hudConfig.getInt("achievement.stay", 50);
        int fadeOut = hudConfig.getInt("achievement.fade_out", 15);

        String title    = rawTitle.replace("{job}", jobName).replace("{level}", String.valueOf(newLevel));
        String subtitle = rawSubtitle.replace("{job}", jobName).replace("{level}", String.valueOf(newLevel));

        debugHud("[HUD-DEBUG] Achievement popup → joueur=" + player.getName()
            + " titre=\"" + title + "\" sous-titre=\"" + subtitle + "\""
            + " fadeIn=" + fadeIn + " stay=" + stay + " fadeOut=" + fadeOut);
        String mode = hudConfig.getString("achievement.mode", "TITLE_AND_CHAT").trim().toUpperCase();
        if (mode.contains("TITLE")) {
            sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
        }
        if (mode.contains("ACTIONBAR") && data.isActionBarHudEnabled()) {
            String actionbar = hudConfig.getString("achievement.actionbar", "&6&lNIVEAU {level} &8- &b{job}");
            sendActionBar(player, colorize(actionbar
                .replace("{job}", jobName)
                .replace("{level}", String.valueOf(newLevel))));
            debugHud("[HUD-DEBUG] Achievement actionbar fallback envoye a " + player.getName());
        }
        boolean sentChat = false;
        if (mode.contains("CHAT")) {
            sendAchievementChat(player, hudConfig, jobName, newLevel, "mode");
            sentChat = true;
        }
        if (!sentChat && hudConfig.getBoolean("achievement.force_chat_fallback", true)) {
            sendAchievementChat(player, hudConfig, jobName, newLevel, "force_chat_fallback");
        }
        sendVanillaAchievementToast(player, hudConfig, jobId);
    }

    private void sendAchievementChat(Player player, org.bukkit.configuration.file.FileConfiguration hudConfig,
            String jobName, int newLevel, String source) {
        String chat = hudConfig.getString("achievement.chat", "&6&lNIVEAU {level} &8- &b{job}");
        String message = colorize(chat
            .replace("{job}", jobName)
            .replace("{level}", String.valueOf(newLevel)));
        player.sendMessage(message);
        debugHud("[HUD-DEBUG] Achievement chat fallback envoye a " + player.getName()
            + " source=" + source + " message=\"" + message + "\"");
    }

    private void sendVanillaAchievementToast(final Player player,
            org.bukkit.configuration.file.FileConfiguration hudConfig, String jobId) {
        if (!hudConfig.getBoolean("achievement.vanilla_toast.enabled", false)) return;

        String key = hudConfig.getString("achievement.vanilla_toast.mapping." + jobId,
            hudConfig.getString("achievement.vanilla_toast.achievement", "OPEN_INVENTORY"));
        final org.bukkit.Achievement achievement;
        try {
            achievement = org.bukkit.Achievement.valueOf(key.trim().toUpperCase().replace('-', '_'));
        } catch (Exception ex) {
            KjobLogger.warn("[HUD] Achievement vanilla inconnu dans hud.yml: " + key);
            return;
        }

        String method = hudConfig.getString("achievement.vanilla_toast.method", "BUKKIT").trim().toUpperCase().replace('-', '_');
        if ("VANILLA".equals(method) || "AWARD".equals(method)) method = "BUKKIT";
        if ("STATISTIC".equals(method) || "NMS".equals(method)) method = "PACKET";

        if ("PACKET".equals(method)) {
            sendAchievementStatisticPacket(player, achievement);
            return;
        }

        if ("PACKET_THEN_BUKKIT".equals(method)) {
            sendAchievementStatisticPacket(player, achievement);
            int delay = Math.max(1, hudConfig.getInt("achievement.vanilla_toast.bukkit_after_packet_ticks", 2));
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        sendBukkitAchievementToast(player, plugin.getConfigManager().getHudConfig(), achievement);
                    }
                }
            }, delay);
            return;
        }

        if (!"BUKKIT".equals(method)) {
            KjobLogger.warn("[HUD] achievement.vanilla_toast.method inconnu: " + method + " - fallback BUKKIT");
        }
        sendBukkitAchievementToast(player, hudConfig, achievement);
    }

    private boolean sendAchievementStatisticPacket(Player player, org.bukkit.Achievement achievement) {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + NMS + ".entity.CraftPlayer");
            Class<?> packetClass = Class.forName("net.minecraft.server." + NMS + ".Packet");
            Class<?> statisticPacketClass = Class.forName("net.minecraft.server." + NMS + ".PacketPlayOutStatistic");
            Class<?> achievementListClass = Class.forName("net.minecraft.server." + NMS + ".AchievementList");

            String fieldName = achievementListField(achievement);
            if (fieldName == null) {
                debugHud("[HUD-DEBUG] Achievement packet mapping absent pour " + achievement.name());
                return false;
            }

            Object nmsAchievement = achievementListClass.getField(fieldName).get(null);
            java.util.Map<Object, Integer> stats = new java.util.HashMap<Object, Integer>();
            stats.put(nmsAchievement, Integer.valueOf(1));
            Object packet = statisticPacketClass.getConstructor(java.util.Map.class).newInstance(stats);
            sendPacketTo(player, packet, craftPlayerClass, packetClass);
            debugHud("[HUD-DEBUG] Achievement packet toast envoye a " + player.getName()
                + " achievement=" + achievement.name() + " field=" + fieldName
                + " (experimental: certains clients 1.8 ne montrent pas le toast)");
            return true;
        } catch (Exception ex) {
            Throwable cause = (ex instanceof InvocationTargetException && ex.getCause() != null) ? ex.getCause() : ex;
            debugHud("[HUD-DEBUG] Achievement packet toast impossible: "
                + cause.getClass().getSimpleName() + " " + cause.getMessage());
            return false;
        }
    }

    private void sendBukkitAchievementToast(final Player player,
            org.bukkit.configuration.file.FileConfiguration hudConfig, final org.bukkit.Achievement achievement) {

        final boolean hadBefore;
        try {
            hadBefore = player.hasAchievement(achievement);
            if (hadBefore || hudConfig.getBoolean("achievement.vanilla_toast.force_reaward", true)) {
                player.removeAchievement(achievement);
            }
        } catch (Exception ex) {
            debugHud("[HUD-DEBUG] Achievement vanilla remove impossible: " + ex.getClass().getSimpleName() + " " + ex.getMessage());
            return;
        }

        final boolean restoreIfNew = hudConfig.getBoolean("achievement.vanilla_toast.restore_if_not_previously_awarded", true);
        final int restoreTicks = Math.max(5, hudConfig.getInt("achievement.vanilla_toast.restore_after_ticks", 60));

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                try {
                    player.awardAchievement(achievement);
                    debugHud("[HUD-DEBUG] Achievement vanilla toast envoye a " + player.getName()
                        + " achievement=" + achievement.name() + " hadBefore=" + hadBefore);
                } catch (Exception ex) {
                    debugHud("[HUD-DEBUG] Achievement vanilla award impossible: "
                        + ex.getClass().getSimpleName() + " " + ex.getMessage());
                    org.bukkit.Achievement fallback = getFallbackAchievement(achievement);
                    if (fallback != null) {
                        debugHud("[HUD-DEBUG] Achievement vanilla fallback vers " + fallback.name()
                            + " pour " + player.getName());
                        sendBukkitAchievementToast(player, plugin.getConfigManager().getHudConfig(), fallback);
                    }
                    return;
                }

                if (!hadBefore && restoreIfNew) {
                    Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (!player.isOnline()) return;
                            try {
                                player.removeAchievement(achievement);
                                debugHud("[HUD-DEBUG] Achievement vanilla restaure/retire pour "
                                    + player.getName() + " achievement=" + achievement.name());
                            } catch (Exception ex) {
                                debugHud("[HUD-DEBUG] Achievement vanilla restore impossible: "
                                    + ex.getClass().getSimpleName() + " " + ex.getMessage());
                            }
                        }
                    }, restoreTicks);
                }
            }
        }, 1L);
    }

    private org.bukkit.Achievement getFallbackAchievement(org.bukkit.Achievement current) {
        String key = plugin.getConfigManager().getHudConfig()
            .getString("achievement.vanilla_toast.fallback_achievement", "OPEN_INVENTORY");
        try {
            org.bukkit.Achievement fallback = org.bukkit.Achievement.valueOf(key.trim().toUpperCase().replace('-', '_'));
            return fallback == current ? null : fallback;
        } catch (Exception ex) {
            debugHud("[HUD-DEBUG] Achievement fallback invalide: " + key);
            return current == org.bukkit.Achievement.OPEN_INVENTORY ? null : org.bukkit.Achievement.OPEN_INVENTORY;
        }
    }

    private String achievementListField(org.bukkit.Achievement achievement) {
        switch (achievement) {
            case OPEN_INVENTORY: return "f";
            case MINE_WOOD: return "g";
            case BUILD_WORKBENCH: return "h";
            case BUILD_PICKAXE: return "i";
            case BUILD_FURNACE: return "j";
            case ACQUIRE_IRON: return "k";
            case BUILD_HOE: return "l";
            case MAKE_BREAD: return "m";
            case BAKE_CAKE: return "n";
            case BUILD_BETTER_PICKAXE: return "o";
            case COOK_FISH: return "p";
            case ON_A_RAIL: return "q";
            case BUILD_SWORD: return "r";
            case KILL_ENEMY: return "s";
            case KILL_COW: return "t";
            case FLY_PIG: return "u";
            case SNIPE_SKELETON: return "v";
            case GET_DIAMONDS: return "w";
            case DIAMONDS_TO_YOU: return "x";
            case NETHER_PORTAL: return "y";
            case GHAST_RETURN: return "z";
            case GET_BLAZE_ROD: return "A";
            case BREW_POTION: return "B";
            case END_PORTAL: return "C";
            case THE_END: return "D";
            case ENCHANTMENTS: return "E";
            case OVERKILL: return "F";
            case BOOKCASE: return "G";
            case BREED_COW: return "H";
            case SPAWN_WITHER: return "I";
            case KILL_WITHER: return "J";
            case FULL_BEACON: return "K";
            case EXPLORE_ALL_BIOMES: return "L";
            case OVERPOWERED: return "M";
            default: return null;
        }
    }

    /**
     * Supprime l'état HUD d'un joueur et masque sa bossbar.
     * À appeler sur PlayerQuitEvent.
     */
    public void removePlayer(Player player) {
        PlayerHudState state = states.remove(player.getUniqueId());
        if (state != null) hideBossBar(player, state);
    }

    public void clearActionBar(Player player) {
        if (player == null || !player.isOnline()) return;
        PlayerHudState state = states.get(player.getUniqueId());
        if (state != null) clearActionBarState(state);
        sendActionBar(player, "");
    }

    /** Arrête le scheduler et nettoie toutes les bossbars actives. */
    public void shutdown() {
        if (updateTask != null) updateTask.cancel();
        for (UUID uuid : states.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            PlayerHudState state = states.get(uuid);
            if (p != null && state != null) hideBossBar(p, state);
        }
        states.clear();
    }

    // ─── Tick interne ────────────────────────────────────────────────────────

    private void tick() {
        long now = System.currentTimeMillis();

        for (UUID uuid : states.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                PlayerHudState state = states.remove(uuid);
                if (state != null) hideBossBar(null, state);
                continue;
            }

            PlayerHudState state = states.get(uuid);
            if (state == null) continue;

            if (state.testBossBarUntilMs > 0L) {
                if (now >= state.testBossBarUntilMs) {
                    hideBossBar(player, state);
                    state.testBossBarUntilMs = 0L;
                }
                continue;
            }

            PlayerData data = plugin.getPlayerDataManager().get(player);
            if (data == null || !data.isHudEnabled()) {
                if (state.bossBarEntityId != -1) hideBossBar(player, state);
                continue;
            }

            // Purge fenêtre d'accumulation expirée → envoi actionbar
            if (abEnabled && data.isActionBarHudEnabled() && state.accumulatedXp > 0 && state.accumulatingJobId != null
                    && now - state.windowStartMs > hudWindowMs) {
                flushActionBar(player, data, state);
                state.accumulatedXp = 0;
            }

            // Ré-envoi actionbar pendant la durée d'affichage (elle s'efface automatiquement)
            if (abEnabled && data.isActionBarHudEnabled() && state.cachedActionBarMsg != null && now < state.displayUntilMs) {
                sendActionBar(player, state.cachedActionBarMsg);
            }

            // Bossbar : rafraîchie selon bossUpdateMs (bossbar.update_interval_ticks)
            if (bossEnabled && data.isBossBarHudEnabled() && state.snapshotJobId != null) {
                boolean shouldStayVisible = bossResetMs <= 0L || now - state.lastXpMs < bossResetMs;
                if (shouldStayVisible) {
                    if (now - state.lastBossBarRefreshMs >= bossUpdateMs) {
                        refreshBossBar(player, data, state);
                        state.lastBossBarRefreshMs = now;
                    }
                } else if (state.bossBarEntityId != -1) {
                    hideBossBar(player, state);
                }
            } else if (state.bossBarEntityId != -1) {
                hideBossBar(player, state);
            }
        }
    }

    // ─── Actionbar ───────────────────────────────────────────────────────────

    private void flushActionBar(Player player, PlayerData data, PlayerHudState state) {
        long now = System.currentTimeMillis();

        JobDefinition def = plugin.getJobRegistry().getJob(state.accumulatingJobId);
        if (def == null) return;

        String fmt = abFormat;

        int xpNext = state.snapshotXpNext;
        int pct    = xpNext > 0 ? Math.min(100, (int)((double) state.snapshotXp / xpNext * 100)) : 100;

        String msg = colorize(fmt
            .replace("{job}",      def.getDisplayName())
            .replace("{level}",    String.valueOf(state.snapshotLevel))
            .replace("{xp}",       String.valueOf(state.snapshotXp))
            .replace("{xp_next}",  String.valueOf(xpNext))
            .replace("{xp_gained}", String.valueOf(state.accumulatedXp))
            .replace("{percent}",  String.valueOf(pct)));

        state.cachedActionBarMsg = msg;
        state.displayUntilMs = now + abDisplayMs;
        sendActionBar(player, msg);
    }

    private void clearActionBarState(PlayerHudState state) {
        state.accumulatedXp = 0;
        state.accumulatingJobId = null;
        state.windowStartMs = 0L;
        state.cachedActionBarMsg = null;
        state.displayUntilMs = 0L;
    }

    // ─── BossBar ─────────────────────────────────────────────────────────────

    private void refreshBossBar(Player player, PlayerData data, PlayerHudState state) {
        String jobId = state.snapshotJobId;
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        if (def == null) return;

        int level  = data.getLevel(jobId);
        int xp     = data.getXP(jobId);
        int xpNext = def.getXpForLevel(level);
        float progress = xpNext > 0 ? Math.min(1f, (float) xp / xpNext) : 1f;

        String fmt = level >= def.getMaxLevel()
            ? plugin.getConfigManager().getHudConfig().getString(
                "bossbar.title_format_max_level", "&b{job} Lv.&e{level} &8| &6MAX")
            : plugin.getConfigManager().getHudConfig().getString(
                "bossbar.title_format", "&b{job} Lv.&e{level} &8| &a{xp}&8/&a{xp_next} XP");

        String title = colorize(fmt
            .replace("{job}",     def.getDisplayName())
            .replace("{level}",   String.valueOf(level))
            .replace("{xp}",      String.valueOf(xp))
            .replace("{xp_next}", String.valueOf(xpNext))
            .replace("{percent}", String.valueOf((int)(progress * 100))));

        sendBossBar(player, progress, title, state);
    }

    // ─── NMS : ActionBar (PacketPlayOutChat type 2) ───────────────────────────

    private void sendActionBar(Player player, String message) {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + NMS + ".entity.CraftPlayer");
            Class<?> packetClass      = Class.forName("net.minecraft.server." + NMS + ".PacketPlayOutChat");
            Class<?> chatBaseClass    = Class.forName("net.minecraft.server." + NMS + ".IChatBaseComponent");
            Class<?> chatSerClass     = Class.forName("net.minecraft.server." + NMS + ".IChatBaseComponent$ChatSerializer");

            Object chatComp = chatSerClass.getMethod("a", String.class)
                .invoke(null, "{\"text\":\"" + escapeJson(message) + "\"}");
            Object packet = packetClass.getConstructor(chatBaseClass, byte.class)
                .newInstance(chatComp, (byte) 2);

            Object handle = craftPlayerClass.getMethod("getHandle").invoke(player);
            Object conn   = handle.getClass().getField("playerConnection").get(handle);
            conn.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + NMS + ".Packet"))
                .invoke(conn, packet);
        } catch (Exception e) {
            Throwable cause = (e instanceof InvocationTargetException && e.getCause() != null) ? e.getCause() : e;
            KjobLogger.warn("[HUD] ActionBar NMS " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            if (plugin.getConfigManager().isDebugHud()) cause.printStackTrace();
        }
    }

    // ─── NMS : Title (PacketPlayOutTitle) ────────────────────────────────────

    private void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        debugHud("[HUD-DEBUG] sendTitle → " + player.getName()
            + " titre=\"" + title + "\" sous-titre=\"" + subtitle + "\"");
        try {
            Class<?> craftPlayerClass   = Class.forName("org.bukkit.craftbukkit." + NMS + ".entity.CraftPlayer");
            Class<?> craftChatMsgClass  = Class.forName("org.bukkit.craftbukkit." + NMS + ".util.CraftChatMessage");
            Class<?> packetTitleClass   = Class.forName("net.minecraft.server." + NMS + ".PacketPlayOutTitle");
            Class<?> enumActionClass    = Class.forName("net.minecraft.server." + NMS + ".PacketPlayOutTitle$EnumTitleAction");
            Class<?> chatBaseClass      = Class.forName("net.minecraft.server." + NMS + ".IChatBaseComponent");
            Class<?> packetClass        = Class.forName("net.minecraft.server." + NMS + ".Packet");

            Object handle = craftPlayerClass.getMethod("getHandle").invoke(player);
            Object conn   = handle.getClass().getField("playerConnection").get(handle);
            Method sendPacket = conn.getClass().getMethod("sendPacket", packetClass);

            if (plugin.getConfigManager().getHudConfig().getBoolean("achievement.reset_before_send", true)) {
                Object resetAction = enumActionClass.getField("RESET").get(null);
                Object resetPacket = packetTitleClass
                    .getConstructor(enumActionClass, chatBaseClass)
                    .newInstance(resetAction, null);
                sendPacket.invoke(conn, resetPacket);
                debugHud("[HUD-DEBUG] Packet RESET envoye avant title");
            }

            // Timings — TIMES doit être envoyé en premier pour fixer les durées d'animation.
            Object timesAction = enumActionClass.getField("TIMES").get(null);
            Object timesPacket = packetTitleClass
                .getConstructor(enumActionClass, chatBaseClass, int.class, int.class, int.class)
                .newInstance(timesAction, null, fadeIn, stay, fadeOut);
            sendPacket.invoke(conn, timesPacket);
            debugHud("[HUD-DEBUG] Packet TIMES envoyé (fadeIn=" + fadeIn + " stay=" + stay + " fadeOut=" + fadeOut + ")");

            // CraftChatMessage.fromString() convertit les codes §-couleur en vrai composant
            // IChatBaseComponent (avec attributs color/bold/italic), contrairement à
            // ChatSerializer.a(json) qui crée un ChatComponentText avec des §-littéraux.
            // Certains clients 1.8 ignorent les §-codes dans le texte brut d'un composant JSON.
            Object[] titleComps = (Object[]) craftChatMsgClass.getMethod("fromString", String.class)
                .invoke(null, title);
            Object titleComp = titleComps[0];
            Object titleAction = enumActionClass.getField("TITLE").get(null);
            Object titlePacket = packetTitleClass
                .getConstructor(enumActionClass, chatBaseClass, int.class, int.class, int.class)
                .newInstance(titleAction, titleComp, fadeIn, stay, fadeOut);
            sendPacket.invoke(conn, titlePacket);
            debugHud("[HUD-DEBUG] Packet TITLE envoyé");

            // Sous-titre
            if (subtitle != null && !subtitle.isEmpty()) {
                Object[] subComps = (Object[]) craftChatMsgClass.getMethod("fromString", String.class)
                    .invoke(null, subtitle);
                Object subComp = subComps[0];
                Object subAction = enumActionClass.getField("SUBTITLE").get(null);
                Object subPacket = packetTitleClass
                    .getConstructor(enumActionClass, chatBaseClass, int.class, int.class, int.class)
                    .newInstance(subAction, subComp, fadeIn, stay, fadeOut);
                sendPacket.invoke(conn, subPacket);
                debugHud("[HUD-DEBUG] Packet SUBTITLE envoyé");
            }
        } catch (Exception e) {
            Throwable cause = (e instanceof InvocationTargetException && e.getCause() != null) ? e.getCause() : e;
            KjobLogger.warn("[HUD] Title NMS " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            if (plugin.getConfigManager().isDebugHud()) cause.printStackTrace();
        }
    }

    // ─── NMS : BossBar (faux EntityWither côté client) ───────────────────────

    private void sendBossBar(Player player, float progress, String title, PlayerHudState state) {
        try {
            Class<?> craftWorldClass   = Class.forName("org.bukkit.craftbukkit." + NMS + ".CraftWorld");
            Class<?> worldServerClass  = Class.forName("net.minecraft.server." + NMS + ".WorldServer");
            String entityTypeForPacket = state.nmsWither != null && state.bossEntityType != null
                ? state.bossEntityType
                : bossEntityType;
            float maxHealthForPacket = state.nmsWither != null && state.bossEntityMaxHealth > 0.0F
                ? state.bossEntityMaxHealth
                : bossEntityMaxHealth;
            Class<?> entityWitherClass = Class.forName("net.minecraft.server." + NMS + "." + bossEntityClassName(entityTypeForPacket));
            Class<?> entityLivingClass = Class.forName("net.minecraft.server." + NMS + ".EntityLiving");
            Class<?> entityClass       = Class.forName("net.minecraft.server." + NMS + ".Entity");
            Class<?> spawnPktClass     = Class.forName("net.minecraft.server." + NMS + ".PacketPlayOutSpawnEntityLiving");
            Class<?> metaPktClass      = Class.forName("net.minecraft.server." + NMS + ".PacketPlayOutEntityMetadata");
            Class<?> teleportPktClass  = Class.forName("net.minecraft.server." + NMS + ".PacketPlayOutEntityTeleport");
            Class<?> dataWatcherClass  = Class.forName("net.minecraft.server." + NMS + ".DataWatcher");
            Class<?> craftPlayerClass  = Class.forName("org.bukkit.craftbukkit." + NMS + ".entity.CraftPlayer");
            Class<?> packetClass       = Class.forName("net.minecraft.server." + NMS + ".Packet");

            Object nmsWorld = craftWorldClass.getMethod("getHandle").invoke(player.getWorld());
            // La santé du boss controle le remplissage de la bossbar.
            // Minimum 15f (5%) pour garantir qu'elle soit visuellement présente à l'écran.
            float health = Math.max((float) (bossMinProgress * maxHealthForPacket), progress * maxHealthForPacket);
            BossBarLocation bossLocation = computeBossBarLocation(player);

            if (state.nmsWither == null) {
                // Premier affichage : créer l'entité wither côté serveur (non ajoutée au monde)
                // Le constructeur est EntityWither(World) — on passe par la superclasse de WorldServer
                Class<?> worldClass = worldServerClass.getSuperclass(); // World
                Object wither = entityWitherClass.getConstructor(worldClass).newInstance(nmsWorld);

                // Assigner un ID élevé personnalisé pour éviter toute collision avec les entités
                // réelles du serveur. Entity.d(int) = setEntityId. Le compteur global Entity.entityCount
                // a déjà été incrémenté par le constructeur, mais on écrase l'ID ici.
                int customId = FAKE_ENTITY_COUNTER.getAndIncrement();
                entityWitherClass.getMethod("d", int.class).invoke(wither, customId);

                // Position légèrement au-dessus du joueur.
                // IMPORTANT : en 1.8 le rendu de la bossbar a un rayon maximum (~192 blocs).
                // y+260 dépasse ce rayon → bossbar invisible. On utilise y+30 (wither hors champ de vision).
                // Positionne l'entité à y+1000 : hors de tout rayon de rendu → modèle invisible.
                // NE PAS appeler setInvisible(true) : le flag 0x20 dans DataWatcher index 0
                // fait croire au client 1.8 que l'entité est invisible, et il n'affiche pas la
                // boss bar pour les entités avec ce flag (vérifié sur Lunar Client et vanilla).
                entityWitherClass.getMethod("setPosition", double.class, double.class, double.class)
                    .invoke(wither, bossLocation.x, bossLocation.y, bossLocation.z);
                applyBossBarVisibility(wither);

                entityWitherClass.getMethod("setCustomName", String.class).invoke(wither, title);
                entityWitherClass.getMethod("setHealth", float.class).invoke(wither, health);

                int entityId = customId;
                state.nmsWither = wither;
                state.bossBarEntityId = entityId;
                state.bossEntityType = bossEntityType;
                state.bossEntityMaxHealth = bossEntityMaxHealth;

                Object spawnPkt = spawnPktClass.getConstructor(entityLivingClass).newInstance(wither);

                // Vérification diagnostic : lire le type d'entité stocké dans le packet
                try {
                    java.lang.reflect.Field fEntityType = spawnPktClass.getDeclaredField("b");
                    fEntityType.setAccessible(true);
                    int pktEntityType = fEntityType.getInt(spawnPkt);
                    debugHud("[HUD-DEBUG] BossBar spawné pour " + player.getName()
                        + " — entityId=" + entityId + " typeInPacket=" + pktEntityType
                        + " health=" + health + "/" + maxHealthForPacket
                        + " pos=" + bossLocation
                        + " mode=" + bossPositionMode
                        + " invisible=" + bossInvisibleEntity
                        + " titre=\"" + title + "\"");
                } catch (Exception dbgEx) {
                    debugHud("[HUD-DEBUG] BossBar spawné pour " + player.getName()
                        + " — entityId=" + entityId + " health=" + health + "/" + maxHealthForPacket
                        + " pos=" + bossLocation + " mode=" + bossPositionMode + " titre=\"" + title + "\"");
                }

                sendPacketTo(player, spawnPkt, craftPlayerClass, packetClass);

                // Certains clients (vanilla, Lunar, etc.) ne lisent pas le DataWatcher du
                // PacketPlayOutSpawnEntityLiving. On envoie un metadata packet explicite
                // immédiatement après pour garantir que la santé est prise en compte.
                Method getDataWatcherSpawn = findMethod(wither.getClass(), "getDataWatcher");
                if (getDataWatcherSpawn != null) {
                    Object dw = getDataWatcherSpawn.invoke(wither);

                    // Log diagnostic : afficher les entrées DataWatcher pour confirmer la santé
                    try {
                        java.util.List<?> dwEntries = (java.util.List<?>) dw.getClass().getMethod("c").invoke(dw);
                        StringBuilder sb = new StringBuilder("[HUD-DEBUG] DataWatcher (").append(dwEntries.size()).append(" entrées):");
                        for (Object e : dwEntries) {
                            int idx  = (int) e.getClass().getMethod("a").invoke(e);
                            int type = (int) e.getClass().getMethod("c").invoke(e);
                            Object val = e.getClass().getMethod("b").invoke(e);
                            sb.append(" [").append(idx).append("/t").append(type).append("=").append(val).append("]");
                        }
                        KjobLogger.info(sb.toString());
                    } catch (Exception dwEx) {
                        debugHud("[HUD-DEBUG] DataWatcher log impossible: " + dwEx.getMessage());
                    }

                    Object metaSpawnPkt = metaPktClass
                        .getConstructor(int.class, dataWatcherClass, boolean.class)
                        .newInstance(entityId, dw, true);
                    sendPacketTo(player, metaSpawnPkt, craftPlayerClass, packetClass);
                    debugHud("[HUD-DEBUG] BossBar metadata envoyé après spawn (health=" + health + "/" + maxHealthForPacket + ")");
                }

            } else {
                // Mise à jour : santé + titre puis metadata packet
                if (bossFollowPlayer) {
                    entityWitherClass.getMethod("setPosition", double.class, double.class, double.class)
                        .invoke(state.nmsWither, bossLocation.x, bossLocation.y, bossLocation.z);
                    Object teleportPkt = teleportPktClass.getConstructor(entityClass).newInstance(state.nmsWither);
                    sendPacketTo(player, teleportPkt, craftPlayerClass, packetClass);
                }
                entityWitherClass.getMethod("setHealth", float.class).invoke(state.nmsWither, health);
                entityWitherClass.getMethod("setCustomName", String.class).invoke(state.nmsWither, title);
                applyBossBarVisibility(state.nmsWither);

                Method getDataWatcher = findMethod(state.nmsWither.getClass(), "getDataWatcher");
                if (getDataWatcher == null) return;

                Object dw = getDataWatcher.invoke(state.nmsWither);
                Object metaPkt = metaPktClass
                    .getConstructor(int.class, dataWatcherClass, boolean.class)
                    .newInstance(state.bossBarEntityId, dw, true);
                sendPacketTo(player, metaPkt, craftPlayerClass, packetClass);
            }
        } catch (Exception e) {
            Throwable cause = (e instanceof InvocationTargetException && e.getCause() != null) ? e.getCause() : e;
            KjobLogger.warn("[HUD] BossBar NMS " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            if (plugin.getConfigManager().isDebugHud()) cause.printStackTrace();
        }
    }

    private void hideBossBar(Player player, PlayerHudState state) {
        if (state.bossBarEntityId == -1) return;
        try {
            if (player != null && player.isOnline()) {
                Class<?> destroyPktClass  = Class.forName("net.minecraft.server." + NMS + ".PacketPlayOutEntityDestroy");
                Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + NMS + ".entity.CraftPlayer");
                Class<?> packetClass      = Class.forName("net.minecraft.server." + NMS + ".Packet");

                Object destroyPkt = destroyPktClass.getConstructor(int[].class)
                    .newInstance((Object) new int[]{ state.bossBarEntityId });
                sendPacketTo(player, destroyPkt, craftPlayerClass, packetClass);
            }
        } catch (Exception e) {
            KjobLogger.warn("[HUD] BossBar destroy NMS \"" + e.getClass().getSimpleName() + "\": " + e.getMessage());
        } finally {
            state.bossBarEntityId = -1;
            state.nmsWither = null;
            state.bossEntityType = null;
            state.bossEntityMaxHealth = 0.0F;
            state.testBossBarUntilMs = 0L;
        }
    }

    private void sendPacketTo(Player player, Object packet,
            Class<?> craftPlayerClass, Class<?> packetClass) throws Exception {
        Object handle = craftPlayerClass.getMethod("getHandle").invoke(player);
        Object conn   = handle.getClass().getField("playerConnection").get(handle);
        conn.getClass().getMethod("sendPacket", packetClass).invoke(conn, packet);
    }

    // ─── Utilitaires ─────────────────────────────────────────────────────────

    private BossBarLocation computeBossBarLocation(Player player) {
        Location loc = player.getLocation();
        String mode = normalizeBossPositionMode(bossPositionMode);

        if ("FRONT".equals(mode)) {
            double distance = bossForwardOffset != 0.0D ? bossForwardOffset : 24.0D;
            Vector direction = loc.getDirection();
            direction.setY(0.0D);
            if (direction.lengthSquared() < 0.001D) direction = new Vector(0, 0, 1);
            direction.normalize();
            return new BossBarLocation(
                loc.getX() + direction.getX() * distance,
                loc.getY() + 1.5D,
                loc.getZ() + direction.getZ() * distance
            );
        }

        if ("EYE_FRONT".equals(mode)) {
            Location eye = player.getEyeLocation();
            double distance = bossForwardOffset != 0.0D ? bossForwardOffset : 24.0D;
            Vector direction = eye.getDirection();
            if (direction.lengthSquared() < 0.001D) direction = new Vector(0, 0, 1);
            direction.normalize();
            return new BossBarLocation(
                eye.getX() + direction.getX() * distance,
                eye.getY() + direction.getY() * distance,
                eye.getZ() + direction.getZ() * distance
            );
        }

        double x = loc.getX();
        double y;
        double z = loc.getZ();

        if ("PLAYER".equals(mode)) {
            y = loc.getY();
        } else if ("ABOVE".equals(mode)) {
            double offset = bossOffsetY == 0.0D ? 30.0D : Math.abs(bossOffsetY);
            y = loc.getY() + offset;
        } else {
            y = loc.getY() + bossOffsetY;
        }

        if (bossForwardOffset != 0.0D) {
            Vector direction = loc.getDirection();
            x += direction.getX() * bossForwardOffset;
            y += direction.getY() * bossForwardOffset;
            z += direction.getZ() * bossForwardOffset;
        }
        return new BossBarLocation(x, y, z);
    }

    private void applyBossBarVisibility(Object wither) {
        try {
            wither.getClass().getMethod("setInvisible", boolean.class).invoke(wither, bossInvisibleEntity);
        } catch (Exception e) {
            if (bossInvisibleEntity) {
                debugHud("[HUD-DEBUG] setInvisible indisponible: " + e.getClass().getSimpleName() + " " + e.getMessage());
            }
        }
    }

    private String bossEntityClassName(String entityType) {
        return "ENDER_DRAGON".equals(entityType) ? "EntityEnderDragon" : "EntityWither";
    }

    private String normalizeBossEntityType(String value) {
        String normalized = value == null ? "WITHER" : value.trim().toUpperCase().replace('-', '_');
        if ("DRAGON".equals(normalized)) normalized = "ENDER_DRAGON";
        if (!"WITHER".equals(normalized) && !"ENDER_DRAGON".equals(normalized)) {
            KjobLogger.warn("[HUD] bossbar.entity_type invalide: " + value + " - fallback WITHER");
            return "WITHER";
        }
        return normalized;
    }

    private String normalizeBossPositionMode(String value) {
        String normalized = value == null ? "BELOW" : value.trim().toUpperCase().replace('-', '_');
        if ("EYEFRONT".equals(normalized)) normalized = "EYE_FRONT";
        if ("UNDER".equals(normalized) || "DOWN".equals(normalized)) normalized = "BELOW";
        if ("UP".equals(normalized)) normalized = "ABOVE";
        if (!"BELOW".equals(normalized)
                && !"ABOVE".equals(normalized)
                && !"FRONT".equals(normalized)
                && !"EYE_FRONT".equals(normalized)
                && !"PLAYER".equals(normalized)) {
            KjobLogger.warn("[HUD] bossbar.position_mode invalide: " + value + " - fallback FRONT");
            return "FRONT";
        }
        return normalized;
    }

    private float defaultMaxHealthFor(String entityType) {
        return "ENDER_DRAGON".equals(normalizeBossEntityType(entityType)) ? 200.0F : 300.0F;
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }

    /** Cherche une méthode sans paramètre en remontant la hiérarchie de classes. */
    private void debugHud(String message) {
        if (plugin.getConfigManager().isDebugHud()) {
            KjobLogger.info(message);
        }
    }

    private static Method findMethod(Class<?> start, String name) {
        Class<?> klass = start;
        while (klass != null) {
            for (Method m : klass.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
            klass = klass.getSuperclass();
        }
        return null;
    }

    // ─── État par joueur ──────────────────────────────────────────────────────

    private static final class BossBarLocation {
        private final double x;
        private final double y;
        private final double z;

        private BossBarLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return "x=" + round(x) + ",y=" + round(y) + ",z=" + round(z);
        }

        private static double round(double value) {
            return Math.round(value * 10.0D) / 10.0D;
        }
    }

    private static final class PlayerHudState {
        // Accumulation actionbar
        String accumulatingJobId = null;
        int    accumulatedXp     = 0;
        long   windowStartMs     = 0;
        long   lastXpMs          = 0;

        // Affichage actionbar
        String cachedActionBarMsg = null;
        long   displayUntilMs    = 0;

        // Snapshot données affichage (mis à jour à chaque onXpGain)
        String snapshotJobId  = null;
        int    snapshotLevel  = 0;
        int    snapshotXp     = 0;
        int    snapshotXpNext = 0;

        // BossBar — faux EntityWither côté client
        Object nmsWither          = null;
        int    bossBarEntityId    = -1;
        String bossEntityType     = null;
        float  bossEntityMaxHealth = 0.0F;
        long   lastBossBarRefreshMs = 0;
        long   testBossBarUntilMs = 0L;

        // Level-up popup
        long lastPopupMs = 0;
    }
}
