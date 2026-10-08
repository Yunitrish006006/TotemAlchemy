package dev.totem.alchemy.liquid;

/**
 * Converts base reaction timing into the timing captured by one pending reaction.
 *
 * <p>{@link LiquidProperties#reactionSpeedMultiplier()} is a speed multiplier: {@code 1.0} is neutral,
 * values above one shorten required time, and values below one lengthen it. Timing is captured when the
 * reaction is scheduled so later save/load, extraction, or mixture changes do not reinterpret existing progress.</p>
 */
public final class LiquidReactionSpeedPolicy {
    private LiquidReactionSpeedPolicy() {
    }

    public static ReactionTiming scale(
            int elapsedTicks,
            int requiredTicks,
            LiquidProperties properties
    ) {
        int baseRequired = Math.max(1, requiredTicks);
        int baseElapsed = Math.max(0, Math.min(baseRequired, elapsedTicks));
        LiquidProperties resolved = properties == null
                ? LiquidProperties.neutral()
                : properties;

        double adjustedRequiredValue = Math.ceil(baseRequired / resolved.reactionSpeedMultiplier());
        int adjustedRequired;
        if (!Double.isFinite(adjustedRequiredValue) || adjustedRequiredValue >= Integer.MAX_VALUE) {
            adjustedRequired = Integer.MAX_VALUE;
        } else {
            adjustedRequired = Math.max(1, (int) adjustedRequiredValue);
        }

        if (baseElapsed <= 0) {
            return new ReactionTiming(0, adjustedRequired);
        }
        if (baseElapsed >= baseRequired) {
            return new ReactionTiming(adjustedRequired, adjustedRequired);
        }

        double progress = baseElapsed / (double) baseRequired;
        int adjustedElapsed = Math.min(
                adjustedRequired,
                Math.max(0, (int) Math.floor(progress * adjustedRequired))
        );
        return new ReactionTiming(adjustedElapsed, adjustedRequired);
    }

    public record ReactionTiming(int elapsedTicks, int requiredTicks) {
        public ReactionTiming {
            requiredTicks = Math.max(1, requiredTicks);
            elapsedTicks = Math.max(0, Math.min(requiredTicks, elapsedTicks));
        }
    }
}
