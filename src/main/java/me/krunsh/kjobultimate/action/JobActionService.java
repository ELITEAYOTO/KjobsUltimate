package me.krunsh.kjobultimate.action;

import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.hooks.HookManager;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Accounting central des actions de métier.
 *
 * Une action acceptée utilise UNE SEULE quantité "units" pour :
 * - XP ;
 * - argent ;
 * - HUD ;
 * - progression de quête.
 *
 * V3.16 ajoute des compteurs O(1) pour /kjobs perf.
 */
public final class JobActionService {

    private final KjobUltimate plugin;

    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong applied = new AtomicLong();
    private final AtomicLong unitsApplied = new AtomicLong();
    private final AtomicLong xpActual = new AtomicLong();
    private final AtomicLong forcedRejected = new AtomicLong();
    private final AtomicLong inactiveRejected = new AtomicLong();
    private final AtomicLong capRejected = new AtomicLong();
    private final AtomicLong totalNanos = new AtomicLong();
    private final AtomicLong maxNanos = new AtomicLong();

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

        attempts.incrementAndGet();

        long start =
            System.nanoTime();

        try {
            if (forced
                    && !reward.isAllowForced()) {

                forcedRejected.incrementAndGet();

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

                inactiveRejected.incrementAndGet();
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

            if (reward.getXp() > 0
                    && result.getXpActual() <= 0
                    && !result.isAtMaxLevel()
                    && plugin.getXpManager()
                        .isDailyCapReached(
                            data,
                            job.getId()
                        )) {

                capRejected.incrementAndGet();

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

            applied.incrementAndGet();
            unitsApplied.addAndGet(units);
            xpActual.addAndGet(
                Math.max(
                    0,
                    result.getXpActual()
                )
            );

            return true;

        } finally {
            long elapsed =
                Math.max(
                    0L,
                    System.nanoTime() - start
                );

            totalNanos.addAndGet(elapsed);
            updateMax(maxNanos, elapsed);
        }
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
            player.sendMessage(message);
        }
    }

    public long getAttempts() {
        return attempts.get();
    }

    public long getApplied() {
        return applied.get();
    }

    public long getUnitsApplied() {
        return unitsApplied.get();
    }

    public long getXpActualTotal() {
        return xpActual.get();
    }

    public long getForcedRejected() {
        return forcedRejected.get();
    }

    public long getInactiveRejected() {
        return inactiveRejected.get();
    }

    public long getCapRejected() {
        return capRejected.get();
    }

    public double getAverageMillis() {
        long count = attempts.get();

        if (count <= 0L) {
            return 0D;
        }

        return (totalNanos.get() / 1_000_000D) / count;
    }

    public double getMaxMillis() {
        return maxNanos.get() / 1_000_000D;
    }

    private static void updateMax(
            AtomicLong target,
            long value) {

        while (true) {
            long previous =
                target.get();

            if (value <= previous) {
                return;
            }

            if (target.compareAndSet(
                    previous,
                    value
                )) {

                return;
            }
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
