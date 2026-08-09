package me.krunsh.kjobultimate.hooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.Test;

/** Empêche le retour des casts vers les plugins principaux Kgui/Kfaction. */
public class PublicApiBoundaryTest {

    @Test
    public void optionalHooksUseOnlyPublishedApiPackages() throws Exception {
        Path sourceRoot = Paths.get("src/main/java");
        StringBuilder source = new StringBuilder();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    source.append(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
            });
        }

        String all = source.toString();
        assertFalse(all.contains("import me.krunsh.kgui.Kgui"));
        assertFalse(all.contains("import me.krunsh.kfaction.Kfaction"));
        assertTrue(all.contains("import me.krunsh.kgui.api.KguiApi"));
        assertTrue(all.contains("import me.krunsh.kfaction.api.v2.KfactionApis"));
    }
}
