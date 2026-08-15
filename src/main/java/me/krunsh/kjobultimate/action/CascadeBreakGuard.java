package me.krunsh.kjobultimate.action;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Empêche un plugin tiers de recompter un bloc supérieur déjà crédité comme
 * membre d'une cascade canne à sucre / cactus.
 *
 * Ce n'est PAS le cooldown anti-farm longue durée.
 * Les entrées vivent seulement quelques ticks.
 */
final class CascadeBreakGuard {

    private final Map<Key, Long> suppressed =
        new HashMap<Key, Long>();

    private int operations;

    public boolean consume(
            Player player,
            Block block) {

        if (player == null
                || block == null
                || block.getWorld() == null) {

            return false;
        }

        long now =
            System.currentTimeMillis();

        cleanupMaybe(now);

        Long expiresAt =
            suppressed.remove(
                Key.of(
                    player,
                    block
                )
            );

        return expiresAt != null
            && expiresAt.longValue() >= now;
    }

    public void suppress(
            Player player,
            Block block,
            int ttlTicks) {

        if (player == null
                || block == null
                || block.getWorld() == null
                || ttlTicks <= 0) {

            return;
        }

        long now =
            System.currentTimeMillis();

        cleanupMaybe(now);

        long durationMs =
            Math.max(
                1L,
                (long) ttlTicks * 50L
            );

        suppressed.put(
            Key.of(
                player,
                block
            ),
            Long.valueOf(
                now + durationMs
            )
        );
    }

    private void cleanupMaybe(
            long now) {

        operations++;

        if ((operations & 63) != 0
                && suppressed.size() < 256) {

            return;
        }

        Iterator<Map.Entry<Key, Long>> iterator =
            suppressed.entrySet()
                .iterator();

        while (iterator.hasNext()) {

            Map.Entry<Key, Long> entry =
                iterator.next();

            if (entry.getValue() == null
                    || entry.getValue()
                        .longValue() < now) {

                iterator.remove();
            }
        }
    }

    private static final class Key {

        private final UUID playerId;
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;
        private final int hash;

        private Key(
                UUID playerId,
                UUID worldId,
                int x,
                int y,
                int z) {

            this.playerId = playerId;
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;

            int computed =
                17;

            computed =
                31 * computed
                    + playerId.hashCode();

            computed =
                31 * computed
                    + worldId.hashCode();

            computed =
                31 * computed
                    + x;

            computed =
                31 * computed
                    + y;

            computed =
                31 * computed
                    + z;

            this.hash =
                computed;
        }

        private static Key of(
                Player player,
                Block block) {

            World world =
                block.getWorld();

            return new Key(
                player.getUniqueId(),
                world.getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
            );
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(
                Object other) {

            if (this == other) {
                return true;
            }

            if (!(other instanceof Key)) {
                return false;
            }

            Key key =
                (Key) other;

            return x == key.x
                && y == key.y
                && z == key.z
                && playerId.equals(
                    key.playerId
                )
                && worldId.equals(
                    key.worldId
                );
        }
    }
}
