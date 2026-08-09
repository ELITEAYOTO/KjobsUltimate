package me.krunsh.kjobultimate.quests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Rubrique séquentielle de quêtes; une seule étape peut progresser à la fois. */
public final class QuestChainDefinition {
    private final String id;
    private final String displayName;
    private final String jobId;
    private final List<QuestDefinition> stages;

    public QuestChainDefinition(String id, String displayName, String jobId,
            List<QuestDefinition> stages) {
        this.id = id;
        this.displayName = displayName;
        this.jobId = jobId;
        ArrayList<QuestDefinition> sorted =
                new ArrayList<QuestDefinition>(stages);
        Collections.sort(sorted, new Comparator<QuestDefinition>() {
            @Override
            public int compare(QuestDefinition first, QuestDefinition second) {
                int byStage = Integer.compare(first.getChainStage(),
                        second.getChainStage());
                return byStage != 0 ? byStage
                        : first.getId().compareToIgnoreCase(second.getId());
            }
        });
        this.stages = Collections.unmodifiableList(sorted);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getJobId() { return jobId; }
    public List<QuestDefinition> getStages() { return stages; }
}
