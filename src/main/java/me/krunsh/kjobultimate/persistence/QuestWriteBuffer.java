package me.krunsh.kjobultimate.persistence;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.QuestData;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * V3.15 — buffer de persistance des quêtes.
 *
 * Objectif :
 * ne plus créer une tâche async + une écriture SQL pour chaque bloc/action.
 *
 * Les changements sont fusionnés en RAM par (UUID, questId), puis écrits par
 * lots sur le scheduler async Bukkit.
 *
 * Les resets administratifs utilisent une barrière :
 * - la clé est bloquée ;
 * - les anciennes écritures pending sont supprimées ;
 * - on attend la fin du batch éventuellement déjà parti ;
 * - le reset DB peut alors s'exécuter sans qu'une vieille progression ne
 *   revienne ensuite.
 */
public final class QuestWriteBuffer {

    private static final long CONTROL_DELAY_TICKS = 20L;

    private final KjobUltimate plugin;
    private final QuestWriteQueue queue = new QuestWriteQueue();

    private final AtomicBoolean flushRunning = new AtomicBoolean(false);
    private final Object idleMonitor = new Object();

    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong coalesced = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong batches = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong immediateFlushes = new AtomicLong();
    private final AtomicLong quitFlushRequests = new AtomicLong();

    private final AtomicInteger peakPending = new AtomicInteger();

    private volatile Settings settings = Settings.defaults();
    private volatile boolean started;
    private volatile boolean shuttingDown;

    private volatile long lastFlushAtMillis;
    private volatile long lastBatchMillis;
    private volatile long maxBatchMillis;
    private volatile long lastFailureLogAtMillis;

    private BukkitTask periodicTask;

