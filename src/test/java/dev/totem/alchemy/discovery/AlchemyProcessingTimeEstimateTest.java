package dev.totem.alchemy.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AlchemyProcessingTimeEstimateTest {
    @Test
    void everySampleThresholdUsesItsDocumentedAccuracyAndRange() {
        List<ExpectedBand> cases = List.of(
                new ExpectedBand(1, 20, 100, 300),
                new ExpectedBand(2, 20, 100, 300),
                new ExpectedBand(3, 35, 150, 250),
                new ExpectedBand(5, 35, 150, 250),
                new ExpectedBand(6, 50, 180, 220),
                new ExpectedBand(11, 50, 180, 220),
                new ExpectedBand(12, 70, 190, 210),
                new ExpectedBand(19, 70, 190, 210),
                new ExpectedBand(20, 85, 195, 205),
                new ExpectedBand(39, 85, 195, 205),
                new ExpectedBand(40, 100, 200, 200)
        );

        for (ExpectedBand expected : cases) {
            AlchemyProcessingTimeEstimate actual = AlchemyProcessingTimeEstimate
                    .fromObservations(expected.samples(), 400)
                    .orElseThrow();
            assertEquals(expected.samples(), actual.samples());
            assertEquals(expected.accuracyPercent(), actual.accuracyPercent());
            assertEquals(expected.lowerTenths(), actual.lowerTenths());
            assertEquals(expected.upperTenths(), actual.upperTenths());
            assertEquals(expected.accuracyPercent() == 100, actual.exact());
        }
    }

    @Test
    void lowConfidenceSnapshotContainsOnlyDerivedRangeNotExactAverage() {
        AlchemyProcessingTimeEstimate estimate = AlchemyProcessingTimeEstimate
                .fromObservations(1, 411)
                .orElseThrow();
        String encoded = estimate.encodeSnapshotEntry("minecraft:sugar");

        assertEquals("T|minecraft:sugar|1|20|100|300", encoded);
        assertFalse(encoded.contains("411"));
        assertEquals(estimate, AlchemyProcessingTimeEstimate.parseSnapshotEntry(encoded)
                .orElseThrow().estimate());
    }

    @Test
    void legacyExactAverageIsReducedToCurrentVisibleBandWhenParsed() {
        AlchemyProcessingTimeEstimate parsed = AlchemyProcessingTimeEstimate
                .parseSnapshotEntry("T|minecraft:sugar|3|411")
                .orElseThrow().estimate();

        assertEquals(3, parsed.samples());
        assertEquals(35, parsed.accuracyPercent());
        assertEquals(150, parsed.lowerTenths());
        assertEquals(250, parsed.upperTenths());
        assertFalse(parsed.exact());
    }

    @Test
    void fullAccuracyDisclosesOnlyTheRoundedTenthSecondEstimate() {
        AlchemyProcessingTimeEstimate estimate = AlchemyProcessingTimeEstimate
                .fromObservations(40, 411)
                .orElseThrow();

        assertEquals(100, estimate.accuracyPercent());
        assertEquals(206, estimate.lowerTenths());
        assertEquals(206, estimate.upperTenths());
        assertTrue(estimate.exact());
        assertEquals("T|minecraft:sugar|40|100|206|206",
                estimate.encodeSnapshotEntry("minecraft:sugar"));
    }

    @Test
    void shortDurationsClampTheLowConfidenceLowerBoundToZero() {
        AlchemyProcessingTimeEstimate estimate = AlchemyProcessingTimeEstimate
                .fromObservations(1, 20)
                .orElseThrow();

        assertEquals(0, estimate.lowerTenths());
        assertEquals(100, estimate.upperTenths());
    }

    @Test
    void missingAndMalformedTimingSnapshotsRemainUnrecorded() {
        assertTrue(AlchemyProcessingTimeEstimate.fromObservations(0, 400).isEmpty());
        assertTrue(AlchemyProcessingTimeEstimate.fromObservations(1, 0).isEmpty());
        assertTrue(AlchemyProcessingTimeEstimate.parseSnapshotEntry("T|minecraft:sugar|0|400").isEmpty());
        assertTrue(AlchemyProcessingTimeEstimate
                .parseSnapshotEntry("T|minecraft:sugar|1|100|100|300").isEmpty());
        assertTrue(AlchemyProcessingTimeEstimate.parseSnapshotEntry("not-a-timing-entry").isEmpty());
    }

    private record ExpectedBand(int samples, int accuracyPercent, int lowerTenths, int upperTenths) {
    }
}
