package dev.totem.alchemy.gametest;

import dev.totem.alchemy.reaction.AlchemyReactionDataLoader;
import dev.totem.alchemy.reaction.AlchemyReactionResolver;
import dev.totem.alchemy.reaction.IngredientReaction;
import dev.totem.alchemy.registry.AlchemyItems;
import dev.totem.alchemy.resource.AlchemyContentPackState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class TotemOnlyIngredientReactionMigrationGameTest {
    private static final Identifier AWKWARD =
            Identifier.fromNamespaceAndPath("totem", "alchemy/awkward");

    @GameTest(maxTicks = 20)
    public void totemOnlyIngredientRegistryFollowsTotemPackState(GameTestHelper helper) {
        long count = AlchemyReactionDataLoader.ingredientReactions().stream()
                .filter(reaction -> "totem_alchemy".equals(reaction.id().getNamespace()))
                .filter(reaction -> reaction.id().getPath().startsWith("totem/"))
                .count();

        List<ItemStack> ingredients = List.of(
                new ItemStack(AlchemyItems.COCOA_POWDER),
                new ItemStack(AlchemyItems.PIG_MANURE),
                new ItemStack(AlchemyItems.WOOD_ASH)
        );

        if (!AlchemyContentPackState.totemAlchemyEnabled()) {
            require(helper, count == 0,
                    "Totem-only ingredient reactions leaked while Totem Alchemy was disabled");
            for (ItemStack ingredient : ingredients) {
                require(helper, AlchemyReactionResolver.resolveIngredientReaction(AWKWARD, ingredient).isEmpty(),
                        "Disabled Totem Alchemy still resolves " + ingredient.getItem());
            }
            helper.succeed();
            return;
        }

        require(helper, count == 3,
                "Expected exactly three Totem-owned ingredient reactions, got " + count);

        IngredientReaction cocoa = AlchemyReactionResolver.resolveIngredientReaction(
                AWKWARD, new ItemStack(AlchemyItems.COCOA_POWDER)
        ).orElseThrow(() -> helper.assertionException("Cocoa Powder reaction was not loaded"));
        IngredientReaction manure = AlchemyReactionResolver.resolveIngredientReaction(
                AWKWARD, new ItemStack(AlchemyItems.PIG_MANURE)
        ).orElseThrow(() -> helper.assertionException("Pig Manure reaction was not loaded"));
        IngredientReaction ash = AlchemyReactionResolver.resolveIngredientReaction(
                AWKWARD, new ItemStack(AlchemyItems.WOOD_ASH)
        ).orElseThrow(() -> helper.assertionException("Wood Ash reaction was not loaded"));

        require(helper, cocoa.processingTicks() == 320 && cocoa.outcomes().size() == 3,
                "Cocoa Powder policy changed during pack migration");
        require(helper, manure.processingTicks() == 400 && manure.outcomes().size() == 3,
                "Pig Manure policy changed during pack migration");
        require(helper, ash.processingTicks() == 380 && ash.outcomes().size() == 3,
                "Wood Ash policy changed during pack migration");
        require(helper, cocoa.outcomes().stream().anyMatch(outcome ->
                        outcome.resultPotionId().equals(
                                Identifier.fromNamespaceAndPath("totem", "alchemy/saturation")
                        )),
                "Totem-owned Cocoa Powder saturation outcome was lost");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
