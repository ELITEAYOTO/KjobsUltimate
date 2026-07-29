package me.krunsh.kjobultimate.listeners.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener Phase 3 — Job Mineur.
 * Accorde XP et argent au joueur quand il casse un bloc défini dans mineur.yml.
 */
public final class MinerListener implements Listener {

    private static final String JOB_ID = "mineur";

    private final KjobUltimate plugin;

    public MinerListener(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Gate 1 : mode créatif (respecte la config anti_abuse.block_creative)
        if (player.getGameMode() == GameMode.CREATIVE
                && plugin.getConfigManager().isBlockXpCreative()) return;
        // Gate 2 : mode spectateur (respecte la config + bypass permission)
        if (player.getGameMode() == GameMode.SPECTATOR
                && plugin.getConfigManager().isBlockXpSpectator()
                && !player.hasPermission("kjob.bypass.gamemodecheck")) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;

        // Gate 3 : job actif dans un slot
        if (!plugin.getSlotManager().isJobActive(data, JOB_ID)) return;

        String blockKey = event.getBlock().getType().name();

        JobDefinition job = plugin.getJobRegistry().getJob(JOB_ID);
        if (job == null) return;

        // Gate 4 : bloc déclaré dans la config du job
        JobDefinition.ActionReward action = job.getAction(blockKey);
        if (action == null) return;

        // Gate 5 : silk touch bloqué pour ce bloc
        if (action.isSilkTouchBlocked()
                && plugin.getConfigManager().isSilkTouchBlocked()
                && hasSilkTouch(player)) {
            return;
        }

        // Gate 6 : anti-farm position (évite de casser le même bloc plusieurs fois en boucle)
        String locationKey = event.getBlock().getWorld().getName()
            + ":" + event.getBlock().getX()
            + ":" + event.getBlock().getY()
            + ":" + event.getBlock().getZ();
        if (data.isBlockOnCooldown(locationKey)) return;

        // Gate 7 : plafond quotidien
        plugin.getXpManager().checkDailyReset(data, JOB_ID);
        if (plugin.getXpManager().isDailyCapReached(data, JOB_ID)) {
            player.sendMessage(plugin.getConfigManager().getMessage("anti_abuse.daily_cap_reached")
                .replace("{prefix}", plugin.getConfigManager().getPrefix())
                .replace("{job}", job.getDisplayName()));
            return;
        }

        // Activer le cooldown sur cette position (anti-farm) AVANT d'attribuer l'XP
        long blockCooldownMs = plugin.getConfigManager().getBlockCooldown() * 1000L;
        data.setBlockCooldown(locationKey, blockCooldownMs);
        LevelUpResult result = plugin.getXpManager().addXP(player, data, JOB_ID, action.getXp());

        // Attribution argent (Vault)
        if (action.getMoney() > 0 && plugin.getHookManager().isVaultEnabled()) {
            plugin.getHookManager().getVaultHook().deposit(player.getName(), action.getMoney());
        }

        // Level up
        if (result.isLeveledUp()) {
            plugin.getXpManager().handleLevelUp(player, data, JOB_ID, result);
        }

        if (plugin.getHudManager() != null)
            plugin.getHudManager().onXpGain(player, data, JOB_ID, result.getXpActual(), result);
        if (plugin.getQuestManager() != null) {
            // La data 1.8 fait partie de l'identite des minerais custom.
            // QuestDefinition garde la compatibilite des objectifs sans data.
            String questTarget = blockKey + ":" + (event.getBlock().getData() & 0xFF);
            plugin.getQuestManager().progress(player, "MINE", questTarget, 1);
        }
    }

    private boolean hasSilkTouch(Player player) {
        ItemStack tool = player.getInventory().getItemInHand();
        return tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH);
    }
}
