package me.krunsh.kjobultimate.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Charge et expose les définitions de tous les jobs depuis src/main/resources/jobs/*.yml.
 * Les fichiers sont copiés dans plugins/KjobUltimate/jobs/ si absents.
 */
public final class JobRegistry {

    private final KjobUltimate plugin;
    private final Map<String, JobDefinition> jobs = new LinkedHashMap<>();

    // Liste des IDs de jobs attendus (dans l'ordre d'affichage)
    private static final List<String> JOB_IDS = Arrays.asList(
        "mineur", "farmer", "hunter", "pretorien", "artisan", "pilleur"
    );

    public JobRegistry(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    /**
     * Charge (ou recharge) tous les jobs depuis jobs/*.yml.
     */
    public void loadAll() {
        jobs.clear();
        File jobsDir = new File(plugin.getDataFolder(), "jobs");
        if (!jobsDir.exists()) jobsDir.mkdirs();

        for (String jobId : JOB_IDS) {
            String fileName = "jobs/" + jobId + ".yml";
            File jobFile = new File(plugin.getDataFolder(), fileName);
            if (!jobFile.exists()) {
                // Copier depuis les ressources internes
                try {
                    plugin.saveResource(fileName, false);
                } catch (IllegalArgumentException e) {
                    KjobLogger.warn("Fichier ressource manquant : " + fileName + " — job ignoré.");
                    continue;
                }
            }
            try {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(jobFile);
                JobDefinition def = JobDefinition.fromConfig(jobId, cfg);
                jobs.put(jobId, def);
                if (plugin.getConfigManager().isDebug()) {
                    KjobLogger.info("Job chargé : " + jobId + " (max level: " + def.getMaxLevel() + ")");
                }
            } catch (Exception e) {
                KjobLogger.error("Erreur lors du chargement du job " + jobId, e);
            }
        }

        KjobLogger.success(jobs.size() + "/" + JOB_IDS.size() + " jobs chargés.");
    }

    public JobDefinition getJob(String jobId) {
        return jobs.get(jobId);
    }

    public Collection<JobDefinition> getAllJobs() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    public Set<String> getJobIds() {
        return Collections.unmodifiableSet(jobs.keySet());
    }

    public int getJobCount() {
        return jobs.size();
    }

    public boolean isValidJob(String jobId) {
        return jobs.containsKey(jobId);
    }

    public List<String> getExpectedJobIds() {
        return Collections.unmodifiableList(JOB_IDS);
    }
}
