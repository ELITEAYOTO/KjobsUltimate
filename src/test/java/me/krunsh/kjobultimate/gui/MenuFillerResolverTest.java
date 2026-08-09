package me.krunsh.kjobultimate.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MenuFillerResolverTest {

    @Test
    public void resolvesEachMenusOwnFiller() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("filler.material", "ROOT");
        yaml.set("confirm_leave.filler.material", "CONFIRM");
        yaml.set("settings.filler.material", "SETTINGS");

        assertEquals("ROOT", MenuFillerResolver.resolve(yaml).getString("material"));
        assertEquals("CONFIRM", MenuFillerResolver.resolve(
            yaml.getConfigurationSection("confirm_leave")).getString("material"));
        assertEquals("SETTINGS", MenuFillerResolver.resolve(
            yaml.getConfigurationSection("settings")).getString("material"));
    }

    @Test
    public void doesNotTreatMenuSectionAsFiller() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("menu.title", "Example");
        ConfigurationSection menu = yaml.getConfigurationSection("menu");
        assertNull(MenuFillerResolver.resolve(menu));
    }
}