    public QuestWriteBuffer(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin ne peut pas être null.");
        }
        this.plugin = plugin;
    }

    public void start() {
        if (started) {
            return;
        }

        started = true;
        shuttingDown = false;
        reloadSettings();

        periodicTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            new Runnable() {
                @Override
                public void run() {
                    periodicTick();
                }
            },
            CONTROL_DELAY_TICKS,
            CONTROL_DELAY_TICKS
        );

        Settings current = settings;

        KjobLogger.success(
            "QuestWriteBuffer V3.15 actif - "
                + "enabled=" + current.enabled
                + ", interval=" + current.flushIntervalTicks + "t"
                + ", batch=" + current.maxBatchSize
                + ", txBatch=true"
                + ", completionImmediate=" + current.flushOnCompletion
        );
    }

    /**
     * Recharge uniquement un snapshot de réglages.
     *
     * La task de contrôle reste à 20 ticks et décide si le vrai intervalle de
     * flush est atteint : /kjobs reload n'a donc pas besoin de recréer des
     * schedulers à chaque changement.
     */
    public void reloadSettings() {
        boolean wasEnabled = settings.enabled;

        settings = Settings.load(
            plugin.getConfigManager().getMainConfig()
        );

        /*
         * Si le coalescing est désactivé à chaud, les snapshots déjà présents
         * ne doivent pas rester bloqués en RAM : on demande un drain forcé.
         */
        if (started
                && wasEnabled
                && !settings.enabled
                && queue.size() > 0) {

            flushAsync(true);
        }
    }

    /** Service initialisé et capable de sécuriser les écritures/reset. */
    public boolean isAvailable() {
        return started && !shuttingDown;
    }

    /** true = coalescing périodique activé par la configuration. */
    public boolean isEnabled() {
        return isAvailable() && settings.enabled;
    }

    public boolean isFlushOnCompletion() {
        return settings.flushOnCompletion;
    }

    public boolean isFlushOnQuit() {
        return settings.flushOnQuit;
    }

    public void enqueue(
            UUID playerId,
            QuestData questData,
            boolean forceImmediate) {

        if (playerId == null || questData == null || !isAvailable()) {
            return;
        }

        boolean merged = queue.offer(
            playerId,
            questData.getQuestId(),
            questData.getProgress(),
            questData.isCompleted(),
            questData.isClaimed(),
            questData.getCompletedAt()
        );

        enqueued.incrementAndGet();

        if (merged) {
            coalesced.incrementAndGet();
        }

        updatePeak();

        /*
         * enabled=false désactive le COALESCING, pas la couche de sécurité.
         * On garde donc la queue/barrière mais on flush immédiatement.
         */
        if (!settings.enabled) {
            immediateFlushes.incrementAndGet();
            flushAsync(true);
            return;
        }

        boolean completionPriority =
            questData.isCompleted()
                && settings.flushOnCompletion;

        if (forceImmediate || completionPriority) {
            immediateFlushes.incrementAndGet();
            requestImmediateFlush();
        }
    }

    public void onPlayerQuit(UUID playerId) {
        if (playerId == null || !isAvailable() || !settings.flushOnQuit) {
            return;
        }

        if (queue.countForPlayer(playerId) <= 0) {
            return;
        }

        quitFlushRequests.incrementAndGet();
        requestImmediateFlush();
    }

    public void requestImmediateFlush() {
        if (!isAvailable()) {
            return;
        }

        flushAsync(!settings.enabled);
    }

    private void periodicTick() {
        if (!isAvailable() || !settings.enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        long intervalMillis =
            Math.max(1L, settings.flushIntervalTicks) * 50L;

        if (lastFlushAtMillis == 0L
                || now - lastFlushAtMillis >= intervalMillis) {

            flushAsync(false);
        }
    }

    private void flushAsync(boolean force) {
        if (shuttingDown && !force) {
            return;
        }

        if (!force && !settings.enabled) {
            return;
        }

        if (queue.size() <= 0) {
            return;
        }

        if (!flushRunning.compareAndSet(false, true)) {
            return;
        }

        final int batchLimit =
            Math.max(1, settings.maxBatchSize);

        final List<QuestWriteSnapshot> batch =
            queue.drain(batchLimit);

        if (batch.isEmpty()) {
            markIdle();
            return;
        }

        lastFlushAtMillis = System.currentTimeMillis();
        batches.incrementAndGet();

        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            new Runnable() {
                @Override
                public void run() {
                    persistBatch(batch);
                }
            }
        );
    }

    private void persistBatch(List<QuestWriteSnapshot> batch) {
        long start = System.nanoTime();

        int failed = 0;
        Throwable firstFailure = null;

        try {
            /*
             * V3.15 : un batch = une connexion/transaction DB.
             *
             * Le DatabaseManager rollback le lot entier en cas d'erreur. On
             * peut donc soit valider tout le batch, soit requeue tout le batch
             * sans devoir deviner quelles lignes ont réellement été écrites.
             */
            plugin.getDatabaseManager()
                .saveQuestProgressBatch(batch);

            persisted.addAndGet(
                batch.size()
            );

        } catch (Throwable failure) {
            failed = batch.size();
            failures.addAndGet(
                failed
            );
            firstFailure = failure;

            if (settings.retryFailed) {
                for (QuestWriteSnapshot snapshot : batch) {
                    if (snapshot == null
                            || queue.isBlocked(
                                snapshot.getPlayerId(),
                                snapshot.getQuestId())) {

                        continue;
                    }

                    queue.requeue(snapshot);
                    retries.incrementAndGet();
                }
            }

        } finally {
            long elapsed =
                Math.max(
                    0L,
                    (System.nanoTime() - start) / 1_000_000L
                );

            lastBatchMillis = elapsed;
            updateMaxBatch(elapsed);

            markIdle();

            if (failed > 0) {
                logBatchFailure(failed, batch.size(), firstFailure);
                return;
            }

            if (plugin.getConfigManager().isDebugQuest()) {
                KjobLogger.info(
                    "[QuestWriteBuffer] batch="
                        + batch.size()
                        + ", pending="
                        + queue.size()
                        + ", coalesced="
                        + String.format(
                            java.util.Locale.US,
                            "%.1f",
                            getCoalescePercent()
                        )
                        + "%, time="
                        + elapsed
                        + "ms"
                );
            }

            /*
             * Un backlog supérieur au budget est drainé sans attendre le
             * prochain intervalle. Un échec arrête au contraire la boucle afin
             * d'éviter un retry-spin si la DB est indisponible.
             */
            if (!shuttingDown
                    && queue.size() > 0
                    && (settings.drainBacklogImmediately
                        || !settings.enabled)) {

                final boolean forceNext =
                    !settings.enabled;

                Bukkit.getScheduler().runTask(
                    plugin,
                    new Runnable() {
                        @Override
                        public void run() {
                            flushAsync(forceNext);
                        }
                    }
                );
            }
        }
    }

    /**
     * Barrière utilisée avant un reset administratif non-monotone.
     */
    public void beginExclusive(UUID playerId, String questId) {
        queue.blockAndDropPending(playerId, questId);
    }

    /**
     * Attend uniquement le batch de progression en cours.
     *
     * À appeler depuis une tâche ASYNC, sauf pendant le shutdown serveur.
     */
    public boolean awaitIdle(long timeoutMillis) {
        long timeout = Math.max(0L, timeoutMillis);
        long deadline = System.currentTimeMillis() + timeout;

        synchronized (idleMonitor) {
            while (flushRunning.get()) {
                long remaining =
                    deadline - System.currentTimeMillis();

                if (remaining <= 0L) {
                    return false;
                }

                try {
                    idleMonitor.wait(
                        Math.min(remaining, 250L)
                    );
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return true;
    }

    public void endExclusive(
            final UUID playerId,
            final String questId) {

        queue.unblock(playerId, questId);

        if (shuttingDown || !settings.enabled || queue.size() <= 0) {
            return;
        }

        Bukkit.getScheduler().runTask(
            plugin,
            new Runnable() {
                @Override
                public void run() {
                    requestImmediateFlush();
                }
            }
        );
    }

    /**
     * Drain final avant fermeture de DatabaseManager.
     */
    public void shutdownAndFlush() {
        if (!started) {
            return;
        }

        shuttingDown = true;

        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }

        Settings current = settings;

        boolean idle =
            awaitIdle(
                current.shutdownWaitMillis
            );

        if (!idle) {
            KjobLogger.warn(
                "[QuestWriteBuffer] Timeout en attente du batch async au shutdown."
            );
        }

        int saved = 0;
        int failed = 0;

        /*
         * Les clés bloquées par un reset en cours sont laissées volontairement
         * de côté : PlayerDataManager.saveAll() écrit juste après l'état RAM
         * complet, qui est alors la source de vérité.
         */
        while (queue.size() > 0) {
            List<QuestWriteSnapshot> batch =
                queue.drain(
                    Math.max(
                        1,
                        current.maxBatchSize
                    )
                );

            if (batch.isEmpty()) {
                break;
            }

            try {
                plugin.getDatabaseManager()
                    .saveQuestProgressBatch(batch);

                saved += batch.size();
                persisted.addAndGet(
                    batch.size()
                );

            } catch (Exception failure) {
                failed += batch.size();
                failures.addAndGet(
                    batch.size()
                );

                KjobLogger.error(
                    "[QuestWriteBuffer] Flush final batch impossible ("
                        + batch.size()
                        + " snapshot(s)). PlayerDataManager.saveAll() "
                        + "tentera ensuite la sauvegarde complète.",
                    failure
                );
            }
        }

        int left = queue.size();

        KjobLogger.info(
            "[QuestWriteBuffer] Shutdown - saved="
                + saved
                + ", failed="
                + failed
                + ", pendingIgnored="
                + left
        );

        queue.clear();
        started = false;
    }

    private void markIdle() {
        flushRunning.set(false);

        synchronized (idleMonitor) {
            idleMonitor.notifyAll();
        }
    }

    private void updatePeak() {
        int current = queue.size();

        while (true) {
            int previous = peakPending.get();

            if (current <= previous) {
                return;
            }

            if (peakPending.compareAndSet(previous, current)) {
                return;
            }
        }
    }

    private void updateMaxBatch(long elapsed) {
        if (elapsed > maxBatchMillis) {
            maxBatchMillis = elapsed;
        }
    }

    private void logBatchFailure(
            int failed,
            int batchSize,
            Throwable firstFailure) {

        long now = System.currentTimeMillis();
        long minInterval =
            Math.max(1L, settings.failureLogIntervalTicks) * 50L;

        if (now - lastFailureLogAtMillis < minInterval) {
            return;
        }

        lastFailureLogAtMillis = now;

        KjobLogger.error(
            "[QuestWriteBuffer] "
                + failed
                + "/"
                + batchSize
                + " écriture(s) ont échoué ; "
                + (settings.retryFailed
                    ? "retry conservé."
                    : "retry désactivé."),
            firstFailure
        );
    }

    public int getPendingCount() {
        return queue.size();
    }

    public int getPeakPending() {
        return peakPending.get();
    }

    public boolean isFlushRunning() {
        return flushRunning.get();
    }

    public long getEnqueued() {
        return enqueued.get();
    }

    public long getCoalesced() {
        return coalesced.get();
    }

    public long getPersisted() {
        return persisted.get();
    }

    public long getBatchCount() {
        return batches.get();
    }

    public long getFailureCount() {
        return failures.get();
    }

    public long getRetryCount() {
        return retries.get();
    }

    public long getImmediateFlushCount() {
        return immediateFlushes.get();
    }

    public long getQuitFlushRequests() {
        return quitFlushRequests.get();
    }

    public long getLastBatchMillis() {
        return lastBatchMillis;
    }

    public long getMaxBatchMillis() {
        return maxBatchMillis;
    }

    public double getCoalescePercent() {
        long total = enqueued.get();

        if (total <= 0L) {
            return 0D;
        }

        return (coalesced.get() * 100D) / total;
    }

    private static final class Settings {

        private final boolean enabled;
        private final int flushIntervalTicks;
        private final int maxBatchSize;
        private final boolean flushOnCompletion;
        private final boolean flushOnQuit;
        private final boolean retryFailed;
        private final boolean drainBacklogImmediately;
        private final int failureLogIntervalTicks;
        private final long shutdownWaitMillis;

        private Settings(
                boolean enabled,
                int flushIntervalTicks,
                int maxBatchSize,
                boolean flushOnCompletion,
                boolean flushOnQuit,
                boolean retryFailed,
                boolean drainBacklogImmediately,
                int failureLogIntervalTicks,
                long shutdownWaitMillis) {

            this.enabled = enabled;
            this.flushIntervalTicks = flushIntervalTicks;
            this.maxBatchSize = maxBatchSize;
            this.flushOnCompletion = flushOnCompletion;
            this.flushOnQuit = flushOnQuit;
            this.retryFailed = retryFailed;
            this.drainBacklogImmediately = drainBacklogImmediately;
            this.failureLogIntervalTicks = failureLogIntervalTicks;
            this.shutdownWaitMillis = shutdownWaitMillis;
        }

        private static Settings load(FileConfiguration config) {
            if (config == null) {
                return defaults();
            }

            String base =
                "performance.persistence.quest_buffer.";

            return new Settings(
                config.getBoolean(
                    base + "enabled",
                    true
                ),
                clamp(
                    config.getInt(
                        base + "flush_interval_ticks",
                        40
                    ),
                    20,
                    1200
                ),
                clamp(
                    config.getInt(
                        base + "max_batch_size",
                        500
                    ),
                    1,
                    5000
                ),
                config.getBoolean(
                    base + "flush_on_completion",
                    true
                ),
                config.getBoolean(
                    base + "flush_on_quit",
                    true
                ),
                config.getBoolean(
                    base + "retry_failed",
                    true
                ),
                config.getBoolean(
                    base + "drain_backlog_immediately",
                    true
                ),
                clamp(
                    config.getInt(
                        base + "failure_log_interval_ticks",
                        200
                    ),
                    20,
                    12000
                ),
                clampLong(
                    config.getLong(
                        base + "shutdown_wait_ms",
                        5000L
                    ),
                    1000L,
                    30000L
                )
            );
        }

        private static Settings defaults() {
            return new Settings(
                true,
                40,
                500,
                true,
                true,
                true,
                true,
                200,
                5000L
            );
        }

        private static int clamp(
                int value,
                int minimum,
                int maximum) {

            return Math.max(
                minimum,
                Math.min(
                    maximum,
                    value
                )
            );
        }

        private static long clampLong(
                long value,
                long minimum,
                long maximum) {

            return Math.max(
                minimum,
                Math.min(
                    maximum,
                    value
                )
            );
        }
    }
}
