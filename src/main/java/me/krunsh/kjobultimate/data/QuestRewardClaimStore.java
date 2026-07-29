package me.krunsh.kjobultimate.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Registre durable et monotone des distributions de récompenses de quête.
 *
 * La ligne (uuid, quest_id) est volontairement unique et séparée de
 * quest_progress. Une ancienne sauvegarde asynchrone de progression ne peut
 * donc jamais rouvrir une récompense déjà réservée.
 */
public final class QuestRewardClaimStore {

    public static final String PREPARED = "PREPARED";
    public static final String DISTRIBUTING = "DISTRIBUTING";
    public static final String DISTRIBUTED = "DISTRIBUTED";
    public static final String FAILED = "FAILED";

    public enum ReservationResult {
        RESERVED,
        ALREADY_RESERVED,
        LEGACY_ALREADY_CLAIMED
    }

    private QuestRewardClaimStore() {
    }

    public static void createTable(Connection connection, String stringType,
                                   String numberType, String textType,
                                   String tableSuffix) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS quest_reward_claims (" +
            "uuid " + stringType + " NOT NULL," +
            "quest_id " + stringType + " NOT NULL," +
            "player_name " + stringType + "," +
            "status " + stringType + " NOT NULL," +
            "reserved_at " + numberType + " NOT NULL," +
            "updated_at " + numberType + " NOT NULL," +
            "last_error " + textType + "," +
            "PRIMARY KEY (uuid, quest_id)" +
            ")" + tableSuffix;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    /**
     * Réserve une récompense exactement une fois.
     *
     * La vérification de l'ancien champ quest_progress.claimed assure la
     * compatibilité avec les données créées avant l'ajout du registre.
     */
    public static ReservationResult reserve(Connection connection, String uuid,
                                            String questId, String playerName,
                                            long now) throws SQLException {
        boolean ownsTransaction = connection.getAutoCommit();
        if (ownsTransaction) connection.setAutoCommit(false);
        try {
            if (legacyClaimed(connection, uuid, questId)) {
                if (ownsTransaction) connection.commit();
                return ReservationResult.LEGACY_ALREADY_CLAIMED;
            }
            if (findStatus(connection, uuid, questId) != null) {
                if (ownsTransaction) connection.commit();
                return ReservationResult.ALREADY_RESERVED;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO quest_reward_claims " +
                    "(uuid, quest_id, player_name, status, reserved_at, updated_at, last_error) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, uuid);
                statement.setString(2, questId);
                statement.setString(3, playerName);
                statement.setString(4, PREPARED);
                statement.setLong(5, now);
                statement.setLong(6, now);
                statement.setString(7, null);
                statement.executeUpdate();
            } catch (SQLException duplicate) {
                if (!isConstraintViolation(duplicate)) throw duplicate;
                if (ownsTransaction) connection.rollback();
                return ReservationResult.ALREADY_RESERVED;
            }

            if (ownsTransaction) connection.commit();
            return ReservationResult.RESERVED;
        } catch (SQLException failure) {
            if (ownsTransaction) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    failure.addSuppressed(ignored);
                }
            }
            throw failure;
        } finally {
            if (ownsTransaction) connection.setAutoCommit(true);
        }
    }

    public static boolean transition(Connection connection, String uuid,
                                     String questId, String expectedStatus,
                                     String newStatus, long now,
                                     String error) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE quest_reward_claims " +
                "SET status=?, updated_at=?, last_error=? " +
                "WHERE uuid=? AND quest_id=? AND status=?")) {
            statement.setString(1, newStatus);
            statement.setLong(2, now);
            statement.setString(3, abbreviate(error, 2000));
            statement.setString(4, uuid);
            statement.setString(5, questId);
            statement.setString(6, expectedStatus);
            return statement.executeUpdate() == 1;
        }
    }

    public static boolean cancelPrepared(Connection connection, String uuid,
                                         String questId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM quest_reward_claims " +
                "WHERE uuid=? AND quest_id=? AND status=?")) {
            statement.setString(1, uuid);
            statement.setString(2, questId);
            statement.setString(3, PREPARED);
            return statement.executeUpdate() == 1;
        }
    }

    public static void clear(Connection connection, String uuid,
                             String questId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM quest_reward_claims WHERE uuid=? AND quest_id=?")) {
            statement.setString(1, uuid);
            statement.setString(2, questId);
            statement.executeUpdate();
        }
    }

    public static String findStatus(Connection connection, String uuid,
                                    String questId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM quest_reward_claims WHERE uuid=? AND quest_id=?")) {
            statement.setString(1, uuid);
            statement.setString(2, questId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("status") : null;
            }
        }
    }

    public static int countUnresolved(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) AS total FROM quest_reward_claims " +
                "WHERE status=? OR status=? OR status=?")) {
            statement.setString(1, PREPARED);
            statement.setString(2, DISTRIBUTING);
            statement.setString(3, FAILED);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("total") : 0;
            }
        }
    }

    private static boolean legacyClaimed(Connection connection, String uuid,
                                         String questId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT claimed FROM quest_progress WHERE uuid=? AND quest_id=?")) {
            statement.setString(1, uuid);
            statement.setString(2, questId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt("claimed") == 1;
            }
        }
    }

    private static boolean isConstraintViolation(SQLException exception) {
        String state = exception.getSQLState();
        return (state != null && state.startsWith("23"))
            || exception.getErrorCode() == 19
            || exception.getErrorCode() == 1062;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
