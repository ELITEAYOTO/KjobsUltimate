package me.krunsh.kjobultimate.quests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

public class QuestDefinitionMaterialDataTest {

    @Test
    public void exactDataTargetOnlyMatchesItsVariant() {
        QuestDefinition quest = quest("PRISMARINE:2");

        assertTrue(quest.matches("MINE", "PRISMARINE:2"));
        assertFalse(quest.matches("MINE", "PRISMARINE:0"));
        assertFalse(quest.matches("MINE", "PRISMARINE"));
    }

    @Test
    public void materialTargetWithoutDataAcceptsAllVariants() {
        QuestDefinition quest = quest("STONE");

        assertTrue(quest.matches("MINE", "STONE"));
        assertTrue(quest.matches("MINE", "STONE:0"));
        assertTrue(quest.matches("MINE", "STONE:6"));
        assertFalse(quest.matches("MINE", "COBBLESTONE:0"));
    }

    private static QuestDefinition quest(String target) {
        return new QuestDefinition(
            "test", "Test", "mineur", "MINE", target,
            1, 0, 0, Collections.<String>emptyList()
        );
    }
}
