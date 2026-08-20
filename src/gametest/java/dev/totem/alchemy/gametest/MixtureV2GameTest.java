package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class MixtureV2GameTest {
    @GameTest(maxTicks = 20)
    public void materialSettingsControlReactionTimeAndStarters(GameTestHelper helper) {
        require(helper, BrewingMaterialSettings.processingTicks(Items.MAGMA_CREAM) == 460,
                "Magma cream did not load its data-driven 460 tick processing time");
        require(helper, BrewingMaterialSettings.isStarter(Items.NETHER_WART),
                "Nether wart was not loaded as a starter material");
        require(helper, BrewingMaterialSettings.isStarter(Items.RED_MUSHROOM),
                "Red mushroom was not loaded as a starter material");
        require(helper, !BrewingMaterialSettings.isStarter(Items.MAGMA_CREAM),
                "Magma cream was incorrectly treated as a starter material");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void effectMaterialWithoutStarterImmediatelyDestroysStability(GameTestHelper helper) {
        AlchemyMixtureState water = AlchemyMixtureBrewing.waterState(1);
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), water, new ItemStack(Items.MAGMA_CREAM)),
                "Direct effect ingredient could not be scheduled on an unstarted water base");
        require(helper, water.stability() == 0,
                "Effect ingredient without starter did not immediately set stability to zero");
        require(helper, water.hasProvenance("unstable:no_starter"),
                "Invalid starter sequence was not recorded in mixture provenance");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void layeredBrewingPreservesPreviouslyDiscoveredEffects(GameTestHelper helper) {
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        ItemStack first = dev.totem.alchemy.mixture.AlchemyMixtureBrewing.applyBrewingStandIngredient(
                new ItemStack(Items.MAGMA_CREAM),
                awkward,
                PotionContents.createItemStack(Items.POTION, Potions.STRENGTH),
                new MultiOutcomeBrewing.Outcome(Potions.STRENGTH, "message.deadrecall.alchemy.outcome.strength")
        );
        ItemStack second = dev.totem.alchemy.mixture.AlchemyMixtureBrewing.applyBrewingStandIngredient(
                new ItemStack(Items.SPIDER_EYE),
                first,
                PotionContents.createItemStack(Items.POTION, Potions.POISON),
                new MultiOutcomeBrewing.Outcome(Potions.POISON, "message.deadrecall.alchemy.outcome.poison")
        );
        AlchemyMixtureState result = AlchemyMixtureBottle.fromPotion(second);
        require(helper, result.effects().containsKey("minecraft:strength"),
                "Second ingredient erased the existing strength effect");
        require(helper, result.effects().containsKey("minecraft:poison"),
                "Second ingredient did not add its poison effect");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void gunpowderChangesDeliveryFormWithoutDeletingEffects(GameTestHelper helper) {
        ItemStack strength = PotionContents.createItemStack(Items.POTION, Potions.STRENGTH);
        AlchemyMixtureState stored = AlchemyMixtureBottle.fromPotion(strength);
        AlchemyMixtureBottle.writeState(strength, stored);

        ItemStack result = AlchemyMixtureBrewing.applyBrewingStandIngredient(
                new ItemStack(Items.GUNPOWDER),
                strength,
                PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRENGTH),
                null
        );
        AlchemyMixtureState mixture = AlchemyMixtureBottle.fromPotion(result);
        require(helper, result.is(Items.SPLASH_POTION),
                "Gunpowder did not produce a splash-potion container");
        require(helper, mixture.deliveryForm() == AlchemyMixtureState.DeliveryForm.SPLASH,
                "Gunpowder did not persist the splash delivery form");
        require(helper, mixture.effects().containsKey("minecraft:strength"),
                "Gunpowder deleted the existing potion effect");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void overcookingCrossesEachMutationThresholdOnlyOnce(GameTestHelper helper) {
        AlchemyMixtureState mixture = AlchemyMixtureBottle.fromPotion(
                PotionContents.createItemStack(Items.POTION, Potions.STRENGTH));
        mixture.setStability(36);
        RandomSource random = RandomSource.create(12345L);

        require(helper, mixture.tickOvercook(random, 20), "First overcook second did not update the mixture");
        require(helper, mixture.stability() == 35, "Overcook did not reduce stability by one point per second");
        require(helper, mixture.hasProvenance("mutation:35"), "35 stability mutation was not recorded");

        int provenanceSize = mixture.provenance().size();
        mixture.tickOvercook(random, 20);
        require(helper, mixture.provenance().size() == provenanceSize,
                "35 stability mutation fired more than once");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
