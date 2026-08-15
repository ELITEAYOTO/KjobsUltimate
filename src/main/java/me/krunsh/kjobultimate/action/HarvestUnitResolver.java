package me.krunsh.kjobultimate.action;

import me.krunsh.kjobultimate.KjobUltimate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

/**
 * Détermine combien de blocs une récolte verticale détruit réellement.
 *
 * Exemple :
 *   Y66 SUGAR_CANE
 *   Y65 SUGAR_CANE <- cassé par le joueur
 *   Y64 SUGAR_CANE
 *
 * Le résultat est 2 : Y65 + Y66.
 * Le bloc inférieur Y64 n'est jamais crédité.
 */
public final class HarvestUnitResolver {

    private final KjobUltimate plugin;
    private final CascadeBreakGuard guard;

    public HarvestUnitResolver(
            KjobUltimate plugin) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                "plugin ne peut pas être null."
            );
        }

        this.plugin = plugin;
        this.guard =
            new CascadeBreakGuard();
    }

    public boolean consumeSuppressed(
            Player player,
            Block block) {

        if (!isVerticalCrop(
                block == null
                    ? null
                    : block.getType())) {

            return false;
        }

        return guard.consume(
            player,
            block
        );
    }

    /**
     * Retourne le nombre d'unités à créditer et marque tous les blocs supérieurs
     * inspectés afin qu'un éventuel BlockBreakEvent secondaire ne soit pas
     * recompté.
     */
    public int resolveAndSuppress(
            Player player,
            Block brokenBlock) {

        if (player == null
                || brokenBlock == null) {

            return 0;
        }

        Material type =
            brokenBlock.getType();

        if (!isVerticalCrop(type)) {
            return 1;
        }

        String material =
            type.name();

        if (!plugin.getConfigManager()
                .isVerticalHarvestEnabled(
                    material
                )) {

            return 1;
        }

        int maxUnits =
            plugin.getConfigManager()
                .getVerticalHarvestMaxUnits(
                    material
                );

        int maxScan =
            plugin.getConfigManager()
                .getVerticalHarvestMaxScan(
                    material
                );

        int guardTicks =
            plugin.getConfigManager()
                .getHarvestCascadeGuardTicks();

        int units =
            1;

        Block cursor =
            brokenBlock;

        for (int scanned = 1;
                scanned < maxScan;
                scanned++) {

            cursor =
                cursor.getRelative(
                    BlockFace.UP
                );

            if (cursor == null
                    || cursor.getType() != type) {

                break;
            }

            /*
             * Même lorsqu'on a atteint maxUnits, on continue jusqu'à maxScan
             * pour protéger contre un double BlockBreakEvent artificiel.
             */
            guard.suppress(
                player,
                cursor,
                guardTicks
            );

            if (units < maxUnits) {
                units++;
            }
        }

        return units;
    }

    private static boolean isVerticalCrop(
            Material material) {

        return material == Material.SUGAR_CANE_BLOCK
            || material == Material.CACTUS;
    }
}
