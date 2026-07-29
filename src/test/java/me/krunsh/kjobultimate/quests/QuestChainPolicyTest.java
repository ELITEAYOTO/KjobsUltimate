package me.krunsh.kjobultimate.quests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import me.krunsh.kjobultimate.data.QuestData;
import org.junit.Test;

public class QuestChainPolicyTest {

    @Test
    public void oneActionCannotSpillIntoTheNextStage() {
        QuestDefinition first = quest("stone_1", "stone", 1, "mineur", 0);
        QuestDefinition second = quest("stone_2", "stone", 2, "mineur", 0);
        QuestChainDefinition chain = new QuestChainDefinition(
                "stone", "Pierre", "mineur", Arrays.asList(first, second));
        Map<String, QuestChainDefinition> chains =
                Collections.singletonMap("stone", chain);
        Map<String, QuestData> progress = new LinkedHashMap<String, QuestData>();

        Set<String> snapshot = QuestChainPolicy.activeQuestIds(
                Arrays.asList(first, second), chains, progress);
        assertEquals(Collections.singleton("stone_1"), snapshot);

        QuestData completedFirst = new QuestData("stone_1");
        completedFirst.addProgress(10, 10);
        progress.put("stone_1", completedFirst);

        // La boucle de l'action en cours conserve son instantane.
        assertTrue(snapshot.contains("stone_1"));
        assertFalse(snapshot.contains("stone_2"));

        // L'action suivante voit alors la nouvelle etape.
        Set<String> nextSnapshot = QuestChainPolicy.activeQuestIds(
                Arrays.asList(first, second), chains, progress);
        assertEquals(Collections.singleton("stone_2"), nextSnapshot);
    }

    @Test
    public void differentChainsCanProgressAtTheSameTime() {
        QuestDefinition stone = quest("stone_1", "stone", 1, "mineur", 0);
        QuestDefinition ore = quest("ore_1", "ore", 1, "mineur", 0);
        Map<String, QuestChainDefinition> chains =
                new LinkedHashMap<String, QuestChainDefinition>();
        chains.put("stone", new QuestChainDefinition(
                "stone", "Pierre", "mineur", Collections.singletonList(stone)));
        chains.put("ore", new QuestChainDefinition(
                "ore", "Minerai", "mineur", Collections.singletonList(ore)));

        Set<String> active = QuestChainPolicy.activeQuestIds(
                Arrays.asList(stone, ore), chains,
                Collections.<String, QuestData>emptyMap());

        assertEquals(2, active.size());
        assertTrue(active.contains("stone_1"));
        assertTrue(active.contains("ore_1"));
    }

    @Test
    public void completedHistoryUnlocksNextStageEvenBeforeClaim() {
        QuestDefinition first = quest("wheat_1", "wheat", 1, "farmer", 0);
        QuestDefinition second = quest("wheat_2", "wheat", 2, "farmer", 0);
        QuestChainDefinition chain = new QuestChainDefinition(
                "wheat", "Ble", "farmer", Arrays.asList(first, second));
        QuestData completed = new QuestData("wheat_1", 100, true, false, 42L);
        Map<String, QuestData> progress =
                Collections.singletonMap("wheat_1", completed);

        assertEquals(second, QuestChainPolicy.firstIncomplete(chain, progress));
        assertEquals(QuestChainPolicy.CLAIMABLE,
                QuestChainPolicy.state(chain, first, progress, true, 0));
        assertEquals(QuestChainPolicy.ACTIVE,
                QuestChainPolicy.state(chain, second, progress, true, 0));
    }

    @Test
    public void inactiveJobPausesAndLevelGateLocksActiveStage() {
        QuestDefinition gated = quest(
                "stone_1", "stone", 1, "mineur", 5);
        QuestChainDefinition chain = new QuestChainDefinition(
                "stone", "Pierre", "mineur",
                Collections.singletonList(gated));
        Map<String, QuestData> progress =
                Collections.<String, QuestData>emptyMap();

        assertEquals(QuestChainPolicy.PAUSED_JOB,
                QuestChainPolicy.state(chain, gated, progress, false, 10));
        assertEquals(QuestChainPolicy.LOCKED_LEVEL,
                QuestChainPolicy.state(chain, gated, progress, true, 4));
        assertEquals(QuestChainPolicy.ACTIVE,
                QuestChainPolicy.state(chain, gated, progress, true, 5));
    }

    @Test
    public void fullyCompletedChainHasNoActiveStage() {
        QuestDefinition only = quest(
                "single", "single", 1, "artisan", 0);
        QuestChainDefinition chain = new QuestChainDefinition(
                "single", "Unique", "artisan",
                Collections.singletonList(only));
        QuestData completed = new QuestData(
                "single", 1, true, true, 42L);

        assertNull(QuestChainPolicy.firstIncomplete(chain,
                Collections.singletonMap("single", completed)));
    }

    private static QuestDefinition quest(String id, String chain, int stage,
            String job, int minLevel) {
        return new QuestDefinition(id, id, job, "MINE", "STONE", 10,
                minLevel, 0, Collections.<String>emptyList(),
                chain, stage, true);
    }
}
