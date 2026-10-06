package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.reaction.AlchemyReactionDataLoader;
import dev.totem.alchemy.reaction.AlchemyReactionResolver;
import dev.totem.alchemy.reaction.IngredientReaction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
