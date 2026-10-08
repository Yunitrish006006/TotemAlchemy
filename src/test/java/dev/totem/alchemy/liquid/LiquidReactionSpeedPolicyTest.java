package dev.totem.alchemy.liquid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiquidReactionSpeedPolicyTest {
    @Test
    void neutralSpeedPreservesReactionTiming() {
        LiquidReactionSpeedPolicy.ReactionTiming timing =
                LiquidReactionSpeedPolicy.scale(40, 100, LiquidProperties.neutral());

        assertEquals(40, timing.elapsedTicks());
        assertEquals(100, timing.requiredTicks());
    }

    @Test
    void speedAboveOneShortensRequiredTime() {
        LiquidProperties fast = new LiquidProperties(1.0D, 2.0D, 1.0D, 1.0D);

        LiquidReactionSpeedPolicy.ReactionTiming timing =
                LiquidReactionSpeedPolicy.scale(0, 101, fast);

        assertEquals(0, timing.elapsedTicks());
        assertEquals(51, timing.requiredTicks());
    }

    @Test
    void speedBelowOneLengthensRequiredTime() {
        LiquidProperties slow = new LiquidProperties(1.0D, 0.5D, 1.0D, 1.0D);

        LiquidReactionSpeedPolicy.ReactionTiming timing =
                LiquidReactionSpeedPolicy.scale(0, 100, slow);

        assertEquals(200, timing.requiredTicks());
    }

    @Test
    void resumedReactionPreservesProgressFraction() {
        LiquidProperties fast = new LiquidProperties(1.0D, 2.0D, 1.0D, 1.0D);

        LiquidReactionSpeedPolicy.ReactionTiming timing =
                LiquidReactionSpeedPolicy.scale(40, 100, fast);

        assertEquals(20, timing.elapsedTicks());
        assertEquals(50, timing.requiredTicks());
    }

    @Test
    void completedReactionRemainsCompleteAfterScaling() {
        LiquidProperties slow = new LiquidProperties(1.0D, 0.25D, 1.0D, 1.0D);

        LiquidReactionSpeedPolicy.ReactionTiming timing =
                LiquidReactionSpeedPolicy.scale(100, 100, slow);

        assertEquals(400, timing.elapsedTicks());
        assertEquals(400, timing.requiredTicks());
    }

    @Test
    void veryFastReactionStillRequiresAtLeastOneTick() {
        LiquidProperties fast = new LiquidProperties(1.0D, 10_000.0D, 1.0D, 1.0D);

        LiquidReactionSpeedPolicy.ReactionTiming timing =
                LiquidReactionSpeedPolicy.scale(0, 5, fast);

        assertEquals(1, timing.requiredTicks());
    }
}
