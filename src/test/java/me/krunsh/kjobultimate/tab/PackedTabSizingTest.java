package me.krunsh.kjobultimate.tab;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PackedTabSizingTest {

    @Test
    public void hiddenRealPlayersAlwaysProduceFullThreeByFifteenGrid() {
        assertEquals(45, limit(1, true));
        assertEquals(45, limit(2, true));
        assertEquals(45, limit(5, true));
        assertEquals(45, limit(50, true));
    }

    @Test
    public void visibleRealPlayersAreAccountedForDynamically() {
        assertEquals(44, limit(1, false));
        assertEquals(43, limit(2, false));
        assertEquals(40, limit(5, false));
    }

    private int limit(int onlinePlayers, boolean hideRealPlayers) {
        return PackedTabSizing.fakeEntryLimit(true, 3, 15, 45, 0,
            onlinePlayers, hideRealPlayers);
    }
}
