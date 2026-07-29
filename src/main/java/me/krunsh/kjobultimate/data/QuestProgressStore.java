package me.krunsh.kjobultimate.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Ecritures SQL de progression.
 *
 * Les sauvegardes ordinaires sont monotones afin qu'une tache asynchrone
 * ancienne ne puisse jamais ecraser une progression, une completion ou un
 * claim plus recent. Seul le reset administratif utilise replace().
 */
public final class QuestProgressStore {

    private QuestProgressStore() {}

    public static void saveMonotonic(Connection connection, boolean mysql,
            String uuid, String questId, int progress, boolean completed,
            boolean claimed, long completedAt) throws SQLException {
        write(connection, monotonicUpsertSql(mysql), uuid, questId, progress,
                completed, claimed, completedAt);
    }

    public static void replace(Connection connection, boolean mysql,
            String uuid, String questId, int progress, boolean completed,
            boolean claimed, long completedAt) throws SQLException {
        write(connection, replaceSql(mysql), uuid, questId, progress,
                completed, claimed, completedAt);
    }

    private static void write(Connection connection, String sql,
            String uuid, String questId, int progress, boolean completed,
            boolean claimed, long completedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, questId);
            statement.setInt(3, Math.max(0, progress));
            statement.setInt(4, completed ? 1 : 0);
            statement.setInt(5, claimed ? 1 : 0);
            statement.setLong(6, Math.max(0L, completedAt));
            statement.executeUpdate();
        }
    }

    static String monotonicUpsertSql(boolean mysql) {
        if (mysql) {
            return "INSERT INTO quest_progress "
                + "(uuid, quest_id, progress, completed, claimed, completed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "progress=GREATEST(progress, VALUES(progress)), "
                + "completed=GREATEST(completed, VALUES(completed)), "
                + "claimed=GREATEST(claimed, VALUES(claimed)), "
                + "completed_at=CASE "
                + "WHEN completed_at=0 THEN VALUES(completed_at) "
                + "WHEN VALUES(completed_at)=0 THEN completed_at "
                + "ELSE LEAST(completed_at, VALUES(completed_at)) END";
        }
        return "INSERT INTO quest_progress "
            + "(uuid, quest_id, progress, completed, claimed, completed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(uuid, quest_id) DO UPDATE SET "
            + "progress=MAX(quest_progress.progress, excluded.progress), "
            + "completed=MAX(quest_progress.completed, excluded.completed), "
            + "claimed=MAX(quest_progress.claimed, excluded.claimed), "
            + "completed_at=CASE "
            + "WHEN quest_progress.completed_at=0 THEN excluded.completed_at "
            + "WHEN excluded.completed_at=0 THEN quest_progress.completed_at "
            + "ELSE MIN(quest_progress.completed_at, excluded.completed_at) END";
    }

    static String replaceSql(boolean mysql) {
        if (mysql) {
            return "INSERT INTO quest_progress "
                + "(uuid, quest_id, progress, completed, claimed, completed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "progress=VALUES(progress), completed=VALUES(completed), "
                + "claimed=VALUES(claimed), completed_at=VALUES(completed_at)";
        }
        return "INSERT OR REPLACE INTO quest_progress "
            + "(uuid, quest_id, progress, completed, claimed, completed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
    }
}
