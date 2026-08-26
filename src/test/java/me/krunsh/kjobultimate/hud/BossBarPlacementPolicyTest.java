package me.krunsh.kjobultimate.hud;

import org.junit.Assert;
import org.junit.Test;

public final class BossBarPlacementPolicyTest {

    @Test
    public void absoluteAnchorNeverCrossesClientRemovalLimit() {
        Assert.assertEquals(
            -60.0D,
            BossBarPlacementPolicy.clampAbsoluteY(-60.0D),
            0.0D
        );
        Assert.assertEquals(
            -62.0D,
            BossBarPlacementPolicy.clampAbsoluteY(-200.0D),
            0.0D
        );
        Assert.assertEquals(
            -16.0D,
            BossBarPlacementPolicy.clampAbsoluteY(40.0D),
            0.0D
        );
        Assert.assertEquals(
            -60.0D,
            BossBarPlacementPolicy.clampAbsoluteY(Double.NaN),
            0.0D
        );
        Assert.assertFalse(
            BossBarPlacementPolicy.isKilledByClient(-62.0D)
        );
        Assert.assertTrue(
            BossBarPlacementPolicy.isKilledByClient(-64.01D)
        );
    }

    @Test
    public void anchorRemainsStableAcrossNormalPlayerHeights() {
        double anchorY =
            BossBarPlacementPolicy.clampAbsoluteY(-60.0D);
        double[] playerHeights = {
            5.0D,
            30.0D,
            64.0D,
            120.0D,
            250.0D
        };

        for (double playerY : playerHeights) {
            Assert.assertEquals(-60.0D, anchorY, 0.0D);
            Assert.assertTrue(playerY > anchorY);
            Assert.assertTrue(
                "ancre hors de la portée Dragon pour Y=" + playerY,
                BossBarPlacementPolicy.remainsInDragonRenderRange(
                    0.0D,
                    playerY,
                    0.0D,
                    12.0D,
                    anchorY,
                    0.0D
                )
            );
        }

        Assert.assertEquals(
            853.333D,
            BossBarPlacementPolicy.DRAGON_RENDER_DISTANCE,
            0.001D
        );
    }

    @Test
    public void absurdCameraHeightFallsOutsideTheRealClientRange() {
        Assert.assertFalse(
            BossBarPlacementPolicy.remainsInDragonRenderRange(
                0.0D,
                1000.0D,
                0.0D,
                0.0D,
                -60.0D,
                0.0D
            )
        );
    }

    @Test
    public void reanchorOnlyAfterConfiguredMovement() {
        Assert.assertTrue(
            BossBarPlacementPolicy.shouldReanchor(
                false,
                0.0D,
                -60.0D,
                0.0D,
                0.0D,
                -60.0D,
                0.0D,
                12.0D
            )
        );
        Assert.assertFalse(
            BossBarPlacementPolicy.shouldReanchor(
                true,
                0.0D,
                -60.0D,
                0.0D,
                11.99D,
                -60.0D,
                0.0D,
                12.0D
            )
        );
        Assert.assertTrue(
            BossBarPlacementPolicy.shouldReanchor(
                true,
                0.0D,
                -60.0D,
                0.0D,
                12.0D,
                -60.0D,
                0.0D,
                12.0D
            )
        );
    }

    @Test
    public void metadataPacketIsOnlyRequiredForRealChanges() {
        Assert.assertFalse(
            BossBarPlacementPolicy.requiresMetadataUpdate(
                true,
                150.0F,
                "Farmer",
                true,
                150.0F,
                "Farmer",
                true
            )
        );
        Assert.assertTrue(
            BossBarPlacementPolicy.requiresMetadataUpdate(
                true,
                150.0F,
                "Farmer",
                true,
                151.0F,
                "Farmer",
                true
            )
        );
        Assert.assertTrue(
            BossBarPlacementPolicy.requiresMetadataUpdate(
                true,
                150.0F,
                "Farmer",
                true,
                150.0F,
                "Mineur",
                true
            )
        );
    }

    @Test
    public void legacyAutoDragonConfigIsDetectedWithoutAmbiguousBranch() {
        Assert.assertTrue(
            BossBarPlacementPolicy.shouldMigrateLegacyDragonPlacement(
                "ENDER_DRAGON",
                "AUTO",
                false,
                true,
                true
            )
        );
        Assert.assertFalse(
            BossBarPlacementPolicy.shouldMigrateLegacyDragonPlacement(
                "ENDER_DRAGON",
                "AUTO",
                true,
                true,
                true
            )
        );
        Assert.assertFalse(
            BossBarPlacementPolicy.shouldMigrateLegacyDragonPlacement(
                "WITHER",
                "AUTO",
                false,
                true,
                true
            )
        );
    }
}
