package dev.totem.alchemy.liquid;

/**
 * Converts raw stability damage into whole stability points while preserving fractional progress.
 *
 * <p>{@link LiquidProperties#stabilityMultiplier()} scales stability loss: {@code 1.0} is neutral,
 * {@code 0.0} prevents new stability damage, values below one reduce the loss rate, and values above
 * one increase it. Fractional damage is carried between events so sub-one multipliers remain effective
 * even when runtime damage arrives one point at a time.</p>
 */
public final class LiquidStabilityPolicy {
    private static final double EPSILON = 1.0E-12D;

    private LiquidStabilityPolicy() {
    }

    public static DamageStep scaleDamage(
            int rawDamage,
            double currentCarry,
            LiquidProperties properties
    ) {
        double carry = sanitizeCarry(currentCarry);
        if (rawDamage <= 0) {
            return new DamageStep(0, carry);
        }

        LiquidProperties resolved = properties == null
                ? LiquidProperties.neutral()
                : properties;
        double scaled = rawDamage * resolved.stabilityMultiplier();
        if (!Double.isFinite(scaled) || scaled >= Integer.MAX_VALUE) {
            return new DamageStep(Integer.MAX_VALUE, 0.0D);
        }

        double accumulated = carry + Math.max(0.0D, scaled);
        if (!Double.isFinite(accumulated) || accumulated >= Integer.MAX_VALUE) {
            return new DamageStep(Integer.MAX_VALUE, 0.0D);
        }

        int wholeDamage = (int) Math.floor(accumulated + EPSILON);
        double nextCarry = accumulated - wholeDamage;
        if (nextCarry < EPSILON) {
            nextCarry = 0.0D;
        } else if (nextCarry >= 1.0D) {
            // Floating-point guard: carry is always canonicalized to [0, 1).
            wholeDamage = Math.min(Integer.MAX_VALUE, wholeDamage + 1);
            nextCarry = 0.0D;
        }
        return new DamageStep(wholeDamage, nextCarry);
    }

    private static double sanitizeCarry(double carry) {
        if (!Double.isFinite(carry) || carry <= 0.0D) {
            return 0.0D;
        }
        return Math.min(Math.nextDown(1.0D), carry);
    }

    public record DamageStep(int wholeDamage, double carry) {
        public DamageStep {
            wholeDamage = Math.max(0, wholeDamage);
            carry = sanitizeCarry(carry);
        }
    }
}
