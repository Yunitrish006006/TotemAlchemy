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
    public void concurrentMaterialsKeepIndependentProgressAndPerfectWindows(GameTestHelper helper) {
        AlchemyMixtureState mixture = AlchemyMixtureBrewing.waterState(1);
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), mixture, new ItemStack(Items.NETHER_WART)),
                "First material could not start its reaction");
        mixture.tickReactions(60);
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), mixture, new ItemStack(Items.SUGAR)),
                "Second material could not start while the first material was still reacting");

        require(helper, mixture.reactions().size() == 2,
                "Concurrent materials were collapsed into one shared reaction");
        AlchemyMixtureState.Reaction wart = reactionFor(helper, mixture, "minecraft:nether_wart");
        AlchemyMixtureState.Reaction sugar = reactionFor(helper, mixture, "minecraft:sugar");
        require(helper, wart.elapsedTicks() == 60 && sugar.elapsedTicks() == 0,
                "The later material inherited the earlier material's progress");

        mixture.tickReactions(300);
        require(helper, mixture.completedStages().size() == 1 && mixture.reactions().size() == 1,
                "The shorter material did not finish independently");
        require(helper, "minecraft:sugar".equals(mixture.completedStages().iterator().next().ingredientId()),
                "The wrong concurrent material completed first");

        RandomSource random = RandomSource.create(5678L);
        require(helper, mixture.tickCompletedStages(random, 100),
                "Completed material's perfect window did not advance beside a pending material");
        mixture.tickReactions(100);
        require(helper, !mixture.hasPendingReactions() && mixture.completedStages().size() == 2,
                "Longer material did not complete on its own timer");
        require(helper, mixture.canonicalPotionId() == null,
                "Branched concurrent reactions incorrectly claimed one canonical potion identity");
        require(helper, mixture.stability() == 100,
                "A material lost stability while still inside its own perfect window");

        mixture.tickCompletedStages(random, 1);
        AlchemyMixtureState.CompletedStage sugarStage = mixture.completedStages().stream()
                .filter(stage -> "minecraft:sugar".equals(stage.ingredientId()))
                .findFirst()
                .orElseThrow(() -> helper.assertionException("Sugar stage disappeared after completion"));
        AlchemyMixtureState.CompletedStage wartStage = mixture.completedStages().stream()
                .filter(stage -> "minecraft:nether_wart".equals(stage.ingredientId()))
                .findFirst()
                .orElseThrow(() -> helper.assertionException("Nether-wart stage disappeared after completion"));
        require(helper, sugarStage.damagingTicks() == 1 && wartStage.damagingTicks() == 0,
                "Completed materials shared one overcook timer instead of retaining independent windows");
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

        require(helper, mixture.tickOvercook(random, mixture.perfectWindowTicks()),
                "Perfect extraction window did not advance");
        require(helper, mixture.stability() == 36,
                "Perfect extraction window damaged stability before it expired");
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

    private static AlchemyMixtureState.Reaction reactionFor(
            GameTestHelper helper,
            AlchemyMixtureState mixture,
            String ingredientId
    ) {
        return mixture.reactions().stream()
                .filter(reaction -> ingredientId.equals(reaction.ingredientId()))
                .findFirst()
                .orElseThrow(() -> helper.assertionException("Missing reaction for " + ingredientId));
    }
}
