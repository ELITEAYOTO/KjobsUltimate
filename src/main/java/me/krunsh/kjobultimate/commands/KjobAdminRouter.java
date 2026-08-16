package me.krunsh.kjobultimate.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.action.JobActionService;
import me.krunsh.kjobultimate.hud.HudManager;
import me.krunsh.kjobultimate.performance.BlockCooldownService;
import me.krunsh.kjobultimate.performance.UiInvalidationQueue;
import me.krunsh.kjobultimate.persistence.QuestWriteBuffer;

/**
 * Ajoute /kjobs perf sans dupliquer KjobAdminCommand.
 *
 * Toutes les anciennes sous-commandes sont déléguées au command handler
 * existant. "perf reset" ne modifie pas les services : il mémorise simplement
 * une nouvelle baseline locale.
 */
public final class KjobAdminRouter
        implements CommandExecutor, TabCompleter {

    private final KjobUltimate plugin;
    private final KjobAdminCommand delegate;

    private Baseline baseline =
        new Baseline();

    public KjobAdminRouter(
            KjobUltimate plugin,
            KjobAdminCommand delegate) {

        if (plugin == null
                || delegate == null) {

            throw new IllegalArgumentException(
                "plugin/delegate ne peuvent pas être null."
            );
        }

        this.plugin = plugin;
        this.delegate = delegate;

        resetBaseline();
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (args != null
                && args.length > 0
                && "perf".equalsIgnoreCase(args[0])) {

            if (!hasPermission(sender)) {
                sender.sendMessage(
                    "§8[§6Jobs§8] §cTu n'as pas la permission."
                );
                return true;
            }

            if (args.length > 1
                    && "reset".equalsIgnoreCase(args[1])) {

                resetBaseline();

                sender.sendMessage(
                    "§8[§6Jobs§8] §aMétriques performance remises à zéro."
                );

                return true;
            }

            sendPerf(sender);
            return true;
        }

        return delegate.onCommand(
            sender,
            command,
            label,
            args
        );
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args) {

        if (args != null
                && args.length == 2
                && "perf".equalsIgnoreCase(args[0])
                && hasPermission(sender)) {

            String token =
                args[1] == null
                    ? ""
                    : args[1].toLowerCase(Locale.ROOT);

            return "reset".startsWith(token)
                ? Collections.singletonList("reset")
                : Collections.<String>emptyList();
        }

        List<String> delegated =
            delegate.onTabComplete(
                sender,
                command,
                alias,
                args
            );

        if (!hasPermission(sender)
                || args == null
                || args.length != 1) {

            return delegated;
        }

        ArrayList<String> values =
            new ArrayList<String>();

        if (delegated != null) {
            values.addAll(delegated);
        }

        String token =
            args[0] == null
                ? ""
                : args[0].toLowerCase(Locale.ROOT);

        if ("perf".startsWith(token)
                && !values.contains("perf")) {

            values.add("perf");
        }

        Collections.sort(values);
        return values;
    }

    private void sendPerf(CommandSender sender) {
        JobActionService actions =
            plugin.getJobActionService();

        BlockCooldownService cooldowns =
            plugin.getBlockCooldownService();

        UiInvalidationQueue ui =
            plugin.getUiInvalidationQueue();

        QuestWriteBuffer quests =
            plugin.getQuestWriteBuffer();

        HudManager hud =
            plugin.getHudManager();

        sender.sendMessage(
            "§8----------------------------------------------"
        );

        sender.sendMessage(
            "§6§lKjobs §7- Performance V3.16"
        );

        sender.sendMessage(
            "§7Online: §f"
                + Bukkit.getOnlinePlayers().size()
                + " §8| §7PlayerData cache: §f"
                + (plugin.getPlayerDataManager() == null
                    ? 0
                    : plugin.getPlayerDataManager().getCacheSize())
        );

        if (actions != null) {
            long attempts =
                delta(
                    actions.getAttempts(),
                    baseline.actionAttempts
                );

            long actionApplied =
                delta(
                    actions.getApplied(),
                    baseline.actionApplied
                );

            sender.sendMessage(
                "§eActions §7attempts=§f"
                    + attempts
                    + " §7applied=§a"
                    + actionApplied
                    + " §7units=§f"
                    + delta(
                        actions.getUnitsApplied(),
                        baseline.actionUnits
                    )
                    + " §7xp=§f"
                    + delta(
                        actions.getXpActualTotal(),
                        baseline.actionXp
                    )
            );

            sender.sendMessage(
                "§7Action reject §8forced=§f"
                    + delta(
                        actions.getForcedRejected(),
                        baseline.actionForced
                    )
                    + " §8inactive=§f"
                    + delta(
                        actions.getInactiveRejected(),
                        baseline.actionInactive
                    )
                    + " §8cap=§f"
                    + delta(
                        actions.getCapRejected(),
                        baseline.actionCap
                    )
                    + " §8avg/max=§f"
                    + fmt(actions.getAverageMillis())
                    + "/"
                    + fmt(actions.getMaxMillis())
                    + "ms"
            );
        }

        if (cooldowns != null) {
            sender.sendMessage(
                "§bCooldowns §7players=§f"
                    + cooldowns.getTrackedPlayers()
                    + " §7entries=§f"
                    + cooldowns.getTotalEntries()
                    + " §7checks=§f"
                    + delta(
                        cooldowns.getChecks(),
                        baseline.cooldownChecks
                    )
                    + " §7hits=§f"
                    + delta(
                        cooldowns.getHits(),
                        baseline.cooldownHits
                    )
            );

            sender.sendMessage(
                "§7Cooldown cleanup §8pruned=§f"
                    + delta(
                        cooldowns.getExpiredPruned(),
                        baseline.cooldownPruned
                    )
                    + " §8evicted=§f"
                    + delta(
                        cooldowns.getCapacityEvictions(),
                        baseline.cooldownEvicted
                    )
            );
        }

        if (ui != null) {
            long marks =
                delta(
                    ui.getDirtyMarks(),
                    baseline.uiMarks
                );

            long merged =
                delta(
                    ui.getCoalescedMarks(),
                    baseline.uiCoalesced
                );

            sender.sendMessage(
                "§dKgui §7pending=§f"
                    + ui.size()
                    + " §7marks=§f"
                    + marks
                    + " §7coalesced=§a"
                    + merged
                    + " §7("
                    + percent(merged, marks)
                    + "%)"
                    + " §7invalidations=§f"
                    + delta(
                        ui.getPlayerInvalidations(),
                        baseline.uiInvalidations
                    )
            );
        }

        if (quests != null) {
            long enqueued =
                delta(
                    quests.getEnqueued(),
                    baseline.questEnqueued
                );

            long merged =
                delta(
                    quests.getCoalesced(),
                    baseline.questCoalesced
                );

            sender.sendMessage(
                "§aQuestDB §7pending=§f"
                    + quests.getPendingCount()
                    + " §7peak=§f"
                    + quests.getPeakPending()
                    + " §7enqueued=§f"
                    + enqueued
                    + " §7coalesced=§a"
                    + percent(merged, enqueued)
                    + "%"
            );

            sender.sendMessage(
                "§7QuestDB writes §8persisted=§f"
                    + delta(
                        quests.getPersisted(),
                        baseline.questPersisted
                    )
                    + " §8batches=§f"
                    + delta(
                        quests.getBatchCount(),
                        baseline.questBatches
                    )
                    + " §8fail=§f"
                    + delta(
                        quests.getFailureCount(),
                        baseline.questFailures
                    )
                    + " §8retry=§f"
                    + delta(
                        quests.getRetryCount(),
                        baseline.questRetries
                    )
                    + " §8last/max=§f"
                    + quests.getLastBatchMillis()
                    + "/"
                    + quests.getMaxBatchMillis()
                    + "ms"
            );
        }

        if (hud != null) {
            sender.sendMessage(
                "§6HUD §7active=§f"
                    + hud.getActivePlayers()
                    + " §7tracked=§f"
                    + hud.getTrackedPlayers()
                    + " §7visits=§f"
                    + delta(
                        hud.getSchedulerPlayerVisits(),
                        baseline.hudVisits
                    )
                    + " §7tick avg/max=§f"
                    + fmt(hud.getAverageTickMillis())
                    + "/"
                    + fmt(hud.getMaxTickMillis())
                    + "ms"
            );

            long bossPackets =
                delta(
                    hud.getBossSpawnPackets(),
                    baseline.hudBossSpawn
                )
                + delta(
                    hud.getBossMetadataPackets(),
                    baseline.hudBossMetadata
                )
                + delta(
                    hud.getBossTeleportPackets(),
                    baseline.hudBossTeleport
                )
                + delta(
                    hud.getBossDestroyPackets(),
                    baseline.hudBossDestroy
                );

            sender.sendMessage(
                "§7HUD packets §8action=§f"
                    + delta(
                        hud.getActionBarPackets(),
                        baseline.hudActionPackets
                    )
                    + " §8boss=§f"
                    + bossPackets
                    + " §8title=§f"
                    + delta(
                        hud.getTitlePackets(),
                        baseline.hudTitlePackets
                    )
                    + " §8toast=§f"
                    + delta(
                        hud.getStatisticPackets(),
                        baseline.hudStatisticPackets
                    )
            );

            sender.sendMessage(
                "§7HUD NMS §8cache=§a"
                    + (hud.isNmsCacheReady() ? "ON" : "OFF")
                    + " §8reflection-lookups=§f"
                    + hud.getNmsReflectionResolutions()
                    + " §8failures=§f"
                    + delta(
                        hud.getNmsFailureCount(),
                        baseline.hudFailures
                    )
                    + " §8particle-safe=§f"
                    + delta(
                        hud.getBossParticleSafePlacements(),
                        baseline.hudSafePlacements
                    )
            );
        }

        sender.sendMessage(
            "§8/§7kjobs perf reset §8= nouveau point de comparaison"
        );

        sender.sendMessage(
            "§8----------------------------------------------"
        );
    }

    private void resetBaseline() {
        Baseline next =
            new Baseline();

        JobActionService actions =
            plugin.getJobActionService();

        if (actions != null) {
            next.actionAttempts = actions.getAttempts();
            next.actionApplied = actions.getApplied();
            next.actionUnits = actions.getUnitsApplied();
            next.actionXp = actions.getXpActualTotal();
            next.actionForced = actions.getForcedRejected();
            next.actionInactive = actions.getInactiveRejected();
            next.actionCap = actions.getCapRejected();
        }

        BlockCooldownService cooldowns =
            plugin.getBlockCooldownService();

        if (cooldowns != null) {
            next.cooldownChecks = cooldowns.getChecks();
            next.cooldownHits = cooldowns.getHits();
            next.cooldownPruned = cooldowns.getExpiredPruned();
            next.cooldownEvicted = cooldowns.getCapacityEvictions();
        }

        UiInvalidationQueue ui =
            plugin.getUiInvalidationQueue();

        if (ui != null) {
            next.uiMarks = ui.getDirtyMarks();
            next.uiCoalesced = ui.getCoalescedMarks();
            next.uiInvalidations = ui.getPlayerInvalidations();
        }

        QuestWriteBuffer quests =
            plugin.getQuestWriteBuffer();

        if (quests != null) {
            next.questEnqueued = quests.getEnqueued();
            next.questCoalesced = quests.getCoalesced();
            next.questPersisted = quests.getPersisted();
            next.questBatches = quests.getBatchCount();
            next.questFailures = quests.getFailureCount();
            next.questRetries = quests.getRetryCount();
        }

        HudManager hud =
            plugin.getHudManager();

        if (hud != null) {
            next.hudVisits = hud.getSchedulerPlayerVisits();
            next.hudActionPackets = hud.getActionBarPackets();
            next.hudBossSpawn = hud.getBossSpawnPackets();
            next.hudBossMetadata = hud.getBossMetadataPackets();
            next.hudBossTeleport = hud.getBossTeleportPackets();
            next.hudBossDestroy = hud.getBossDestroyPackets();
            next.hudTitlePackets = hud.getTitlePackets();
            next.hudStatisticPackets = hud.getStatisticPackets();
            next.hudFailures = hud.getNmsFailureCount();
            next.hudSafePlacements = hud.getBossParticleSafePlacements();
        }

        baseline = next;
    }

    private boolean hasPermission(CommandSender sender) {
        return sender != null
            && (sender.isOp()
                || sender.hasPermission("kjobsultimate.admin")
                || sender.hasPermission("kjobsultimate.admin.*"));
    }

    private static long delta(
            long value,
            long base) {

        return Math.max(
            0L,
            value - base
        );
    }

    private static String fmt(double value) {
        return String.format(
            Locale.US,
            "%.3f",
            Math.max(0D, value)
        );
    }

    private static String percent(
            long part,
            long total) {

        if (total <= 0L
                || part <= 0L) {

            return "0.0";
        }

        return String.format(
            Locale.US,
            "%.1f",
            Math.min(
                100D,
                (part * 100D) / total
            )
        );
    }

    private static final class Baseline {

        private long actionAttempts;
        private long actionApplied;
        private long actionUnits;
        private long actionXp;
        private long actionForced;
        private long actionInactive;
        private long actionCap;

        private long cooldownChecks;
        private long cooldownHits;
        private long cooldownPruned;
        private long cooldownEvicted;

        private long uiMarks;
        private long uiCoalesced;
        private long uiInvalidations;

        private long questEnqueued;
        private long questCoalesced;
        private long questPersisted;
        private long questBatches;
        private long questFailures;
        private long questRetries;

        private long hudVisits;
        private long hudActionPackets;
        private long hudBossSpawn;
        private long hudBossMetadata;
        private long hudBossTeleport;
        private long hudBossDestroy;
        private long hudTitlePackets;
        private long hudStatisticPackets;
        private long hudFailures;
        private long hudSafePlacements;
    }
}
