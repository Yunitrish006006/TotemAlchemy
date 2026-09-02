package dev.totem.alchemy.gametest;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureColor;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.UseRemainder;

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

    @GameTest(maxTicks = 40)
    public void largeFlaskShowsDosesAndEffectsAndNeverReturnsGlass(GameTestHelper helper) {
        AlchemyMixtureState mixture = new AlchemyMixtureState(3);
        mixture.setBaseActivated(true);
        mixture.putEffect("minecraft:speed", 20.0D * 180.0D * 3.0D, 0);
        ItemStack flask = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
        AlchemyMixtureBottle.writeState(flask, mixture);

        PotionContents contents = flask.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        boolean showsSpeed = false;
        for (var effect : contents.getAllEffects()) {
            showsSpeed |= effect.getEffect().equals(MobEffects.SPEED);
        }
        require(helper, showsSpeed, "Large flask did not expose its potion effects through PotionContents");
        require(helper, flask.isBarVisible() && flask.getBarWidth() == 13,
                "Full large flask did not expose a full vanilla amount bar");
        require(helper, !flask.has(DataComponents.USE_REMAINDER),
                "Filled large flask still carried a glass-bottle use remainder");

        flask.remove(DataComponents.POTION_CONTENTS);
        flask.set(DataComponents.USE_REMAINDER,
                new UseRemainder(new ItemStackTemplate(Items.GLASS_BOTTLE)));
        require(helper, AlchemyMixtureBottle.refreshPortablePresentation(flask)
                        && flask.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).hasEffects()
                        && !flask.has(DataComponents.USE_REMAINDER),
                "Legacy large flask did not migrate its effects and glass-bottle remainder");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, flask);
        for (int expected = 2; expected >= 0; expected--) {
            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            held.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            ItemStack result = held.finishUsingItem(player.level(), player);
            player.setItemInHand(InteractionHand.MAIN_HAND, result);
            require(helper, result.is(AlchemyItems.LARGE_POTION_FLASK),
                    "Drinking a large-flask dose replaced it with another container");
            require(helper, AlchemyMixtureBottle.storedMixture(result).volumeUnits() == expected,
                    "Large flask did not decrement to " + expected + " dose(s)");
            int expectedWidth = Math.round(13.0F * expected / AlchemyMixtureState.MAX_VOLUME_UNITS);
            require(helper, result.isBarVisible() == (expected > 0)
                            && result.getBarWidth() == expectedWidth,
                    "Large flask amount bar did not follow its remaining doses");
        }
        require(helper, countItem(player, Items.GLASS_BOTTLE) == 0,
                "Drinking the large flask returned a glass bottle");
        helper.succeed();
    }

    private static int countItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
