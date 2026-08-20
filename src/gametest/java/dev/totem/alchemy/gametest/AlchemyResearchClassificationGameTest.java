package dev.totem.alchemy.gametest;

import dev.totem.alchemy.discovery.AlchemyObservedFrequency;
import dev.totem.alchemy.discovery.AlchemyResearchTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AlchemyResearchClassificationGameTest {
    @GameTest(maxTicks = 20)
    public void observedFrequencyUsesStableBands(GameTestHelper helper) {
        require(helper, AlchemyObservedFrequency.classify(0.00D) == AlchemyObservedFrequency.VERY_RARE, "0% should be very rare");
        require(helper, AlchemyObservedFrequency.classify(0.099D) == AlchemyObservedFrequency.VERY_RARE, "below 10% should be very rare");
        require(helper, AlchemyObservedFrequency.classify(0.10D) == AlchemyObservedFrequency.RARE, "10% should be rare");
        require(helper, AlchemyObservedFrequency.classify(0.249D) == AlchemyObservedFrequency.RARE, "below 25% should be rare");
        require(helper, AlchemyObservedFrequency.classify(0.25D) == AlchemyObservedFrequency.OCCASIONAL, "25% should be occasional");
        require(helper, AlchemyObservedFrequency.classify(0.449D) == AlchemyObservedFrequency.OCCASIONAL, "below 45% should be occasional");
        require(helper, AlchemyObservedFrequency.classify(0.45D) == AlchemyObservedFrequency.COMMON, "45% should be common");
        require(helper, AlchemyObservedFrequency.classify(0.649D) == AlchemyObservedFrequency.COMMON, "below 65% should be common");
        require(helper, AlchemyObservedFrequency.classify(0.65D) == AlchemyObservedFrequency.FREQUENT, "65% should be frequent");
        require(helper, AlchemyObservedFrequency.classify(1.00D) == AlchemyObservedFrequency.FREQUENT, "100% should be frequent");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void masteryRequiresAccuracyAndEnoughSamples(GameTestHelper helper) {
        require(helper, AlchemyResearchTier.classify(5, 0.0D) != AlchemyResearchTier.MASTER,
                "Five lucky observations must not grant mastery");
        require(helper, AlchemyResearchTier.classify(40, 0.01D) == AlchemyResearchTier.MASTER,
                "Forty observations within 1% should grant mastery");
        require(helper, AlchemyResearchTier.classify(40, 0.05D) == AlchemyResearchTier.EXPERT,
                "Within 5% should be expert when sample count is sufficient");
        require(helper, AlchemyResearchTier.classify(40, 0.20D) == AlchemyResearchTier.FAMILIAR,
                "Within 20% should be familiar");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
