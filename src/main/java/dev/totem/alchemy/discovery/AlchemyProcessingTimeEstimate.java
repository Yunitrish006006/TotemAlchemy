package dev.totem.alchemy.discovery;

import java.util.Optional;

/**
 * Server-derived, player-visible processing-time estimate. Low-confidence snapshots contain only a rounded
 * range, so the exact observed average is not disclosed to the client before the research is complete.
 */
public record AlchemyProcessingTimeEstimate(
        int samples,
        int accuracyPercent,
        int lowerTenths,
        int upperTenths
) {
    private static final String SNAPSHOT_PREFIX = "T|";

    public AlchemyProcessingTimeEstimate {
        if (samples <= 0) {
            throw new IllegalArgumentException("Processing-time samples must be positive");
        }
        if (accuracyPercent <= 0 || accuracyPercent > 100) {
            throw new IllegalArgumentException("Processing-time accuracy must be between 1 and 100");
        }
        if (lowerTenths < 0 || upperTenths < lowerTenths) {
            throw new IllegalArgumentException("Processing-time range must be non-negative and ordered");
        }
        if (accuracyPercent == 100 && lowerTenths != upperTenths) {
            throw new IllegalArgumentException("A fully accurate processing-time estimate must be exact");
        }
    }

    /** Builds the deterministic disclosure band for one player's persisted timing observations. */
    public static Optional<AlchemyProcessingTimeEstimate> fromObservations(int samples, int averageTicks) {
        if (samples <= 0 || averageTicks <= 0) {
            return Optional.empty();
        }
        DisclosureBand band = DisclosureBand.forSamples(samples);
        int observedTenths = Math.max(0, (int) Math.round(averageTicks / 2.0D));
        int centerTenths = roundToMultiple(observedTenths, band.roundingTenths());
        int lowerTenths = Math.max(0, centerTenths - band.marginTenths());
        int upperTenths = centerTenths + band.marginTenths();
        return Optional.of(new AlchemyProcessingTimeEstimate(
                samples, band.accuracyPercent(), lowerTenths, upperTenths));
    }

    /** New typed snapshot format: ingredient, sample count, accuracy, and derived range in tenths of a second. */
    public String encodeSnapshotEntry(String ingredientId) {
        if (ingredientId == null || ingredientId.isBlank() || ingredientId.indexOf('|') >= 0) {
            throw new IllegalArgumentException("Invalid processing-time ingredient id");
        }
        return SNAPSHOT_PREFIX + ingredientId + "|" + samples + "|" + accuracyPercent
                + "|" + lowerTenths + "|" + upperTenths;
    }

    /**
     * Parses the current derived-range format and the legacy {@code T|ingredient|samples|averageTicks} format.
     * Legacy averages are immediately reduced to the same visible disclosure band before reaching the UI.
     */
    public static Optional<SnapshotEntry> parseSnapshotEntry(String encoded) {
        if (encoded == null || !encoded.startsWith(SNAPSHOT_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = encoded.split("\\|", -1);
        try {
            if (parts.length == 6 && validIngredientId(parts[1])) {
                AlchemyProcessingTimeEstimate estimate = new AlchemyProcessingTimeEstimate(
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5])
                );
                return Optional.of(new SnapshotEntry(parts[1], estimate));
            }
            if (parts.length == 4 && validIngredientId(parts[1])) {
                return fromObservations(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))
                        .map(estimate -> new SnapshotEntry(parts[1], estimate));
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed or out-of-range snapshots are ignored instead of breaking the client research cache.
        }
        return Optional.empty();
    }

    public boolean exact() {
        return accuracyPercent == 100 && lowerTenths == upperTenths;
    }

    private static boolean validIngredientId(String ingredientId) {
        return ingredientId != null && !ingredientId.isBlank() && ingredientId.indexOf('|') < 0;
    }

    private static int roundToMultiple(int value, int multiple) {
        return multiple <= 1 ? value : (int) Math.round(value / (double) multiple) * multiple;
    }

    public record SnapshotEntry(String ingredientId, AlchemyProcessingTimeEstimate estimate) {
        public SnapshotEntry {
            if (!validIngredientId(ingredientId) || estimate == null) {
                throw new IllegalArgumentException("Invalid processing-time snapshot entry");
            }
        }
    }

    private record DisclosureBand(int minimumSamples, int accuracyPercent, int marginTenths, int roundingTenths) {
        private static final DisclosureBand[] BANDS = {
                new DisclosureBand(40, 100, 0, 1),
                new DisclosureBand(20, 85, 5, 5),
                new DisclosureBand(12, 70, 10, 5),
                new DisclosureBand(6, 50, 20, 10),
                new DisclosureBand(3, 35, 50, 20),
                new DisclosureBand(1, 20, 100, 50)
        };

        private static DisclosureBand forSamples(int samples) {
            for (DisclosureBand band : BANDS) {
                if (samples >= band.minimumSamples()) {
                    return band;
                }
            }
            throw new IllegalArgumentException("Processing-time samples must be positive");
        }
    }
}
