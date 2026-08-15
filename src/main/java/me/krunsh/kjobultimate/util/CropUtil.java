package me.krunsh.kjobultimate.util;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Fonctions utilitaires pour la vérification de maturité des cultures.
 *
 * IMPORTANT Minecraft/Bukkit 1.8 :
 * - le BLOC de blé est Material.CROPS (ID 59) ;
 * - Material.WHEAT représente l'item récolté.
 *
 * V3.14 : seule la vraie identité de bloc CROPS est acceptée.
 */
public final class CropUtil {

    private CropUtil() {
    }

    @SuppressWarnings("deprecation")
    public static boolean isMature(
            Block block) {

        if (block == null
                || block.getType() == null) {

            return false;
        }

        Material type =
            block.getType();

        byte data =
            block.getData();

        switch (type) {

            case CROPS:
                return data >= 7;

            case CARROT:
                return data >= 7;

            case POTATO:
                return data >= 7;

            case NETHER_WARTS:
                return data >= 3;

            case COCOA:
                return ((data >> 2) & 3) >= 2;

            case MELON_BLOCK:
            case PUMPKIN:
            case SUGAR_CANE_BLOCK:
            case CACTUS:
                return true;

            default:
                return true;
        }
    }

    public static boolean isFarmingCrop(
            Material material) {

        if (material == null) {
            return false;
        }

        switch (material) {

            case CROPS:
            case CARROT:
            case POTATO:
            case NETHER_WARTS:
            case COCOA:
            case MELON_BLOCK:
            case PUMPKIN:
            case SUGAR_CANE_BLOCK:
            case CACTUS:
            case LOG:
            case LOG_2:
            case LEAVES:
            case LEAVES_2:
                return true;

            default:
                return false;
        }
    }
}
