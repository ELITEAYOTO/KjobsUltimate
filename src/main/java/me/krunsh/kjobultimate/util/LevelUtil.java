package me.krunsh.kjobultimate.util;

import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;

/**
 * Fonctions statiques pour le calcul et l'affichage de progression de niveau.
 */
public final class LevelUtil {

    private LevelUtil() {}

    /**
     * Retourne le pourcentage de progression vers le prochain niveau (0.0 à 1.0).
     */
    public static float getProgressPercent(PlayerData data, JobDefinition job) {
        int level = data.getLevel(job.getId());
        if (level >= job.getMaxLevel()) return 1.0f;
        int required = job.getXpForLevel(level);
        if (required <= 0) return 1.0f;
        return Math.min(1.0f, (float) data.getXP(job.getId()) / required);
    }

    /**
     * Formate une valeur XP en abrégé lisible (ex. 1500 → "1.5k", 2000000 → "2.0M").
     */
    public static String formatXP(int xp) {
        if (xp >= 1_000_000) return String.format("%.1fM", xp / 1_000_000.0);
        if (xp >= 10_000)    return String.format("%.0fk", xp / 1_000.0);
        return String.valueOf(xp);
    }

    /**
     * Retourne la progression sous forme "actuel / requis XP" ou "MAX".
     */
    public static String formatProgress(PlayerData data, JobDefinition job) {
        int level = data.getLevel(job.getId());
        if (level >= job.getMaxLevel()) return "MAX";
        return formatXP(data.getXP(job.getId())) + " / "
            + formatXP(job.getXpForLevel(level)) + " XP";
    }
}
