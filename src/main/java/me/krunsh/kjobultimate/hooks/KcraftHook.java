package me.krunsh.kjobultimate.hooks;

import me.krunsh.kcraft.api.events.KcraftPostCraftEvent;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.action.CraftUnitResolver;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.ConfiguredItemMatcher;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Hook Kcraft V3.13.
 *
 * Ordre de résolution Artisan :
 * 1. action exacte "KCRAFT:<craftId>" ;
 * 2. fallback historique sur le Material du résultat.
 *
 * Les crafts forcés ne donnent rien par défaut :
 * allow_forced: true doit être explicite sur l'action retenue.
 */
public final class KcraftHook implements Listener {

    private static final String ARTISAN_JOB_ID =
        "artisan";

    private static final String PILLEUR_JOB_ID =
        "pilleur";

    private final KjobUltimate plugin;

    public KcraftHook(
            KjobUltimate plugin) {

        this.plugin =
            plugin;
    }

    public void register() {

        plugin.getServer()
            .getPluginManager()
            .registerEvents(
                this,
                plugin
            );

        KjobLogger.info(
            "Kcraft hook enregistre - listener KcraftPostCraftEvent V3.13 actif."
        );
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = false
    )
    public void onKcraftPost(
            KcraftPostCraftEvent event) {

        if (event == null
                || !event.isSuccess()) {

            return;
        }

        Player player =
            event.getPlayer();

        if (player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {

            return;
        }

        ItemStack craftResult =
            event.getResult();

        if (craftResult == null
                || craftResult.getType() == null
                || craftResult.getType() == Material.AIR) {

            return;
        }

        handleArtisanCraft(
            player,
            craftResult,
            event.getCraftId(),
            event.wasForced()
        );

        handlePilleurDynamiteCraft(
            player,
            craftResult,
            event.wasForced()
        );
    }

    private void handleArtisanCraft(
            Player player,
            ItemStack craftResult,
            String craftId,
            boolean forced) {

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    ARTISAN_JOB_ID
                );

        if (job == null) {
            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(
                    player
                );

        if (data == null
                || !plugin.getSlotManager()
                    .isJobActive(
                        data,
                        ARTISAN_JOB_ID
                    )) {

            return;
        }

        JobDefinition.ActionReward action =
            resolveArtisanAction(
                job,
                craftId,
                craftResult
            );

        if (action == null) {
            return;
        }

        int units =
            CraftUnitResolver
                .resolveKcraft(
                    craftResult,
                    action
                );

        if (units <= 0) {
            return;
        }

        plugin.getJobActionService()
            .apply(
                player,
                data,
                job,
                action,
                units,
                "CRAFT",
                craftResult
                    .getType()
                    .name(),
                forced
            );
    }

    private JobDefinition.ActionReward resolveArtisanAction(
            JobDefinition job,
            String craftId,
            ItemStack craftResult) {

        if (craftId != null
                && !craftId.trim().isEmpty()) {

            JobDefinition.ActionReward exact =
                job.getAction(
                    "KCRAFT:"
                        + craftId
                );

            if (exact != null) {
                return exact;
            }
        }

        return job.getAction(
            craftResult
                .getType()
                .name()
        );
    }

    private void handlePilleurDynamiteCraft(
            Player player,
            ItemStack craftResult,
            boolean forced) {

        if (!isDynamiteItem(
                craftResult
            )) {

            return;
        }

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    PILLEUR_JOB_ID
                );

        if (job == null) {
            return;
        }

        JobDefinition.ActionReward action =
            job.getAction(
                "DYNAMITE_CRAFT"
            );

        if (action == null) {
            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(
                    player
                );

        if (data == null
                || !plugin.getSlotManager()
                    .isJobActive(
                        data,
                        PILLEUR_JOB_ID
                    )) {

            return;
        }

        /*
         * Compatibilité du comportement Pilleur existant :
         * chaque dynamite produite reste une unité, indépendamment du nouveau
         * count_mode Artisan.
         */
        int units =
            Math.max(
                1,
                craftResult.getAmount()
            );

        plugin.getJobActionService()
            .apply(
                player,
                data,
                job,
                action,
                units,
                "DYNAMITE_CRAFT",
                "DYNAMITE",
                forced
            );
    }

    private boolean isDynamiteItem(
            ItemStack item) {

        return ConfiguredItemMatcher
            .matches(
                plugin,
                item,
                "pilleur.dynamite_item"
            )
            || ConfiguredItemMatcher
                .matches(
                    plugin,
                    item,
                    "pilleur.dynamite_nbt_example"
                );
    }
}
