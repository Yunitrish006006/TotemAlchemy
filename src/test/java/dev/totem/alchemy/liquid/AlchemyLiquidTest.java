package dev.totem.alchemy.liquid;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlchemyLiquidTest {
    @Test
    void neutralPropertiesUseOneAsEveryMultiplier() {
        LiquidProperties neutral = LiquidProperties.neutral();

        assertSame(LiquidProperties.NEUTRAL, neutral);
        assertEquals(1.0D, neutral.stabilityMultiplier());
        assertEquals(1.0D, neutral.reactionSpeedMultiplier());
        assertEquals(1.0D, neutral.durationMultiplier());
        assertEquals(1.0D, neutral.potencyMultiplier());
    }

    @Test
    void liquidRetainsIdentityAndProperties() {
        Identifier id = Identifier.fromNamespaceAndPath("totem", "alchemy/milk");
        LiquidProperties properties = new LiquidProperties(1.15D, 0.8D, 1.25D, 0.9D);

        AlchemyLiquid liquid = new AlchemyLiquid(id, properties);

        assertEquals(id, liquid.id());
        assertEquals(properties, liquid.properties());
    }

    @Test
    void neutralFactoryUsesNeutralProperties() {
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", "water");

        AlchemyLiquid liquid = AlchemyLiquid.neutral(id);

        assertEquals(id, liquid.id());
        assertSame(LiquidProperties.NEUTRAL, liquid.properties());
    }

    @Test
    void liquidRejectsMissingIdentityOrProperties() {
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", "water");

        assertThrows(NullPointerException.class,
                () -> new AlchemyLiquid(null, LiquidProperties.neutral()));
        assertThrows(NullPointerException.class,
                () -> new AlchemyLiquid(id, null));
    }

    @Test
    void propertyValidationRejectsInvalidMultipliers() {
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidProperties(Double.NaN, 1.0D, 1.0D, 1.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidProperties(-0.01D, 1.0D, 1.0D, 1.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidProperties(1.0D, 0.0D, 1.0D, 1.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidProperties(1.0D, Double.POSITIVE_INFINITY, 1.0D, 1.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidProperties(1.0D, 1.0D, -0.01D, 1.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidProperties(1.0D, 1.0D, 1.0D, Double.NEGATIVE_INFINITY));
    }

    @Test
    void zeroCanRepresentCompleteStabilityOrEffectSuppression() {
        LiquidProperties properties = new LiquidProperties(0.0D, 1.0D, 0.0D, 0.0D);

        assertEquals(0.0D, properties.stabilityMultiplier());
        assertEquals(0.0D, properties.durationMultiplier());
        assertEquals(0.0D, properties.potencyMultiplier());
    }
}
