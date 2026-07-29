package me.krunsh.kjobultimate.util;

import de.tr7zw.changeme.nbtapi.NBTItem;
import me.krunsh.kjobultimate.KjobUltimate;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Match un item Bukkit contre une section config simple:
 * material: TNT
 * data: 0
 * nbt:
 *   sparrowmc-item: "dynamite"
 */
public final class ConfiguredItemMatcher {

    private ConfiguredItemMatcher() {}

    public static boolean matches(KjobUltimate plugin, ItemStack item, String path) {
        if (plugin == null || item == null || item.getType() == Material.AIR) return false;

        ConfigurationSection section = plugin.getConfigManager().getMainConfig().getConfigurationSection(path);
        if (section == null) return false;

        if (!matchesMaterial(item, section)) return false;
        if (!matchesData(item, section)) return false;
        return matchesNbt(item, getNbtSection(section));
    }

    private static boolean matchesMaterial(ItemStack item, ConfigurationSection section) {
        String materialName = section.getString("material", "").trim();
        if (materialName.isEmpty() || "*".equals(materialName)) return true;

        Material expected = Material.matchMaterial(materialName);
        return expected != null && item.getType() == expected;
    }

    private static boolean matchesData(ItemStack item, ConfigurationSection section) {
        if (!section.contains("data")) return true;
        int expected = section.getInt("data", -1);
        return expected < 0 || item.getDurability() == (short) expected;
    }

    private static ConfigurationSection getNbtSection(ConfigurationSection section) {
        ConfigurationSection nested = section.getConfigurationSection("nbt");
        if (nested != null) return nested;

        // Compat ancien format: pilleur.dynamite_nbt_example directement en key/value.
        boolean hasLegacyKeys = false;
        for (String key : section.getKeys(false)) {
            if (!"material".equalsIgnoreCase(key) && !"data".equalsIgnoreCase(key)) {
                hasLegacyKeys = true;
                break;
            }
        }
        return hasLegacyKeys ? section : null;
    }

    private static boolean matchesNbt(ItemStack item, ConfigurationSection nbtSection) {
        if (nbtSection == null || nbtSection.getKeys(false).isEmpty()) return true;

        try {
            NBTItem nbtItem = new NBTItem(item);
            for (String key : nbtSection.getKeys(false)) {
                String expected = nbtSection.getString(key, "");
                if (!nbtItem.hasKey(key)) return false;
                if (!expected.equals(nbtItem.getString(key))) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
