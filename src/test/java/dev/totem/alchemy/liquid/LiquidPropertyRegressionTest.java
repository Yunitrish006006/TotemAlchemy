package dev.totem.alchemy.liquid;

import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class LiquidPropertyRegressionTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void builtInLiquidsAndRepresentativeMixturesRemainNeutralAtCurrentBalance() {
        for (AlchemyLiquid liquid : List.of(
                AlchemyLiquids.WATER,
                AlchemyLiquids.MILK,
                AlchemyLiquids.HONEY
        )) {
            assertSame(LiquidProperties.NEUTRAL, liquid.properties());
            assertSame(liquid, AlchemyLiquids.get(liquid.id()).orElseThrow());
        }

        for (LiquidComposition composition : List.of(
                LiquidComposition.single(AlchemyLiquids.WATER_ID, 1.0D),
                LiquidComposition.single(AlchemyLiquids.MILK_ID, 1.0D),
                LiquidComposition.single(AlchemyLiquids.HONEY_ID, 1.0D),
                LiquidComposition.of(Map.of(
                        AlchemyLiquids.WATER_ID, 1.0D,
                        AlchemyLiquids.MILK_ID, 1.0D,
                        AlchemyLiquids.HONEY_ID, 1.0D
                )),
                LiquidComposition.of(Map.of(
                        AlchemyLiquids.WATER_ID, 7.0D,
                        AlchemyLiquids.MILK_ID, 2.0D,
                        AlchemyLiquids.HONEY_ID, 1.0D
                ))
        )) {
            LiquidProperties resolved = LiquidPropertyResolver.resolve(composition);
            assertEquals(1.0D, resolved.stabilityMultiplier(), EPSILON);
            assertEquals(1.0D, resolved.reactionSpeedMultiplier(), EPSILON);
            assertEquals(1.0D, resolved.durationMultiplier(), EPSILON);
            assertEquals(1.0D, resolved.potencyMultiplier(), EPSILON);
        }
    }

    @Test
    void neutralResolvedPropertiesPreserveAllCurrentRuntimePolicyOutputs() {
        LiquidProperties resolved = LiquidPropertyResolver.resolve(
                LiquidComposition.of(Map.of(
                        AlchemyLiquids.WATER_ID, 2.0D,
                        AlchemyLiquids.MILK_ID, 3.0D,
                        AlchemyLiquids.HONEY_ID, 5.0D
                ))
        );

        LiquidStabilityPolicy.DamageStep stability =
                LiquidStabilityPolicy.scaleDamage(4, 0.25D, resolved);
        assertEquals(4, stability.wholeDamage());
        assertEquals(0.25D, stability.carry(), EPSILON);

        LiquidReactionSpeedPolicy.ReactionTiming timing =
                LiquidReactionSpeedPolicy.scale(30, 120, resolved);
        assertEquals(30, timing.elapsedTicks());
        assertEquals(120, timing.requiredTicks());

        AlchemyMixtureState.EffectDose dose =
                AlchemyMixtureState.EffectDose.fromDuration(600, 1);
        LiquidEffectPresentationPolicy.Presentation presentation =
                LiquidEffectPresentationPolicy.present(dose, 2, resolved);

        assertFalse(presentation.suppressed());
        assertEquals(dose.durationForVolume(2), presentation.minecraftDurationTicks());
        assertEquals(dose.amplifierCap(), presentation.minecraftAmplifier());
        assertEquals(1200.0D, dose.quantity(), EPSILON);
    }

    @Test
    void unknownLiquidWeightRemainsNeutralInRegressionBoundary() {
        LiquidComposition composition = LiquidComposition.of(Map.of(
                AlchemyLiquids.WATER_ID, 1.0D,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("other", "unknown"), 3.0D
        ));

        LiquidProperties resolved = LiquidPropertyResolver.resolve(composition);

        assertEquals(LiquidProperties.NEUTRAL, resolved);
    }
}
