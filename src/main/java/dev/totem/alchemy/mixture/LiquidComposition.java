package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable liquid composition value object.
 *
 * <p>M3-T01 deliberately preserves caller-provided component magnitudes. Normalization, epsilon cleanup,
 * and canonical ordering are added by M3-T02.</p>
 */
public final class LiquidComposition {
    private static final LiquidComposition EMPTY = new LiquidComposition(Map.of());

    private final Map<Identifier, Double> components;

    private LiquidComposition(Map<Identifier, Double> components) {
        Map<Identifier, Double> copy = new LinkedHashMap<>();
        Objects.requireNonNull(components, "components").forEach((liquidId, amount) -> {
            Identifier id = Objects.requireNonNull(liquidId, "liquid id");
            Double value = Objects.requireNonNull(amount, "liquid amount");
            if (!Double.isFinite(value) || value < 0.0D) {
                throw new IllegalArgumentException(
                        "Liquid amount for " + id + " must be finite and non-negative"
                );
            }
            copy.put(id, value);
        });
        this.components = Collections.unmodifiableMap(copy);
    }

    public static LiquidComposition empty() {
        return EMPTY;
    }

    public static LiquidComposition of(Map<Identifier, Double> components) {
        if (components == null || components.isEmpty()) {
            return EMPTY;
        }
        return new LiquidComposition(components);
    }

    public static LiquidComposition single(Identifier liquidId, double amount) {
        return of(Map.of(liquidId, amount));
    }

    public Map<Identifier, Double> components() {
        return components;
    }

    public double amount(Identifier liquidId) {
        return liquidId == null ? 0.0D : components.getOrDefault(liquidId, 0.0D);
    }

    public double totalAmount() {
        return components.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof LiquidComposition composition
                && components.equals(composition.components);
    }

    @Override
    public int hashCode() {
        return components.hashCode();
    }

    @Override
    public String toString() {
        return "LiquidComposition" + components;
    }
}
