package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Optional;

public final class AlchemyBrewing {
    private AlchemyBrewing() {}

    public static Optional<RecipeHolder<BrewingRecipe>> recipe(ServerLevel level, ItemStack input,
                                                              ItemStack ingredient) {
        return level.recipeAccess().getRecipeFor(RecipeType.BREWING, new BrewingInput(input, ingredient), level);
    }

    public static boolean hasMix(ServerLevel level, ItemStack input, ItemStack ingredient) {
        return AlchemyMixtureBrewing.canApplyBrewingStandIngredient(input, ingredient)
                || recipe(level, input, ingredient).isPresent();
    }

    public static ItemStack mix(ServerLevel level, ItemStack ingredient, ItemStack input) {
        ItemStack output = recipe(level, input, ingredient)
                .map(holder -> holder.value().assemble(new BrewingInput(input, ingredient)))
                .orElseGet(input::copy);
        return preserveMixture(ingredient, input, output);
    }

    public static ItemStack preserveMixture(ItemStack ingredient, ItemStack input, ItemStack vanillaOutput) {
        if (input.isEmpty() || vanillaOutput.isEmpty()) return vanillaOutput;
        boolean layeredRule = AlchemyMixtureBrewing.canApplyBrewingStandIngredient(input, ingredient);
        if (!layeredRule && ItemStack.matches(input, vanillaOutput)) return vanillaOutput;
        ItemStack selectedOutput = MultiOutcomeBrewing.applyBatchOutcome(ingredient, input, vanillaOutput);
        ItemStack output = AlchemyMixtureBrewing.applyBrewingStandOutcomes(
                ingredient, input, selectedOutput, MultiOutcomeBrewing.activeOutcomes());
        if (output.isEmpty()) return input.copy();
        if (ingredient.is(Items.RED_MUSHROOM)
                && input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)
                && output.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD)) {
            VanillaBrewingChance.markUnstableMushroomBase(output);
        } else {
            VanillaBrewingChance.carryUnstableMushroomBase(input, output);
        }
        return output;
    }
}
