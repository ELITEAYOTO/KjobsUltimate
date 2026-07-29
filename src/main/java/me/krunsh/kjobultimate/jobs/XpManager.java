package me.krunsh.kjobultimate.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.config.ConfigManager;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.entity.Player;

/**
 * Centralise tout le calcul XP : attribution, multiplicateurs, level up, récompenses.
 * Toutes les méthodes sont appelées depuis le main thread Bukkit.
 *
 * Ordre d'exécution (voir FLUX-XP-LEVELUP.md) :
 *   1. Vérifier les gates (gamemode, job actif, action valide…) — fait dans les listeners
 *   2. Calculer XP brut
 *   3. Appliquer multiplicateurs (permission + event)
 *   4. Mettre à jour RAM (PlayerData)
 *   5. Appliquer récompenses niveau si level up
 *   6. Notifier HUD + quêtes — délégué aux managers respectifs
 */
public final class XpManager {

    private final KjobUltimate plugin;

    public XpManager(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    /**
     * Ajoute de l'XP à un joueur pour un job donné.
     * Gère la boucle multi-level et les récompenses de niveau.
     *
     * @param player  Joueur cible (connecté)
     * @param data    Données RAM du joueur
     * @param jobId   Identifiant du job
     * @param baseXp  XP brut avant multiplicateurs
     * @return        Résultat du calcul (level up, nouveau niveau, XP restant)
     */
    public LevelUpResult addXP(Player player, PlayerData data, String jobId, int baseXp) {
        JobDefinition job = plugin.getJobRegistry().getJob(jobId);
        if (job == null) return LevelUpResult.noLevelUp(data.getLevel(jobId), data.getXP(jobId), 0);

        int currentLevel = data.getLevel(jobId);
        int maxLevel     = job.getMaxLevel();

        // Déjà au niveau max : aucun XP
        if (currentLevel >= maxLevel) {
            return LevelUpResult.maxLevel(maxLevel);
        }

        // Appliquer multiplicateurs (permission x event x bonus admin)
        double xpDouble = baseXp
            * getPermissionMultiplier(player)
            * getEventMultiplier()
            * data.getBonusMultiplier(jobId);
        int xp = Math.max(1, (int) Math.floor(xpDouble));

        // Mise à jour du daily XP (anti-abuse cap)
        data.addDailyXP(jobId, xp);

        int currentXP  = data.getXP(jobId) + xp;
        int levelsGained = 0;

        // Boucle multi-level
        while (currentLevel < maxLevel) {
            int xpRequired = job.getXpForLevel(currentLevel);
            if (xpRequired <= 0 || currentXP < xpRequired) break;
            currentXP -= xpRequired;
            currentLevel++;
            levelsGained++;
        }

        // Persister en RAM
        data.setXP(jobId, currentXP);
        data.setLevel(jobId, currentLevel);
        data.setDisplayJob(jobId);  // met aussi à jour lastXpTimestamp

        LevelUpResult result;
        if (levelsGained > 0) {
            result = LevelUpResult.leveled(levelsGained, currentLevel, currentXP, xp);
            // Appliquer les récompenses pour chaque niveau gagné
            int startLevel = currentLevel - levelsGained;
            for (int lvl = startLevel + 1; lvl <= currentLevel; lvl++) {
                applyLevelRewards(player, jobId, lvl);
            }
        } else {
            result = LevelUpResult.noLevelUp(currentLevel, currentXP, xp);
        }

        if (plugin.getConfigManager().isDebugXp()) {
            KjobLogger.info("[XP] " + player.getName() + " +" + xp + " XP " + jobId
                + " (base=" + baseXp + ") → niveau " + currentLevel);
        }

        return result;
    }

    /**
     * Exécute les commandes de récompense définies dans jobs/<jobId>.yml pour un niveau donné.
     */
    private void applyLevelRewards(Player player, String jobId, int level) {
        JobDefinition job = plugin.getJobRegistry().getJob(jobId);
        if (job == null) return;

        for (String cmd : job.getLevelRewardCommands(level)) {
            String resolved = cmd
                .replace("{player}", player.getName())
                .replace("{level}", String.valueOf(level))
                .replace("{job}", jobId)
                .trim();
            try {
                executeRewardCommand(player, resolved);
            } catch (Exception e) {
                KjobLogger.error("Erreur lors de l'execution de la recompense niveau " + level
                    + " pour " + jobId + " : " + cmd, e);
            }
        }
    }

    private void executeRewardCommand(Player player, String command) {
        if (command == null || command.trim().isEmpty()) return;

        String lower = command.toLowerCase();
        if (lower.startsWith("[player]")) {
            dispatchAsPlayer(player, command.substring("[player]".length()).trim());
            return;
        }
        if (lower.startsWith("[joueur]")) {
            dispatchAsPlayer(player, command.substring("[joueur]".length()).trim());
            return;
        }
        if (lower.startsWith("[command]")) {
            dispatchAsConsole(command.substring("[command]".length()).trim());
            return;
        }
        if (lower.startsWith("[console]")) {
            dispatchAsConsole(command.substring("[console]".length()).trim());
            return;
        }
        dispatchAsConsole(command);
    }

    private void dispatchAsConsole(String command) {
        if (command == null || command.trim().isEmpty()) return;
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command.trim());
    }

