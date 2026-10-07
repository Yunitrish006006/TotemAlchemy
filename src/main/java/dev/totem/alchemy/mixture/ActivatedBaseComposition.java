package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable activated-base composition value object.
 *
 * <p>Entries store absolute activated-base units by base identifier. M4-T01 deliberately does not derive
 * aggregate activated units, concentration, or mixture-state compatibility behavior.</p>
 */
public final class ActivatedBaseComposition {
    private static final ActivatedBaseComposition EMPTY = new ActivatedBaseComposition(Map.of());

    private final Map<Identifier, Double> components;

    private ActivatedBaseComposition(Map<Identifier, Double> components) {
        Map<Identifier, Double> copy = new LinkedHashMap<>();
        Objects.requireNonNull(components, "components").entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    Identifier id = Objects.requireNonNull(entry.getKey(), "base id");
                    Double value = Objects.requireNonNull(entry.getValue(), "activated base units");
                    if (!Double.isFinite(value) || value < 0.0D) {
                        throw new IllegalArgumentException(
                                "Activated base units for " + id + " must be finite and non-negative"
                        );
                    }
                    copy.put(id, value);
                });
        this.components = Collections.unmodifiableMap(copy);
    }

    public static ActivatedBaseComposition empty() {
        return EMPTY;
    }

    public static ActivatedBaseComposition of(Map<Identifier, Double> components) {
        if (components == null || components.isEmpty()) {
            return EMPTY;
        }
        return new ActivatedBaseComposition(components);
    }

    public static ActivatedBaseComposition single(Identifier baseId, double units) {
        return of(Map.of(baseId, units));
    }

    public Map<Identifier, Double> components() {
        return components;
    }

    public double units(Identifier baseId) {
        return baseId == null ? 0.0D : components.getOrDefault(baseId, 0.0D);
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ActivatedBaseComposition composition
                && components.equals(composition.components);
    }

    @Override
    public int hashCode() {
        return components.hashCode();
    }

    @Override
    public String toString() {
        return "ActivatedBaseComposition" + components;
    }
}
