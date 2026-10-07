package dev.totem.alchemy.reaction;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Read-only facade for Manual and Discovery consumers of the merged reaction registry.
 *
 * <p>This deliberately exposes registry truth without Brewing Stand batch state, fallback rolls, or other
 * runtime selection mechanics.</p>
 */
public final class AlchemyReactionReader {
    private AlchemyReactionReader() {}

    public static List<ReactionOutcome> outcomesForActivatedBase(ItemStack ingredient) {
        return resolveActivatedBase(ingredient)
                .map(IngredientReaction::outcomes)
                .orElseGet(List::of);
    }

    public static double outcomeProbability(String ingredientId, String potionId) {
        Identifier potion = Identifier.tryParse(potionId);
        if (potion == null) {
            return -1.0D;
        }
        Optional<IngredientReaction> reaction = resolveActivatedBase(itemStack(ingredientId));
        return reaction.map(value -> outcomeProbability(value, potion)).orElse(-1.0D);
    }

    public static double noEffectProbability(String ingredientId) {
        Optional<IngredientReaction> reaction = resolveActivatedBase(itemStack(ingredientId));
        return reaction.map(AlchemyReactionReader::noEffectProbability).orElse(-1.0D);
    }

    static double outcomeProbability(IngredientReaction reaction, Identifier potionId) {
        if (reaction == null || potionId == null) {
            return -1.0D;
        }
        return reaction.outcomes().stream()
                .filter(outcome -> outcome.resultPotionId().equals(potionId))
                .findFirst()
                .map(ReactionOutcome::chance)
                .orElse(-1.0D);
    }

    static double noEffectProbability(IngredientReaction reaction) {
        if (reaction == null) {
            return -1.0D;
        }
        double miss = 1.0D;
        for (ReactionOutcome outcome : reaction.outcomes()) {
            miss *= 1.0D - clampProbability(outcome.chance());
        }
        return miss;
    }

    private static Optional<IngredientReaction> resolveActivatedBase(ItemStack ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return Optional.empty();
        }
        return BrewingReactionContext.resolveLegacyActivated(ingredient);
    }

    private static ItemStack itemStack(String ingredientId) {
        Identifier id = Identifier.tryParse(ingredientId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || !BuiltInRegistries.ITEM.getKey(item).equals(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static double clampProbability(double probability) {
        if (!Double.isFinite(probability)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, probability));
    }
}
