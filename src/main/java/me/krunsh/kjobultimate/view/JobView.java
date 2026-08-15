package me.krunsh.kjobultimate.view;

/**
 * Snapshot immutable de l'état d'un métier pour un joueur.
 *
 * Cette classe ne contient aucune logique Bukkit, SQL ou métier.
 * Elle sert uniquement de représentation stable pour :
 *
 * - Kgui
 * - PlaceholderAPI
 * - HUD
 * - future API Kjobs
 * - outils de debug
 *
 * Toutes les valeurs sont calculées en amont par JobsViewService.
 */
public final class JobView {

    private final String id;
    private final String displayName;

    private final int level;
    private final int maxLevel;

    private final int xp;
    private final int xpRequired;
    private final int xpRemaining;
    private final int xpPercent;

    private final boolean maxLevelReached;

    private final boolean active;
    private final boolean favorite;
    private final int slot;

    private final int dailyXp;
    private final int dailyXpCap;
    private final int dailyXpRemaining;
    private final boolean dailyXpCapEnabled;

    private final String iconMaterial;
    private final short iconData;
    private final String cit;

    JobView(
            String id,
            String displayName,
            int level,
            int maxLevel,
            int xp,
            int xpRequired,
            int xpRemaining,
            int xpPercent,
            boolean maxLevelReached,
            boolean active,
            boolean favorite,
            int slot,
            int dailyXp,
            int dailyXpCap,
            int dailyXpRemaining,
            boolean dailyXpCapEnabled,
            String iconMaterial,
            short iconData,
            String cit) {

        this.id = safe(id);
        this.displayName = safe(displayName);

        this.level = Math.max(0, level);
        this.maxLevel = Math.max(0, maxLevel);

        this.xp = Math.max(0, xp);
        this.xpRequired = Math.max(0, xpRequired);
        this.xpRemaining = Math.max(0, xpRemaining);
        this.xpPercent = clampPercent(xpPercent);

        this.maxLevelReached = maxLevelReached;

        this.active = active;
        this.favorite = favorite;
        this.slot = slot;

        this.dailyXp = Math.max(0, dailyXp);
        this.dailyXpCap = Math.max(0, dailyXpCap);
        this.dailyXpRemaining = Math.max(0, dailyXpRemaining);
        this.dailyXpCapEnabled = dailyXpCapEnabled;

        this.iconMaterial = safe(iconMaterial);
        this.iconData = iconData;
        this.cit = safe(cit);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getXp() {
        return xp;
    }

    public int getXpRequired() {
        return xpRequired;
    }

    public int getXpRemaining() {
        return xpRemaining;
    }

    public int getXpPercent() {
        return xpPercent;
    }

    public boolean isMaxLevelReached() {
        return maxLevelReached;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isFavorite() {
        return favorite;
    }

    /**
     * @return numéro du slot actif, ou -1 si le métier n'est pas actif.
     */
    public int getSlot() {
        return slot;
    }

    public int getDailyXp() {
        return dailyXp;
    }

    /**
     * @return plafond journalier configuré. 0 signifie illimité.
     */
    public int getDailyXpCap() {
        return dailyXpCap;
    }

    public int getDailyXpRemaining() {
        return dailyXpRemaining;
    }

    public boolean isDailyXpCapEnabled() {
        return dailyXpCapEnabled;
    }

    public String getIconMaterial() {
        return iconMaterial;
    }

    public short getIconData() {
        return iconData;
    }

    public String getCit() {
        return cit;
    }

    @Override
    public String toString() {
        return "JobView{"
                + "id='" + id + '\''
                + ", level=" + level
                + ", maxLevel=" + maxLevel
                + ", xp=" + xp
                + ", xpRequired=" + xpRequired
                + ", xpPercent=" + xpPercent
                + ", active=" + active
                + ", favorite=" + favorite
                + ", slot=" + slot
                + '}';
    }
}