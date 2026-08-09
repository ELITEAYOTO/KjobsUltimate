package me.krunsh.kjobultimate.quests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class QuestChainIndexTest {

    @Test
    public void validContiguousChainIsSortedByStage() {
        QuestDefinition second = quest("stone_2", "stone", 2, "mineur", true);
        QuestDefinition first = quest("stone_1", "stone", 1, "mineur", true);
        Map<String, QuestChainIndex.Metadata> declared =
                Collections.singletonMap("stone",
                        new QuestChainIndex.Metadata("Pierre", "mineur"));

        QuestChainIndex.Result result =
                QuestChainIndex.build(Arrays.asList(second, first), declared);

        assertTrue(result.getErrors().toString(), result.isValid());
        assertEquals("stone_1", result.getChains().get("stone")
                .getStages().get(0).getId());
        assertEquals("stone_2", result.getChains().get("stone")
                .getStages().get(1).getId());
    }

    @Test
    public void gapsDuplicatesAndMixedJobsAreRejected() {
        QuestDefinition duplicateA =
                quest("stone_a", "stone", 1, "mineur", true);
        QuestDefinition duplicateB =
                quest("stone_b", "stone", 1, "farmer", true);
        Map<String, QuestChainIndex.Metadata> declared =
                Collections.singletonMap("stone",
                        new QuestChainIndex.Metadata("Pierre", "mineur"));

        QuestChainIndex.Result result = QuestChainIndex.build(
                Arrays.asList(duplicateA, duplicateB), declared);

        assertFalse(result.isValid());
        assertTrue(join(result).contains("dupli"));
        assertTrue(join(result).contains("manqu"));
        assertTrue(join(result).contains("mineur"));
    }

    @Test
    public void explicitChainRequiresDeclaration() {
        QuestChainIndex.Result result = QuestChainIndex.build(
                Collections.singletonList(
                        quest("stone_1", "stone", 1, "mineur", true)),
                Collections.<String, QuestChainIndex.Metadata>emptyMap());

        assertFalse(result.isValid());
        assertTrue(join(result).contains("obligatoire"));
    }

    @Test
    public void legacyQuestRemainsACompatibleSingletonChain() {
        QuestDefinition legacy = new QuestDefinition(
                "legacy", "Legacy", "mineur", "MINE", "STONE",
                10, 0, 0, Collections.<String>emptyList());

        QuestChainIndex.Result result = QuestChainIndex.build(
                Collections.singletonList(legacy),
                Collections.<String, QuestChainIndex.Metadata>emptyMap());

        assertTrue(result.getErrors().toString(), result.isValid());
        assertEquals(1, result.getChains().get("legacy").getStages().size());
    }

    private static QuestDefinition quest(String id, String chain, int stage,
            String job, boolean explicit) {
        return new QuestDefinition(id, id, job, "MINE", "STONE", 10,
                0, 0, Collections.<String>emptyList(),
                chain, stage, explicit);
    }

    private static String join(QuestChainIndex.Result result) {
        StringBuilder text = new StringBuilder();
        for (String error : result.getErrors()) {
            text.append(error.toLowerCase()).append('\n');
        }
        return text.toString();
    }
}
