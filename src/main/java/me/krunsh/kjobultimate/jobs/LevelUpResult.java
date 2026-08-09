package me.krunsh.kjobultimate.jobs;

/**
 * Résultat d'un appel à XpManager.addXP().
 * Indique si un level up s'est produit et le nouvel état du joueur.
 */
public final class LevelUpResult {

    private final boolean leveledUp;
    private final int     levelsGained;
    private final int     newLevel;
    private final int     remainingXP;
    private final boolean atMaxLevel;
    /** XP réellement attribué après multiplicateurs (affiché dans l'actionbar). */
    private final int     xpActual;

    private LevelUpResult(boolean leveledUp, int levelsGained, int newLevel,
                           int remainingXP, boolean atMaxLevel, int xpActual) {
        this.leveledUp    = leveledUp;
        this.levelsGained = levelsGained;
        this.newLevel     = newLevel;
        this.remainingXP  = remainingXP;
        this.atMaxLevel   = atMaxLevel;
        this.xpActual     = xpActual;
    }

    /** Résultat standard sans level up. */
    public static LevelUpResult noLevelUp(int currentLevel, int currentXP, int xpActual) {
        return new LevelUpResult(false, 0, currentLevel, currentXP, false, xpActual);
    }

    /** Résultat avec un ou plusieurs level ups. */
    public static LevelUpResult leveled(int levelsGained, int newLevel, int remainingXP, int xpActual) {
        return new LevelUpResult(true, levelsGained, newLevel, remainingXP, false, xpActual);
    }

    /** Joueur déjà au niveau max — aucun XP accordé. */
    public static LevelUpResult maxLevel(int maxLevel) {
        return new LevelUpResult(false, 0, maxLevel, 0, true, 0);
    }

    public boolean isLeveledUp()    { return leveledUp; }
    public int     getLevelsGained(){ return levelsGained; }
    public int     getNewLevel()    { return newLevel; }
    public int     getRemainingXP() { return remainingXP; }
    public boolean isAtMaxLevel()   { return atMaxLevel; }
    /** XP réellement accordé après multiplicateurs — utiliser pour l'affichage HUD. */
    public int     getXpActual()    { return xpActual; }
}
