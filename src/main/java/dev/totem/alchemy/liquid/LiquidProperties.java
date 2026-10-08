package dev.totem.alchemy.liquid;

/**
 * Immutable multipliers that describe how one registered Alchemy liquid influences mixture chemistry.
 *
 * <p>A value of {@code 1.0} is neutral. The stability multiplier scales stability loss
 * ({@code 0.0} prevents new loss; values above one accelerate loss). The other multipliers
 * are applied by their dedicated M8 runtime tasks.</p>
 */
public record LiquidProperties(
        double stabilityMultiplier,
        double reactionSpeedMultiplier,
        double durationMultiplier,
        double potencyMultiplier
) {
    public static final LiquidProperties NEUTRAL =
            new LiquidProperties(1.0D, 1.0D, 1.0D, 1.0D);

    public LiquidProperties {
        requireFiniteNonNegative(stabilityMultiplier, "stabilityMultiplier");
        requireFinitePositive(reactionSpeedMultiplier, "reactionSpeedMultiplier");
        requireFiniteNonNegative(durationMultiplier, "durationMultiplier");
        requireFiniteNonNegative(potencyMultiplier, "potencyMultiplier");
    }

    public static LiquidProperties neutral() {
        return NEUTRAL;
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and greater than zero");
        }
    }
}