    private void dispatchAsPlayer(Player player, String command) {
        if (command == null || command.trim().isEmpty()) return;
        player.performCommand(command.trim());
    }
    /**
     * Retourne le multiplicateur de permission pour le joueur.
     * Prend le plus élevé parmi les permissions kjob.xp.*.
     */
    public double getPermissionMultiplier(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        double multiplier = 1.0;

        // Lire les multiplicateurs de permissions depuis config.yml
        org.bukkit.configuration.ConfigurationSection permSection =
            cfg.getMainConfig().getConfigurationSection("xp_multipliers.permissions");
        if (permSection != null) {
            for (String perm : permSection.getKeys(false)) {
                if (player.hasPermission(perm)) {
                    double m = permSection.getDouble(perm, 1.0);
                    if (m > multiplier) multiplier = m;
                }
            }
        }
        return multiplier;
    }

    /**
     * Retourne le multiplicateur d'événement global (configuré via /kjobadmin event).
     */
    public double getEventMultiplier() {
        return plugin.getConfigManager().getMainConfig()
            .getDouble("xp_multipliers.event_multiplier", 1.0);
    }

    /**
     * Retourne le multiplicateur de bonus persistant depuis SQLite (table bonus_multipliers).
     * Appelé de manière synchrone — doit être invoqué après chargement au join
     * (le cache PlayerData ne stocke pas les bonus, on interroge la DB de manière
     * synchrone uniquement si nécessaire ; pour la perf, les bonus sont cachés dans PlayerData).
     */
    public double getBonusMultiplier(java.util.UUID uuid, String jobId) {
        try {
            return plugin.getDatabaseManager().getBonusMultiplier(uuid, jobId);
        } catch (Exception e) {
            KjobLogger.error("Erreur lors de la lecture du bonus multiplier pour " + uuid, e);
            return 1.0;
        }
    }

    // ─── Level up + sons ───────────────────────────────────────────────────

    /**
     * Gère les effets post-level-up : message chat, son, déblocage de slots.
     * Appelé depuis chaque listener XP quand result.isLeveledUp() == true.
     * TODO Phase 5 : ajouter bossbar flush, achievement popup, titre NMS.
     */
    public void handleLevelUp(Player player, PlayerData data, String jobId, LevelUpResult result) {
        // 1. Déblocage de slots
        plugin.getSlotManager().checkAndUnlockSlots(player, data, jobId, result.getNewLevel());

        // 2. Message chat
        String msg = plugin.getConfigManager().getMessage("levelup.message")
            .replace("{prefix}", plugin.getConfigManager().getPrefix())
            .replace("{job}",    getJobDisplayName(jobId))
            .replace("{level}", String.valueOf(result.getNewLevel()))
            .replace("{player}", player.getName());
        if (!msg.isEmpty()) player.sendMessage(msg);

        // 3. Son
        playSoundForKey(player, "level_up");

        if (plugin.getHudManager() != null)
            plugin.getHudManager().onLevelUp(player, data, jobId, result.getNewLevel());
    }

