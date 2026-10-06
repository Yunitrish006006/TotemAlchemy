package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/** Immutable data model for a base + ingredient alchemy reaction. */
public record IngredientReaction(
        Identifier id,
        Identifier baseId,
        ReactionIngredient ingredient,
        double successChance,
        double effectYield,
        int maxDose,
        boolean brewingStandCompatible,
        List<ReactionOutcome> outcomes
) {
    public IngredientReaction {
        id = Objects.requireNonNull(id, "id");
        baseId = Objects.requireNonNull(baseId, "baseId");
        ingredient = Objects.requireNonNull(ingredient, "ingredient");
        if (!Double.isFinite(successChance) || successChance < 0.0D || successChance > 1.0D) {
            throw new IllegalArgumentException("Reaction success chance must be finite and within [0, 1]");
        }
        if (!Double.isFinite(effectYield) || effectYield <= 0.0D) {
            throw new IllegalArgumentException("Reaction effect yield must be finite and greater than zero");
        }
        if (maxDose < 1) {
            throw new IllegalArgumentException("Reaction max dose must be at least one");
        }
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
    }
}
