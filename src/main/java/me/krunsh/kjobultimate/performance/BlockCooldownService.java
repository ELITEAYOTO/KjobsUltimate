package me.krunsh.kjobultimate.performance;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;

/**
 * Cooldowns de blocs V3.14.
 *
 * Ancien modèle : Map<String, Long> dans chaque PlayerData avec une grosse
 * String "world:x:y:z" allouée à chaque BlockBreakEvent.
 *
 * Nouveau modèle :
 * - service RAM séparé des données persistantes ;
 * - coordonnées packées dans un long ;
 * - map primitive long -> expiry par monde ;
 * - suppression opportuniste des expirations ;
 * - nombre d'entrées borné par joueur.
 */
public final class BlockCooldownService {

    private final KjobUltimate plugin;

    private final Map<UUID, PlayerTracker> trackers =
        new HashMap<UUID, PlayerTracker>();

    private volatile HotPathSettings settings;

    private long checks;
    private long hits;
    private long expiredPruned;
    private long capacityEvictions;

    public BlockCooldownService(
            KjobUltimate plugin) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                "plugin ne peut pas être null."
            );
        }

        this.plugin = plugin;
        reloadSettings();
    }

    public void reloadSettings() {

        settings =
            HotPathSettings.load(
                plugin
            );

        int maximum =
            settings
                .getBlockCooldownMaxEntriesPerPlayer();

        long now =
            System.currentTimeMillis();

        for (PlayerTracker tracker
                : trackers.values()) {

            if (tracker != null) {
                tracker.cleanupExpired(now);
                tracker.trimToLimit(maximum);
            }
        }
    }

    public boolean isOnCooldown(
            Player player,
            Block block) {

        if (player == null
                || block == null
                || block.getWorld() == null) {

            return false;
        }

        checks++;

        PlayerTracker tracker =
            trackers.get(
                player.getUniqueId()
            );

        if (tracker == null) {
            return false;
        }

        long now =
            System.currentTimeMillis();

        expiredPruned +=
            tracker.cleanupMaybe(
                now,
                settings
                    .getBlockCooldownCleanupEveryChecks()
            );

        boolean result =
            tracker.isOnCooldown(
                block.getWorld().getUID(),
                packCoordinates(
                    block.getX(),
                    block.getY(),
                    block.getZ()
                ),
                now
            );

        if (result) {
            hits++;
        }

        if (tracker.isEmpty()) {
            trackers.remove(
                player.getUniqueId()
            );
        }

        return result;
    }

    public void mark(
            Player player,
            Block block,
            long durationMs) {

        if (player == null
                || block == null
                || block.getWorld() == null
                || durationMs <= 0L) {

            return;
        }

        UUID playerId =
            player.getUniqueId();

        PlayerTracker tracker =
            trackers.get(
                playerId
            );

        if (tracker == null) {
            tracker =
                new PlayerTracker();
            trackers.put(
                playerId,
                tracker
            );
        }

        long now =
            System.currentTimeMillis();

        int evicted =
            tracker.put(
                block.getWorld().getUID(),
                packCoordinates(
                    block.getX(),
                    block.getY(),
                    block.getZ()
                ),
                safeAdd(
                    now,
                    durationMs
                ),
                now,
                settings
                    .getBlockCooldownMaxEntriesPerPlayer()
            );

        capacityEvictions +=
            evicted;
    }

    public void removePlayer(
            UUID playerId) {

        if (playerId != null) {
            trackers.remove(playerId);
        }
    }

    public void clear() {
        trackers.clear();
    }

    public int getTrackedPlayers() {
        return trackers.size();
    }

    public int getTotalEntries() {
        int total = 0;

        for (PlayerTracker tracker
                : trackers.values()) {

            if (tracker != null) {
                total += tracker.size();
            }
        }

        return total;
    }

    public long getChecks() {
        return checks;
    }

    public long getHits() {
        return hits;
    }

    public long getExpiredPruned() {
        return expiredPruned;
    }

    public long getCapacityEvictions() {
        return capacityEvictions;
    }

    /**
     * Packing identique au format classique Minecraft BlockPos :
     * 26 bits X, 26 bits Z, 12 bits Y.
     */
    static long packCoordinates(
            int x,
            int y,
            int z) {

        return ((long) x & 0x3FFFFFFL) << 38
            | ((long) z & 0x3FFFFFFL) << 12
            | ((long) y & 0xFFFL);
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

    private static final class PlayerTracker {

        private final Map<UUID, LongExpiryMap> worlds =
            new HashMap<UUID, LongExpiryMap>();

        private int operations;
        private int size;

        private boolean isOnCooldown(
                UUID worldId,
                long position,
                long now) {

            LongExpiryMap map =
                worlds.get(
                    worldId
                );

            if (map == null) {
                return false;
            }

            long expiry =
                map.get(
                    position
                );

            if (expiry <= 0L) {
                return false;
            }

            if (now >= expiry) {
                if (map.remove(position)) {
                    size--;
                }
                if (map.isEmpty()) {
                    worlds.remove(worldId);
                }
                return false;
            }

            return true;
        }

        private int put(
                UUID worldId,
                long position,
                long expiresAt,
                long now,
                int maximum) {

            int evictions = 0;

            LongExpiryMap map =
                worlds.get(
                    worldId
                );

            if (map == null) {
                map =
                    new LongExpiryMap();
                worlds.put(
                    worldId,
                    map
                );
            }

            boolean alreadyPresent =
                map.containsKey(
                    position
                );

            if (!alreadyPresent
                    && size >= maximum) {

                cleanupExpired(now);

                while (size >= maximum) {
                    if (!removeEarliest()) {
                        break;
                    }
                    evictions++;
                }
            }

            boolean inserted =
                map.put(
                    position,
                    expiresAt
                );

            if (inserted) {
                size++;
            }

            return evictions;
        }

        private int cleanupMaybe(
                long now,
                int everyChecks) {

            operations++;

            if (everyChecks <= 1
                    || operations >= everyChecks) {

                operations = 0;
                return cleanupExpired(now);
            }

            return 0;
        }

        private int cleanupExpired(
                long now) {

            int removed = 0;

            Iterator<Map.Entry<UUID, LongExpiryMap>> iterator =
                worlds.entrySet()
                    .iterator();

            while (iterator.hasNext()) {

                LongExpiryMap map =
                    iterator.next()
                        .getValue();

                if (map == null) {
                    iterator.remove();
                    continue;
                }

                int count =
                    map.removeExpired(
                        now
                    );

                removed += count;
                size -= count;

                if (map.isEmpty()) {
                    iterator.remove();
                }
            }

            if (size < 0) {
                size = 0;
            }

            return removed;
        }

        private void trimToLimit(
                int maximum) {

            while (size > maximum) {
                if (!removeEarliest()) {
                    break;
                }
            }
        }

        private boolean removeEarliest() {

            UUID bestWorld = null;
            long bestKey = 0L;
            long bestExpiry = Long.MAX_VALUE;

            for (Map.Entry<UUID, LongExpiryMap> entry
                    : worlds.entrySet()) {

                LongExpiryMap map =
                    entry.getValue();

                if (map == null
                        || map.isEmpty()) {

                    continue;
                }

                LongExpiryMap.Earliest earliest =
                    map.findEarliest();

                if (earliest != null
                        && earliest.expiresAt < bestExpiry) {

                    bestWorld =
                        entry.getKey();
                    bestKey =
                        earliest.key;
                    bestExpiry =
                        earliest.expiresAt;
                }
            }

            if (bestWorld == null) {
                return false;
            }

            LongExpiryMap map =
                worlds.get(
                    bestWorld
                );

            if (map == null
                    || !map.remove(bestKey)) {

                return false;
            }

            size--;

            if (map.isEmpty()) {
                worlds.remove(bestWorld);
            }

            return true;
        }

        private int size() {
            return size;
        }

        private boolean isEmpty() {
            return size <= 0;
        }
    }

    /**
     * Petite hash-map primitive open-addressing long -> long.
     * Zéro boxing par position de bloc.
     */
    static final class LongExpiryMap {

        private static final byte EMPTY = 0;
        private static final byte USED = 1;
        private static final byte DELETED = 2;

        private long[] keys =
            new long[16];
        private long[] values =
            new long[16];
        private byte[] states =
            new byte[16];

        private int size;
        private int occupied;

        boolean containsKey(
                long key) {

            return findIndex(key) >= 0;
        }

        long get(
                long key) {

            int index =
                findIndex(key);

            return index < 0
                ? 0L
                : values[index];
        }

        /** @return true si une nouvelle clé a été ajoutée. */
        boolean put(
                long key,
                long value) {

            ensureCapacity();

            int mask =
                keys.length - 1;

            int index =
                mix(key) & mask;

            int firstDeleted =
                -1;

            while (true) {

                byte state =
                    states[index];

                if (state == EMPTY) {

                    int target =
                        firstDeleted >= 0
                            ? firstDeleted
                            : index;

                    keys[target] = key;
                    values[target] = value;

                    if (states[target] == EMPTY) {
                        occupied++;
                    }

                    states[target] = USED;
                    size++;
                    return true;
                }

                if (state == DELETED) {
                    if (firstDeleted < 0) {
                        firstDeleted = index;
                    }

                } else if (keys[index] == key) {
                    values[index] = value;
                    return false;
                }

                index =
                    (index + 1) & mask;
            }
        }

        boolean remove(
                long key) {

            int index =
                findIndex(key);

            if (index < 0) {
                return false;
            }

            states[index] = DELETED;
            values[index] = 0L;
            size--;
            return true;
        }

        int removeExpired(
                long now) {

            int removed = 0;

            for (int i = 0;
                    i < states.length;
                    i++) {

                if (states[i] == USED
                        && now >= values[i]) {

                    states[i] = DELETED;
                    values[i] = 0L;
                    size--;
                    removed++;
                }
            }

            if (size == 0) {
                clearArrays();
            } else if (occupied > size * 2
                    && keys.length > 16) {
                rehash(keys.length);
            }

            return removed;
        }

        Earliest findEarliest() {

            long bestExpiry =
                Long.MAX_VALUE;
            long bestKey =
                0L;
            boolean found =
                false;

            for (int i = 0;
                    i < states.length;
                    i++) {

                if (states[i] != USED) {
                    continue;
                }

                if (!found
                        || values[i] < bestExpiry) {

                    found = true;
                    bestExpiry = values[i];
                    bestKey = keys[i];
                }
            }

            return found
                ? new Earliest(
                    bestKey,
                    bestExpiry
                )
                : null;
        }

        boolean isEmpty() {
            return size == 0;
        }

        private int findIndex(
                long key) {

            int mask =
                keys.length - 1;

            int index =
                mix(key) & mask;

            while (true) {

                byte state =
                    states[index];

                if (state == EMPTY) {
                    return -1;
                }

                if (state == USED
                        && keys[index] == key) {

                    return index;
                }

                index =
                    (index + 1) & mask;
            }
        }

        private void ensureCapacity() {

            if ((occupied + 1) * 10
                    < keys.length * 7) {

                return;
            }

            if (size * 10
                    < keys.length * 4) {

                rehash(keys.length);
            } else {
                rehash(keys.length << 1);
            }
        }

        private void rehash(
                int requestedCapacity) {

            int capacity = 16;

            while (capacity < requestedCapacity) {
                capacity <<= 1;
            }

            long[] oldKeys = keys;
            long[] oldValues = values;
            byte[] oldStates = states;

            keys = new long[capacity];
            values = new long[capacity];
            states = new byte[capacity];
            size = 0;
            occupied = 0;

            for (int i = 0;
                    i < oldStates.length;
                    i++) {

                if (oldStates[i] == USED) {
                    put(
                        oldKeys[i],
                        oldValues[i]
                    );
                }
            }
        }

        private void clearArrays() {
            keys = new long[16];
            values = new long[16];
            states = new byte[16];
            size = 0;
            occupied = 0;
        }

        private static int mix(
                long value) {

            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdl;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53l;
            value ^= value >>> 33;

            return (int) (
                value
                    ^ (value >>> 32)
            );
        }

        static final class Earliest {
            private final long key;
            private final long expiresAt;
            private Earliest(long key, long expiresAt) {
                this.key = key;
                this.expiresAt = expiresAt;
            }
        }
    }
}
