package me.krunsh.kjobultimate.listeners;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gère le chargement et déchargement des données joueur.
 *
 * Flux :
 *   PlayerLoginEvent (NORMAL) → déclenche le chargement async SQLite
 *   PlayerJoinEvent  (NORMAL) → vérification du premier join (slot 1 vide → ouvrir GUI)
 *   PlayerQuitEvent  (NORMAL) → sauvegarde async + déchargement du cache
 */
public final class PlayerConnectionListener implements Listener {

    private final KjobUltimate plugin;

    public PlayerConnectionListener(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        // Chargement async des données depuis SQLite dès la connexion
        plugin.getPlayerDataManager().loadAsync(event.getPlayer().getUniqueId(), null);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Le serveur annonce d'abord le joueur réel à tous les clients. Le
        // layout virtuel le retire ensuite, une fois le profil/skin reçu.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getTabManager() != null) {
                plugin.getTabManager().refreshVirtualRealPlayerVisibility();
            }
        }, 10L);

        // Cas rapide : l'async est déjà terminé avant le join
        if (plugin.getPlayerDataManager().get(player) != null) {
            handlePostLoad(player);
            return;
        }

        // Cas lent : l'async n'est pas encore terminé — réessayer toutes les 5 ticks (max 5 secondes)
        awaitDataThenHandle(player, 0);
    }

    /**
     * Attend que les données soient disponibles dans le cache (async SQLite).
     * Re-planifie automatiquement jusqu'à 20 tentatives (100 ticks = 5 secondes).
     */
    private void awaitDataThenHandle(Player player, int attempt) {
        if (!player.isOnline()) return;

        if (plugin.getPlayerDataManager().get(player) != null) {
            handlePostLoad(player);
            return;
        }

        if (attempt >= 20) {
            KjobLogger.error("Impossible de charger les données de " + player.getName() + " après 5 secondes.");
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> awaitDataThenHandle(player, attempt + 1), 5L);
    }

    private void handlePostLoad(Player player) {
        if (!player.isOnline()) return;
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            KjobLogger.error("Données introuvables après chargement pour " + player.getName());
            return;
        }

        // Premier join : message de bienvenue
        boolean isFirstJoin = data.getLastSeen() == data.getFirstJoin();
        if (isFirstJoin) {
            String welcomeMsg = plugin.getConfigManager().getMainConfig()
                .getString("join.first_join_message", "");
            if (!welcomeMsg.isEmpty()) {
                player.sendMessage(welcomeMsg.replace("&", "§"));
            }
        }

        // Si le slot 1 est vide → indiquer au joueur de choisir un job
        // (le GUI sera ouvert en Phase 7 via KguiHook — squelette ici)
        boolean defaultAllJobs = plugin.getConfigManager().getMainConfig()
            .getBoolean("join.default_all_jobs", false);

        if (defaultAllJobs) {
            // Activer tous les jobs par défaut (mode sans restriction)
            initAllJobsForPlayer(player, data);
        } else if (data.getJobInSlot(1) == null) {
            // Pas encore de job — informer le joueur
            String noJobMsg = plugin.getConfigManager().getMessage("misc.no_job_yet");
            if (!noJobMsg.isEmpty()) {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> { if (player.isOnline()) player.sendMessage(noJobMsg); }, 40L);
            }
        }

        if (plugin.getConfigManager().isDebug()) {
            KjobLogger.info("[Join] " + player.getName() + " — "
                + plugin.getSlotManager().getActiveJobs(data).size() + " job(s) actif(s)");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getHudManager() != null) {
            plugin.getHudManager().removePlayer(event.getPlayer());
        }
        plugin.getPlayerDataManager().saveAndUnload(event.getPlayer().getUniqueId());
    }

    /**
     * Active tous les jobs disponibles dans les slots (mode default_all_jobs: true).
     * Chaque job est assigné à un slot numéroté.
     */
    private void initAllJobsForPlayer(Player player, PlayerData data) {
        int slot = 1;
        for (String jobId : plugin.getJobRegistry().getJobIds()) {
            if (slot > plugin.getConfigManager().getMaxSlots()) break;
            if (data.getJobInSlot(slot) == null) {
                plugin.getSlotManager().assignJobToSlot(player, data, slot, jobId);
            }
            slot++;
        }
        // En mode all-jobs, tous les slots sont débloqués
        data.setUnlockedSlots(plugin.getConfigManager().getMaxSlots());
    }
}
