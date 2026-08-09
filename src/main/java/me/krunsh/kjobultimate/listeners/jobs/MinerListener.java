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
import me.krunsh.kjobultimate.jobs.LevelUpResult;

/**
 * Listener du métier Mineur.
 *
 * Responsabilités :
 * - vérifier que le joueur peut recevoir les récompenses du métier ;
 * - identifier précisément les blocs Minecraft 1.8 avec MATERIAL:data ;
 * - appliquer l'XP via XpManager ;
 * - distribuer l'argent configuré ;
 * - transmettre la progression aux quêtes et au HUD.
 *
 * Cette classe ne calcule jamais elle-même les niveaux, les multiplicateurs
 * ou les plafonds d'XP : XpManager reste l'unique source de vérité.
 */
public final class MinerListener implements Listener {

    private static final String JOB_ID = "mineur";

    private static final String LEGACY_GAMEMODE_BYPASS =
        "kjob.bypass.gamemodecheck";

    private static final String GAMEMODE_BYPASS =
        "kjobsultimate.bypass.gamemodecheck";

    private final KjobUltimate plugin;

    public MinerListener(KjobUltimate plugin) {
        this.plugin = Objects.requireNonNull(
            plugin,
            "KjobUltimate ne peut pas être null.");
    }

    /**
     * MONITOR est utilisé car ce listener observe le résultat final du cassage
     * sans modifier l'événement. Un bloc annulé par une protection ne doit
     * jamais donner d'XP, d'argent ou de progression de quête.
     */
    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!isGameModeAllowed(player)) {
            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager().get(player);

        if (data == null) {
            return;
        }

        if (!plugin.getSlotManager()
                .isJobActive(data, JOB_ID)) {
            return;
        }

        JobDefinition job =
            plugin.getJobRegistry().getJob(JOB_ID);

        if (job == null) {
            return;
        }

        BlockIdentity identity =
            BlockIdentity.from(block);

        JobDefinition.ActionReward action =
            resolveAction(job, identity);

        if (action == null) {
            return;
        }

        if (isSilkTouchBlocked(
                player,
                action)) {
            return;
        }

        String locationKey =
            createLocationKey(block);

        if (data.isBlockOnCooldown(locationKey)) {
            return;
        }

        /*
         * Ce contrôle permet d'envoyer le message immédiatement lorsque le
         * joueur n'a plus aucune XP disponible aujourd'hui. XpManager applique
         * aussi le plafond lui-même, ce qui protège les gains partiels et tous
         * les autres appels au service.
         */
        plugin.getXpManager()
            .checkDailyReset(data, JOB_ID);

        if (plugin.getXpManager()
                .isDailyCapReached(data, JOB_ID)) {

            sendDailyCapMessage(
                player,
                job);

            return;
        }

        /*
         * Le cooldown est posé avant les récompenses afin d'éviter qu'un autre
         * traitement synchrone du même emplacement puisse doubler le gain.
         */
        data.setBlockCooldown(
            locationKey,
            getBlockCooldownMillis());

        LevelUpResult result =
            plugin.getXpManager().addXP(
                player,
                data,
                JOB_ID,
                action.getXp());

        depositMoney(
            player,
            action);

        if (result.isLeveledUp()) {
            plugin.getXpManager().handleLevelUp(
                player,
                data,
                JOB_ID,
                result);
        }

        updateHud(
            player,
            data,
            result);

        progressQuest(
            player,
            identity);
    }

    /**
     * Cherche d'abord la variante exacte MATERIAL:data, puis la clé générique.
     *
     * Exemple :
     * - STONE:3 peut avoir une récompense différente de STONE ;
     * - si STONE:3 n'est pas configuré, STONE reste un fallback valide.
     *
     * Le minerai de redstone allumé est également compatible avec une config
     * qui ne déclare que REDSTONE_ORE.
     */
    private JobDefinition.ActionReward resolveAction(
            JobDefinition job,
            BlockIdentity identity) {

        JobDefinition.ActionReward action =
            job.getAction(identity.exactKey);

        if (action != null) {
            return action;
        }

        action =
            job.getAction(identity.materialKey);

        if (action != null) {
            return action;
        }

        if (identity.isGlowingRedstone()) {
            action =
                job.getAction(
                    Material.REDSTONE_ORE.name()
                        + ":"
                        + identity.data);

            if (action != null) {
                return action;
            }

            return job.getAction(
                Material.REDSTONE_ORE.name());
        }

        return null;
    }

    private boolean isGameModeAllowed(Player player) {
        GameMode gameMode =
            player.getGameMode();

        if (gameMode == GameMode.CREATIVE
                && plugin.getConfigManager()
                    .isBlockXpCreative()) {
            return false;
        }

        if (gameMode == GameMode.SPECTATOR
                && plugin.getConfigManager()
                    .isBlockXpSpectator()
                && !hasGameModeBypass(player)) {
            return false;
        }

        return true;
    }

    private boolean hasGameModeBypass(Player player) {
        return player.hasPermission(GAMEMODE_BYPASS)
            || player.hasPermission(
                LEGACY_GAMEMODE_BYPASS);
    }

    private boolean isSilkTouchBlocked(
            Player player,
            JobDefinition.ActionReward action) {

        return action.isSilkTouchBlocked()
            && plugin.getConfigManager()
                .isSilkTouchBlocked()
            && hasSilkTouch(player);
    }

    private boolean hasSilkTouch(Player player) {
        ItemStack tool =
            player.getInventory().getItemInHand();

        return tool != null
            && tool.getType() != Material.AIR
            && tool.containsEnchantment(
                Enchantment.SILK_TOUCH);
    }

    private String createLocationKey(Block block) {
        return block.getWorld().getUID().toString()
            + ":"
            + block.getX()
            + ":"
            + block.getY()
            + ":"
            + block.getZ();
    }

    private long getBlockCooldownMillis() {
        long seconds =
            Math.max(
                0L,
                plugin.getConfigManager()
                    .getBlockCooldown());

        if (seconds > Long.MAX_VALUE / 1000L) {
            return Long.MAX_VALUE;
        }

        return seconds * 1000L;
    }

    private void sendDailyCapMessage(
            Player player,
            JobDefinition job) {

        String message =
            plugin.getConfigManager().getMessage(
                "anti_abuse.daily_cap_reached");

        if (message == null || message.isEmpty()) {
            return;
        }

        player.sendMessage(
            message
                .replace(
                    "{prefix}",
                    plugin.getConfigManager()
                        .getPrefix())
                .replace(
                    "{job}",
                    job.getDisplayName()));
    }

    private void depositMoney(
            Player player,
            JobDefinition.ActionReward action) {

        if (action.getMoney() <= 0.0D
                || plugin.getHookManager() == null
                || !plugin.getHookManager()
                    .isVaultEnabled()
                || plugin.getHookManager()
                    .getVaultHook() == null) {
            return;
        }

        plugin.getHookManager()
            .getVaultHook()
            .deposit(
                player.getName(),
                action.getMoney());
    }

    private void updateHud(
            Player player,
            PlayerData data,
            LevelUpResult result) {

        if (plugin.getHudManager() == null
                || result == null
                || result.getXpActual() <= 0) {
            return;
        }

        plugin.getHudManager().onXpGain(
            player,
            data,
            JOB_ID,
            result.getXpActual(),
            result);
    }

    private void progressQuest(
            Player player,
            BlockIdentity identity) {

        if (plugin.getQuestManager() == null) {
            return;
        }

        /*
         * Les quêtes utilisent toujours une cible précise MATERIAL:data.
         * GLOWING_REDSTONE_ORE est normalisé en REDSTONE_ORE afin qu'un minerai
         * momentanément allumé compte dans la même quête de redstone.
         */
        plugin.getQuestManager().progress(
            player,
            "MINE",
            identity.questKey,
            1);
    }

    /**
     * Identité immuable d'un bloc 1.8.
     */
    private static final class BlockIdentity {

        private final String materialKey;
        private final int data;
        private final String exactKey;
        private final String questKey;

        private BlockIdentity(
                String materialKey,
                int data,
                String exactKey,
                String questKey) {

            this.materialKey = materialKey;
            this.data = data;
            this.exactKey = exactKey;
            this.questKey = questKey;
        }

        private static BlockIdentity from(Block block) {
            String materialKey =
                block.getType().name();

            int data =
                block.getData() & 0xFF;

            String exactKey =
                materialKey + ":" + data;

            String questMaterial =
                block.getType()
                        == Material.GLOWING_REDSTONE_ORE
                    ? Material.REDSTONE_ORE.name()
                    : materialKey;

            String questKey =
                questMaterial + ":" + data;

            return new BlockIdentity(
                materialKey,
                data,
                exactKey,
                questKey);
        }

        private boolean isGlowingRedstone() {
            return Material.GLOWING_REDSTONE_ORE
                .name()
                .equals(materialKey);
        }
    }
}