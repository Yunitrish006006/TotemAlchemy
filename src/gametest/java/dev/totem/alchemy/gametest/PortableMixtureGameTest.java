package dev.totem.alchemy.gametest;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureColor;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PortableMixtureGameTest {
    @GameTest(maxTicks = 20)
    public void threeDoseStateCanBeDrainedOneDrinkAtATime(GameTestHelper helper) {
        AlchemyMixtureState flask = new AlchemyMixtureState(3);
        flask.putEffect("minecraft:speed", 20.0D * 180.0D * 3.0D, 0);

        AlchemyMixtureState first = flask.extractUnits(1);
        require(helper, first.volumeUnits() == 1 && flask.volumeUnits() == 2,
                "First drink did not leave two doses");
        AlchemyMixtureState second = flask.extractUnits(1);
        require(helper, second.volumeUnits() == 1 && flask.volumeUnits() == 1,
                "Second drink did not leave one dose");
        AlchemyMixtureState third = flask.extractUnits(1);
        require(helper, third.volumeUnits() == 1 && flask.isEmpty(),
                "Third drink did not empty the container");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void waterBucketCustomDataPreservesFullMixture(GameTestHelper helper) {
        AlchemyMixtureState mixture = new AlchemyMixtureState(3);
        mixture.setBaseActivated(true);
        mixture.putEffect("minecraft:strength", 20.0D * 180.0D * 3.0D, 0);
        mixture.putEffect("minecraft:poison", 20.0D * 45.0D * 3.0D, 0);

        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        AlchemyMixtureBottle.writeState(bucket, mixture);
        AlchemyMixtureState restored = AlchemyMixtureBottle.storedMixture(bucket);

        require(helper, restored.volumeUnits() == 3, "Potion bucket lost its three-dose volume");
        require(helper, restored.effects().containsKey("minecraft:strength"),
                "Potion bucket lost strength");
        require(helper, restored.effects().containsKey("minecraft:poison"),
                "Potion bucket lost poison");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void visibleColorMixesActualEffectColors(GameTestHelper helper) {
        AlchemyMixtureState mixture = new AlchemyMixtureState(3);
        mixture.putEffect("minecraft:speed", 3000.0D, 0);
        mixture.putEffect("minecraft:poison", 3000.0D, 0);
        int color = AlchemyMixtureColor.rgb(mixture);
        require(helper, color != AlchemyMixtureColor.WATER_RGB,
                "Effect mixture was still rendered as plain water");
        require(helper, color == AlchemyMixtureColor.rgb(mixture.copy()),
                "Mixture color calculation was not deterministic");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
