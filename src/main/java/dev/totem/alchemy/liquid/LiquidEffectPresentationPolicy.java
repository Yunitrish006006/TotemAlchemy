package dev.totem.alchemy.liquid;

import dev.totem.alchemy.mixture.AlchemyMixtureState;

/**
 * Applies liquid duration/potency multipliers only to the portable Minecraft effect presentation.
 *
 * <p>The canonical {@link AlchemyMixtureState.EffectDose#quantity()} is never changed here. A value of
 * {@code 1.0} is neutral. Duration scales the rendered duration; potency scales the continuous
 * level-I-equivalent potency before it is discretized to a Minecraft amplifier.</p>
 */
public final class LiquidEffectPresentationPolicy {
    private static final double EPSILON = 1.0E-9D;

    private LiquidEffectPresentationPolicy() {
    }

    public static boolean isNeutral(LiquidProperties properties) {
        LiquidProperties resolved = properties == null
                ? LiquidProperties.neutral()
                : properties;
        return Double.compare(resolved.durationMultiplier(), 1.0D) == 0
                && Double.compare(resolved.potencyMultiplier(), 1.0D) == 0;
    }

    public static Presentation present(
            AlchemyMixtureState.EffectDose dose,
            int volumeUnits,
            LiquidProperties properties
    ) {
        if (dose == null || dose.quantity() <= EPSILON) {
            return Presentation.SUPPRESSED;
        }

        LiquidProperties resolved = properties == null
                ? LiquidProperties.neutral()
                : properties;
        double durationMultiplier = resolved.durationMultiplier();
        double potencyMultiplier = resolved.potencyMultiplier();
        if (durationMultiplier <= EPSILON || potencyMultiplier <= EPSILON) {
            return Presentation.SUPPRESSED;
        }

        int baseDuration = dose.durationForVolume(Math.max(1, volumeUnits));
        double renderedDuration = baseDuration * durationMultiplier;
        double renderedPotencyLevel = (dose.amplifierCap() + 1.0D) * potencyMultiplier;
        if (!Double.isFinite(renderedDuration) || renderedDuration <= EPSILON
                || !Double.isFinite(renderedPotencyLevel) || renderedPotencyLevel <= EPSILON) {
            return Presentation.SUPPRESSED;
        }

        int durationTicks = renderedDuration >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(1, (int) Math.round(renderedDuration));

        long roundedLevel = Math.round(renderedPotencyLevel);
        int potencyLevel = (int) Math.max(1L, Math.min(256L, roundedLevel));
        return new Presentation(false, renderedPotencyLevel, renderedDuration, durationTicks, potencyLevel - 1);
    }

    public record Presentation(
            boolean suppressed,
            double potencyLevel,
            double durationTicks,
            int minecraftDurationTicks,
            int minecraftAmplifier
    ) {
        private static final Presentation SUPPRESSED =
                new Presentation(true, 0.0D, 0.0D, 0, 0);

        public Presentation {
            potencyLevel = Math.max(0.0D, potencyLevel);
            durationTicks = Math.max(0.0D, durationTicks);
            minecraftDurationTicks = Math.max(0, minecraftDurationTicks);
            minecraftAmplifier = Math.max(0, minecraftAmplifier);
        }
    }
}
