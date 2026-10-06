package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable data model for activating unactivated mixture units into a named alchemy base.
 *
 * <p>Liquid requirements are minimum composition fractions and are intentionally data-only;
 * matching against a full liquid composition belongs to the resolver layer.</p>
 */
public record BaseReaction(
        Identifier id,
        Map<Identifier, Double> minimumLiquidFractions,
        ReactionIngredient starter,
        Identifier resultBaseId,
        double successChance,
        double activationYield,
        boolean brewingStandCompatible,
        int priority
) {
    private static final double EPSILON = 1.0E-6D;

    public BaseReaction {
        id = Objects.requireNonNull(id, "id");
        starter = Objects.requireNonNull(starter, "starter");
        resultBaseId = Objects.requireNonNull(resultBaseId, "resultBaseId");

        if (!Double.isFinite(successChance) || successChance < 0.0D || successChance > 1.0D) {
            throw new IllegalArgumentException("Base reaction success chance must be finite and within [0, 1]");
        }
        if (!Double.isFinite(activationYield) || activationYield <= 0.0D) {
            throw new IllegalArgumentException("Base reaction activation yield must be finite and greater than zero");
        }

        Map<Identifier, Double> sorted = new LinkedHashMap<>();
        Objects.requireNonNull(minimumLiquidFractions, "minimumLiquidFractions").entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    Identifier liquidId = Objects.requireNonNull(entry.getKey(), "liquid id");
                    Double fraction = Objects.requireNonNull(entry.getValue(), "liquid fraction");
                    if (!Double.isFinite(fraction) || fraction <= 0.0D || fraction > 1.0D) {
                        throw new IllegalArgumentException(
                                "Minimum liquid fraction for " + liquidId + " must be within (0, 1]");
                    }
                    sorted.put(liquidId, fraction);
                });

        double totalMinimum = sorted.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalMinimum > 1.0D + EPSILON) {
            throw new IllegalArgumentException("Minimum liquid fractions cannot sum above 1.0");
        }
        minimumLiquidFractions = Collections.unmodifiableMap(sorted);
    }
}
