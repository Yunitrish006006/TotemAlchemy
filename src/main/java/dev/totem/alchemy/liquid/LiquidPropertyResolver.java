package dev.totem.alchemy.liquid;

import dev.totem.alchemy.mixture.LiquidComposition;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves one deterministic weighted property set from a liquid composition.
 *
 * <p>Unknown liquid ids participate as neutral properties instead of being ignored, so an unregistered
 * component cannot accidentally amplify known-liquid modifiers by shrinking the weighting denominator.</p>
 */
public final class LiquidPropertyResolver {
    private LiquidPropertyResolver() {
    }

    public static LiquidProperties resolve(LiquidComposition composition) {
        return resolve(composition, id -> AlchemyLiquids.get(id)
                .map(AlchemyLiquid::properties)
                .orElse(null));
    }

    static LiquidProperties resolve(
            LiquidComposition composition,
            Function<Identifier, LiquidProperties> propertiesById
    ) {
        Objects.requireNonNull(propertiesById, "propertiesById");
        if (composition == null || composition.isEmpty()) {
            return LiquidProperties.neutral();
        }

        LiquidComposition normalized = composition.normalized();
        if (normalized.isEmpty()) {
            return LiquidProperties.neutral();
        }

        double totalWeight = 0.0D;
        double stability = 0.0D;
        double reactionSpeed = 0.0D;
        double duration = 0.0D;
        double potency = 0.0D;

        for (var entry : normalized.components().entrySet()) {
            double weight = entry.getValue();
            if (weight <= LiquidComposition.DEFAULT_EPSILON) {
                continue;
            }
            LiquidProperties properties = propertiesById.apply(entry.getKey());
            if (properties == null) {
                properties = LiquidProperties.neutral();
            }

            totalWeight += weight;
            stability += properties.stabilityMultiplier() * weight;
            reactionSpeed += properties.reactionSpeedMultiplier() * weight;
            duration += properties.durationMultiplier() * weight;
            potency += properties.potencyMultiplier() * weight;
        }

        if (totalWeight <= LiquidComposition.DEFAULT_EPSILON) {
            return LiquidProperties.neutral();
        }

        return new LiquidProperties(
                stability / totalWeight,
                reactionSpeed / totalWeight,
                duration / totalWeight,
                potency / totalWeight
        );
    }
}
