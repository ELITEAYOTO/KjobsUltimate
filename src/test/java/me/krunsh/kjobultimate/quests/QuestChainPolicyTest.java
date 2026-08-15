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

/**
 * Tests de la politique séquentielle V3.
 *
 * Règle principale :
 * completed ne suffit pas à débloquer l'étape suivante ; claimed est requis.
 */
public class QuestChainPolicyTest {

    @Test
    public void oneActionCannotSpillIntoTheNextStage() {

        QuestDefinition first =
                quest(
                    "stone_1",
                    "stone",
                    1,
                    "mineur",
                    0
                );

        QuestDefinition second =
                quest(
                    "stone_2",
                    "stone",
                    2,
                    "mineur",
                    0
                );

        QuestChainDefinition chain =
                new QuestChainDefinition(
                    "stone",
                    "Pierre",
                    "mineur",
                    Arrays.asList(
                        first,
                        second
                    )
                );

        Map<String, QuestChainDefinition> chains =
                Collections.singletonMap(
                    "stone",
                    chain
                );

        Map<String, QuestData> progress =
                new LinkedHashMap<String, QuestData>();

        Set<String> snapshot =
                QuestChainPolicy.activeQuestIds(
                    Arrays.asList(
                        first,
                        second
                    ),
                    chains,
                    progress
                );

        assertEquals(
            Collections.singleton("stone_1"),
            snapshot
        );

        QuestData completedFirst =
                new QuestData("stone_1");

        completedFirst.addProgress(
            10,
            10
        );

        progress.put(
            "stone_1",
            completedFirst
        );

        /*
         * La boucle d'action en cours conserve son snapshot.
         */
        assertTrue(
            snapshot.contains("stone_1")
        );

        assertFalse(
            snapshot.contains("stone_2")
        );

        /*
         * Même l'action suivante reste bloquée sur l'étape 1 tant que sa
         * récompense n'est pas claim.
         */
        Set<String> beforeClaim =
                QuestChainPolicy.activeQuestIds(
                    Arrays.asList(
                        first,
                        second
                    ),
                    chains,
                    progress
                );

        assertEquals(
            Collections.singleton("stone_1"),
            beforeClaim
        );

        completedFirst.markClaimed();

        Set<String> afterClaim =
                QuestChainPolicy.activeQuestIds(
                    Arrays.asList(
                        first,
                        second
                    ),
                    chains,
                    progress
                );

        assertEquals(
            Collections.singleton("stone_2"),
            afterClaim
        );
    }

    @Test
    public void differentChainsCanProgressAtTheSameTime() {

        QuestDefinition stone =
                quest(
                    "stone_1",
                    "stone",
                    1,
                    "mineur",
                    0
                );

        QuestDefinition ore =
                quest(
                    "ore_1",
                    "ore",
                    1,
                    "mineur",
                    0
                );

        Map<String, QuestChainDefinition> chains =
                new LinkedHashMap<String, QuestChainDefinition>();

        chains.put(
            "stone",
            new QuestChainDefinition(
                "stone",
                "Pierre",
                "mineur",
                Collections.singletonList(stone)
            )
        );

        chains.put(
            "ore",
            new QuestChainDefinition(
                "ore",
                "Minerai",
                "mineur",
                Collections.singletonList(ore)
            )
        );

        Set<String> active =
                QuestChainPolicy.activeQuestIds(
                    Arrays.asList(
                        stone,
                        ore
                    ),
                    chains,
                    Collections.<String, QuestData>emptyMap()
                );

        assertEquals(
            2,
            active.size()
        );

        assertTrue(
            active.contains("stone_1")
        );

        assertTrue(
            active.contains("ore_1")
        );
    }

