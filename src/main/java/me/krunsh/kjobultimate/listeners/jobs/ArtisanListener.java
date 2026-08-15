package me.krunsh.kjobultimate.listeners.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.action.CraftUnitResolver;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Artisan vanilla V3.13.
 *
 * Le shift-click n'est plus compté comme un unique craft : CraftUnitResolver
 * estime les exécutions réellement possibles, puis JobActionService utilise la
 * même quantité pour XP / money / HUD / quêtes.
 */
public final class ArtisanListener implements Listener {

    private static final String JOB_ID =
        "artisan";

    private final KjobUltimate plugin;

    public ArtisanListener(
            KjobUltimate plugin) {

        this.plugin =
            plugin;
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onCraftItem(
            CraftItemEvent event) {

        HumanEntity human =
            event.getWhoClicked();

        if (!(human instanceof Player)) {
            return;
        }

        Player player =
            (Player) human;

        if (player.getGameMode()
                == GameMode.CREATIVE
                && plugin.getConfigManager()
                    .isBlockXpCreative()) {

            return;
        }

        Recipe recipe =
            event.getRecipe();

        if (recipe == null) {
            return;
        }

        ItemStack craftResult =
            recipe.getResult();

        if (craftResult == null
                || craftResult.getType() == null
                || craftResult.getType() == Material.AIR) {

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
                        JOB_ID
                    )) {

            return;
        }

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    JOB_ID
                );

        if (job == null) {
            return;
        }

        JobDefinition.ActionReward action =
            job.getAction(
                craftResult
                    .getType()
                    .name()
            );

        if (action == null) {
            return;
        }

        int units =
            CraftUnitResolver
                .resolveVanilla(
                    event,
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
                    .name()
            );
    }
}
