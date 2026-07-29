package me.krunsh.kjobultimate.listeners.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.util.CropUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Listener Phase 3 — Job Farmer.
 * Accorde XP et argent quand le joueur récolte une culture définie dans farmer.yml.
 * Supporte le mode "crops_mature_only" (anti-abuse : cultures immatures ignorées).
 */
public final class FarmerListener implements Listener {

    private static final String JOB_ID = "farmer";

    private final KjobUltimate plugin;

    public FarmerListener(KjobUltimate plugin) {
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

        // Vérification préliminaire : est-ce une culture éligible ?
        if (!CropUtil.isFarmingCrop(event.getBlock().getType())) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;

        // Gate 3 : job actif dans un slot
        if (!plugin.getSlotManager().isJobActive(data, JOB_ID)) return;

        // Gate 5 (farmer) : maturité obligatoire si activée
        if (plugin.getConfigManager().isCropsMatureOnly()
                && !CropUtil.isMature(event.getBlock())) {
            return;
        }

        String blockKey = event.getBlock().getType().name();

        JobDefinition job = plugin.getJobRegistry().getJob(JOB_ID);
        if (job == null) return;

        // Gate 4 : bloc déclaré dans la config du job
        JobDefinition.ActionReward action = job.getAction(blockKey);
        if (action == null) return;

        // Gate 6 : anti-farm position
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

        // Attribution XP
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
            plugin.getQuestManager().progress(player, "HARVEST", blockKey, 1);
        }
    }
}
