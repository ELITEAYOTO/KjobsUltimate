package me.krunsh.kjobultimate.hud;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class HudConfigCompletenessTest {

    @Test
    public void dragonBossBarUsesUnambiguousBelowWorldAnchor() throws Exception {
        InputStream stream =
            HudConfigCompletenessTest.class
                .getClassLoader()
                .getResourceAsStream("hud.yml");

        Assert.assertNotNull("hud.yml absent du jar", stream);

        YamlConfiguration yaml =
            YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
            );

        Assert.assertEquals(
            "ENDER_DRAGON",
            yaml.getString("bossbar.entity_type")
        );
        Assert.assertTrue(
            yaml.getBoolean("bossbar.invisible_entity")
        );
        Assert.assertEquals(
            -60.0D,
            yaml.getDouble("bossbar.placement.dragon_absolute_y"),
            0.001D
        );
        Assert.assertEquals(
            12.0D,
            yaml.getDouble("bossbar.placement.dragon_reanchor_distance"),
            0.001D
        );
        Assert.assertFalse(
            yaml.isSet("bossbar.placement.dragon_distance")
        );
        Assert.assertFalse(
            yaml.isSet("bossbar.placement.dragon_vertical_offset")
        );
        Assert.assertTrue(
            yaml.getString("actionbar.format")
                .contains("{autosell_suffix}")
        );
        Assert.assertFalse(
            yaml.getString("actionbar.autosell_only_format")
                .trim().isEmpty()
        );
        Assert.assertEquals(
            2,
            yaml.getInt("actionbar.currency_decimals")
        );
    }
}
