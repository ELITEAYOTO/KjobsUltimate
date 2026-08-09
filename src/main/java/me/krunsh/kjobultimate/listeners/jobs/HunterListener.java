package me.krunsh.kjobultimate.listeners.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.hooks.KstackerHook;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Listener Phase 4 — Job Hunter.
 * Accorde XP et argent quand le joueur tue un mob défini dans hunter.yml.
 * Supporte Kstacker : si l'entité est un ghost, multiplie l'XP de base (cap = 3x).
 */
public final class HunterListener implements Listener {

    private static final String JOB_ID    = "hunter";
    private static final int    STACK_CAP = 3;

    private final KjobUltimate plugin;

    public HunterListener(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = event.getEntity();

        Player killer = entity.getKiller();
        if (killer == null) return;

        // Gate 1 : mode créatif (respecte la config anti_abuse.block_creative)
        if (killer.getGameMode() == GameMode.CREATIVE
                && plugin.getConfigManager().isBlockXpCreative()) return;
        // Gate 2 : mode spectateur (respecte la config + bypass permission)
        if (killer.getGameMode() == GameMode.SPECTATOR
                && plugin.getConfigManager().isBlockXpSpectator()
                && !killer.hasPermission("kjob.bypass.gamemodecheck")) return;

        PlayerData data = plugin.getPlayerDataManager().get(killer);
        if (data == null) return;

        // Gate 3 : job actif dans un slot
        if (!plugin.getSlotManager().isJobActive(data, JOB_ID)) return;

        String entityKey = entity instanceof Skeleton
            && ((Skeleton) entity).getSkeletonType() == Skeleton.SkeletonType.WITHER
                ? "WITHER_SKELETON"
                : entity.getType().name();

        JobDefinition job = plugin.getJobRegistry().getJob(JOB_ID);
        if (job == null) return;

        // Gate 4 : entité déclarée dans la config du job
        JobDefinition.ActionReward action = job.getAction(entityKey);
        if (action == null) return;

        // Gate 7 : plafond quotidien
        plugin.getXpManager().checkDailyReset(data, JOB_ID);
        if (plugin.getXpManager().isDailyCapReached(data, JOB_ID)) {
            killer.sendMessage(plugin.getConfigManager().getMessage("anti_abuse.daily_cap_reached")
                .replace("{prefix}", plugin.getConfigManager().getPrefix())
                .replace("{job}", job.getDisplayName()));
            return;
        }

        // Kstacker : si ghost, multiplier XP de base (limité à STACK_CAP)
        int killMultiplier = 1;
        KstackerHook kstacker = plugin.getHookManager().getKstackerHook();
        if (kstacker != null && kstacker.isGhostEntity(entity)) {
            killMultiplier = Math.min(kstacker.getKillMultiplier(entity), STACK_CAP);
        }

        int baseXp = action.getXp() * killMultiplier;

        // Attribution XP
        LevelUpResult result = plugin.getXpManager().addXP(killer, data, JOB_ID, baseXp);

        // Attribution argent (Vault) — scale avec multiplicateur Kstacker
        if (action.getMoney() > 0 && plugin.getHookManager().isVaultEnabled()) {
            plugin.getHookManager().getVaultHook()
                .deposit(killer.getName(), action.getMoney() * killMultiplier);
        }

        // Level up
        if (result.isLeveledUp()) {
            plugin.getXpManager().handleLevelUp(killer, data, JOB_ID, result);
        }

        if (plugin.getHudManager() != null)
            plugin.getHudManager().onXpGain(killer, data, JOB_ID, result.getXpActual(), result);
        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().progress(killer, "KILL", entityKey, killMultiplier);
        }
    }
}
