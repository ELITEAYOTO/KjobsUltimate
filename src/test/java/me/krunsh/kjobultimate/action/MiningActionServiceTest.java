package me.krunsh.kjobultimate.action;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

import me.krunsh.kjobultimate.jobs.JobDefinition;

public final class MiningActionServiceTest {

    @Test
    public void resolvesOneExplicitPriorityChain() {
        JobDefinition job = job();

        Assert.assertEquals(
            100,
            MiningActionService.resolveAction(
                job,
                Material.STONE,
                3,
                "azurite",
                "rubis"
            ).getXp()
        );

        Assert.assertEquals(
            80,
            MiningActionService.resolveAction(
                job,
                Material.STONE,
                3,
                "unknown",
                "rubis"
            ).getXp()
        );

        Assert.assertEquals(
            20,
            MiningActionService.resolveAction(
                job,
                Material.STONE,
                3,
                null,
                null
            ).getXp()
        );

        Assert.assertEquals(
            5,
            MiningActionService.resolveAction(
                job,
                Material.STONE,
                5,
                null,
                null
            ).getXp()
        );
    }

    @Test
    public void glowingRedstoneUsesVanillaRedstoneFallback() {
        JobDefinition job = job();

        Assert.assertEquals(
            9,
            MiningActionService.resolveAction(
                job,
                Material.GLOWING_REDSTONE_ORE,
                0,
                null,
                null
            ).getXp()
        );
    }

    private static JobDefinition job() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("actions.KMINERAI:AZURITE.xp", 100);
        yaml.set("actions.KMINERAI_DROP:RUBIS.xp", 80);
        yaml.set("actions.STONE:3.xp", 20);
        yaml.set("actions.STONE.xp", 5);
        yaml.set("actions.REDSTONE_ORE.xp", 9);
        return JobDefinition.fromConfig("mineur", yaml);
    }
}
