package me.krunsh.kjobultimate.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Ecritures SQL de progression des quetes.
 *
 * Les sauvegardes ordinaires sont monotones afin qu'une tache asynchrone
 * ancienne ne puisse jamais ecraser une progression, une completion ou un
 * claim plus recent. Seul le reset administratif utilise replace().
 *
 * V3.14.1 :
 * SQLite n'utilise volontairement PAS
 * "ON CONFLICT (...) DO UPDATE".
 *
 * Certains serveurs 1.8.8 chargent deja un driver SQLite ancien avant les
 * plugins. DriverManager peut alors fournir ce driver meme si KjobsUltimate
 * embarque une version recente relocalisee.
 *
 * La strategie SQLite utilise uniquement des syntaxes historiques :
 * 1. INSERT OR IGNORE
 * 2. UPDATE avec MAX/MIN
 *
 * Elle reste monotone et fonctionne sur les anciennes versions SQLite.
 */
public final class QuestProgressStore {

    private QuestProgressStore() {
    }

    public static void saveMonotonic(
            Connection connection,
            boolean mysql,
            String uuid,
            String questId,
            int progress,
            boolean completed,
            boolean claimed,
            long completedAt)
            throws SQLException {

        int safeProgress =
            Math.max(
                0,
                progress
            );

        int safeCompleted =
            completed
                ? 1
                : 0;

        int safeClaimed =
            claimed
                ? 1
                : 0;

        long safeCompletedAt =
            Math.max(
                0L,
                completedAt
            );

        if (mysql) {

            try (PreparedStatement statement =
                    connection.prepareStatement(
                        monotonicUpsertSql(true)
                    )) {

                bindInsert(
                    statement,
                    uuid,
                    questId,
                    safeProgress,
                    safeCompleted,
                    safeClaimed,
                    safeCompletedAt
                );

                statement.executeUpdate();
            }

            return;
        }

        saveSqliteMonotonic(
            connection,
            uuid,
            questId,
            safeProgress,
            safeCompleted,
            safeClaimed,
            safeCompletedAt
        );
    }

    /**
     * Fusion monotone SQLite compatible avec les anciennes versions.
     */
    private static void saveSqliteMonotonic(
            Connection connection,
            String uuid,
            String questId,
            int progress,
            int completed,
            int claimed,
            long completedAt)
            throws SQLException {

        /*
         * Si la ligne n'existe pas encore, on cree directement le snapshot.
         * Si elle existe, INSERT OR IGNORE ne modifie rien.
         */
        try (PreparedStatement insert =
                connection.prepareStatement(
                    sqliteInsertIfAbsentSql()
                )) {

            bindInsert(
                insert,
                uuid,
                questId,
                progress,
                completed,
                claimed,
                completedAt
            );

            insert.executeUpdate();
        }

        /*
         * La fusion est ensuite appliquee dans tous les cas.
         *
         * progress/completed/claimed ne peuvent que monter.
         * completed_at conserve le plus ancien timestamp non nul.
         */
        try (PreparedStatement update =
                connection.prepareStatement(
                    sqliteMonotonicUpdateSql()
                )) {

            update.setInt(
                1,
                progress
            );

            update.setInt(
                2,
                completed
            );

            update.setInt(
                3,
                claimed
            );

            update.setLong(
                4,
                completedAt
            );

            update.setLong(
                5,
                completedAt
            );

            update.setLong(
                6,
                completedAt
            );

            update.setString(
                7,
                uuid
            );

            update.setString(
                8,
                questId
            );

            update.executeUpdate();
        }
    }

    public static void replace(
            Connection connection,
            boolean mysql,
            String uuid,
            String questId,
            int progress,
            boolean completed,
            boolean claimed,
            long completedAt)
            throws SQLException {

        try (PreparedStatement statement =
                connection.prepareStatement(
                    replaceSql(mysql)
                )) {

            bindInsert(
                statement,
                uuid,
                questId,
                Math.max(
                    0,
                    progress
                ),
                completed
                    ? 1
                    : 0,
                claimed
                    ? 1
                    : 0,
                Math.max(
                    0L,
                    completedAt
                )
            );

            statement.executeUpdate();
        }
    }

    private static void bindInsert(
            PreparedStatement statement,
            String uuid,
            String questId,
            int progress,
            int completed,
            int claimed,
            long completedAt)
            throws SQLException {

        statement.setString(
            1,
            uuid
        );

        statement.setString(
            2,
            questId
        );

        statement.setInt(
            3,
            progress
        );

        statement.setInt(
            4,
            completed
        );

        statement.setInt(
            5,
            claimed
        );

        statement.setLong(
            6,
            completedAt
        );
    }

    static String monotonicUpsertSql(
            boolean mysql) {

        if (!mysql) {
            /*
             * Conserve la methode package-private attendue par les anciens tests,
             * mais SQLite utilise desormais les deux statements specialises
             * ci-dessous.
             */
            return sqliteInsertIfAbsentSql();
        }

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

    static String sqliteInsertIfAbsentSql() {

        return "INSERT OR IGNORE INTO quest_progress "
            + "(uuid, quest_id, progress, completed, claimed, completed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
    }

    static String sqliteMonotonicUpdateSql() {

        return "UPDATE quest_progress SET "
            + "progress=MAX(progress, ?), "
            + "completed=MAX(completed, ?), "
            + "claimed=MAX(claimed, ?), "
            + "completed_at=CASE "
            + "WHEN completed_at=0 THEN ? "
            + "WHEN ?=0 THEN completed_at "
            + "ELSE MIN(completed_at, ?) END "
            + "WHERE uuid=? AND quest_id=?";
    }

    static String replaceSql(
            boolean mysql) {

        if (mysql) {

            return "INSERT INTO quest_progress "
                + "(uuid, quest_id, progress, completed, claimed, completed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "progress=VALUES(progress), "
                + "completed=VALUES(completed), "
                + "claimed=VALUES(claimed), "
                + "completed_at=VALUES(completed_at)";
        }

        return "INSERT OR REPLACE INTO quest_progress "
            + "(uuid, quest_id, progress, completed, claimed, completed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
    }
}
