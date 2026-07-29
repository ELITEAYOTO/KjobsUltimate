package me.krunsh.kjobultimate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class QuestProgressStoreTest {

    private Connection connection;

    @Before
    public void setUp() throws Exception {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE quest_progress ("
                    + "uuid TEXT NOT NULL, quest_id TEXT NOT NULL, "
                    + "progress INTEGER NOT NULL, completed INTEGER NOT NULL, "
                    + "claimed INTEGER NOT NULL, completed_at INTEGER NOT NULL, "
                    + "PRIMARY KEY (uuid, quest_id))");
        }
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    public void lateAsyncSnapshotCannotRegressProgressOrFlags() throws Exception {
        QuestProgressStore.saveMonotonic(
                connection, false, "player", "stone_1",
                10, true, true, 200L);
        QuestProgressStore.saveMonotonic(
                connection, false, "player", "stone_1",
                3, false, false, 0L);

        Stored row = read();
        assertEquals(10, row.progress);
        assertTrue(row.completed);
        assertTrue(row.claimed);
        assertEquals(200L, row.completedAt);
    }

    @Test
    public void earliestNonZeroCompletionTimestampIsPreserved() throws Exception {
        QuestProgressStore.saveMonotonic(
                connection, false, "player", "stone_1",
                10, true, false, 300L);
        QuestProgressStore.saveMonotonic(
                connection, false, "player", "stone_1",
                10, true, false, 200L);

        assertEquals(200L, read().completedAt);
    }

    @Test
    public void explicitAdministrativeResetCanMoveStateBackwards() throws Exception {
        QuestProgressStore.saveMonotonic(
                connection, false, "player", "stone_1",
                10, true, true, 200L);
        QuestProgressStore.replace(
                connection, false, "player", "stone_1",
                0, false, false, 0L);

        Stored row = read();
        assertEquals(0, row.progress);
        assertFalse(row.completed);
        assertFalse(row.claimed);
        assertEquals(0L, row.completedAt);
    }

    @Test
    public void mysqlSqlUsesAtomicMonotonicMerge() {
        String sql = QuestProgressStore.monotonicUpsertSql(true);
        assertTrue(sql.contains("GREATEST(progress"));
        assertTrue(sql.contains("GREATEST(completed"));
        assertTrue(sql.contains("GREATEST(claimed"));
    }

    private Stored read() throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT progress, completed, claimed, completed_at "
                     + "FROM quest_progress WHERE uuid='player' "
                     + "AND quest_id='stone_1'")) {
            assertTrue(result.next());
            return new Stored(result.getInt(1), result.getInt(2) != 0,
                    result.getInt(3) != 0, result.getLong(4));
        }
    }

    private static final class Stored {
        private final int progress;
        private final boolean completed;
        private final boolean claimed;
        private final long completedAt;

        private Stored(int progress, boolean completed,
                boolean claimed, long completedAt) {
            this.progress = progress;
            this.completed = completed;
            this.claimed = claimed;
            this.completedAt = completedAt;
        }
    }
}
