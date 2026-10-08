package dev.totem.alchemy.mixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlchemyMixtureLiquidStabilityTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void stabilityDamageCarrySurvivesCopySplitMergeAndCodec() {
        AlchemyMixtureState state = AlchemyMixtureState.decode(
                "V|2\nS|80\nD|0.5\nB|1\n"
        );

        assertEquals(0.5D, state.stabilityDamageCarry(), EPSILON);
        assertEquals(0.5D, state.copy().stabilityDamageCarry(), EPSILON);

        AlchemyMixtureState extracted = state.extractUnits(1);
        assertEquals(1, state.volumeUnits());
        assertEquals(1, extracted.volumeUnits());
        assertEquals(0.5D, state.stabilityDamageCarry(), EPSILON);
        assertEquals(0.5D, extracted.stabilityDamageCarry(), EPSILON);

        String encoded = extracted.encode();
        assertTrue(encoded.lines().anyMatch(line -> line.equals("D|0.5")));

        AlchemyMixtureState restored = AlchemyMixtureState.decode(encoded);
        assertEquals(0.5D, restored.stabilityDamageCarry(), EPSILON);

        assertTrue(state.mergeFrom(restored));
        assertEquals(2, state.volumeUnits());
        assertEquals(78, state.stability());
        assertEquals(0.5D, state.stabilityDamageCarry(), EPSILON);
    }

    @Test
    void mergeUsesVolumeWeightedCarryWithoutDuplicatingSplitProgress() {
        AlchemyMixtureState oneUnit = AlchemyMixtureState.decode(
                "V|1\nS|90\nD|0.25\nB|1\n"
        );
        AlchemyMixtureState twoUnits = AlchemyMixtureState.decode(
                "V|2\nS|90\nD|0.75\nB|1\n"
        );

        assertTrue(oneUnit.mergeFrom(twoUnits));

        assertEquals(88, oneUnit.stability());
        assertEquals((0.25D + 0.75D * 2.0D) / 3.0D,
                oneUnit.stabilityDamageCarry(), EPSILON);
    }

    @Test
    void legacyCodecDefaultsCarryToZeroAndExplicitStabilityResetClearsIt() {
        AlchemyMixtureState legacy = AlchemyMixtureState.decode(
                "V|1\nS|80\nB|1\n"
        );
        assertEquals(0.0D, legacy.stabilityDamageCarry(), EPSILON);

        AlchemyMixtureState modern = AlchemyMixtureState.decode(
                "V|1\nS|80\nD|0.75\nB|1\n"
        );
        modern.setStability(65);

        assertEquals(65, modern.stability());
        assertEquals(0.0D, modern.stabilityDamageCarry(), EPSILON);
    }
}
