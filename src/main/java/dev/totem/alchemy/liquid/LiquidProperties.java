package dev.totem.alchemy.liquid;

/**
 * Immutable multipliers that describe how one registered Alchemy liquid influences mixture chemistry.
 *
 * <p>A value of {@code 1.0} is neutral. M8-T01 defines only the value contract; mixed-liquid
 * weighting and runtime application are introduced by later M8 tasks.</p>
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
