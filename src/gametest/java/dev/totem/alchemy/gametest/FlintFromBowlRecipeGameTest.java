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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/** Verifies the external datapack recipe and the Alchemy-owned Stone Bowl return. */
public final class FlintFromBowlRecipeGameTest {
    private static final Identifier RECIPE_ID = Identifier.fromNamespaceAndPath("deadrecall", "flint_from_bowl");

    @GameTest(maxTicks = 20)
    public void flintRecipeLoadsAndReturnsTheStoneBowl(GameTestHelper helper) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, RECIPE_ID);
        RecipeHolder<?> holder = helper.getLevel().getServer().getRecipeManager().byKey(key)
                .orElseThrow(() -> helper.assertionException("Missing Flint-from-Bowl datapack recipe"));
        if (!(holder.value() instanceof FlintFromBowlRecipe recipe)) {
            throw helper.assertionException("Flint-from-Bowl recipe did not use the TotemAlchemy serializer");
        }

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

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
