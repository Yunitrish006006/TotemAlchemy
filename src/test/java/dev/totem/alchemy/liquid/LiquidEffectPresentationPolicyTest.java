package dev.totem.alchemy.liquid;

import dev.totem.alchemy.mixture.AlchemyMixtureState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidEffectPresentationPolicyTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void neutralPropertiesPreserveExistingPortablePresentation() {
        AlchemyMixtureState.EffectDose dose =
                AlchemyMixtureState.EffectDose.fromDuration(600, 1);

        LiquidEffectPresentationPolicy.Presentation presentation =
                LiquidEffectPresentationPolicy.present(dose, 2, LiquidProperties.neutral());

        assertFalse(presentation.suppressed());
        assertEquals(dose.durationForVolume(2), presentation.minecraftDurationTicks());
        assertEquals(dose.amplifierCap(), presentation.minecraftAmplifier());
    }

    @Test
    void durationMultiplierChangesRenderedDurationOnly() {
        AlchemyMixtureState.EffectDose dose =
                AlchemyMixtureState.EffectDose.fromDuration(600, 0);
        LiquidProperties properties = new LiquidProperties(1.0D, 1.0D, 1.5D, 1.0D);

        LiquidEffectPresentationPolicy.Presentation presentation =
                LiquidEffectPresentationPolicy.present(dose, 1, properties);

        assertEquals(900.0D, presentation.durationTicks(), EPSILON);
        assertEquals(900, presentation.minecraftDurationTicks());
        assertEquals(1.0D, presentation.potencyLevel(), EPSILON);
        assertEquals(0, presentation.minecraftAmplifier());
        assertEquals(600.0D, dose.quantity(), EPSILON);
    }

    @Test
    void potencyMultiplierChangesRenderedPotencyOnly() {
        AlchemyMixtureState.EffectDose dose =
                AlchemyMixtureState.EffectDose.fromDuration(600, 0);
        LiquidProperties properties = new LiquidProperties(1.0D, 1.0D, 1.0D, 2.0D);

        LiquidEffectPresentationPolicy.Presentation presentation =
                LiquidEffectPresentationPolicy.present(dose, 1, properties);

        assertEquals(2.0D, presentation.potencyLevel(), EPSILON);
        assertEquals(1, presentation.minecraftAmplifier());
        assertEquals(600.0D, presentation.durationTicks(), EPSILON);
        assertEquals(600, presentation.minecraftDurationTicks());
        assertEquals(600.0D, dose.quantity(), EPSILON);
    }

    @Test
    void zeroDurationOrPotencySuppressesRenderedEffectWithoutDeletingDose() {
        AlchemyMixtureState.EffectDose dose =
                AlchemyMixtureState.EffectDose.fromDuration(400, 0);

        assertTrue(LiquidEffectPresentationPolicy.present(
                dose, 1, new LiquidProperties(1.0D, 1.0D, 0.0D, 1.0D)
        ).suppressed());
        assertTrue(LiquidEffectPresentationPolicy.present(
                dose, 1, new LiquidProperties(1.0D, 1.0D, 1.0D, 0.0D)
        ).suppressed());
        assertEquals(400.0D, dose.quantity(), EPSILON);
    }

    @Test
    void nonNeutralPresentationNeverMutatesCanonicalEffectDose() {
        AlchemyMixtureState.EffectDose dose =
                new AlchemyMixtureState.EffectDose(2400.0D, 2);
        double quantityBefore = dose.quantity();
        int amplifierBefore = dose.amplifierCap();

        LiquidEffectPresentationPolicy.present(
                dose,
                3,
                new LiquidProperties(1.0D, 1.0D, 0.75D, 1.5D)
        );

        assertEquals(quantityBefore, dose.quantity(), EPSILON);
        assertEquals(amplifierBefore, dose.amplifierCap());
    }
}
