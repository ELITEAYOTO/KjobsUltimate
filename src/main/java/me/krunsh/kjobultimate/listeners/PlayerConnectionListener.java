package me.krunsh.kjobultimate.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Gère le chargement et déchargement des données joueur.
 *
 * V3.11 :
 * aucune responsabilité TAB ne reste dans ce listener.
 */
public final class PlayerConnectionListener implements Listener {

    private final KjobUltimate plugin;

    public PlayerConnectionListener(
            KjobUltimate plugin) {

        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLogin(
            PlayerLoginEvent event) {

        if (event.getResult()
                != PlayerLoginEvent.Result.ALLOWED) {

            return;
        }

        plugin.getPlayerDataManager()
            .loadAsync(
                event.getPlayer()
                    .getUniqueId(),
                null
            );
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(
            PlayerJoinEvent event) {

        Player player =
            event.getPlayer();

        if (plugin.getPlayerDataManager()
                .get(player) != null) {

            handlePostLoad(player);
            return;
        }

        awaitDataThenHandle(
            player,
            0
        );
    }

    private void awaitDataThenHandle(
            Player player,
            int attempt) {

        if (!player.isOnline()) {
            return;
        }

        if (plugin.getPlayerDataManager()
                .get(player) != null) {

            handlePostLoad(player);
            return;
        }

        if (attempt >= 20) {

            KjobLogger.error(
                "Impossible de charger les données de "
                    + player.getName()
                    + " après 5 secondes."
            );

            return;
        }

        plugin.getServer()
            .getScheduler()
            .runTaskLater(
                plugin,
                () -> awaitDataThenHandle(
                    player,
                    attempt + 1
                ),
                5L
            );
    }

    private void handlePostLoad(
            Player player) {

        if (!player.isOnline()) {
            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(player);

        if (data == null) {

            KjobLogger.error(
                "Données introuvables après chargement pour "
                    + player.getName()
            );

            return;
        }

        boolean isFirstJoin =
            data.getLastSeen()
                == data.getFirstJoin();

        if (isFirstJoin) {

            String welcomeMsg =
                plugin.getConfigManager()
                    .getMainConfig()
                    .getString(
                        "join.first_join_message",
                        ""
                    );

            if (!welcomeMsg.isEmpty()) {
                player.sendMessage(
                    welcomeMsg.replace(
                        "&",
                        "§"
                    )
                );
            }
        }

        boolean defaultAllJobs =
            plugin.getConfigManager()
                .getMainConfig()
                .getBoolean(
                    "join.default_all_jobs",
                    false
                );

        if (defaultAllJobs) {

            initAllJobsForPlayer(
                player,
                data
            );

        } else if (data.getJobInSlot(1) == null) {

            String noJobMsg =
                plugin.getConfigManager()
                    .getMessage(
                        "misc.no_job_yet"
                    );

            if (!noJobMsg.isEmpty()) {

                plugin.getServer()
                    .getScheduler()
                    .runTaskLater(
                        plugin,
                        () -> {
                            if (player.isOnline()) {
                                player.sendMessage(
                                    noJobMsg
                                );
                            }
                        },
                        40L
                    );
            }
        }

        if (plugin.getConfigManager()
                .isDebug()) {

            KjobLogger.info(
                "[Join] "
                    + player.getName()
                    + " — "
                    + plugin.getSlotManager()
                        .getActiveJobs(data)
                        .size()
                    + " job(s) actif(s)"
            );
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(
            PlayerQuitEvent event) {

        Player player =
            event.getPlayer();

        if (plugin.getHudManager() != null) {
            plugin.getHudManager()
                .removePlayer(player);
        }

        plugin.invalidateViewCaches(
            player.getUniqueId()
        );

        plugin.getPlayerDataManager()
            .saveAndUnload(
                player.getUniqueId()
            );
    }

    private void initAllJobsForPlayer(
            Player player,
            PlayerData data) {

        int slot = 1;

        for (String jobId
                : plugin.getJobRegistry()
                    .getJobIds()) {

            if (slot
                    > plugin.getConfigManager()
                        .getMaxSlots()) {

                break;
            }

            if (data.getJobInSlot(slot)
                    == null) {

                plugin.getSlotManager()
                    .assignJobToSlot(
                        player,
                        data,
                        slot,
                        jobId
                    );
            }

            slot++;
        }

        data.setUnlockedSlots(
            plugin.getConfigManager()
                .getMaxSlots()
        );
    }
}
