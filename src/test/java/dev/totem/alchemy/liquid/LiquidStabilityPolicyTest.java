package dev.totem.alchemy.liquid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiquidStabilityPolicyTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void neutralMultiplierPreservesExistingDamageRate() {
        LiquidStabilityPolicy.DamageStep step = LiquidStabilityPolicy.scaleDamage(
                5, 0.0D, LiquidProperties.neutral());

        assertEquals(5, step.wholeDamage());
        assertEquals(0.0D, step.carry(), EPSILON);
    }

    @Test
    void fractionalMultiplierCarriesSubPointDamageAcrossEvents() {
        LiquidProperties halfDamage = new LiquidProperties(0.5D, 1.0D, 1.0D, 1.0D);

        LiquidStabilityPolicy.DamageStep first =
                LiquidStabilityPolicy.scaleDamage(1, 0.0D, halfDamage);
        LiquidStabilityPolicy.DamageStep second =
                LiquidStabilityPolicy.scaleDamage(1, first.carry(), halfDamage);

        assertEquals(0, first.wholeDamage());
        assertEquals(0.5D, first.carry(), EPSILON);
        assertEquals(1, second.wholeDamage());
        assertEquals(0.0D, second.carry(), EPSILON);
    }

    @Test
    void multiplierAboveOneCanProduceMultipleDamagePoints() {
        LiquidProperties amplifiedDamage = new LiquidProperties(1.5D, 1.0D, 1.0D, 1.0D);

        LiquidStabilityPolicy.DamageStep first =
                LiquidStabilityPolicy.scaleDamage(1, 0.0D, amplifiedDamage);
        LiquidStabilityPolicy.DamageStep second =
                LiquidStabilityPolicy.scaleDamage(1, first.carry(), amplifiedDamage);

        assertEquals(1, first.wholeDamage());
        assertEquals(0.5D, first.carry(), EPSILON);
        assertEquals(2, second.wholeDamage());
        assertEquals(0.0D, second.carry(), EPSILON);
    }

    @Test
    void zeroMultiplierPreventsNewStabilityDamageWithoutDiscardingExistingCarry() {
        LiquidProperties protectedLiquid = new LiquidProperties(0.0D, 1.0D, 1.0D, 1.0D);

        LiquidStabilityPolicy.DamageStep step =
                LiquidStabilityPolicy.scaleDamage(20, 0.75D, protectedLiquid);

        assertEquals(0, step.wholeDamage());
        assertEquals(0.75D, step.carry(), EPSILON);
    }
}
