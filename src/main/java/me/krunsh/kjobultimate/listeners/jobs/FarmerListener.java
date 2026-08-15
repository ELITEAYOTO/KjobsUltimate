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
 * - CROPS est la vraie identité du blé Bukkit 1.8 ;
 * - WHEAT reste accepté comme ancienne clé de config ;
 * - canne à sucre / cactus : une casse peut créditer plusieurs unités ;
 * - XP, money et quête utilisent exactement la même quantité.
 */
public final class FarmerListener implements Listener {

    private static final String JOB_ID =
        "farmer";

    private static final String LEGACY_GAMEMODE_BYPASS =
        "kjob.bypass.gamemodecheck";

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
            resolveAction(
                job,
                block.getType()
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

        String locationKey =
            createLocationKey(
                block
            );

        if (data.isBlockOnCooldown(
                locationKey
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
            data.setBlockCooldown(
                locationKey,
                getBlockCooldownMillis()
            );
        }
    }

    private JobDefinition.ActionReward resolveAction(
            JobDefinition job,
            Material blockType) {

        if (job == null
                || blockType == null) {

            return null;
        }

        JobDefinition.ActionReward exact =
            job.getAction(
                blockType.name()
            );

        if (exact != null) {
            return exact;
        }

        /*
         * Compatibilité avec les anciens farmer.yml de KjobsUltimate :
         * le bloc de blé 1.8 est CROPS mais l'ancienne config utilisait WHEAT.
         */
        if (blockType == Material.CROPS) {
            return job.getAction(
                "WHEAT"
            );
        }

        return null;
    }

    private boolean isGameModeAllowed(
            Player player) {

        boolean bypass =
            player.hasPermission(
                GAMEMODE_BYPASS
            )
            || player.hasPermission(
                LEGACY_GAMEMODE_BYPASS
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

    private String createLocationKey(
            Block block) {

        return block.getWorld()
            .getName()
            + ":"
            + block.getX()
            + ":"
            + block.getY()
            + ":"
            + block.getZ();
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
