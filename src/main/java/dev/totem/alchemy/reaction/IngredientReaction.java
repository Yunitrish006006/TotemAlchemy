package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable data model for a base + ingredient alchemy reaction. */
public record IngredientReaction(
        Identifier id,
        Identifier baseId,
        ReactionIngredient ingredient,
        double successChance,
        double effectYield,
        int processingTicks,
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
        if (processingTicks < 1) {
            throw new IllegalArgumentException("Reaction processing ticks must be at least one");
        }
        if (maxDose < 1) {
            throw new IllegalArgumentException("Reaction max dose must be at least one");
        }
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        Set<Identifier> uniqueOutcomeIds = new HashSet<>();
        for (ReactionOutcome outcome : outcomes) {
            Objects.requireNonNull(outcome, "outcome");
            if (!uniqueOutcomeIds.add(outcome.resultPotionId())) {
                throw new IllegalArgumentException(
                        "Duplicate reaction outcome potion: " + outcome.resultPotionId()
                );
            }
        }
    }
}
