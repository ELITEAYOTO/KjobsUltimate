package me.krunsh.kjobultimate.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * File RAM dédupliquée des progressions de quêtes.
 *
 * Structure :
 *   UUID -> questId -> PendingWrite
 *
 * Un joueur qui casse 100 blocs avant le prochain flush n'occupe toujours
 * qu'une seule entrée pour la même quête.
 */
final class QuestWriteQueue {

    private final ConcurrentMap<UUID, ConcurrentMap<String, PendingWrite>> pending =
        new ConcurrentHashMap<UUID, ConcurrentMap<String, PendingWrite>>();

    private final ConcurrentMap<UUID, Set<String>> blocked =
        new ConcurrentHashMap<UUID, Set<String>>();

    private final AtomicInteger pendingCount = new AtomicInteger();

    boolean offer(
            UUID playerId,
            String questId,
            int progress,
            boolean completed,
            boolean claimed,
            long completedAt) {

        if (playerId == null || questId == null || questId.trim().isEmpty()) {
            return false;
        }

        ConcurrentMap<String, PendingWrite> playerWrites = pending.get(playerId);
        if (playerWrites == null) {
            ConcurrentMap<String, PendingWrite> created =
                new ConcurrentHashMap<String, PendingWrite>();

            ConcurrentMap<String, PendingWrite> raced =
                pending.putIfAbsent(playerId, created);

            playerWrites = raced == null ? created : raced;
        }

        while (true) {
            PendingWrite existing = playerWrites.get(questId);

            if (existing != null) {
                if (existing.tryMerge(
                        progress,
                        completed,
                        claimed,
                        completedAt)) {

                    return true;
                }

                if (playerWrites.remove(questId, existing)) {
                    pendingCount.decrementAndGet();
                }
                continue;
            }

            PendingWrite created = new PendingWrite(
                playerId,
                questId,
                progress,
                completed,
                claimed,
                completedAt
            );

            PendingWrite raced =
                playerWrites.putIfAbsent(questId, created);

            if (raced == null) {
                pendingCount.incrementAndGet();
                return false;
            }

            if (raced.tryMerge(
                    progress,
                    completed,
                    claimed,
                    completedAt)) {

                return true;
            }

            if (playerWrites.remove(questId, raced)) {
                pendingCount.decrementAndGet();
            }
        }
    }

    List<QuestWriteSnapshot> drain(int maximum) {
        int limit = Math.max(1, maximum);

        if (pendingCount.get() <= 0) {
            return Collections.emptyList();
        }

        List<QuestWriteSnapshot> result =
            new ArrayList<QuestWriteSnapshot>(
                Math.min(limit, pendingCount.get())
            );

        for (ConcurrentMap.Entry<UUID, ConcurrentMap<String, PendingWrite>> playerEntry
                : pending.entrySet()) {

            UUID playerId = playerEntry.getKey();
            ConcurrentMap<String, PendingWrite> playerWrites = playerEntry.getValue();

            if (playerWrites == null || playerWrites.isEmpty()) {
                pending.remove(playerId, playerWrites);
                continue;
            }

            for (ConcurrentMap.Entry<String, PendingWrite> questEntry
                    : playerWrites.entrySet()) {

                if (result.size() >= limit) {
                    return result;
                }

                String questId = questEntry.getKey();

                if (isBlocked(playerId, questId)) {
                    continue;
                }

                PendingWrite write = questEntry.getValue();
                if (write == null) {
                    continue;
                }

                QuestWriteSnapshot snapshot = write.closeAndSnapshot();
                if (snapshot == null) {
                    continue;
                }

                if (playerWrites.remove(questId, write)) {
                    pendingCount.decrementAndGet();
                    result.add(snapshot);
                } else {
                    write.reopenForRetry();
                }
            }

            if (playerWrites.isEmpty()) {
                pending.remove(playerId, playerWrites);
            }
        }

        return result;
    }

