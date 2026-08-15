package me.krunsh.kjobultimate.listeners.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.action.HarvestUnitResolver;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.CropUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Job Farmer V3.13.
 *
 * Garanties :
 * - observe l'état final de BlockBreakEvent en MONITOR ;
 * - CROPS est l'unique identité du blé Bukkit 1.8 ;
 * - canne à sucre / cactus : une casse peut créditer plusieurs unités ;
 * - XP, money et quête utilisent exactement la même quantité.
 */
public final class FarmerListener implements Listener {

    private static final String JOB_ID =
        "farmer";

    private static final String GAMEMODE_BYPASS =
        "kjobsultimate.bypass.gamemodecheck";

    private final KjobUltimate plugin;
    private final HarvestUnitResolver harvestUnits;

    public FarmerListener(
            KjobUltimate plugin) {

        this.plugin =
            plugin;

        this.harvestUnits =
            new HarvestUnitResolver(
                plugin
            );
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onBlockBreak(
            BlockBreakEvent event) {

        Player player =
            event.getPlayer();

        Block block =
            event.getBlock();

        if (player == null
                || block == null
                || !CropUtil.isFarmingCrop(
                    block.getType()
                )) {

            return;
        }

        /*
         * Un plugin tiers peut créer un second BlockBreakEvent pour une canne
         * supérieure que Kjobs a déjà comptée dans le premier événement.
         */
        if (harvestUnits.consumeSuppressed(
                player,
                block
            )) {

            return;
        }

        if (!isGameModeAllowed(
                player
            )) {

            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(
                    player
                );

        if (data == null) {
            return;
        }

        if (!plugin.getSlotManager()
                .isJobActive(
                    data,
                    JOB_ID
                )) {

            return;
        }

        if (plugin.getConfigManager()
                .isCropsMatureOnly()
                && !CropUtil.isMature(
                    block
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

        String blockKey =
            block.getType()
                .name();

        JobDefinition.ActionReward action =
            job.getAction(
                blockKey
            );

        if (action == null) {
            return;
        }

        int units =
            harvestUnits.resolveAndSuppress(
                player,
                block
            );

        if (units <= 0) {
            return;
        }

        if (plugin.getBlockCooldownService()
                .isOnCooldown(
                    player,
                    block
                )) {

            return;
        }

        boolean applied =
            plugin.getJobActionService()
                .apply(
                    player,
                    data,
                    job,
                    action,
                    units,
                    "HARVEST",
                    blockKey
                );

        /*
         * Même comportement que l'ancien listener :
         * aucun cooldown longue durée si le gain a été rejeté (ex. cap atteint).
         */
        if (applied) {
            plugin.getBlockCooldownService()
                .mark(
                    player,
                    block,
                    getBlockCooldownMillis()
                );
        }
    }

    private boolean isGameModeAllowed(
            Player player) {

        boolean bypass =
            player.hasPermission(
                GAMEMODE_BYPASS
            );

        if (player.getGameMode()
                == GameMode.CREATIVE
                && plugin.getConfigManager()
                    .isBlockXpCreative()
                && !bypass) {

            return false;
        }

        if (player.getGameMode()
                == GameMode.SPECTATOR
                && plugin.getConfigManager()
                    .isBlockXpSpectator()
                && !bypass) {

            return false;
        }

        return true;
    }

    private long getBlockCooldownMillis() {

        return Math.max(
            0L,
            (long) plugin
                .getConfigManager()
                .getBlockCooldown()
        ) * 1000L;
    }
}
