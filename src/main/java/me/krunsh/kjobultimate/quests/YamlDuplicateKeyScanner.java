package me.krunsh.kjobultimate.quests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refuse les cles YAML dupliquees avant que SnakeYAML 1.x ne puisse en
 * ecraser silencieusement une.
 */
public final class YamlDuplicateKeyScanner {
    private static final Pattern KEY =
            Pattern.compile("^(\\s*)([A-Za-z0-9._-]+):(?:\\s.*)?$");

    private YamlDuplicateKeyScanner() {}

    public static List<String> scan(File file) throws IOException {
        List<String> errors = new ArrayList<String>();
        Deque<Scope> scopes = new ArrayDeque<Scope>();
        scopes.push(new Scope(-1, "quests.yml"));
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")
                        || trimmed.startsWith("-")) continue;
                Matcher matcher = KEY.matcher(line);
                if (!matcher.matches()) continue;

                int indent = matcher.group(1).length();
                String key = matcher.group(2);
                while (scopes.peek().indent >= indent) scopes.pop();
                Scope parent = scopes.peek();
                Integer previous = parent.keys.put(key, lineNumber);
                String path = parent.path + "." + key;
                if (previous != null) {
                    errors.add(path + ": cle dupliquee lignes "
                            + previous + " et " + lineNumber + ".");
                }
                scopes.push(new Scope(indent, path));
            }
        }
        return errors;
    }

    private static final class Scope {
        private final int indent;
        private final String path;
        private final Map<String, Integer> keys = new HashMap<String, Integer>();

        private Scope(int indent, String path) {
            this.indent = indent;
            this.path = path;
        }
    }
}
