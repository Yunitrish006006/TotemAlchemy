package dev.totem.alchemy.liquid;

import dev.totem.alchemy.mixture.LiquidComposition;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LiquidPropertyResolverTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void emptyOrNullCompositionResolvesNeutral() {
        assertSame(LiquidProperties.NEUTRAL, LiquidPropertyResolver.resolve(null));
        assertSame(LiquidProperties.NEUTRAL,
                LiquidPropertyResolver.resolve(LiquidComposition.empty()));
    }

    @Test
    void builtInNeutralLiquidsResolveNeutralAtAnyWeights() {
        LiquidComposition composition = LiquidComposition.of(Map.of(
                AlchemyLiquids.WATER_ID, 6.0D,
                AlchemyLiquids.MILK_ID, 3.0D,
                AlchemyLiquids.HONEY_ID, 1.0D
        ));

        LiquidProperties resolved = LiquidPropertyResolver.resolve(composition);

        assertEquals(1.0D, resolved.stabilityMultiplier(), EPSILON);
        assertEquals(1.0D, resolved.reactionSpeedMultiplier(), EPSILON);
        assertEquals(1.0D, resolved.durationMultiplier(), EPSILON);
        assertEquals(1.0D, resolved.potencyMultiplier(), EPSILON);
    }

    @Test
    void weightedResolutionUsesNormalizedCompositionFractions() {
        Identifier water = AlchemyLiquids.WATER_ID;
        Identifier milk = AlchemyLiquids.MILK_ID;
        Identifier honey = AlchemyLiquids.HONEY_ID;
        LiquidComposition composition = LiquidComposition.of(Map.of(
                water, 2.0D,
                milk, 1.0D,
                honey, 1.0D
        ));

        LiquidProperties resolved = LiquidPropertyResolver.resolve(composition, id -> {
            if (id.equals(water)) return new LiquidProperties(1.0D, 1.0D, 1.0D, 1.0D);
            if (id.equals(milk)) return new LiquidProperties(2.0D, 0.5D, 1.5D, 0.75D);
            if (id.equals(honey)) return new LiquidProperties(0.5D, 2.0D, 0.5D, 1.5D);
            return null;
        });

        assertEquals(1.125D, resolved.stabilityMultiplier(), EPSILON);
        assertEquals(1.125D, resolved.reactionSpeedMultiplier(), EPSILON);
        assertEquals(1.0D, resolved.durationMultiplier(), EPSILON);
        assertEquals(1.0625D, resolved.potencyMultiplier(), EPSILON);
    }

    @Test
    void unknownLiquidsContributeNeutralWeightInsteadOfBeingIgnored() {
        Identifier known = Identifier.fromNamespaceAndPath("totem", "alchemy/test_known");
        Identifier unknown = Identifier.fromNamespaceAndPath("other", "unknown");
        LiquidComposition composition = LiquidComposition.of(Map.of(
                known, 1.0D,
                unknown, 1.0D
        ));

        LiquidProperties resolved = LiquidPropertyResolver.resolve(
                composition,
                id -> id.equals(known)
                        ? new LiquidProperties(3.0D, 2.0D, 0.5D, 1.5D)
                        : null
        );

        assertEquals(2.0D, resolved.stabilityMultiplier(), EPSILON);
        assertEquals(1.5D, resolved.reactionSpeedMultiplier(), EPSILON);
        assertEquals(0.75D, resolved.durationMultiplier(), EPSILON);
        assertEquals(1.25D, resolved.potencyMultiplier(), EPSILON);
    }

    @Test
    void canonicalCompositionOrderMakesResolutionStable() {
        Identifier a = Identifier.fromNamespaceAndPath("totem", "a");
        Identifier b = Identifier.fromNamespaceAndPath("totem", "b");
        LiquidProperties aProperties = new LiquidProperties(0.5D, 2.0D, 1.25D, 0.75D);
        LiquidProperties bProperties = new LiquidProperties(1.5D, 0.5D, 0.75D, 1.25D);

        LiquidProperties first = LiquidPropertyResolver.resolve(
                LiquidComposition.of(new java.util.LinkedHashMap<>(Map.of(a, 3.0D, b, 1.0D))),
                id -> id.equals(a) ? aProperties : bProperties
        );
        LiquidProperties second = LiquidPropertyResolver.resolve(
                LiquidComposition.of(new java.util.LinkedHashMap<>(Map.of(b, 1.0D, a, 3.0D))),
                id -> id.equals(a) ? aProperties : bProperties
        );

        assertEquals(first, second);
    }
}
