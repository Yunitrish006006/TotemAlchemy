package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.alchemy.BrewingStationPolicy;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.reaction.AlchemyReactionDataLoader;
import dev.totem.alchemy.reaction.AlchemyReactionReader;
import dev.totem.alchemy.reaction.AlchemyReactionResolver;
import dev.totem.alchemy.reaction.IngredientReaction;
import dev.totem.alchemy.reaction.ReactionIngredient;
import dev.totem.alchemy.reaction.ReactionOutcome;
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

        BrewingStationPolicy.Decision bareDecision = BrewingStationPolicy.evaluate(
                helper.getLevel(), new ItemStack(Items.SUGAR), List.of());
        require(helper, Math.abs(bareDecision.baseChance()
                        - BrewingStationPolicy.DEFAULT_SUCCESS_CHANCE) < EPSILON,
                "Bare station lookup retained a hidden hard-coded sugar chance");

        VanillaBrewingChance.markUnstableMushroomBase(awkward);
        BrewingStationPolicy.Decision unstableDecision = BrewingStationPolicy.evaluate(
                helper.getLevel(), new ItemStack(Items.SUGAR), List.of(awkward));
        require(helper, Math.abs(unstableDecision.baseChance() - 0.70D) < EPSILON,
                "Resolver-backed Brewing Stand policy did not preserve unstable-base penalty");
        require(helper, sugar.outcomes().size() == 3,
                "Sugar outcome set was not migrated");
        require(helper, Math.abs(AlchemyReactionReader.outcomeProbability(
                        "minecraft:sugar", "minecraft:swiftness") - 0.94D) < EPSILON,
                "Merged reaction reader lost sugar swiftness truth");
        require(helper, Math.abs(AlchemyReactionReader.outcomeProbability(
                        "minecraft:sugar", "minecraft:slowness") - 0.03D) < EPSILON,
                "Merged reaction reader lost sugar slowness truth");
        require(helper, Math.abs(AlchemyReactionReader.outcomeProbability(
                        "minecraft:sugar", "totem:alchemy/saturation") - 0.03D) < EPSILON,
                "Merged reaction reader lost sugar saturation truth");
        require(helper, Math.abs(AlchemyReactionReader.noEffectProbability("minecraft:sugar")
                        - ((1.0D - 0.94D) * (1.0D - 0.03D) * (1.0D - 0.03D))) < EPSILON,
                "Merged reaction reader no-effect truth did not reflect the full outcome set");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:sugar", "minecraft:swiftness") - 0.94D) < EPSILON,
                "Sugar swiftness probability did not come from migrated reaction data");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:sugar", "minecraft:slowness") - 0.03D) < EPSILON,
                "Sugar slowness probability did not come from migrated reaction data");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:sugar", "totem:alchemy/saturation") - 0.03D) < EPSILON,
                "Sugar saturation probability did not come from migrated reaction data");

        MultiOutcomeBrewing.Outcome canonicalSugar = MultiOutcomeBrewing.canonicalOutcome(sugar);
        require(helper, canonicalSugar != null && canonicalSugar.potion().is(Potions.SWIFTNESS),
                "Deterministic Brewing Stand selection did not choose sugar's highest-chance swiftness outcome");

        MultiOutcomeBrewing.beginBatch(
                helper.getLevel().getRandom(),
                new ItemStack(Items.SUGAR),
                List.of(awkward),
                true
        );
        try {
            require(helper, MultiOutcomeBrewing.activeOutcomes().size() == 1
                            && MultiOutcomeBrewing.activeOutcomes().getFirst().potion().is(Potions.SWIFTNESS),
                    "Native Brewing Stand batch did not collapse sugar to one canonical swiftness outcome");
        } finally {
            MultiOutcomeBrewing.clearBatch();
        }

        MultiOutcomeBrewing.beginBatch(
                new ItemStack(Items.BROWN_MUSHROOM),
                List.of(awkward),
                0.0F,
                0.0F,
                0.999F
        );
        try {
            require(helper, MultiOutcomeBrewing.activeOutcomes().size() == 2,
                    "Probabilistic Alchemy outcome path no longer permits multiple selected outcomes");
        } finally {
            MultiOutcomeBrewing.clearBatch();
        }

        IngredientReaction priorityTie = new IngredientReaction(
                id("totem", "test/priority_tie"),
                AWKWARD,
                ReactionIngredient.item(id("minecraft", "sugar")),
                0.9D,
                1.0D,
                300,
                1,
                true,
                List.of(
                        new ReactionOutcome(id("minecraft", "slowness"), 0.5D, 1),
                        new ReactionOutcome(id("minecraft", "swiftness"), 0.5D, 2)
                )
        );
        require(helper, MultiOutcomeBrewing.canonicalOutcome(priorityTie).potion().is(Potions.SWIFTNESS),
                "Deterministic outcome tie-break did not prefer higher priority");

        IngredientReaction idTie = new IngredientReaction(
                id("totem", "test/id_tie"),
                AWKWARD,
                ReactionIngredient.item(id("minecraft", "sugar")),
                0.9D,
                1.0D,
                300,
                1,
                true,
                List.of(
                        new ReactionOutcome(id("minecraft", "swiftness"), 0.5D, 2),
                        new ReactionOutcome(id("minecraft", "leaping"), 0.5D, 2)
                )
        );
        require(helper, MultiOutcomeBrewing.canonicalOutcome(idTie).potion().is(Potions.LEAPING),
                "Deterministic outcome tie-break did not prefer lexicographically smaller potion id");
        helper.succeed();
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
