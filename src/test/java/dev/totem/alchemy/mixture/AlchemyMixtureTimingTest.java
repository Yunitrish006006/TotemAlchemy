package dev.totem.alchemy.mixture;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void everyCompletedStageReceivesAScaledPerfectWindow() {
        assertEquals(100, AlchemyMixtureState.perfectWindowTicksForProcessing(20));
        assertEquals(100, AlchemyMixtureState.perfectWindowTicksForProcessing(400));
        assertEquals(200, AlchemyMixtureState.perfectWindowTicksForProcessing(800));
        assertEquals(300, AlchemyMixtureState.perfectWindowTicksForProcessing(2_000));

        AlchemyMixtureState state = activeMixture();
        state.addReaction(reaction("long-stage", 799, 800));
        assertTrue(state.tickReactions(1));
        assertEquals(200, state.perfectWindowTicks());
        assertEquals(AlchemyMixtureTiming.State.PERFECT, AlchemyMixtureTiming.classify(state));

        RandomSource random = RandomSource.create(1234L);
        assertTrue(state.tickCompletedStages(random, 200));
        assertEquals(100, state.stability());
        assertEquals(AlchemyMixtureTiming.State.PERFECT, AlchemyMixtureTiming.classify(state));

        assertTrue(state.tickCompletedStages(random, 1));
        assertEquals(100, state.stability());
        assertEquals(AlchemyMixtureTiming.State.SLIGHTLY_OVERDONE, AlchemyMixtureTiming.classify(state));
        assertTrue(state.tickCompletedStages(random, 19));
        assertEquals(99, state.stability());
    }

    @Test
    void legacyFinishedMixturesGainTheDefaultPerfectWindow() {
        AlchemyMixtureState legacy = AlchemyMixtureState.decode("V|1\nS|100\nB|1\nO|1\n");

        assertEquals(100, legacy.perfectWindowTicks());
        assertEquals(AlchemyMixtureTiming.State.PERFECT, AlchemyMixtureTiming.classify(legacy));
    }

    @Test
    void completedStageTimersSurviveTheMixtureCodec() {
        AlchemyMixtureState state = activeMixture();
        state.addReaction(reaction("saved-stage", 399, 400));
        state.tickReactions(1);
        state.tickCompletedStages(RandomSource.create(9L), 42);

        AlchemyMixtureState restored = AlchemyMixtureState.decode(state.encode());
        AlchemyMixtureState.CompletedStage stage = restored.completedStages().iterator().next();
        assertEquals("saved-stage", stage.id());
        assertEquals(42, stage.overcookTicks());
        assertEquals(100, stage.perfectWindowTicks());
        assertEquals(AlchemyMixtureTiming.State.PERFECT, AlchemyMixtureTiming.classify(stage));
    }

    @Test
    void bottlingLockChangesTheSynchronizedHudSignature() {
        AlchemyMixtureState state = activeMixture();
        state.putEffect("minecraft:speed", 20.0D * 180.0D, 0);
        int before = AlchemyMixtureTiming.visualSignature(state);

        state.lockHeatIfFinished();

        assertNotEquals(before, AlchemyMixtureTiming.visualSignature(state));
    }

    @Test
    void bottlingFinishedPotionDiscardsCompletedIngredientHistoryOnly() {
        AlchemyMixtureState state = activeMixture();
        state.addReaction(reaction("finished-stage", 1_000, 1_000));
        state.tickReactions(1);
        state.putEffect("minecraft:speed", 20.0D * 180.0D, 0);
        assertTrue(state.hasCompletedStages());

        state.lockHeatIfFinished();

        assertTrue(state.isHeatLockedAfterBottling());
        assertTrue(state.completedStages().isEmpty());
        assertTrue(state.provenance().stream().noneMatch(marker -> marker.contains("minecraft:nether_wart")));
        assertTrue(state.effects().containsKey("minecraft:speed"));
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

    private static AlchemyMixtureState decoded(int damagingTicks, int stability) {
        int perfectWindowTicks = AlchemyMixtureState.perfectWindowTicksForProcessing(
                AlchemyMixtureState.DEFAULT_REACTION_TICKS);
        return AlchemyMixtureState.decode("V|1\nS|" + stability + "\nB|1\nO|"
                + (perfectWindowTicks + damagingTicks) + "\nW|" + perfectWindowTicks + "\n");
    }
}
