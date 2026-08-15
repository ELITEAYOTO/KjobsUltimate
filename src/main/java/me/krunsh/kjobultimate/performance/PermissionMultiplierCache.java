package me.krunsh.kjobultimate.performance;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Cache RAM borné du multiplicateur de permissions XP. */
public final class PermissionMultiplierCache {

    private final Map<UUID, Entry> values =
        new HashMap<UUID, Entry>();

    private long ttlMs;
    private int maxPlayers;

    public void configure(
            long ttlMs,
            int maxPlayers) {

        this.ttlMs =
            Math.max(
                0L,
                ttlMs
            );

        this.maxPlayers =
            Math.max(
                1,
                maxPlayers
            );

        trimToLimit();
    }

    /**
     * Retourne Double.NaN en cas de miss afin d'éviter de boxer un Double à
     * chaque gain XP servi par le cache.
     */
    public double getOrNaN(
            UUID playerId,
            long now) {

        if (playerId == null
                || ttlMs <= 0L) {

            return Double.NaN;
        }

        Entry entry =
            values.get(
                playerId
            );

        if (entry == null) {
            return Double.NaN;
        }

        if (now >= entry.expiresAt) {
            values.remove(
                playerId
            );
            return Double.NaN;
        }

        return entry.multiplier;
    }

    public void put(
            UUID playerId,
            double multiplier,
            long now) {

        if (playerId == null
                || ttlMs <= 0L) {

            return;
        }

        if (!values.containsKey(playerId)
                && values.size() >= maxPlayers) {

            pruneExpired(now);

            if (values.size() >= maxPlayers) {
                evictOne();
            }
        }

        values.put(
            playerId,
            new Entry(
                multiplier,
                safeAdd(
                    now,
                    ttlMs
                )
            )
        );
    }

    public void remove(
            UUID playerId) {

        if (playerId != null) {
            values.remove(playerId);
        }
    }

    public void clear() {
        values.clear();
    }

    public int size() {
        return values.size();
    }

    private void pruneExpired(
            long now) {

        Iterator<Map.Entry<UUID, Entry>> iterator =
            values.entrySet()
                .iterator();

        while (iterator.hasNext()) {
            Entry entry =
                iterator.next()
                    .getValue();

            if (entry == null
                    || now >= entry.expiresAt) {

                iterator.remove();
            }
        }
    }

    private void evictOne() {
        Iterator<UUID> iterator =
            values.keySet()
                .iterator();

        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private void trimToLimit() {
        while (values.size() > maxPlayers) {
            evictOne();
        }
    }

    private static long safeAdd(
            long first,
            long second) {

        if (second > 0L
                && first > Long.MAX_VALUE - second) {

            return Long.MAX_VALUE;
        }

        return first + second;
    }

    private static final class Entry {

        private final double multiplier;
        private final long expiresAt;

        private Entry(
                double multiplier,
                long expiresAt) {

            this.multiplier = multiplier;
            this.expiresAt = expiresAt;
        }
    }
}
