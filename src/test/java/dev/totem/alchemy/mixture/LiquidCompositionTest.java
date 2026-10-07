package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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
