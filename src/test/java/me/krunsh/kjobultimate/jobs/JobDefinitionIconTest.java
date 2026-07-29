package me.krunsh.kjobultimate.jobs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JobDefinitionIconTest {

    @Test
    public void loadsMaterialDataAndExactCitFromJob() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("icon.material", "IRON_PICKAXE");
        yaml.set("icon.data", 7);
        yaml.set("icon.nbt_cit", "job_Mineur-icon_v2");

        JobDefinition.JobIcon icon = JobDefinition.fromConfig("mineur", yaml).getIcon();
        assertEquals("IRON_PICKAXE", icon.getMaterial());
        assertEquals(7, icon.getData());
        assertEquals("job_Mineur-icon_v2", icon.getCit());
        assertTrue(icon.isConfigured());
    }

    @Test
    public void acceptsCitAliasAndMissingIcon() {
        YamlConfiguration withAlias = new YamlConfiguration();
        withAlias.set("icon.cit", "farmer-icon");
        assertEquals("farmer-icon", JobDefinition.fromConfig("farmer", withAlias).getIcon().getCit());

        JobDefinition.JobIcon missing = JobDefinition.fromConfig("plain", new YamlConfiguration()).getIcon();
        assertFalse(missing.isConfigured());
    }
}
