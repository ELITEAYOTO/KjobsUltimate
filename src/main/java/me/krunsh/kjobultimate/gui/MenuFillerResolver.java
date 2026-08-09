package me.krunsh.kjobultimate.gui;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Résout les fillers d'un menu.
 *
 * Formats pris en charge :
 *
 * Ancien format :
 * filler:
 *   material: STAINED_GLASS_PANE
 *
 * Nouveau format :
 * fillers:
 *   border:
 *     material: STAINED_GLASS_PANE
 *   holes:
 *     clear: true
 *     slots: "10,11"
 */
public final class MenuFillerResolver {

    private MenuFillerResolver() {
    }

    /**
     * Compatibilité avec l'ancien appel : retourne filler: uniquement.
     */
    public static ConfigurationSection resolve(
            ConfigurationSection menuSection) {
        return menuSection == null
            ? null
            : menuSection.getConfigurationSection("filler");
    }

    /**
     * Retourne l'ancien filler puis les fillers nommés, dans l'ordre YAML.
     */
    public static List<ConfigurationSection> resolveAll(
            ConfigurationSection menuSection) {

        if (menuSection == null) {
            return Collections.emptyList();
        }

        List<ConfigurationSection> result =
            new ArrayList<ConfigurationSection>();

        ConfigurationSection legacy =
            menuSection.getConfigurationSection("filler");

        if (legacy != null) {
            result.add(legacy);
        }

        ConfigurationSection named =
            menuSection.getConfigurationSection("fillers");

        if (named != null) {
            for (String key : named.getKeys(false)) {
                ConfigurationSection child =
                    named.getConfigurationSection(key);

                if (child != null) {
                    result.add(child);
                }
            }
        }

        return result;
    }

    /**
     * Slots à ignorer pour un filler donné.
     */
    public static Set<Integer> excludedSlots(
            ConfigurationSection fillerSection) {

        LinkedHashSet<Integer> result =
            new LinkedHashSet<Integer>();

        appendSlots(
            result,
            fillerSection,
            "exclude_slots");

        appendSlots(
            result,
            fillerSection,
            "excluded_slots");

        // Alias pratique : dans un filler normal, remove_slots retire ces slots.
        appendSlots(
            result,
            fillerSection,
            "remove_slots");

        return result;
    }

    /**
     * Slots vidés après l'application de tous les fillers.
     */
    public static Set<Integer> clearSlots(
            ConfigurationSection menuSection) {

        LinkedHashSet<Integer> result =
            new LinkedHashSet<Integer>();

        appendSlots(
            result,
            menuSection,
            "clear_slots");

        appendSlots(
            result,
            menuSection,
            "remove_filler_slots");

        return result;
    }

    private static void appendSlots(
            Set<Integer> target,
            ConfigurationSection section,
            String key) {

        if (section == null || !section.contains(key)) {
            return;
        }

        appendSlots(
            target,
            section.get(key));
    }

    private static void appendSlots(
            Set<Integer> target,
            Object raw) {

        if (raw == null) return;

        if (raw instanceof Number) {
            target.add(
                Integer.valueOf(
                    ((Number) raw).intValue()));
            return;
        }

        if (raw instanceof List) {
            for (Object part : (List<?>) raw) {
                appendSlots(target, part);
            }
            return;
        }

        String value =
            String.valueOf(raw).trim();

        if (value.isEmpty()) return;

        String[] parts = value.split(",");

        for (String part : parts) {
            String token = part.trim();

            if (token.isEmpty()) continue;

            int dash = token.indexOf('-');

            if (dash > 0) {
                Integer start =
                    parseInt(
                        token.substring(0, dash).trim());

                Integer end =
                    parseInt(
                        token.substring(dash + 1).trim());

                if (start == null || end == null) {
                    continue;
                }

                int step = start.intValue()
                    <= end.intValue()
                        ? 1
                        : -1;

                for (int slot = start.intValue();
                        slot != end.intValue() + step;
                        slot += step) {
                    target.add(Integer.valueOf(slot));
                }
            } else {
                Integer slot = parseInt(token);

                if (slot != null) {
                    target.add(slot);
                }
            }
        }
    }

    private static Integer parseInt(
            String value) {

        try {
            return Integer.valueOf(
                Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}