    // ─── Anti-abuse helpers ──────────────────────────────────────────────────

    /**
     * Réinitialise le compteur XP quotidien si 24h se sont écoulées.
     * Appeler avant isDailyCapReached().
     */
    public void checkDailyReset(PlayerData data, String jobId) {
        long lastReset = data.getDailyXpResetTimeMap().getOrDefault(jobId, 0L);
        if (System.currentTimeMillis() - lastReset >= 86_400_000L) {
            data.resetDailyXP(jobId);
        }
    }

    /**
     * Retourne true si le joueur a atteint le plafond XP quotidien pour ce job.
     * Appeler checkDailyReset() avant cette méthode.
     */
    public boolean isDailyCapReached(PlayerData data, String jobId) {
        if (!plugin.getConfigManager().isDailyCapEnabled()) return false;
        int cap = plugin.getConfigManager().getMainConfig()
            .getInt("anti_abuse.daily_xp_cap.amount", 0);
        return cap > 0 && data.getDailyXP(jobId) >= cap;
    }

    // ─── Helpers privés ──────────────────────────────────────────────────────

    private String getJobDisplayName(String jobId) {
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        return def != null ? def.getDisplayName() : jobId;
    }

    private void playSoundForKey(Player player, String soundKey) {
        try {
            boolean enabled = plugin.getConfigManager().getSoundsConfig()
                .getBoolean(soundKey + ".enabled", true);
            if (!enabled) return;
            String soundName = plugin.getConfigManager().getSoundsConfig()
                .getString(soundKey + ".sound", "LEVEL_UP");
            float volume = (float) plugin.getConfigManager().getSoundsConfig()
                .getDouble(soundKey + ".volume", 1.0);
            float pitch  = (float) plugin.getConfigManager().getSoundsConfig()
                .getDouble(soundKey + ".pitch",  1.0);
            player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName), volume, pitch);
        } catch (Exception ignored) {}
    }

    // ─── Admin : ajout XP sans multiplicateurs ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Ajoute (ou retire si négatif) de l’XP sans appliquer de multiplicateurs ni l’anti-abuse.
     * Déclenche quand même la boucle de level-up et les récompenses de palier.
     * Usage exclusif : commande /kjobadmin xp.
     */
    public LevelUpResult adminAddXp(Player player, PlayerData data, String jobId, int amount) {
        JobDefinition job = plugin.getJobRegistry().getJob(jobId);
        if (job == null) return LevelUpResult.noLevelUp(data.getLevel(jobId), data.getXP(jobId), 0);

        int currentLevel = data.getLevel(jobId);
        int maxLevel     = job.getMaxLevel();

        // Montant négatif : soustraction simple, niveau inchangé
        if (amount <= 0) {
            int newXp = Math.max(0, data.getXP(jobId) + amount);
            data.setXP(jobId, newXp);
            return LevelUpResult.noLevelUp(currentLevel, newXp, 0);
        }

        if (currentLevel >= maxLevel) return LevelUpResult.maxLevel(maxLevel);

        int currentXP    = data.getXP(jobId) + amount;
        int levelsGained = 0;

        while (currentLevel < maxLevel) {
            int xpRequired = job.getXpForLevel(currentLevel);
            if (xpRequired <= 0 || currentXP < xpRequired) break;
            currentXP -= xpRequired;
            currentLevel++;
            levelsGained++;
        }

        data.setXP(jobId, currentXP);
        data.setLevel(jobId, currentLevel);

        if (levelsGained > 0) {
            int startLevel = currentLevel - levelsGained;
            for (int lvl = startLevel + 1; lvl <= currentLevel; lvl++) {
                applyLevelRewards(player, jobId, lvl);
            }
            return LevelUpResult.leveled(levelsGained, currentLevel, currentXP, amount);
        }
        return LevelUpResult.noLevelUp(currentLevel, currentXP, amount);
    }
}
