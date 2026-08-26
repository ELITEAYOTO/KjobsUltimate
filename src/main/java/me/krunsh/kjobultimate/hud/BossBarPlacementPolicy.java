package me.krunsh.kjobultimate.hud;

/**
 * Pure rules used by the 1.8 packet-only bossbar implementation.
 *
 * <p>The fake dragon cannot rely on the vanilla invisibility flag: the 1.8
 * dragon renderer still draws its model. It is therefore anchored below the
 * normal world while remaining above the client-side removal limit.</p>
 */
final class BossBarPlacementPolicy {

    static final double CLIENT_KILL_Y = -64.0D;
    static final double DRAGON_WIDTH = 16.0D;
    static final double DRAGON_HEIGHT = 8.0D;
    static final double DRAGON_RENDER_DISTANCE =
        ((DRAGON_WIDTH + DRAGON_HEIGHT + DRAGON_WIDTH) / 3.0D)
            * 64.0D;
    static final double DEFAULT_ABSOLUTE_Y = -60.0D;
    static final double MIN_SAFE_ABSOLUTE_Y = -62.0D;
    static final double MAX_HIDDEN_ABSOLUTE_Y = -16.0D;
    static final double DEFAULT_REANCHOR_DISTANCE = 12.0D;

    private BossBarPlacementPolicy() {
    }

    static double clampAbsoluteY(double configuredY) {
        if (Double.isNaN(configuredY)
                || Double.isInfinite(configuredY)) {

            return DEFAULT_ABSOLUTE_Y;
        }

        return Math.max(
            MIN_SAFE_ABSOLUTE_Y,
            Math.min(MAX_HIDDEN_ABSOLUTE_Y, configuredY)
        );
    }

    static boolean isKilledByClient(double entityY) {
        return entityY < CLIENT_KILL_Y;
    }

    /**
     * Reproduit le contrôle de distance de Entity 1.8 pour le Dragon.
     *
     * <p>Le constructeur client du Dragon fixe ignoreFrustumCheck=true. Le
     * modèle n'est donc pas écarté par le frustum, mais Entity conserve son
     * contrôle de distance basé sur la taille moyenne de la bounding box,
     * multipliée par 64. Avec une entité 16 x 8 x 16, la portée vaut environ
     * 853 blocs.</p>
     */
    static boolean remainsInDragonRenderRange(
            double cameraX,
            double cameraY,
            double cameraZ,
            double dragonX,
            double dragonY,
            double dragonZ) {

        double dx = dragonX - cameraX;
        double dy = dragonY - cameraY;
        double dz = dragonZ - cameraZ;

        return dx * dx + dy * dy + dz * dz
            < DRAGON_RENDER_DISTANCE * DRAGON_RENDER_DISTANCE;
    }

    static boolean shouldMigrateLegacyDragonPlacement(
            String entityType,
            String normalizedProfile,
            boolean hasAbsoluteY,
            boolean hasLegacyDistance,
            boolean hasLegacyVerticalOffset) {

        return "ENDER_DRAGON".equals(entityType)
            && "AUTO".equals(normalizedProfile)
            && !hasAbsoluteY
            && (hasLegacyDistance || hasLegacyVerticalOffset);
    }

    static boolean shouldReanchor(
            boolean anchorKnown,
            double anchorX,
            double anchorY,
            double anchorZ,
            double desiredX,
            double desiredY,
            double desiredZ,
            double minimumDistance) {

        if (!anchorKnown) {
            return true;
        }

        double dx = desiredX - anchorX;
        double dy = desiredY - anchorY;
        double dz = desiredZ - anchorZ;
        double safeDistance = Math.max(0.0D, minimumDistance);

        return dx * dx + dy * dy + dz * dz
            >= safeDistance * safeDistance;
    }

    static boolean requiresMetadataUpdate(
            boolean metadataKnown,
            float previousHealth,
            String previousTitle,
            boolean previousInvisible,
            float health,
            String title,
            boolean invisible) {

        if (!metadataKnown
                || Float.floatToIntBits(previousHealth)
                    != Float.floatToIntBits(health)
                || previousInvisible != invisible) {

            return true;
        }

        return previousTitle == null
            ? title != null
            : !previousTitle.equals(title);
    }
}
