package me.krunsh.kjobultimate.data;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QuestRewardClaimStoreTest {

    private Connection connection;

    @Before
    public void setUp() throws Exception {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE quest_progress (" +
                "uuid TEXT NOT NULL, quest_id TEXT NOT NULL, progress INTEGER NOT NULL, " +
                "completed INTEGER NOT NULL, claimed INTEGER NOT NULL, completed_at INTEGER NOT NULL, " +
                "PRIMARY KEY (uuid, quest_id))");
        }
        QuestRewardClaimStore.createTable(connection, "TEXT", "INTEGER", "TEXT", ";");
    }

    @After
    public void tearDown() throws Exception {
        connection.close();
    }

    @Test
    public void reservesOnlyOnce() throws Exception {
        assertEquals(QuestRewardClaimStore.ReservationResult.RESERVED,
            QuestRewardClaimStore.reserve(connection, "u1", "q1", "Alice", 10L));
        assertEquals(QuestRewardClaimStore.ReservationResult.ALREADY_RESERVED,
            QuestRewardClaimStore.reserve(connection, "u1", "q1", "Alice", 11L));
        assertEquals(QuestRewardClaimStore.PREPARED,
            QuestRewardClaimStore.findStatus(connection, "u1", "q1"));
    }

    @Test
    public void honorsClaimsCreatedBeforeLedgerMigration() throws Exception {
        insertProgress("u2", "q2", true);
        assertEquals(QuestRewardClaimStore.ReservationResult.LEGACY_ALREADY_CLAIMED,
            QuestRewardClaimStore.reserve(connection, "u2", "q2", "Bob", 20L));
        assertNull(QuestRewardClaimStore.findStatus(connection, "u2", "q2"));
    }

    @Test
    public void statusTransitionsAreCompareAndSet() throws Exception {
        QuestRewardClaimStore.reserve(connection, "u3", "q3", "Cara", 30L);
        assertTrue(QuestRewardClaimStore.transition(connection, "u3", "q3",
            QuestRewardClaimStore.PREPARED, QuestRewardClaimStore.DISTRIBUTING, 31L, null));
        assertFalse(QuestRewardClaimStore.transition(connection, "u3", "q3",
            QuestRewardClaimStore.PREPARED, QuestRewardClaimStore.DISTRIBUTING, 32L, null));
        assertTrue(QuestRewardClaimStore.transition(connection, "u3", "q3",
            QuestRewardClaimStore.DISTRIBUTING, QuestRewardClaimStore.DISTRIBUTED, 33L, null));
        assertEquals(QuestRewardClaimStore.DISTRIBUTED,
            QuestRewardClaimStore.findStatus(connection, "u3", "q3"));
    }

    @Test
    public void preparedReservationCanBeCancelledBeforeDelivery() throws Exception {
        QuestRewardClaimStore.reserve(connection, "u4", "q4", "Dora", 40L);
        assertTrue(QuestRewardClaimStore.cancelPrepared(connection, "u4", "q4"));
        assertNull(QuestRewardClaimStore.findStatus(connection, "u4", "q4"));
        assertEquals(QuestRewardClaimStore.ReservationResult.RESERVED,
            QuestRewardClaimStore.reserve(connection, "u4", "q4", "Dora", 41L));
    }

    @Test
    public void distributingReservationCannotBeCancelledAndIsReported() throws Exception {
        QuestRewardClaimStore.reserve(connection, "u5", "q5", "Eve", 50L);
        QuestRewardClaimStore.transition(connection, "u5", "q5",
            QuestRewardClaimStore.PREPARED, QuestRewardClaimStore.DISTRIBUTING, 51L, null);
        assertFalse(QuestRewardClaimStore.cancelPrepared(connection, "u5", "q5"));
        assertEquals(1, QuestRewardClaimStore.countUnresolved(connection));
        QuestRewardClaimStore.transition(connection, "u5", "q5",
            QuestRewardClaimStore.DISTRIBUTING, QuestRewardClaimStore.FAILED, 52L, "command failed");
        assertEquals(1, QuestRewardClaimStore.countUnresolved(connection));
        QuestRewardClaimStore.clear(connection, "u5", "q5");
        assertEquals(0, QuestRewardClaimStore.countUnresolved(connection));
    }

    private void insertProgress(String uuid, String questId, boolean claimed) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO quest_progress " +
                "(uuid, quest_id, progress, completed, claimed, completed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid);
            statement.setString(2, questId);
            statement.setInt(3, 10);
            statement.setInt(4, 1);
            statement.setInt(5, claimed ? 1 : 0);
            statement.setLong(6, 1L);
            statement.executeUpdate();
        }
    }
}
