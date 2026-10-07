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

class ActivatedBaseCompositionTest {
    private static final Identifier AWKWARD = id("minecraft", "awkward");
    private static final Identifier MUSHROOM_BASE = id("totem", "alchemy/mushroom_base");

    @Test
    void copiesInputAndExposesStableUnitLookup() {
        Map<Identifier, Double> source = new LinkedHashMap<>();
        source.put(AWKWARD, 2.0D);
        source.put(MUSHROOM_BASE, 1.0D);

        ActivatedBaseComposition composition = ActivatedBaseComposition.of(source);
        source.put(AWKWARD, 99.0D);

        assertEquals(2.0D, composition.units(AWKWARD));
        assertEquals(1.0D, composition.units(MUSHROOM_BASE));
        assertEquals(0.0D, composition.units(id("totem", "missing")));
        assertFalse(composition.isEmpty());
    }

    @Test
    void componentsUseDeterministicBaseIdOrdering() {
        Map<Identifier, Double> source = new LinkedHashMap<>();
        source.put(MUSHROOM_BASE, 1.0D);
        source.put(AWKWARD, 2.0D);

        ActivatedBaseComposition composition = ActivatedBaseComposition.of(source);

        assertEquals(List.of(AWKWARD, MUSHROOM_BASE),
                composition.components().keySet().stream().toList());
    }

    @Test
    void emptyAndSingleFactoriesRemainValueObjects() {
        assertTrue(ActivatedBaseComposition.empty().isEmpty());
        assertEquals(ActivatedBaseComposition.empty(), ActivatedBaseComposition.of(Map.of()));

        ActivatedBaseComposition single = ActivatedBaseComposition.single(AWKWARD, 1.5D);
        assertEquals(Map.of(AWKWARD, 1.5D), single.components());
        assertEquals(1.5D, single.units(AWKWARD));
    }

    @Test
    void derivesTotalActivatedUnitsWithoutChangingComponents() {
        ActivatedBaseComposition composition = ActivatedBaseComposition.of(Map.of(
                AWKWARD, 2.0D,
                MUSHROOM_BASE, 0.5D
        ));

        assertEquals(2.5D, composition.totalUnits());
        assertEquals(2.0D, composition.units(AWKWARD));
        assertEquals(0.5D, composition.units(MUSHROOM_BASE));
        assertEquals(0.0D, ActivatedBaseComposition.empty().totalUnits());
    }

    @Test
    void rejectsNegativeOrNonFiniteUnits() {
        assertThrows(IllegalArgumentException.class, () ->
                ActivatedBaseComposition.single(AWKWARD, -0.01D));
        assertThrows(IllegalArgumentException.class, () ->
                ActivatedBaseComposition.single(AWKWARD, Double.NaN));
        assertThrows(IllegalArgumentException.class, () ->
                ActivatedBaseComposition.single(AWKWARD, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNullBaseIdsAndUnitValues() {
        Map<Identifier, Double> nullId = new LinkedHashMap<>();
        nullId.put(null, 1.0D);
        assertThrows(NullPointerException.class, () -> ActivatedBaseComposition.of(nullId));

        Map<Identifier, Double> nullUnits = new LinkedHashMap<>();
        nullUnits.put(AWKWARD, null);
        assertThrows(NullPointerException.class, () -> ActivatedBaseComposition.of(nullUnits));
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
