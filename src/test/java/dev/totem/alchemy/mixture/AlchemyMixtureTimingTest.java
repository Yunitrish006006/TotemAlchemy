package dev.totem.alchemy.mixture;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlchemyMixtureTimingTest {
    @Test
    void emptyMixtureHasNoTimingHint() {
        assertEquals(AlchemyMixtureTiming.State.EMPTY,
                AlchemyMixtureTiming.classify(AlchemyMixtureState.empty()));
    }

    @Test
    void pendingBoundariesRemainQualitative() {
        assertStage(199, 1_000, AlchemyMixtureTiming.State.JUST_STARTED);
        assertStage(200, 1_000, AlchemyMixtureTiming.State.WORKING);
        assertStage(549, 1_000, AlchemyMixtureTiming.State.WORKING);
        assertStage(550, 1_000, AlchemyMixtureTiming.State.ALMOST_READY);
        assertStage(849, 1_000, AlchemyMixtureTiming.State.ALMOST_READY);
        assertStage(850, 1_000, AlchemyMixtureTiming.State.NEARLY_READY);
        assertStage(999, 1_000, AlchemyMixtureTiming.State.NEARLY_READY);
    }

    @Test
    void pendingMixtureUsesItsSlowestReaction() {
        AlchemyMixtureState state = activeMixture();
        state.addReaction(reaction("fast", 900, 1_000));
        state.addReaction(reaction("slow", 300, 1_000));

        assertEquals(300, AlchemyMixtureTiming.slowestProgressPerMille(state));
        assertEquals(AlchemyMixtureTiming.State.WORKING, AlchemyMixtureTiming.classify(state));
    }

    @Test
    void completedMixtureDistinguishesJustRightFromPerfect() {
        AlchemyMixtureState justRight = activeMixture();
        justRight.setStability(94);
        AlchemyMixtureState perfect = activeMixture();
        perfect.setStability(95);

        assertEquals(AlchemyMixtureTiming.State.JUST_RIGHT, AlchemyMixtureTiming.classify(justRight));
        assertEquals(AlchemyMixtureTiming.State.PERFECT, AlchemyMixtureTiming.classify(perfect));
        assertEquals(AlchemyMixtureTiming.State.SLIGHTLY_OVERDONE,
                AlchemyMixtureTiming.classify(decoded(0, 69)));
        assertEquals(AlchemyMixtureTiming.State.OVERDONE,
                AlchemyMixtureTiming.classify(decoded(0, 35)));
        assertEquals(AlchemyMixtureTiming.State.BADLY_OVERDONE,
                AlchemyMixtureTiming.classify(decoded(0, 15)));
    }

    @Test
    void overcookBoundariesDistinguishMildStrongAndBadlyOverdone() {
        assertEquals(AlchemyMixtureTiming.State.SLIGHTLY_OVERDONE,
                AlchemyMixtureTiming.classify(decoded(1, 99)));
        assertEquals(AlchemyMixtureTiming.State.SLIGHTLY_OVERDONE,
                AlchemyMixtureTiming.classify(decoded(400, 80)));
        assertEquals(AlchemyMixtureTiming.State.OVERDONE,
                AlchemyMixtureTiming.classify(decoded(401, 79)));
        assertEquals(AlchemyMixtureTiming.State.OVERDONE,
                AlchemyMixtureTiming.classify(decoded(1_300, 36)));
        assertEquals(AlchemyMixtureTiming.State.BADLY_OVERDONE,
                AlchemyMixtureTiming.classify(decoded(1_301, 35)));
        assertEquals(AlchemyMixtureTiming.State.BADLY_OVERDONE,
                AlchemyMixtureTiming.classify(decoded(20, 35)));
    }

    private static void assertStage(int elapsed, int required, AlchemyMixtureTiming.State expected) {
        AlchemyMixtureState state = activeMixture();
        state.addReaction(reaction("test", elapsed, required));
        assertEquals(expected, AlchemyMixtureTiming.classify(state));
    }

    private static AlchemyMixtureState activeMixture() {
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.setBaseActivated(true);
        return state;
    }

    private static AlchemyMixtureState.Reaction reaction(String id, int elapsed, int required) {
        return new AlchemyMixtureState.Reaction(
                id, "minecraft:nether_wart", elapsed, required, 1,
                "minecraft:water", "minecraft:awkward", Map.of(), Map.of());
    }

    private static AlchemyMixtureState decoded(int overcookTicks, int stability) {
        return AlchemyMixtureState.decode("V|1\nS|" + stability + "\nB|1\nO|" + overcookTicks + "\n");
    }
}
