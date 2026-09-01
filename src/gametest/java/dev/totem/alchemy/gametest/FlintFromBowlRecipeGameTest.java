package dev.totem.alchemy.gametest;

import dev.totem.alchemy.recipe.FlintFromBowlRecipe;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/** Verifies the external datapack recipe and the Alchemy-owned Stone Bowl return. */
public final class FlintFromBowlRecipeGameTest {
    private static final Identifier FLINT_RECIPE_ID = Identifier.fromNamespaceAndPath("deadrecall", "flint_from_bowl");
    private static final Identifier COCOA_RECIPE_ID = Identifier.fromNamespaceAndPath("deadrecall", "cocoa_powder");

    @GameTest(maxTicks = 20)
    public void flintRecipeLoadsAndReturnsTheStoneBowl(GameTestHelper helper) {
        RecipeHolder<?> holder = recipe(helper, FLINT_RECIPE_ID, "Flint-from-Bowl");
        if (!(holder.value() instanceof FlintFromBowlRecipe recipe)) {
            throw helper.assertionException("Flint-from-Bowl recipe did not use the TotemAlchemy serializer");
        }

        requireBookDisplay(helper, recipe, "Flint-from-Bowl");

        CraftingInput input = CraftingInput.of(2, 1, List.of(
                new ItemStack(Items.GRAVEL), new ItemStack(AlchemyItems.STONE_BOWL)
        ));
        require(helper, recipe.matches(input, helper.getLevel()), "Gravel plus Stone Bowl did not match the flint recipe");

        ItemStack output = recipe.assemble(input);
        require(helper, output.is(Items.FLINT) && output.getCount() == 1,
                "Flint-from-Bowl recipe did not produce one flint");

        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        require(helper, remaining.get(0).isEmpty(), "Gravel unexpectedly produced a remaining item");
        require(helper, remaining.get(1).is(AlchemyItems.STONE_BOWL),
                "Flint-from-Bowl recipe did not return the Stone Bowl");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void cocoaPowderRecipeLoadsAsARecipeBookRecipe(GameTestHelper helper) {
        RecipeHolder<?> holder = recipe(helper, COCOA_RECIPE_ID, "Cocoa Powder");
        if (!(holder.value() instanceof CraftingRecipe recipe)) {
            throw helper.assertionException("Cocoa Powder recipe was not a normal crafting recipe");
        }

        requireBookDisplay(helper, recipe, "Cocoa Powder");

        CraftingInput input = CraftingInput.of(3, 1, List.of(
                new ItemStack(Items.COCOA_BEANS),
                new ItemStack(Items.SUGAR),
                new ItemStack(AlchemyItems.STONE_BOWL)
        ));
        require(helper, recipe.matches(input, helper.getLevel()),
                "Cocoa Beans, Sugar, and a Stone Bowl did not match the Cocoa Powder recipe");

        ItemStack output = recipe.assemble(input);
        require(helper, output.is(AlchemyItems.COCOA_POWDER) && output.getCount() == 1,
                "Cocoa Powder recipe did not produce one Bowl of Cocoa Powder");
        require(helper, recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
                "Cocoa Powder recipe unexpectedly returned an ingredient");
        helper.succeed();
    }

    private static RecipeHolder<?> recipe(GameTestHelper helper, Identifier id, String name) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
        return helper.getLevel().getServer().getRecipeManager().byKey(key)
                .orElseThrow(() -> helper.assertionException("Missing " + name + " datapack recipe"));
    }

    private static void requireBookDisplay(GameTestHelper helper, Recipe<?> recipe, String name) {
        require(helper, !recipe.isSpecial(), name + " recipe was marked special and hidden from the recipe book");
        require(helper, !recipe.placementInfo().isImpossibleToPlace(),
                name + " recipe had no usable recipe-book placement information");
        require(helper, !recipe.display().isEmpty(), name + " recipe had no recipe-book display");
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
