package dev.totem.alchemy.mixture;

/**
 * Converts exact reaction progress into deliberately qualitative feedback for player-facing UI.
 *
 * <p>Pending mixtures are classified from their slowest reaction. This keeps the displayed state honest
 * when several ingredients are reacting at different speeds without exposing exact timings.</p>
 */
public final class AlchemyMixtureTiming {
    private static final int STARTED_THRESHOLD_PER_MILLE = 200;
    private static final int WORKING_THRESHOLD_PER_MILLE = 550;
    private static final int ALMOST_THRESHOLD_PER_MILLE = 850;
    private static final int MILD_OVERCOOK_TICKS = 20 * 20;
    private static final int STRONG_OVERCOOK_TICKS = 20 * 65;

    private AlchemyMixtureTiming() {
    }

    public static State classify(AlchemyMixtureState mixture) {
        if (mixture == null || mixture.isEmpty()) {
            return State.EMPTY;
        }

        if (mixture.hasPendingReactions()) {
            int slowestProgress = slowestProgressPerMille(mixture);
            if (slowestProgress < STARTED_THRESHOLD_PER_MILLE) {
                return State.JUST_STARTED;
            }
            if (slowestProgress < WORKING_THRESHOLD_PER_MILLE) {
                return State.WORKING;
            }
            if (slowestProgress < ALMOST_THRESHOLD_PER_MILLE) {
                return State.ALMOST_READY;
            }
            return State.NEARLY_READY;
        }

        int overcookTicks = mixture.overcookTicks();
        int stability = mixture.stability();
        if (overcookTicks <= 0) {
            if (stability >= 95) {
                return State.PERFECT;
            }
            if (stability >= 70) {
                return State.JUST_RIGHT;
            }
            if (stability > 35) {
                return State.SLIGHTLY_OVERDONE;
            }
            return stability > 15 ? State.OVERDONE : State.BADLY_OVERDONE;
        }
        if (overcookTicks <= MILD_OVERCOOK_TICKS && stability >= 70) {
            return State.SLIGHTLY_OVERDONE;
        }
        if (overcookTicks <= STRONG_OVERCOOK_TICKS && stability > 35) {
            return State.OVERDONE;
        }
        return State.BADLY_OVERDONE;
    }

    static int slowestProgressPerMille(AlchemyMixtureState mixture) {
        int slowest = 1_000;
        boolean found = false;
        for (AlchemyMixtureState.Reaction reaction : mixture.reactions()) {
            int progress = (int) Math.min(1_000L,
                    (long) reaction.elapsedTicks() * 1_000L / reaction.requiredTicks());
            slowest = Math.min(slowest, progress);
            found = true;
        }
        return found ? slowest : 1_000;
    }

    public enum State {
        EMPTY("empty"),
        JUST_STARTED("just_started"),
        WORKING("working"),
        ALMOST_READY("almost_ready"),
        NEARLY_READY("nearly_ready"),
        JUST_RIGHT("just_right"),
        PERFECT("perfect"),
        SLIGHTLY_OVERDONE("slightly_overdone"),
        OVERDONE("overdone"),
        BADLY_OVERDONE("badly_overdone");

        private final String translationSuffix;

        State(String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        public String translationKey() {
            return "tooltip.deadrecall.alchemy.mixture.timing." + translationSuffix;
        }
    }
}
