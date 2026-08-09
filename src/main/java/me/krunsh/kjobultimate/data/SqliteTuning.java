package me.krunsh.kjobultimate.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Construit les PRAGMA SQLite avec des bornes sûres et testables. */
public final class SqliteTuning {
    private SqliteTuning() {
    }

    public static List<String> pragmas(int busyTimeoutMs,
                                       int walAutocheckpointPages,
                                       int cacheSizePages,
                                       long journalSizeLimitBytes) {
        int busy = clamp(busyTimeoutMs, 100, 120000);
        int checkpoint = clamp(walAutocheckpointPages, 100, 100000);
        int cache = clamp(cacheSizePages, 100, 1000000);
        long journalLimit = clamp(journalSizeLimitBytes,
                1024L * 1024L, 1024L * 1024L * 1024L);

        List<String> values = new ArrayList<String>();
        values.add("PRAGMA journal_mode=WAL;");
        values.add("PRAGMA synchronous=NORMAL;");
        values.add("PRAGMA busy_timeout=" + busy + ";");
        values.add("PRAGMA wal_autocheckpoint=" + checkpoint + ";");
        values.add("PRAGMA journal_size_limit=" + journalLimit + ";");
        values.add("PRAGMA temp_store=MEMORY;");
        values.add("PRAGMA cache_size=-" + cache + ";");
        values.add("PRAGMA foreign_keys=ON;");
        return Collections.unmodifiableList(values);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
