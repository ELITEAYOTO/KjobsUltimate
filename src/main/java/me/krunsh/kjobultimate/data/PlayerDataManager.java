package me.krunsh.kjobultimate.data;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cache RAM des données joueurs.
 * Charge en async au join, sauvegarde en async au quit.
 * get(Player) est toujours appelable depuis le main thread sans I/O.
 */
public final class PlayerDataManager {

    private final KjobUltimate plugin;
    private final DatabaseManager db;

    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    private BukkitTask autosaveTask;

    public PlayerDataManager(KjobUltimate plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db     = db;
        startAutosave();
    }

    /**
     * Charge les données du joueur en async, puis appelle le callback sur le main thread.
     * Appelé depuis PlayerLoginEvent ou PlayerJoinEvent.
     */
    public void loadAsync(UUID uuid, Runnable onLoaded) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerData data = db.loadPlayer(uuid);
                if (data == null) {
                    data = new PlayerData(uuid);
                    long now = System.currentTimeMillis();
                    data.setFirstJoin(now);
                    data.setLastSeen(now);
                    data.setUnlockedSlots(plugin.getConfigManager().getDefaultSlots());
                    db.savePlayer(data);
                    if (plugin.getConfigManager().isDebug()) {
                        KjobLogger.info("Nouveau joueur créé : " + uuid);
                    }
                }
                cache.put(uuid, data);
                // Charger les bonus multipliers (cache RAM pour eviter I/O sur le main thread)
                try {
                    java.util.Map<String, Double> bonuses = db.loadBonusMultipliers(uuid);
                    for (java.util.Map.Entry<String, Double> e : bonuses.entrySet()) {
                        data.setBonusMultiplier(e.getKey(), e.getValue());
                    }
                    data.markClean(); // setBonusMultiplier passe dirty a true — on remet clean
                } catch (java.sql.SQLException ex) {
                    KjobLogger.error("Impossible de charger les bonus multipliers de " + uuid, ex);
                }
            } catch (Exception e) {
                KjobLogger.error("Impossible de charger les données de " + uuid, e);
                // On crée une instance vide pour que le joueur ne crashe pas
                PlayerData fallback = new PlayerData(uuid);
                cache.put(uuid, fallback);
            }
            // Callback sur le main thread
            if (onLoaded != null) {
                plugin.getServer().getScheduler().runTask(plugin, onLoaded);
            }
        });
    }

    /**
     * Sauvegarde les données en async et retire le joueur du cache.
     * Appelé depuis PlayerQuitEvent.
     */
    public void saveAndUnload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data == null) return;
        data.setLastSeen(System.currentTimeMillis());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                db.savePlayer(data);
            } catch (Exception e) {
                KjobLogger.error("Impossible de sauvegarder les données de " + uuid, e);
            }
        });
    }

    /**
     * Retourne les données du joueur depuis le cache RAM.
     * Ne doit être appelé que depuis le main thread.
     * Retourne null si le joueur n'est pas connecté (ne devrait jamais arriver en pratique).
     */
    public PlayerData get(Player player) {
        return cache.get(player.getUniqueId());
    }

    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Sauvegarde synchrone de toutes les données — utilisé sur onDisable uniquement.
     */
    public void saveAll() {
        for (PlayerData data : cache.values()) {
            try {
                data.setLastSeen(System.currentTimeMillis());
                db.savePlayer(data);
            } catch (Exception e) {
                KjobLogger.error("Erreur lors du saveAll pour " + data.getUuid(), e);
            }
        }
        KjobLogger.info("Autosave final : " + cache.size() + " joueur(s) sauvegardé(s).");
    }

    /**
     * Lance le scheduler d'autosave périodique.
     * Sauvegarde uniquement les données marquées dirty pour ne pas écraser les autres.
     */
    private void startAutosave() {
        int intervalMinutes = plugin.getConfigManager().getAutosaveInterval();
        long intervalTicks  = (long) intervalMinutes * 60 * 20;

        autosaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int count = 0;
            for (PlayerData data : cache.values()) {
                if (data.isDirty()) {
                    try {
                        db.savePlayer(data);
                        data.markClean();
                        count++;
                    } catch (Exception e) {
                        KjobLogger.error("Autosave échoué pour " + data.getUuid(), e);
                    }
                }
            }
            if (count > 0 && plugin.getConfigManager().isDebug()) {
                KjobLogger.info("Autosave : " + count + " joueur(s) sauvegardé(s).");
            }
        }, intervalTicks, intervalTicks);
    }

    public void cancelAutosave() {
        if (autosaveTask != null) autosaveTask.cancel();
    }

    public int getCacheSize() {
        return cache.size();
    }
}
