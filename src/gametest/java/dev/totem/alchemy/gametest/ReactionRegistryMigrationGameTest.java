package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.alchemy.BrewingStationPolicy;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.reaction.AlchemyReactionDataLoader;
import dev.totem.alchemy.reaction.AlchemyReactionResolver;
import dev.totem.alchemy.reaction.IngredientReaction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

public final class ReactionRegistryMigrationGameTest {
    private static final double EPSILON = 0.000_001D;
    private static final Identifier AWKWARD = id("totem", "alchemy/awkward");

    @GameTest(maxTicks = 20)
    public void legacyOutcomeCatalogLoadsFromReactionRegistry(GameTestHelper helper) {
        require(helper, AlchemyReactionDataLoader.ingredientReactions().size() == 53,
                "Expected 53 migrated ingredient reactions, got "
                        + AlchemyReactionDataLoader.ingredientReactions().size());

        IngredientReaction sugar = AlchemyReactionResolver.resolveIngredientReaction(
                AWKWARD,
                new ItemStack(Items.SUGAR)
        ).orElseThrow(() -> helper.assertionException("Sugar reaction was not loaded from registry data"));

        require(helper, sugar.processingTicks() == 300,
                "Sugar processing time was not migrated");
        require(helper, Math.abs(sugar.successChance() - 0.90D) < EPSILON,
                "Sugar processing success chance was not migrated");

        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        require(helper, Math.abs(VanillaBrewingChance.chanceFor(
                        new ItemStack(Items.SUGAR), List.of(awkward)) - sugar.successChance()) < EPSILON,
                "Brewing Stand success chance did not resolve from reaction data");

        BrewingStationPolicy.Decision vanillaDecision = BrewingStationPolicy.evaluate(
                helper.getLevel(), new ItemStack(Items.SUGAR), List.of(awkward));
        require(helper, vanillaDecision.nativeVanillaRecipe(),
                "Awkward + sugar was not classified as a native Minecraft Brewing Stand recipe");
        require(helper, Math.abs(vanillaDecision.baseChance() - 0.90D) < EPSILON,
                "Station policy lost the reaction-backed sugar base chance");
        require(helper, Math.abs(vanillaDecision.effectiveChance() - 1.0D) < EPSILON,
                "Native Minecraft Brewing Stand recipe did not reach 100% success");

        BrewingStationPolicy.Decision customDecision = BrewingStationPolicy.evaluate(
                helper.getLevel(), new ItemStack(Items.BROWN_MUSHROOM), List.of(awkward));
        require(helper, !customDecision.nativeVanillaRecipe(),
                "Custom-only brown mushroom chemistry was misclassified as native vanilla");
        require(helper, Math.abs(customDecision.effectiveChance() - 0.80D) < EPSILON,
                "Custom Brewing Stand chemistry did not preserve reaction success chance");

        require(helper, Math.abs(VanillaBrewingChance.chanceFor(new ItemStack(Items.SUGAR))
                        - VanillaBrewingChance.DEFAULT_SUCCESS_CHANCE) < EPSILON,
                "Bare ingredient lookup retained a hidden hard-coded sugar chance");

        VanillaBrewingChance.markUnstableMushroomBase(awkward);
        require(helper, Math.abs(VanillaBrewingChance.chanceFor(
                        new ItemStack(Items.SUGAR), List.of(awkward)) - 0.70D) < EPSILON,
                "Resolver-backed Brewing Stand chance did not preserve unstable-base penalty");
        require(helper, sugar.outcomes().size() == 3,
                "Sugar outcome set was not migrated");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:sugar", "minecraft:swiftness") - 0.94D) < EPSILON,
                "Sugar swiftness probability did not come from migrated reaction data");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:sugar", "minecraft:slowness") - 0.03D) < EPSILON,
                "Sugar slowness probability did not come from migrated reaction data");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:sugar", "totem:alchemy/saturation") - 0.03D) < EPSILON,
                "Sugar saturation probability did not come from migrated reaction data");
        helper.succeed();
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
