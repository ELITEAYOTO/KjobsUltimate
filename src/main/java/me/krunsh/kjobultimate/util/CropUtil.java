package me.krunsh.kjobultimate.util;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Fonctions utilitaires pour la vérification de maturité des cultures.
 * Utilisé par FarmerListener pour le mode crops_mature_only (anti-abuse).
 */
public final class CropUtil {

    private CropUtil() {}

    /**
     * Retourne true si la culture est à maturité (moissonnement légitime).
     * Basé sur les valeurs data (byte) des blocs Bukkit 1.8.8.
     */
    @SuppressWarnings("deprecation")
    public static boolean isMature(Block block) {
        Material type = block.getType();
        byte data = block.getData();
        switch (type) {
            case WHEAT:          return data >= 7;
            case CARROT:         return data >= 7;
            case POTATO:         return data >= 7;
            case NETHER_WARTS:   return data >= 3; // NETHER_WARTS = bloc en monde (ID 115, Bukkit 1.8.8)
            case COCOA:          return ((data >> 2) & 3) >= 2;
            // Blocs récoltables sans maturité à vérifier :
            case MELON_BLOCK:
            case PUMPKIN:
            case SUGAR_CANE_BLOCK:
            case CACTUS:
                return true;
            default:
                return true;
        }
    }

    /**
     * Retourne true si le matériau est une culture éligible au job Farmer.
     * Correspond aux actions déclarées dans farmer.yml.
     */
    public static boolean isFarmingCrop(Material material) {
        switch (material) {
            case WHEAT:
            case CARROT:
            case POTATO:
            case NETHER_WARTS: // bloc nether wart en monde (ID 115)
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
