package me.krunsh.kjobultimate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.Test;

public class SqliteTuningTest {

    @Test
    public void configuredValuesProduceAllDurabilityPragmas() {
        List<String> pragmas = SqliteTuning.pragmas(
                5000, 1000, 8000, 67108864L);

        assertTrue(pragmas.contains("PRAGMA journal_mode=WAL;"));
        assertTrue(pragmas.contains("PRAGMA synchronous=NORMAL;"));
        assertTrue(pragmas.contains("PRAGMA busy_timeout=5000;"));
        assertTrue(pragmas.contains("PRAGMA wal_autocheckpoint=1000;"));
        assertTrue(pragmas.contains("PRAGMA journal_size_limit=67108864;"));
        assertTrue(pragmas.contains("PRAGMA cache_size=-8000;"));
        assertTrue(pragmas.contains("PRAGMA foreign_keys=ON;"));
    }

    @Test
    public void unsafeValuesAreClamped() {
        List<String> pragmas = SqliteTuning.pragmas(0, 1, 1, 1);

        assertTrue(pragmas.contains("PRAGMA busy_timeout=100;"));
        assertTrue(pragmas.contains("PRAGMA wal_autocheckpoint=100;"));
        assertTrue(pragmas.contains("PRAGMA cache_size=-100;"));
        assertTrue(pragmas.contains("PRAGMA journal_size_limit=1048576;"));
    }

    @Test
    public void sqliteDriverAcceptsTheConfiguredPragmas() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            for (String pragma : SqliteTuning.pragmas(
                    4321, 1000, 8000, 67108864L)) {
                statement.execute(pragma);
            }
            try (ResultSet result = statement.executeQuery("PRAGMA busy_timeout;")) {
                assertTrue(result.next());
                assertEquals(4321, result.getInt(1));
            }
        }
    }
}