    void requeue(QuestWriteSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        if (isBlocked(snapshot.getPlayerId(), snapshot.getQuestId())) {
            return;
        }

        offer(
            snapshot.getPlayerId(),
            snapshot.getQuestId(),
            snapshot.getProgress(),
            snapshot.isCompleted(),
            snapshot.isClaimed(),
            snapshot.getCompletedAt()
        );
    }

    void blockAndDropPending(UUID playerId, String questId) {
        if (playerId == null || questId == null) {
            return;
        }

        blockedSet(playerId).add(questId);

        ConcurrentMap<String, PendingWrite> playerWrites = pending.get(playerId);
        if (playerWrites == null) {
            return;
        }

        PendingWrite removed = playerWrites.remove(questId);
        if (removed != null) {
            removed.closeWithoutSnapshot();
            pendingCount.decrementAndGet();
        }

        if (playerWrites.isEmpty()) {
            pending.remove(playerId, playerWrites);
        }
    }

    void unblock(UUID playerId, String questId) {
        if (playerId == null || questId == null) {
            return;
        }

        Set<String> values = blocked.get(playerId);
        if (values == null) {
            return;
        }

        values.remove(questId);

        if (values.isEmpty()) {
            blocked.remove(playerId, values);
        }
    }

    boolean isBlocked(UUID playerId, String questId) {
        Set<String> values = blocked.get(playerId);
        return values != null && values.contains(questId);
    }

    int size() {
        return Math.max(0, pendingCount.get());
    }

    int countForPlayer(UUID playerId) {
        ConcurrentMap<String, PendingWrite> values = pending.get(playerId);
        return values == null ? 0 : values.size();
    }

    void clear() {
        for (ConcurrentMap<String, PendingWrite> values : pending.values()) {
            if (values == null) {
                continue;
            }

            for (PendingWrite write : values.values()) {
                if (write != null) {
                    write.closeWithoutSnapshot();
                }
            }
        }

        pending.clear();
        blocked.clear();
        pendingCount.set(0);
    }

    private Set<String> blockedSet(UUID playerId) {
        Set<String> values = blocked.get(playerId);

        if (values != null) {
            return values;
        }

        Set<String> created =
            Collections.newSetFromMap(
                new ConcurrentHashMap<String, Boolean>()
            );

        Set<String> raced =
            blocked.putIfAbsent(playerId, created);

        return raced == null ? created : raced;
    }

    private static long mergeCompletedAt(long current, long incoming) {
        long a = Math.max(0L, current);
        long b = Math.max(0L, incoming);

        if (a == 0L) {
            return b;
        }
        if (b == 0L) {
            return a;
        }

        return Math.min(a, b);
    }

    private static final class PendingWrite {

        private final UUID playerId;
        private final String questId;

        private int progress;
        private boolean completed;
        private boolean claimed;
        private long completedAt;
        private boolean closed;

        private PendingWrite(
                UUID playerId,
                String questId,
                int progress,
                boolean completed,
                boolean claimed,
                long completedAt) {

            this.playerId = playerId;
            this.questId = questId;
            this.progress = Math.max(0, progress);
            this.completed = completed;
            this.claimed = claimed;
            this.completedAt = Math.max(0L, completedAt);
        }

        private synchronized boolean tryMerge(
                int incomingProgress,
                boolean incomingCompleted,
                boolean incomingClaimed,
                long incomingCompletedAt) {

            if (closed) {
                return false;
            }

            progress = Math.max(progress, Math.max(0, incomingProgress));
            completed = completed || incomingCompleted;
            claimed = claimed || incomingClaimed;
            completedAt = mergeCompletedAt(completedAt, incomingCompletedAt);

            return true;
        }

        private synchronized QuestWriteSnapshot closeAndSnapshot() {
            if (closed) {
                return null;
            }

            closed = true;

            return new QuestWriteSnapshot(
                playerId,
                questId,
                progress,
                completed,
                claimed,
                completedAt
            );
        }

        private synchronized void reopenForRetry() {
            closed = false;
        }

        private synchronized void closeWithoutSnapshot() {
            closed = true;
        }
    }
}
