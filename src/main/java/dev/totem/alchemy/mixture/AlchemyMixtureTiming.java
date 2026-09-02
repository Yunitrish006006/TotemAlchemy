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

        if (mixture.hasCompletedStages()) {
            State worst = State.PERFECT;
            for (AlchemyMixtureState.CompletedStage stage : mixture.completedStages()) {
                worst = worse(worst, classify(stage));
            }
            if (mixture.stability() <= 15) {
                return State.BADLY_OVERDONE;
            }
            if (mixture.stability() <= 35) {
                return worse(worst, State.OVERDONE);
            }
            return worst;
        }

        int overcookTicks = mixture.overcookTicks();
        int stability = mixture.stability();
        if (overcookTicks <= mixture.perfectWindowTicks()) {
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
        int damagingTicks = overcookTicks - mixture.perfectWindowTicks();
        if (damagingTicks <= MILD_OVERCOOK_TICKS && stability >= 70) {
            return State.SLIGHTLY_OVERDONE;
        }
        if (damagingTicks <= STRONG_OVERCOOK_TICKS && stability > 35) {
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

    public static State classify(AlchemyMixtureState.Reaction reaction) {
        if (reaction == null) {
            return State.EMPTY;
        }
        int progress = (int) Math.min(1_000L,
                (long) reaction.elapsedTicks() * 1_000L / reaction.requiredTicks());
        if (progress < STARTED_THRESHOLD_PER_MILLE) {
            return State.JUST_STARTED;
        }
        if (progress < WORKING_THRESHOLD_PER_MILLE) {
            return State.WORKING;
        }
        if (progress < ALMOST_THRESHOLD_PER_MILLE) {
            return State.ALMOST_READY;
        }
        return State.NEARLY_READY;
    }

    public static State classify(AlchemyMixtureState.CompletedStage stage) {
        if (stage == null) {
            return State.EMPTY;
        }
        if (stage.overcookTicks() <= stage.perfectWindowTicks()) {
            return State.PERFECT;
        }
        int damagingTicks = stage.damagingTicks();
        if (damagingTicks <= MILD_OVERCOOK_TICKS) {
            return State.SLIGHTLY_OVERDONE;
        }
        if (damagingTicks <= STRONG_OVERCOOK_TICKS) {
            return State.OVERDONE;
        }
        return State.BADLY_OVERDONE;
    }

    public static int visualSignature(AlchemyMixtureState mixture) {
        if (mixture == null) {
            return 0;
        }
        int signature = 31 + Boolean.hashCode(mixture.isHeatLockedAfterBottling());
        for (AlchemyMixtureState.Reaction reaction : mixture.reactions()) {
            signature = 31 * signature + reaction.id().hashCode();
            signature = 31 * signature + classify(reaction).ordinal();
        }
        for (AlchemyMixtureState.CompletedStage stage : mixture.completedStages()) {
            signature = 31 * signature + stage.id().hashCode();
            signature = 31 * signature + classify(stage).ordinal();
        }
        return 31 * signature + classify(mixture).ordinal();
    }

    private static State worse(State left, State right) {
        return severity(left) >= severity(right) ? left : right;
    }

    private static int severity(State state) {
        return switch (state) {
            case EMPTY -> -1;
            case JUST_STARTED -> 0;
            case WORKING -> 1;
            case ALMOST_READY -> 2;
            case NEARLY_READY -> 3;
            case JUST_RIGHT, PERFECT -> 4;
            case SLIGHTLY_OVERDONE -> 5;
            case OVERDONE -> 6;
            case BADLY_OVERDONE -> 7;
        };
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
