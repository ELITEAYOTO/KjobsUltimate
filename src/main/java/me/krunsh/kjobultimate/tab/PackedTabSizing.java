package me.krunsh.kjobultimate.tab;

/** Calcul pur du nombre d'entrées fake nécessaires à une grille stable. */
public final class PackedTabSizing {

    private PackedTabSizing() {
    }

    public static int fakeEntryLimit(boolean forceClientRows, int columns, int rows,
                                     int configuredMaximum, int configuredRealReserve,
                                     int onlinePlayers, boolean hideRealPlayers) {
        int safeMaximum = Math.max(1, configuredMaximum);
        if (!forceClientRows) return safeMaximum;

        int totalCells = Math.max(1, columns) * Math.max(1, rows);
        int visibleRealPlayers = hideRealPlayers ? 0
            : Math.max(Math.max(0, configuredRealReserve), Math.max(0, onlinePlayers));
        return Math.max(1, Math.min(safeMaximum, totalCells - visibleRealPlayers));
    }
}
