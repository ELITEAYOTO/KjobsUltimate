package me.krunsh.kjobultimate.util;

import java.util.Locale;
import java.util.Objects;

import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;

/**
 * Fonctions centrales de calcul et d'affichage de la progression d'un métier.
 *
 * Convention :
 * - PlayerData contient le niveau ACTUEL du joueur ;
 * - JobDefinition#getXpRequiredForNextLevel(int) retourne l'XP nécessaire
 *   pour passer de ce niveau actuel au niveau suivant ;
 * - au niveau maximum, l'XP requise vaut 0 et la progression vaut 100 %.
 *
 * Cette classe ne modifie jamais les données du joueur.
 */
public final class LevelUtil {

    private static final float MIN_PROGRESS = 0.0F;
    private static final float MAX_PROGRESS = 1.0F;

    private LevelUtil() {
        throw new AssertionError("Classe utilitaire non instanciable.");
    }

    /**
     * Retourne l'XP enregistrée dans le niveau actuel.
     *
     * Une ancienne donnée négative est affichée comme 0 sans modifier PlayerData.
     */
    public static int getCurrentLevelXp(PlayerData data, JobDefinition job) {
        requireArguments(data, job);
        return Math.max(0, data.getXP(job.getId()));
    }

    /**
     * Retourne l'XP nécessaire pour atteindre le prochain niveau.
     *
     * @return 0 lorsque le joueur est déjà au niveau maximum
     */
    public static int getRequiredXpForNextLevel(
            PlayerData data,
            JobDefinition job) {

        requireArguments(data, job);

        int currentLevel = sanitizeCurrentLevel(data, job);
        if (currentLevel >= job.getMaxLevel()) {
            return 0;
        }

        return Math.max(
            0,
            job.getXpRequiredForNextLevel(currentLevel));
    }

    /**
     * Retourne la progression vers le prochain niveau entre 0.0 et 1.0.
     *
     * Un joueur au niveau maximum retourne toujours 1.0.
     * Une courbe invalide retourne 0.0 afin de ne pas afficher à tort
     * une barre complète.
     */
    public static float getProgressPercent(
            PlayerData data,
            JobDefinition job) {

        requireArguments(data, job);

        int currentLevel = sanitizeCurrentLevel(data, job);
        if (currentLevel >= job.getMaxLevel()) {
            return MAX_PROGRESS;
        }

        int requiredXp =
            job.getXpRequiredForNextLevel(currentLevel);

        if (requiredXp <= 0) {
            return MIN_PROGRESS;
        }

        int currentXp = Math.max(0, data.getXP(job.getId()));
        double ratio = (double) currentXp / (double) requiredXp;

        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return MIN_PROGRESS;
        }

        return (float) clamp(ratio, MIN_PROGRESS, MAX_PROGRESS);
    }

    /**
     * Retourne la progression entière entre 0 et 100.
     *
     * Cette méthode évite que chaque GUI, HUD ou placeholder réimplémente
     * son propre arrondi.
     */
    public static int getProgressPercentage(
            PlayerData data,
            JobDefinition job) {

        return Math.min(
            100,
            Math.max(
                0,
                (int) Math.floor(
                    getProgressPercent(data, job) * 100.0D)));
    }

    /**
     * Formate une quantité d'XP de façon compacte et stable.
     *
     * Exemples :
     * 950       -> "950"
     * 1 500     -> "1.5k"
     * 15 000    -> "15k"
     * 2 000 000 -> "2M"
     *
     * Locale.ROOT garantit l'utilisation du point décimal dans les interfaces
     * Minecraft, indépendamment de la langue du système d'exploitation.
     */
    public static String formatXP(int xp) {
        return formatCompactNumber(xp);
    }

    /**
     * Variante long utilisable pour les cumuls, classements et statistiques.
     */
    public static String formatXP(long xp) {
        return formatCompactNumber(xp);
    }

    /**
     * Retourne "actuel / requis XP" ou "MAX".
     */
    public static String formatProgress(
            PlayerData data,
            JobDefinition job) {

        requireArguments(data, job);

        int currentLevel = sanitizeCurrentLevel(data, job);
        if (currentLevel >= job.getMaxLevel()) {
            return "MAX";
        }

        int currentXp = Math.max(0, data.getXP(job.getId()));
        int requiredXp =
            Math.max(0, job.getXpRequiredForNextLevel(currentLevel));

        return formatXP(currentXp)
            + " / "
            + formatXP(requiredXp)
            + " XP";
    }

    /**
     * Retourne une progression complète destinée aux logs et diagnostics.
     */
    public static String formatDetailedProgress(
            PlayerData data,
            JobDefinition job) {

        requireArguments(data, job);

        int currentLevel = sanitizeCurrentLevel(data, job);
        if (currentLevel >= job.getMaxLevel()) {
            return "Niveau "
                + job.getMaxLevel()
                + " / "
                + job.getMaxLevel()
                + " - MAX";
        }

        return "Niveau "
            + currentLevel
            + " / "
            + job.getMaxLevel()
            + " - "
            + formatProgress(data, job)
            + " ("
            + getProgressPercentage(data, job)
            + "%)";
    }

    private static int sanitizeCurrentLevel(
            PlayerData data,
            JobDefinition job) {

        return Math.min(
            job.getMaxLevel(),
            Math.max(0, data.getLevel(job.getId())));
    }

    private static String formatCompactNumber(long value) {
        if (value == Long.MIN_VALUE) {
            return "-9.2E";
        }

        boolean negative = value < 0L;
        long absolute = Math.abs(value);

        String formatted;
        if (absolute >= 1_000_000_000_000_000_000L) {
            formatted = formatUnit(absolute, 1_000_000_000_000_000_000D, "E");
        } else if (absolute >= 1_000_000_000_000_000L) {
            formatted = formatUnit(absolute, 1_000_000_000_000_000D, "P");
        } else if (absolute >= 1_000_000_000_000L) {
            formatted = formatUnit(absolute, 1_000_000_000_000D, "T");
        } else if (absolute >= 1_000_000_000L) {
            formatted = formatUnit(absolute, 1_000_000_000D, "B");
        } else if (absolute >= 1_000_000L) {
            formatted = formatUnit(absolute, 1_000_000D, "M");
        } else if (absolute >= 1_000L) {
            formatted = formatUnit(absolute, 1_000D, "k");
        } else {
            formatted = String.valueOf(absolute);
        }

        return negative ? "-" + formatted : formatted;
    }

    private static String formatUnit(
            long absoluteValue,
            double divisor,
            String suffix) {

        double scaled = absoluteValue / divisor;

        if (scaled >= 100D || isWholeNumber(scaled)) {
            return String.format(
                Locale.ROOT,
                "%.0f%s",
                scaled,
                suffix);
        }

        return String.format(
            Locale.ROOT,
            "%.1f%s",
            scaled,
            suffix);
    }

    private static boolean isWholeNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0000001D;
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum) {

        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requireArguments(
            PlayerData data,
            JobDefinition job) {

        Objects.requireNonNull(data, "PlayerData ne peut pas être null.");
        Objects.requireNonNull(job, "JobDefinition ne peut pas être null.");
    }
}