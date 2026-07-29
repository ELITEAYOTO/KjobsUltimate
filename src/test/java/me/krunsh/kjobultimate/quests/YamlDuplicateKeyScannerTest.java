package me.krunsh.kjobultimate.quests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

public class YamlDuplicateKeyScannerTest {

    @Test
    public void duplicateKeyInSameMappingIsRejected() throws Exception {
        File file = Files.createTempFile("kjobs-quests-", ".yml").toFile();
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(("quests:\n"
                        + "  stone_1:\n"
                        + "    amount: 10\n"
                        + "    amount: 20\n").getBytes(StandardCharsets.UTF_8));
            }
            List<String> errors = YamlDuplicateKeyScanner.scan(file);
            assertEquals(1, errors.size());
            assertTrue(errors.get(0).contains("amount"));
            assertTrue(errors.get(0).contains("3"));
            assertTrue(errors.get(0).contains("4"));
        } finally {
            file.delete();
        }
    }

    @Test
    public void sameKeyInDifferentQuestMappingsIsAllowed() throws Exception {
        File file = Files.createTempFile("kjobs-quests-", ".yml").toFile();
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(("quests:\n"
                        + "  stone_1:\n"
                        + "    amount: 10\n"
                        + "  wheat_1:\n"
                        + "    amount: 20\n").getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(YamlDuplicateKeyScanner.scan(file).isEmpty());
        } finally {
            file.delete();
        }
    }
}
