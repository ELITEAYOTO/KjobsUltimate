package me.krunsh.kjobultimate.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import me.krunsh.kjobultimate.persistence.QuestWriteSnapshot;

/**
 * Ecriture JDBC batch des snapshots de progression de quetes.
 *
 * La transaction appartient a DatabaseManager. Cette classe ne commit/rollback
 * jamais elle-meme : elle reutilise simplement les PreparedStatement pour tout
 * le lot.
 */
final class QuestProgressBatchStore {

    private QuestProgressBatchStore() {
    }

    static int save(
            Connection connection,
            boolean mysql,
            List<QuestWriteSnapshot> snapshots)
            throws SQLException {

        if (connection == null
                || snapshots == null
                || snapshots.isEmpty()) {

            return 0;
        }

        return mysql
            ? saveMySql(connection, snapshots)
            : saveSqlite(connection, snapshots);
    }

    private static int saveMySql(
            Connection connection,
            List<QuestWriteSnapshot> snapshots)
            throws SQLException {

        int count = 0;

        try (PreparedStatement statement =
                connection.prepareStatement(
                    QuestProgressStore.monotonicUpsertSql(true)
                )) {

            for (QuestWriteSnapshot snapshot : snapshots) {
                if (snapshot == null) {
                    continue;
                }

                bindInsert(
                    statement,
                    snapshot
                );

                statement.addBatch();
                count++;
            }

            if (count > 0) {
                statement.executeBatch();
            }
        }

        return count;
    }

    /**
     * SQLite V3.14.1 :
     * - INSERT OR IGNORE cree les lignes absentes ;
     * - UPDATE MAX/MIN fusionne les snapshots monotones.
     *
     * Les deux PreparedStatement sont prepares une seule fois par batch.
     */
    private static int saveSqlite(
            Connection connection,
            List<QuestWriteSnapshot> snapshots)
            throws SQLException {

        int count = 0;

        try (PreparedStatement insert =
                    connection.prepareStatement(
                        QuestProgressStore.sqliteInsertIfAbsentSql()
                    );

             PreparedStatement update =
                    connection.prepareStatement(
                        QuestProgressStore.sqliteMonotonicUpdateSql()
                    )) {

            for (QuestWriteSnapshot snapshot : snapshots) {
                if (snapshot == null) {
                    continue;
                }

                bindInsert(
                    insert,
                    snapshot
                );
                insert.addBatch();

                bindSqliteUpdate(
                    update,
                    snapshot
                );
                update.addBatch();

                count++;
            }

            if (count > 0) {
                insert.executeBatch();
                update.executeBatch();
            }
        }

        return count;
    }

    private static void bindInsert(
            PreparedStatement statement,
            QuestWriteSnapshot snapshot)
            throws SQLException {

        statement.setString(
            1,
            uuidStorage(
                snapshot.getPlayerId()
            )
        );

        statement.setString(
            2,
            snapshot.getQuestId()
        );

        statement.setInt(
            3,
            Math.max(
                0,
                snapshot.getProgress()
            )
        );

        statement.setInt(
            4,
            snapshot.isCompleted()
                ? 1
                : 0
        );

        statement.setInt(
            5,
            snapshot.isClaimed()
                ? 1
                : 0
        );

        statement.setLong(
            6,
            Math.max(
                0L,
                snapshot.getCompletedAt()
            )
        );
    }

    private static void bindSqliteUpdate(
            PreparedStatement statement,
            QuestWriteSnapshot snapshot)
            throws SQLException {

        int progress =
            Math.max(
                0,
                snapshot.getProgress()
            );

        int completed =
            snapshot.isCompleted()
                ? 1
                : 0;

        int claimed =
            snapshot.isClaimed()
                ? 1
                : 0;

        long completedAt =
            Math.max(
                0L,
                snapshot.getCompletedAt()
            );

        statement.setInt(1, progress);
        statement.setInt(2, completed);
        statement.setInt(3, claimed);
        statement.setLong(4, completedAt);
        statement.setLong(5, completedAt);
        statement.setLong(6, completedAt);
        statement.setString(
            7,
            uuidStorage(
                snapshot.getPlayerId()
            )
        );
        statement.setString(
            8,
            snapshot.getQuestId()
        );
    }

    private static String uuidStorage(
            java.util.UUID uuid) {

        return uuid.toString()
            .replace(
                "-",
                ""
            );
    }
}
