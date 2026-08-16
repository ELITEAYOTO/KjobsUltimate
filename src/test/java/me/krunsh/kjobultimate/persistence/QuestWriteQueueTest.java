package me.krunsh.kjobultimate.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.Test;

public class QuestWriteQueueTest {

    @Test
    public void repeatedProgressIsCoalescedIntoOnePendingEntry() {
        QuestWriteQueue queue = new QuestWriteQueue();
        UUID player = UUID.randomUUID();

        assertFalse(queue.offer(player, "stone_1", 1, false, false, 0L));

        for (int progress = 2; progress <= 100; progress++) {
            assertTrue(queue.offer(
                player,
                "stone_1",
                progress,
                false,
                false,
                0L
            ));
        }

        assertEquals(1, queue.size());

        List<QuestWriteSnapshot> batch = queue.drain(500);

        assertEquals(1, batch.size());
        assertEquals(100, batch.get(0).getProgress());
        assertEquals(0, queue.size());
    }

    @Test
    public void monotonicFlagsAndEarliestCompletionTimestampAreMerged() {
        QuestWriteQueue queue = new QuestWriteQueue();
        UUID player = UUID.randomUUID();

        queue.offer(player, "quest", 10, true, false, 300L);
        queue.offer(player, "quest", 4, false, true, 0L);
        queue.offer(player, "quest", 10, true, true, 200L);

        QuestWriteSnapshot snapshot = queue.drain(1).get(0);

        assertEquals(10, snapshot.getProgress());
        assertTrue(snapshot.isCompleted());
        assertTrue(snapshot.isClaimed());
        assertEquals(200L, snapshot.getCompletedAt());
    }

    @Test
    public void exclusiveBarrierDropsOldStateAndHoldsNewStateUntilUnblock() {
        QuestWriteQueue queue = new QuestWriteQueue();
        UUID player = UUID.randomUUID();

        queue.offer(player, "quest", 50, false, false, 0L);
        queue.blockAndDropPending(player, "quest");

        assertEquals(0, queue.size());

        // Représente une nouvelle progression RAM survenue après le reset.
        queue.offer(player, "quest", 2, false, false, 0L);

        assertTrue(queue.drain(10).isEmpty());
        assertEquals(1, queue.size());

        queue.unblock(player, "quest");

        List<QuestWriteSnapshot> batch = queue.drain(10);
        assertEquals(1, batch.size());
        assertEquals(2, batch.get(0).getProgress());
    }

    @Test
    public void batchLimitIsRespected() {
        QuestWriteQueue queue = new QuestWriteQueue();

        for (int i = 0; i < 50; i++) {
            queue.offer(
                UUID.randomUUID(),
                "quest",
                i,
                false,
                false,
                0L
            );
        }

        assertEquals(50, queue.size());
        assertEquals(20, queue.drain(20).size());
        assertEquals(30, queue.size());
    }
}
