package me.krunsh.kjobultimate.gui;

import org.bukkit.configuration.ConfigurationSection;

/** Selectionne exclusivement le filler propre au menu demande. */
public final class MenuFillerResolver {

    private MenuFillerResolver() {
    }

    public static ConfigurationSection resolve(ConfigurationSection menuSection) {
        return menuSection == null ? null : menuSection.getConfigurationSection("filler");
    }
}
