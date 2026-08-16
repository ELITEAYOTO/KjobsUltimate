package me.krunsh.kjobultimate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.krunsh.kjobultimate.persistence.QuestWriteSnapshot;

public class QuestProgressBatchStoreTest {

    private Connection connection;

    @Before
    public void setUp() throws Exception {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "CREATE TABLE quest_progress ("
                    + "uuid TEXT NOT NULL, quest_id TEXT NOT NULL, "
                    + "progress INTEGER NOT NULL, completed INTEGER NOT NULL, "
                    + "claimed INTEGER NOT NULL, completed_at INTEGER NOT NULL, "
                    + "PRIMARY KEY (uuid, quest_id))"
            );
        }
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    public void sqliteBatchPersistsManySnapshots() throws Exception {
        List<QuestWriteSnapshot> snapshots =
            new ArrayList<QuestWriteSnapshot>();

        for (int i = 0; i < 100; i++) {
            snapshots.add(
                new QuestWriteSnapshot(
                    UUID.nameUUIDFromBytes(("player-" + i).getBytes("UTF-8")),
                    "quest-" + i,
                    i + 1,
                    false,
                    false,
                    0L
                )
            );
        }

        int saved = QuestProgressBatchStore.save(
            connection,
            false,
            snapshots
        );

        assertEquals(100, saved);

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT COUNT(*) FROM quest_progress"
             )) {

            assertTrue(result.next());
            assertEquals(100, result.getInt(1));
        }
    }

    @Test
    public void olderBatchCannotRegressNewerState() throws Exception {
        UUID player = UUID.randomUUID();

        QuestProgressBatchStore.save(
            connection,
            false,
            Arrays.asList(
                new QuestWriteSnapshot(
                    player,
                    "quest",
                    100,
                    true,
                    true,
                    300L
                )
            )
        );

        QuestProgressBatchStore.save(
            connection,
            false,
            Arrays.asList(
                new QuestWriteSnapshot(
                    player,
                    "quest",
                    25,
                    false,
                    false,
                    0L
                )
            )
        );

        Stored stored = read(player, "quest");
        assertEquals(100, stored.progress);
        assertTrue(stored.completed);
        assertTrue(stored.claimed);
        assertEquals(300L, stored.completedAt);
    }

    @Test
    public void duplicateKeyInsideSameBatchMergesMonotonically() throws Exception {
        UUID player = UUID.randomUUID();

        QuestProgressBatchStore.save(
            connection,
            false,
            Arrays.asList(
                new QuestWriteSnapshot(
                    player,
                    "quest",
                    10,
                    true,
                    false,
                    400L
                ),
                new QuestWriteSnapshot(
                    player,
                    "quest",
                    30,
                    true,
                    true,
                    200L
                )
            )
        );

        Stored stored = read(player, "quest");
        assertEquals(30, stored.progress);
        assertTrue(stored.completed);
        assertTrue(stored.claimed);
        assertEquals(200L, stored.completedAt);
    }

    private Stored read(UUID player, String questId) throws Exception {
        String uuid = player.toString().replace("-", "");

        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT progress, completed, claimed, completed_at "
                    + "FROM quest_progress WHERE uuid=? AND quest_id=?")) {

            statement.setString(1, uuid);
            statement.setString(2, questId);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());

                return new Stored(
                    result.getInt(1),
                    result.getInt(2) != 0,
                    result.getInt(3) != 0,
                    result.getLong(4)
                );
            }
        }
    }

    private static final class Stored {
        private final int progress;
        private final boolean completed;
        private final boolean claimed;
        private final long completedAt;

        private Stored(
                int progress,
                boolean completed,
                boolean claimed,
                long completedAt) {

            this.progress = progress;
            this.completed = completed;
            this.claimed = claimed;
            this.completedAt = completedAt;
        }
    }
}
