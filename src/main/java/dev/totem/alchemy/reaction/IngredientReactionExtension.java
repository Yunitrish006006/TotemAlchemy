package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Additive datapack extension for an existing ingredient reaction.
 *
 * <p>Extensions deliberately own only additional outcomes. Scalar reaction policy such as success chance,
 * effect yield, processing time, max dose, and Brewing Stand compatibility remains authoritative on the
 * target reaction.</p>
 */
public record IngredientReactionExtension(
        Identifier id,
        Identifier targetReactionId,
        List<ReactionOutcome> outcomes
) {
    public IngredientReactionExtension {
        id = Objects.requireNonNull(id, "id");
        targetReactionId = Objects.requireNonNull(targetReactionId, "targetReactionId");
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        if (outcomes.isEmpty()) {
            throw new IllegalArgumentException("Ingredient reaction extension must add at least one outcome");
        }

        Set<Identifier> uniqueOutcomeIds = new HashSet<>();
        for (ReactionOutcome outcome : outcomes) {
            Objects.requireNonNull(outcome, "outcome");
            if (!uniqueOutcomeIds.add(outcome.resultPotionId())) {
                throw new IllegalArgumentException(
                        "Duplicate extension outcome potion: " + outcome.resultPotionId()
                );
            }
        }
    }
}
