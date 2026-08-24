package me.krunsh.kjobultimate.action;

import java.util.Locale;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;

/**
 * Chemin unique des gains Mineur, quelle que soit l'origine de la casse.
 *
 * Les casses Bukkit ordinaires et les casses Kminerai déjà matérialisées
 * passent par les mêmes gates, le même cooldown et le même accounting.
 */
public final class MiningActionService {

    private static final String JOB_ID = "mineur";
    private static final String GAMEMODE_BYPASS =
        "kjobsultimate.bypass.gamemodecheck";

    private final KjobUltimate plugin;

    public MiningActionService(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                "plugin ne peut pas être null."
            );
        }
        this.plugin = plugin;
    }

    /**
     * @param customOreId identité stable du minerai Kminerai, ou null
     * @param droppedItemId identité du drop réellement tiré, ou null
     */
    public boolean apply(
            Player player,
            Block position,
            Material material,
            int dataValue,
            String customOreId,
            String droppedItemId) {

        if (player == null
                || position == null
                || material == null
                || material == Material.AIR
                || !isGameModeAllowed(player)) {

            return false;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(player);

        if (data == null
                || !plugin.getSlotManager()
                    .isJobActive(data, JOB_ID)) {

            return false;
        }

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(JOB_ID);

        if (job == null) {
            return false;
        }

        JobDefinition.ActionReward reward =
            resolveAction(
                job,
                material,
                dataValue,
                customOreId,
                droppedItemId
            );

        if (reward == null
                || isSilkTouchBlocked(player, reward)
                || plugin.getBlockCooldownService()
                    .isOnCooldown(player, position)) {

            return false;
        }

        String materialKey =
            material == Material.GLOWING_REDSTONE_ORE
                ? Material.REDSTONE_ORE.name()
                : material.name();

        String questTarget =
            materialKey + ":" + (dataValue & 0xFF);

        boolean applied =
            plugin.getJobActionService()
                .apply(
                    player,
                    data,
                    job,
                    reward,
                    1,
                    "MINE",
                    questTarget
                );

        if (applied) {
            plugin.getBlockCooldownService()
                .mark(
                    player,
                    position,
                    getBlockCooldownMillis()
                );
        }

        return applied;
    }

    /**
     * Priorité explicite : minerai source, drop aléatoire réel, bloc exact,
     * puis matériau générique.
     */
    static JobDefinition.ActionReward resolveAction(
            JobDefinition job,
            Material material,
            int dataValue,
            String customOreId,
            String droppedItemId) {

        if (job == null || material == null) {
            return null;
        }

        JobDefinition.ActionReward exact =
            customAction(job, "KMINERAI:", customOreId);

        if (exact != null) {
            return exact;
        }

        exact =
            customAction(
                job,
                "KMINERAI_DROP:",
                droppedItemId
            );

        if (exact != null) {
            return exact;
        }

        String materialKey = material.name();
        int unsignedData = dataValue & 0xFF;

        if (job.hasDataSpecificActionFor(materialKey)) {
            exact =
                job.getAction(
                    materialKey + ":" + unsignedData
                );

            if (exact != null) {
                return exact;
            }
        }

        JobDefinition.ActionReward generic =
            job.getAction(materialKey);

        if (generic != null
                || material != Material.GLOWING_REDSTONE_ORE) {

            return generic;
        }

        String redstone = Material.REDSTONE_ORE.name();

        if (job.hasDataSpecificActionFor(redstone)) {
            exact =
                job.getAction(
                    redstone + ":" + unsignedData
                );

            if (exact != null) {
                return exact;
            }
        }

        return job.getAction(redstone);
    }

    private static JobDefinition.ActionReward customAction(
            JobDefinition job,
            String prefix,
            String identity) {

        String normalized = normalizeIdentity(identity);

        return normalized.isEmpty()
            ? null
            : job.getAction(prefix + normalized);
    }

    private static String normalizeIdentity(String value) {
        return value == null
            ? ""
            : value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private boolean isGameModeAllowed(Player player) {
        if (player.hasPermission(GAMEMODE_BYPASS)) {
            return true;
        }

        GameMode gameMode = player.getGameMode();

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
            JobDefinition.ActionReward reward) {

        if (!reward.isSilkTouchBlocked()
                || !plugin.getConfigManager()
                    .isSilkTouchBlocked()) {

            return false;
        }

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