    @Test
    public void completedUnclaimedStageBlocksNextStage() {

        QuestDefinition first =
                quest(
                    "wheat_1",
                    "wheat",
                    1,
                    "farmer",
                    0
                );

        QuestDefinition second =
                quest(
                    "wheat_2",
                    "wheat",
                    2,
                    "farmer",
                    0
                );

        QuestChainDefinition chain =
                new QuestChainDefinition(
                    "wheat",
                    "Ble",
                    "farmer",
                    Arrays.asList(
                        first,
                        second
                    )
                );

        QuestData completed =
                new QuestData(
                    "wheat_1",
                    100,
                    true,
                    false,
                    42L
                );

        Map<String, QuestData> progress =
                Collections.singletonMap(
                    "wheat_1",
                    completed
                );

        assertEquals(
            first,
            QuestChainPolicy.firstUnclaimed(
                chain,
                progress
            )
        );

        assertEquals(
            QuestChainPolicy.CLAIMABLE,
            QuestChainPolicy.state(
                chain,
                first,
                progress,
                true,
                0
            )
        );

        assertEquals(
            QuestChainPolicy.LOCKED_CHAIN,
            QuestChainPolicy.state(
                chain,
                second,
                progress,
                true,
                0
            )
        );
    }

    @Test
    public void claimedStageUnlocksNextStage() {

        QuestDefinition first =
                quest(
                    "wheat_1",
                    "wheat",
                    1,
                    "farmer",
                    0
                );

        QuestDefinition second =
                quest(
                    "wheat_2",
                    "wheat",
                    2,
                    "farmer",
                    0
                );

        QuestChainDefinition chain =
                new QuestChainDefinition(
                    "wheat",
                    "Ble",
                    "farmer",
                    Arrays.asList(
                        first,
                        second
                    )
                );

        QuestData claimed =
                new QuestData(
                    "wheat_1",
                    100,
                    true,
                    true,
                    42L
                );

        Map<String, QuestData> progress =
                Collections.singletonMap(
                    "wheat_1",
                    claimed
                );

        assertEquals(
            second,
            QuestChainPolicy.firstUnclaimed(
                chain,
                progress
            )
        );

        assertEquals(
            QuestChainPolicy.CLAIMED,
            QuestChainPolicy.state(
                chain,
                first,
                progress,
                true,
                0
            )
        );

        assertEquals(
            QuestChainPolicy.ACTIVE,
            QuestChainPolicy.state(
                chain,
                second,
                progress,
                true,
                0
            )
        );
    }

    @Test
    public void inactiveJobPausesAndLevelGateLocksCurrentStage() {

        QuestDefinition gated =
                quest(
                    "stone_1",
                    "stone",
                    1,
                    "mineur",
                    5
                );

        QuestChainDefinition chain =
                new QuestChainDefinition(
                    "stone",
                    "Pierre",
                    "mineur",
                    Collections.singletonList(gated)
                );

        Map<String, QuestData> progress =
                Collections.emptyMap();

        assertEquals(
            QuestChainPolicy.PAUSED_JOB,
            QuestChainPolicy.state(
                chain,
                gated,
                progress,
                false,
                10
            )
        );

        assertEquals(
            QuestChainPolicy.LOCKED_LEVEL,
            QuestChainPolicy.state(
                chain,
                gated,
                progress,
                true,
                4
            )
        );

        assertEquals(
            QuestChainPolicy.ACTIVE,
            QuestChainPolicy.state(
                chain,
                gated,
                progress,
                true,
                5
            )
        );
    }

    @Test
    public void fullyClaimedChainHasNoCurrentStage() {

        QuestDefinition only =
                quest(
                    "single",
                    "single",
                    1,
                    "artisan",
                    0
                );

        QuestChainDefinition chain =
                new QuestChainDefinition(
                    "single",
                    "Unique",
                    "artisan",
                    Collections.singletonList(only)
                );

        QuestData claimed =
                new QuestData(
                    "single",
                    1,
                    true,
                    true,
                    42L
                );

        assertNull(
            QuestChainPolicy.firstUnclaimed(
                chain,
                Collections.singletonMap(
                    "single",
                    claimed
                )
            )
        );

        /*
         * L'alias historique suit la même nouvelle règle V3.
         */
        assertNull(
            QuestChainPolicy.firstIncomplete(
                chain,
                Collections.singletonMap(
                    "single",
                    claimed
                )
            )
        );
    }

    private static QuestDefinition quest(
            String id,
            String chain,
            int stage,
            String job,
            int minLevel) {

        return new QuestDefinition(
            id,
            id,
            job,
            "MINE",
            "STONE",
            10,
            minLevel,
            0,
            Collections.<String>emptyList(),
            chain,
            stage,
            true
        );
    }
}