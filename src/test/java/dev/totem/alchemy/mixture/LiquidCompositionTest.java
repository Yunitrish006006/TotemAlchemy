package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidCompositionTest {
    private static final Identifier WATER = id("minecraft", "water");
    private static final Identifier MILK = id("minecraft", "milk");

    @Test
    void copiesInputAndExposesStableLookup() {
        Map<Identifier, Double> source = new LinkedHashMap<>();
        source.put(WATER, 2.0D);
        source.put(MILK, 1.0D);

        LiquidComposition composition = LiquidComposition.of(source);
        source.put(WATER, 99.0D);

        assertEquals(2.0D, composition.amount(WATER));
        assertEquals(1.0D, composition.amount(MILK));
        assertEquals(0.0D, composition.amount(id("minecraft", "honey")));
        assertEquals(3.0D, composition.totalAmount());
        assertFalse(composition.isEmpty());
    }

    @Test
    void preservesUnnormalizedMagnitudesForLaterNormalizationTask() {
        LiquidComposition composition = LiquidComposition.of(Map.of(
                WATER, 4.0D,
                MILK, 2.0D
        ));

        assertEquals(4.0D, composition.amount(WATER));
        assertEquals(2.0D, composition.amount(MILK));
        assertEquals(6.0D, composition.totalAmount());
    }

    @Test
    void componentsUseDeterministicLiquidIdOrdering() {
        Map<Identifier, Double> source = new LinkedHashMap<>();
        source.put(WATER, 2.0D);
        source.put(MILK, 1.0D);

        LiquidComposition composition = LiquidComposition.of(source);

        assertEquals(List.of(MILK, WATER), composition.components().keySet().stream().toList());
        assertEquals(2.0D, composition.amount(WATER));
        assertEquals(1.0D, composition.amount(MILK));
    }

    @Test
    void normalizedCompositionSumsToOneWithoutMutatingRawMagnitudes() {
        LiquidComposition raw = LiquidComposition.of(Map.of(
                WATER, 4.0D,
                MILK, 2.0D
        ));

        LiquidComposition normalized = raw.normalized();

        assertEquals(4.0D, raw.amount(WATER));
        assertEquals(2.0D, raw.amount(MILK));
        assertEquals(2.0D / 3.0D, normalized.amount(WATER), 0.000_000_001D);
        assertEquals(1.0D / 3.0D, normalized.amount(MILK), 0.000_000_001D);
        assertEquals(1.0D, normalized.totalAmount(), 0.000_000_001D);
        assertEquals(normalized, normalized.normalized());
    }

    @Test
    void normalizationPrunesComponentsAtOrBelowEpsilon() {
        LiquidComposition raw = LiquidComposition.of(Map.of(
                WATER, 1.0D,
                MILK, LiquidComposition.DEFAULT_EPSILON / 2.0D
        ));

        LiquidComposition normalized = raw.normalized();

        assertEquals(Map.of(WATER, 1.0D), normalized.components());
        assertEquals(LiquidComposition.empty(),
                LiquidComposition.of(Map.of(WATER, 0.0D, MILK, 0.0D)).normalized());
    }

    @Test
    void normalizationRejectsInvalidEpsilon() {
        LiquidComposition composition = LiquidComposition.single(WATER, 1.0D);

        assertThrows(IllegalArgumentException.class, () -> composition.normalized(-0.01D));
        assertThrows(IllegalArgumentException.class, () -> composition.normalized(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> composition.normalized(Double.POSITIVE_INFINITY));
    }

    @Test
    void emptyAndSingleFactoriesRemainValueObjects() {
        assertTrue(LiquidComposition.empty().isEmpty());
        assertEquals(LiquidComposition.empty(), LiquidComposition.of(Map.of()));

        LiquidComposition single = LiquidComposition.single(WATER, 1.5D);
        assertEquals(Map.of(WATER, 1.5D), single.components());
        assertEquals(1.5D, single.totalAmount());
    }

    @Test
    void rejectsNegativeOrNonFiniteAmounts() {
        assertThrows(IllegalArgumentException.class, () ->
                LiquidComposition.single(WATER, -0.01D));
        assertThrows(IllegalArgumentException.class, () ->
                LiquidComposition.single(WATER, Double.NaN));
        assertThrows(IllegalArgumentException.class, () ->
                LiquidComposition.single(WATER, Double.POSITIVE_INFINITY));
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
