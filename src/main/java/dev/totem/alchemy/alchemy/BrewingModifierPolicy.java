package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Deterministic Brewing Stand handling for vanilla-style potion modifiers.
 *
 * <p>These ingredients transform an existing potion/mixture rather than selecting a new alchemy outcome.
 * Fixed BrewingRecipe outputs are preserved directly. Stored/custom mixtures without a fixed recipe are
 * transformed in-place through their mixture state.</p>
 */
public final class BrewingModifierPolicy {
    private BrewingModifierPolicy() {}

    public static boolean isModifierIngredient(ItemStack ingredient) {
        return ingredient != null && !ingredient.isEmpty()
                && (ingredient.is(Items.REDSTONE)
                || ingredient.is(Items.GLOWSTONE_DUST)
                || ingredient.is(Items.GUNPOWDER)
                || ingredient.is(Items.DRAGON_BREATH));
    }

    public static boolean canApply(ItemStack input, ItemStack ingredient) {
        if (!isModifierIngredient(ingredient) || !AlchemyMixtureBottle.isPotionContainer(input)) {
            return false;
        }

        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(input);
        if (state.isEmpty()) {
            return false;
        }

        if (ingredient.is(Items.REDSTONE) || ingredient.is(Items.GLOWSTONE_DUST)) {
            return !state.effects().isEmpty();
        }
        if (ingredient.is(Items.GUNPOWDER)) {
            return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.DRINKABLE;
        }
        return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.SPLASH;
    }

    public static boolean isDeterministicBatch(
            ServerLevel level,
            ItemStack ingredient,
            Iterable<ItemStack> potionInputs
    ) {
        if (level == null || !isModifierIngredient(ingredient) || potionInputs == null) {
            return false;
        }

        boolean foundApplicableInput = false;
        for (ItemStack input : potionInputs) {
            if (input == null || input.isEmpty()) {
                continue;
            }

            if (AlchemyBrewing.recipe(level, input, ingredient).isPresent()) {
                foundApplicableInput = true;
                continue;
            }

            if (canApply(input, ingredient)) {
                foundApplicableInput = true;
            }
        }
        return foundApplicableInput;
    }

    public static ItemStack apply(
            ItemStack ingredient,
            ItemStack input,
            ItemStack fixedRecipeOutput
    ) {
        if (!isModifierIngredient(ingredient)
                || input == null
                || input.isEmpty()
                || fixedRecipeOutput == null
                || fixedRecipeOutput.isEmpty()) {
            return fixedRecipeOutput;
        }

        // Plain potions should preserve the fixed BrewingRecipe output exactly. Stored mixtures must keep
        // their serialized composition/effect state and therefore use the deterministic mixture transform.
        if (!AlchemyMixtureBottle.hasStoredMixture(input)
                && !ItemStack.matches(input, fixedRecipeOutput)) {
            return fixedRecipeOutput;
        }

        if (!canApply(input, ingredient)) {
            return fixedRecipeOutput;
        }

        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(input);
        if (ingredient.is(Items.REDSTONE)) {
            state.applyRedstoneModifier();
        } else if (ingredient.is(Items.GLOWSTONE_DUST)) {
            state.applyGlowstoneModifier();
        } else if (ingredient.is(Items.GUNPOWDER)) {
            state.setDeliveryForm(AlchemyMixtureState.DeliveryForm.SPLASH);
            state.addProvenance("modifier:minecraft:gunpowder");
        } else if (ingredient.is(Items.DRAGON_BREATH)) {
            state.setDeliveryForm(AlchemyMixtureState.DeliveryForm.LINGERING);
            state.addProvenance("modifier:minecraft:dragon_breath");
        }
        return AlchemyMixtureBottle.toPotion(state);
    }
}
