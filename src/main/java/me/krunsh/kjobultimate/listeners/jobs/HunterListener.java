package me.krunsh.kjobultimate.listeners.jobs;

import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.hooks.KstackerHook;
import me.krunsh.kjobultimate.jobs.JobDefinition;

/**
 * Chasseur V3.14.
 *
 * KStacker produit directement le nombre d'unités. JobActionService applique
 * ensuite cette même valeur à XP / money / HUD / quête.
 */
public final class HunterListener implements Listener {

    private static final String JOB_ID =
        "hunter";

    private static final int STACK_CAP =
        3;

    private static final String GAMEMODE_BYPASS =
        "kjobsultimate.bypass.gamemodecheck";

    private final KjobUltimate plugin;

    public HunterListener(
            KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
        priority = EventPriority.NORMAL,
        ignoreCancelled = true
    )
    public void onEntityDeath(
            EntityDeathEvent event) {

        LivingEntity entity =
            event.getEntity();

        if (entity == null) {
            return;
        }

        Player killer =
            entity.getKiller();

        if (killer == null
                || !isGameModeAllowed(killer)) {

            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(killer);

        if (data == null
                || !plugin.getSlotManager()
                    .isJobActive(
                        data,
                        JOB_ID
                    )) {

            return;
        }

        String entityKey =
            entity instanceof Skeleton
                && ((Skeleton) entity).getSkeletonType()
                    == Skeleton.SkeletonType.WITHER
                ? "WITHER_SKELETON"
                : entity.getType().name();

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(JOB_ID);

        if (job == null) {
            return;
        }

        JobDefinition.ActionReward action =
            job.getAction(entityKey);

        if (action == null) {
            return;
        }

        int units =
            resolveKillUnits(entity);

        plugin.getJobActionService()
            .apply(
                killer,
                data,
                job,
                action,
                units,
                "KILL",
                entityKey
            );
    }

    private int resolveKillUnits(
            LivingEntity entity) {

        if (plugin.getHookManager() == null) {
            return 1;
        }

        KstackerHook kstacker =
            plugin.getHookManager()
                .getKstackerHook();

        if (kstacker == null
                || !kstacker.isGhostEntity(entity)) {

            return 1;
        }

        return Math.max(
            1,
            Math.min(
                STACK_CAP,
                kstacker.getKillMultiplier(entity)
            )
        );
    }

    private boolean isGameModeAllowed(
            Player player) {

        if (player.hasPermission(
                GAMEMODE_BYPASS
            )) {
            return true;
        }

        if (player.getGameMode() == GameMode.CREATIVE
                && plugin.getConfigManager()
                    .isBlockXpCreative()) {
            return false;
        }

        return player.getGameMode() != GameMode.SPECTATOR
            || !plugin.getConfigManager()
                .isBlockXpSpectator();
    }
}
