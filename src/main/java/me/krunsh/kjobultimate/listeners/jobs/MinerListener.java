package me.krunsh.kjobultimate.listeners.jobs;

import java.util.Objects;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;

/**
 * Mineur V3.14 : chemin chaud sans String de coordonnées, sans PlayerState
 * temporaire et sans logique XP/money/quête dupliquée.
 */
public final class MinerListener implements Listener {

    private static final String JOB_ID =
        "mineur";

    private static final String GAMEMODE_BYPASS =
        "kjobsultimate.bypass.gamemodecheck";

    private final KjobUltimate plugin;

    public MinerListener(
            KjobUltimate plugin) {

        this.plugin = Objects.requireNonNull(
            plugin,
            "KjobUltimate ne peut pas être null."
        );
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    @SuppressWarnings("deprecation")
    public void onBlockBreak(
            BlockBreakEvent event) {

        Player player =
            event.getPlayer();
        Block block =
            event.getBlock();

        if (player == null
                || block == null
                || !isGameModeAllowed(player)) {

            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(player);

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

        Material material =
            block.getType();

        if (material == null
                || material == Material.AIR) {

            return;
        }

        String materialKey =
            material.name();

        int dataValue =
            block.getData() & 0xFF;

        JobDefinition.ActionReward action =
            resolveAction(
                job,
                material,
                materialKey,
                dataValue
            );

        if (action == null
                || isSilkTouchBlocked(
                    player,
                    action
                )) {

            return;
        }

        if (plugin.getBlockCooldownService()
                .isOnCooldown(
                    player,
                    block
                )) {

            return;
        }

        String questMaterial =
            material == Material.GLOWING_REDSTONE_ORE
                ? Material.REDSTONE_ORE.name()
                : materialKey;

        /*
         * Cette String n'est créée qu'après toutes les gates et uniquement
         * lorsqu'une action Mineur est réellement configurée.
         */
        String questTarget =
            questMaterial
                + ":"
                + dataValue;

        boolean applied =
            plugin.getJobActionService()
                .apply(
                    player,
                    data,
                    job,
                    action,
                    1,
                    "MINE",
                    questTarget
                );

        if (applied) {
            plugin.getBlockCooldownService()
                .mark(
                    player,
                    block,
                    getBlockCooldownMillis()
                );
        }
    }

    private JobDefinition.ActionReward resolveAction(
            JobDefinition job,
            Material material,
            String materialKey,
            int dataValue) {

        if (job.hasDataSpecificActionFor(
                materialKey
            )) {

            JobDefinition.ActionReward exact =
                job.getAction(
                    materialKey
                        + ":"
                        + dataValue
                );

            if (exact != null) {
                return exact;
            }
        }

        JobDefinition.ActionReward generic =
            job.getAction(
                materialKey
            );

        if (generic != null) {
            return generic;
        }

        if (material
                != Material.GLOWING_REDSTONE_ORE) {

            return null;
        }

        String redstone =
            Material.REDSTONE_ORE.name();

        if (job.hasDataSpecificActionFor(
                redstone
            )) {

            JobDefinition.ActionReward exact =
                job.getAction(
                    redstone
                        + ":"
                        + dataValue
                );

            if (exact != null) {
                return exact;
            }
        }

        return job.getAction(
            redstone
        );
    }

    private boolean isGameModeAllowed(
            Player player) {

        if (player.hasPermission(
                GAMEMODE_BYPASS
            )) {

            return true;
        }

        GameMode gameMode =
            player.getGameMode();

        if (gameMode == GameMode.CREATIVE
                && plugin.getConfigManager()
                    .isBlockXpCreative()) {

            return false;
        }

        return gameMode != GameMode.SPECTATOR
            || !plugin.getConfigManager()
                .isBlockXpSpectator();
    }

    private boolean isSilkTouchBlocked(
            Player player,
            JobDefinition.ActionReward action) {

        return action.isSilkTouchBlocked()
            && plugin.getConfigManager()
                .isSilkTouchBlocked()
            && hasSilkTouch(
                player
            );
    }

    private boolean hasSilkTouch(
            Player player) {

        ItemStack tool =
            player.getInventory()
                .getItemInHand();

        return tool != null
            && tool.getType() != Material.AIR
            && tool.containsEnchantment(
                Enchantment.SILK_TOUCH
            );
    }

    private long getBlockCooldownMillis() {

        long seconds =
            Math.max(
                0L,
                plugin.getConfigManager()
                    .getBlockCooldown()
            );

        return seconds > Long.MAX_VALUE / 1000L
            ? Long.MAX_VALUE
            : seconds * 1000L;
    }
}
