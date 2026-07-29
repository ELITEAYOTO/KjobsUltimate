package me.krunsh.kjobultimate.quests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Construit et valide l'index séquentiel des chaînes. */
public final class QuestChainIndex {
    public static final class Metadata {
        private final String displayName;
        private final String jobId;

        public Metadata(String displayName, String jobId) {
            this.displayName = displayName;
            this.jobId = normalize(jobId);
        }
    }

    public static final class Result {
        private final Map<String, QuestChainDefinition> chains;
        private final List<String> errors;

        private Result(Map<String, QuestChainDefinition> chains,
                List<String> errors) {
            this.chains = Collections.unmodifiableMap(chains);
            this.errors = Collections.unmodifiableList(errors);
        }

        public Map<String, QuestChainDefinition> getChains() { return chains; }
        public List<String> getErrors() { return errors; }
        public boolean isValid() { return errors.isEmpty(); }
    }

    private QuestChainIndex() {}

    public static Result build(Collection<QuestDefinition> quests,
            Map<String, Metadata> declaredChains) {
        LinkedHashMap<String, List<QuestDefinition>> grouped =
                new LinkedHashMap<String, List<QuestDefinition>>();
        List<String> errors = new ArrayList<String>();
        for (QuestDefinition quest : quests) {
            String chainId = normalize(quest.getChainId());
            List<QuestDefinition> stages = grouped.get(chainId);
            if (stages == null) {
                stages = new ArrayList<QuestDefinition>();
                grouped.put(chainId, stages);
            }
            stages.add(quest);
        }

        for (String declared : declaredChains.keySet()) {
            if (!grouped.containsKey(normalize(declared))) {
                errors.add("chains." + declared
                        + ": chaîne déclarée sans aucune quête.");
            }
        }

        LinkedHashMap<String, QuestChainDefinition> built =
                new LinkedHashMap<String, QuestChainDefinition>();
        for (Map.Entry<String, List<QuestDefinition>> entry : grouped.entrySet()) {
            String chainId = entry.getKey();
            List<QuestDefinition> stages = entry.getValue();
            Metadata metadata = declaredChains.get(chainId);
            boolean explicitlyReferenced = false;
            for (QuestDefinition stage : stages) {
                if (stage.isExplicitChain()) explicitlyReferenced = true;
            }
            if (explicitlyReferenced && metadata == null) {
                errors.add("chains." + chainId
                        + ": définition obligatoire manquante.");
            }

            String jobId = metadata == null
                    ? normalize(stages.get(0).getJobId()) : metadata.jobId;
            String display = metadata == null
                    ? stages.get(0).getDisplayName() : metadata.displayName;
            boolean[] seen = new boolean[stages.size() + 1];
            for (QuestDefinition stage : stages) {
                if (!jobId.equals(normalize(stage.getJobId()))) {
                    errors.add("quests." + stage.getId()
                            + ".job: doit être " + jobId
                            + " comme le reste de la chaîne " + chainId + ".");
                }
                int number = stage.getChainStage();
                if (number < 1 || number > stages.size()) {
                    errors.add("quests." + stage.getId()
                            + ".stage: étapes attendues de 1 à "
                            + stages.size() + ", reçu " + number + ".");
                } else if (seen[number]) {
                    errors.add("chains." + chainId
                            + ": étape " + number + " dupliquée.");
                } else {
                    seen[number] = true;
                }
            }
            for (int stage = 1; stage < seen.length; stage++) {
                if (!seen[stage]) {
                    errors.add("chains." + chainId
                            + ": étape " + stage + " manquante.");
                }
            }
            built.put(chainId, new QuestChainDefinition(chainId,
                    display == null || display.trim().isEmpty()
                            ? chainId : display,
                    jobId, stages));
        }
        return new Result(built, errors);
    }

    static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
