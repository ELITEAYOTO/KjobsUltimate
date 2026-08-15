package me.krunsh.kjobultimate.action;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.hooks.HookManager;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.entity.Player;

/**
 * Accounting central des actions de métier.
 *
 * Une action acceptée utilise UNE SEULE quantité "units" pour :
 * - XP ;
 * - argent ;
 * - HUD ;
 * - progression de quête.
 *
 * Les listeners restent responsables de la détection spécifique :
 * maturité, Silk Touch, bloc, entité, recette, cooldown de position, etc.
 */
public final class JobActionService {

    private final KjobUltimate plugin;

    public JobActionService(
            KjobUltimate plugin) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                "plugin ne peut pas être null."
            );
        }

        this.plugin = plugin;
    }

    public boolean apply(
            Player player,
            PlayerData data,
            JobDefinition job,
            JobDefinition.ActionReward reward,
            int units,
            String questType,
            String questTarget) {

        return apply(
            player,
            data,
            job,
            reward,
            units,
            questType,
            questTarget,
            false
        );
    }

    public boolean apply(
            Player player,
            PlayerData data,
            JobDefinition job,
            JobDefinition.ActionReward reward,
            int units,
            String questType,
            String questTarget,
            boolean forced) {

        if (player == null
                || data == null
                || job == null
                || reward == null
                || units <= 0) {

            return false;
        }

        if (forced
                && !reward.isAllowForced()) {

            if (plugin.getConfigManager().isDebugXp()) {
                KjobLogger.info(
                    "[Action] Craft forcé ignoré pour "
                        + player.getName()
                        + "/"
                        + job.getId()
                );
            }

            return false;
        }

        if (!plugin.getSlotManager()
                .isJobActive(
                    data,
                    job.getId()
                )) {

            return false;
        }

        int baseXp =
            saturatingMultiply(
                Math.max(
                    0,
                    reward.getXp()
                ),
                units
            );

        LevelUpResult result =
            plugin.getXpManager()
                .addXP(
                    player,
                    data,
                    job.getId(),
                    baseXp
                );

        /*
         * V3.14 : on ne pré-vérifie plus deux fois le daily cap.
         * XpManager reste la source de vérité. Si aucune XP n'a été attribuée,
         * on ne fait un check supplémentaire que pour distinguer un cap atteint
         * d'un niveau max / multiplicateur à 0.
         */
        if (reward.getXp() > 0
                && result.getXpActual() <= 0
                && !result.isAtMaxLevel()
                && plugin.getXpManager()
                    .isDailyCapReached(
                        data,
                        job.getId()
                    )) {

            sendDailyCapMessage(
                player,
                job
            );

            return false;
        }

        depositMoney(
            player,
            reward,
            units
        );

        if (result.isLeveledUp()) {
            plugin.getXpManager()
                .handleLevelUp(
                    player,
                    data,
                    job.getId(),
                    result
                );
        }

        if (plugin.getHudManager() != null) {
            plugin.getHudManager()
                .onXpGain(
                    player,
                    data,
                    job.getId(),
                    result.getXpActual(),
                    result
                );
        }

        if (plugin.getQuestManager() != null
                && questType != null
                && !questType.trim().isEmpty()) {

            plugin.getQuestManager()
                .progress(
                    player,
                    questType,
                    questTarget,
                    units
                );
        }

        return true;
    }

    private void depositMoney(
            Player player,
            JobDefinition.ActionReward reward,
            int units) {

        if (reward.getMoney() <= 0D) {
            return;
        }

        HookManager hooks =
            plugin.getHookManager();

        if (hooks == null
                || !hooks.isVaultEnabled()
                || hooks.getVaultHook() == null) {

            return;
        }

        double total =
            reward.getMoney()
                * (double) units;

        if (Double.isNaN(total)
                || Double.isInfinite(total)
                || total <= 0D) {

            KjobLogger.warn(
                "[Action] Montant Vault invalide ignoré pour "
                    + player.getName()
                    + " : "
                    + total
            );

            return;
        }

        hooks.getVaultHook()
            .deposit(
                player.getName(),
                total
            );
    }

    private void sendDailyCapMessage(
            Player player,
            JobDefinition job) {

        String message =
            plugin.getConfigManager()
                .getMessage(
                    "anti_abuse.daily_cap_reached"
                )
                .replace(
                    "{job}",
                    job.getDisplayName()
                );

        if (!message.isEmpty()) {
            player.sendMessage(
                message
            );
        }
    }

    private static int saturatingMultiply(
            int value,
            int multiplier) {

        if (value <= 0
                || multiplier <= 0) {

            return 0;
        }

        long result =
            (long) value
                * (long) multiplier;

        return result >= Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int) result;
    }
}
