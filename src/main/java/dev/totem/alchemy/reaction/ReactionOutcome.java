package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * One independently configured potion outcome for a reaction.
 *
 * <p>Chance is expressed as a normalized probability in the inclusive range [0, 1].
 * Priority is only used for deterministic station selection when chances tie.</p>
 */
public record ReactionOutcome(Identifier resultPotionId, double chance, int priority) {
    public ReactionOutcome {
        resultPotionId = Objects.requireNonNull(resultPotionId, "resultPotionId");
        if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            throw new IllegalArgumentException("Outcome chance must be finite and within [0, 1]");
        }
    }
}
