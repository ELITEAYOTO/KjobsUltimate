package me.krunsh.kjobultimate.hooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Garde-fous d'intégration avec les plugins publics.
 *
 * Kgui est une dépendance runtime obligatoire en V3, mais KjobsUltimate ne doit
 * toujours compiler que contre son API publique.
 */
public class PublicApiBoundaryTest {

    @Test
    public void hooksUseOnlyPublishedApiPackages()
            throws Exception {

        Path sourceRoot =
            Paths.get("src/main/java");

        StringBuilder source =
            new StringBuilder();

        try (Stream<Path> paths =
                Files.walk(sourceRoot)) {

            paths
                .filter(
                    path ->
                        path.toString()
                            .endsWith(".java")
                )
                .forEach(
                    path -> {
                        try {

                            source.append(
                                new String(
                                    Files.readAllBytes(path),
                                    StandardCharsets.UTF_8
                                )
                            );

                        } catch (java.io.IOException failure) {

                            throw new IllegalStateException(
                                failure
                            );
                        }
                    }
                );
        }

        String all =
            source.toString();

        assertFalse(
            all.contains(
                "import me.krunsh.kgui.Kgui"
            )
        );

        assertFalse(
            all.contains(
                "import me.krunsh.kfaction.Kfaction"
            )
        );

        assertFalse(
            all.contains(
                "import me.krunsh.kcraft."
            )
        );

        assertFalse(
            all.contains(
                "import me.krunsh.kstacker."
            )
        );

        assertTrue(
            all.contains(
                "import me.krunsh.kgui.api.KguiApi"
            )
        );

        assertTrue(
            all.contains(
                "import me.krunsh.kfaction.api.v2.KfactionApis"
            )
        );
    }

    @Test
    public void buildHasNoMachineLocalSystemDependencies()
            throws Exception {

        String pom =
            new String(
                Files.readAllBytes(
                    Paths.get("pom.xml")
                ),
                StandardCharsets.UTF_8
            );

        assertFalse(pom.contains("<scope>system</scope>"));
        assertFalse(pom.contains("<systemPath>"));
    }

    @Test
    public void kguiIsARequiredRuntimeDependency()
            throws Exception {

        String yaml =
            new String(
                Files.readAllBytes(
                    Paths.get(
                        "src/main/resources/plugin.yml"
                    )
                ),
                StandardCharsets.UTF_8
            )
            .replace(
                "\r\n",
                "\n"
            );

        assertTrue(
            sectionContainsDependency(
                yaml,
                "depend",
                "Kgui"
            )
        );

        assertFalse(
            sectionContainsDependency(
                yaml,
                "softdepend",
                "Kgui"
            )
        );
    }

    private static boolean sectionContainsDependency(
            String yaml,
            String section,
            String dependency) {

        String[] lines =
            yaml.split("\n");

        boolean inside =
            false;

        for (String line : lines) {

            if (line == null) {
                continue;
            }

            if (!line.startsWith(" ")
                    && line.endsWith(":")) {

                inside =
                    (section + ":")
                        .equals(line.trim());

                continue;
            }

            if (!inside) {
                continue;
            }

            String trimmed =
                line.trim();

            if (trimmed.startsWith("- ")) {

                if (dependency.equalsIgnoreCase(
                        trimmed.substring(2).trim())) {

                    return true;
                }

                continue;
            }

            if (!trimmed.isEmpty()
                    && !line.startsWith(" ")) {

                inside = false;
            }
        }

        return false;
    }
}
