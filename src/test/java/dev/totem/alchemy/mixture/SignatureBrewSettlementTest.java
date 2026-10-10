package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewSettlementTest {
    private static final Identifier SIGNATURE = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");
    private static final String SUGAR = "brew:sugar";
    private static final String COCOA = "brew:cocoa";

    private static SignatureBrewDefinition.Result result() {
        return new SignatureBrewDefinition.Result(
                SignatureBrewDefinition.Type.BOTTLED_ITEM,
                Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa"),
                1,
                Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"),
                Identifier.fromNamespaceAndPath("totem", "alchemy/saturation"));
    }

    private static AlchemyMixtureState.Reaction reaction(
            String id, String ingredient, int elapsed, int required, String ordinaryEffect
    ) {
        return new AlchemyMixtureState.Reaction(
                id, ingredient, elapsed, required, 3, null, null,
                Map.of(), Map.of(ordinaryEffect, new AlchemyMixtureState.EffectDose(400.0D, 0)));
    }

    private static AlchemyMixtureState state() {
        var state = new AlchemyMixtureState(3, 8);
        state.addReaction(reaction(SUGAR, "minecraft:sugar", 0, 20, "minecraft:speed"));
        state.addReaction(reaction(COCOA, "totem:alchemy/cocoa_powder", 0, 40, "minecraft:strength"));
        assertTrue(state.replaceSignatureGroups(List.of(
                new SignatureBrewResolver.ReactionGroup(SIGNATURE, List.of(SUGAR, COCOA)))));
        return state;
    }

    @Test
    void committedGroupHoldsOrdinaryOutputsUntilEveryMemberFinishes() {
        var state = state();
        assertTrue(state.commitSignatureGroup(SIGNATURE, result()));
        assertTrue(state.signatureGroups().isEmpty());
        assertFalse(state.commitSignatureGroup(SIGNATURE, result()));

        state.tickReactions(20);
        assertEquals(1, state.reactions().size());
        assertTrue(state.effects().isEmpty());
        assertFalse(state.hasProvenance("reaction:minecraft:sugar"));
        assertTrue(state.completedStages().isEmpty());
        assertTrue(state.claimSignatureResult(SIGNATURE).isEmpty());

        state.tickReactions(20);
        assertTrue(state.reactions().isEmpty());
        assertTrue(state.effects().isEmpty());
        assertTrue(state.completedStages().isEmpty());
        assertTrue(state.signatureProcesses().iterator().next().ready());
        for (int remaining = 2; remaining >= 0; remaining--) {
            var claim = state.claimSignatureBottle(SIGNATURE).orElseThrow();
            assertEquals(result(), claim.result());
            assertEquals(1, claim.mixture().volumeUnits());
            assertFalse(claim.mixture().hasCommittedSignatureProcess());
            assertEquals(remaining, state.volumeUnits());
        }
        assertTrue(state.claimSignatureBottle(SIGNATURE).isEmpty());
        assertTrue(state.signatureProcesses().isEmpty());
    }

    @Test
    void partCompletedGroupSurvivesCodecAndCompletesWithoutDoubleEffect() {
        var state = state();
        assertTrue(state.commitSignatureGroup(SIGNATURE, result()));
        state.tickReactions(20);
        var restored = AlchemyMixtureState.decode(state.encode(), 8);

        assertEquals(state.encode(), restored.encode());
        assertEquals(1, restored.signatureProcesses().size());
        assertEquals(1, restored.signatureProcesses().iterator().next().completedReactionIds().size());
        assertEquals(20, restored.pendingReactionForIngredient("totem:alchemy/cocoa_powder").elapsedTicks());
        restored.tickReactions(20);
        assertTrue(restored.effects().isEmpty());
        assertEquals(result(), restored.claimSignatureBottle(SIGNATURE).orElseThrow().result());
        assertEquals(2, restored.volumeUnits());
        assertEquals(result(), restored.claimSignatureBottle(SIGNATURE).orElseThrow().result());
        assertEquals(result(), restored.claimSignatureBottle(SIGNATURE).orElseThrow().result());
        assertTrue(restored.claimSignatureBottle(SIGNATURE).isEmpty());
    }

    @Test
    void fullyReadyResultSurvivesSaveLoadAndCanOnlyBeClaimedOnce() {
        var state = state();
        assertTrue(state.commitSignatureGroup(SIGNATURE, result()));
        state.tickReactions(40);
        var restored = AlchemyMixtureState.decode(state.encode(), 8);
        assertTrue(restored.signatureProcesses().iterator().next().ready());
        assertTrue(restored.claimSignatureResult(SIGNATURE).isEmpty());
        assertEquals(result(), restored.claimSignatureBottle(SIGNATURE).orElseThrow().result());
        assertEquals(2, restored.volumeUnits());
        var afterFirstBottle = AlchemyMixtureState.decode(restored.encode(), 8);
        assertEquals(result(), afterFirstBottle.claimSignatureBottle(SIGNATURE).orElseThrow().result());
        assertEquals(1, afterFirstBottle.volumeUnits());
        var afterSecondBottle = AlchemyMixtureState.decode(afterFirstBottle.encode(), 8);
        assertEquals(result(), afterSecondBottle.claimSignatureBottle(SIGNATURE).orElseThrow().result());
        assertTrue(afterSecondBottle.isEmpty());
        assertTrue(AlchemyMixtureState.decode(afterSecondBottle.encode(), 8)
                .claimSignatureBottle(SIGNATURE).isEmpty());
    }

    @Test
    void activeGroupRejectsPartialExtractionButAllowsWholeTransfer() {
        var state = state();
        assertTrue(state.commitSignatureGroup(SIGNATURE, result()));
        String before = state.encode();
        assertTrue(state.extractUnits(1).isEmpty());
        assertEquals(before, state.encode());

        var all = state.extractUnits(3);
        assertTrue(state.isEmpty());
        assertEquals(1, all.signatureProcesses().size());
        all.tickReactions(40);
        assertEquals(result(), all.claimSignatureBottle(SIGNATURE).orElseThrow().result());
        assertEquals(2, all.volumeUnits());
    }

    @Test
    void activeGroupRejectsMergeAndReplanWithoutMutatingProgress() {
        var state = state();
        assertTrue(state.commitSignatureGroup(SIGNATURE, result()));
        String before = state.encode();
        assertFalse(state.mergeFrom(new AlchemyMixtureState(1, 8)));
        assertFalse(new AlchemyMixtureState(1, 8).mergeFrom(state));
        assertFalse(state.replaceSignatureGroups(List.of()));
        assertEquals(before, state.encode());
    }

    @Test
    void committedGroupRequiresValidPendingReservation() {
        var state = state();
        var another = Identifier.fromNamespaceAndPath("totem", "alchemy/other");
        assertFalse(state.commitSignatureGroup(another, result()));
        assertFalse(state.commitSignatureGroup(SIGNATURE, null));
        assertEquals(1, state.signatureGroups().size());
        assertTrue(state.signatureProcesses().isEmpty());
    }

    @Test
    void claimedReactionOwnershipSurvivesCompletionAndProtectsReadyResultFromOvercook() {
        var state = state();
        state.setBaseActivated(true);
        assertTrue(state.commitSignatureGroup(SIGNATURE, result()));
        assertTrue(state.isCommittedSignatureMember(SUGAR));
        assertTrue(state.isCommittedSignatureMember(COCOA));
        assertFalse(state.isCommittedSignatureMember("brew:unrelated"));

        state.tickReactions(40);
        assertTrue(state.hasCommittedSignatureProcess());
        assertFalse(state.canOvercook());
        var restored = AlchemyMixtureState.decode(state.encode(), 8);
        assertTrue(restored.isCommittedSignatureMember(SUGAR));
        assertTrue(restored.isCommittedSignatureMember(COCOA));
        assertFalse(restored.canOvercook());
        assertTrue(restored.claimSignatureBottle(SIGNATURE).isPresent());
        assertEquals(2, restored.volumeUnits());
        assertTrue(restored.hasCommittedSignatureProcess());
        assertTrue(restored.claimSignatureBottle(SIGNATURE).isPresent());
        assertTrue(restored.claimSignatureBottle(SIGNATURE).isPresent());
        assertFalse(restored.hasCommittedSignatureProcess());
        assertFalse(restored.isCommittedSignatureMember(SUGAR));
    }

    @Test
    void inertReservationDoesNotAcquireCommittedOwnershipOrResetExistingTimers() {
        var state = state();
        assertFalse(state.hasCommittedSignatureProcess());
        assertFalse(state.isCommittedSignatureMember(SUGAR));
        assertEquals(0, state.pendingReactionForIngredient("minecraft:sugar").elapsedTicks());
        assertEquals(0, state.pendingReactionForIngredient("totem:alchemy/cocoa_powder").elapsedTicks());
        assertEquals(1, state.signatureGroups().size());
        assertTrue(state.signatureProcesses().isEmpty());
        // Ordinary completion executes Minecraft's registry-backed chemistry. The matching
        // runtime regression belongs in a server GameTest with bootstrapped registries.
    }

    @Test
    void threeUnitBottlingConservesExistingChemistryAcrossAllClaims() {
        var state = state();
        state.putEffect("minecraft:regeneration", 900.0D, 0);
        assertTrue(state.commitSignatureGroup(SIGNATURE, result()));
        state.tickReactions(40);

        double extracted = 0.0D;
        for (int expectedRemaining = 2; expectedRemaining >= 0; expectedRemaining--) {
            var claim = state.claimSignatureBottle(SIGNATURE).orElseThrow();
            extracted += claim.mixture().effects().get("minecraft:regeneration").quantity();
            assertEquals(expectedRemaining, state.volumeUnits());
            assertTrue(claim.mixture().hasProvenance("signature:result:" + SIGNATURE));
            assertFalse(claim.mixture().hasCommittedSignatureProcess());
            assertTrue(claim.mixture().isHeatLockedAfterBottling());
        }
        assertEquals(900.0D, extracted, 1.0E-6D);
        assertTrue(state.isEmpty());
        assertTrue(state.claimSignatureBottle(SIGNATURE).isEmpty());
    }

    @Test
    void solidSignatureResultConsumesWholeBatchAndCannotBeClaimedTwice() {
        var state = state();
        var solid = new SignatureBrewDefinition.Result(
                SignatureBrewDefinition.Type.DROP_ITEM,
                Identifier.fromNamespaceAndPath("totem", "alchemy/saltpeter"),
                2, null, null);
        assertTrue(state.commitSignatureGroup(SIGNATURE, solid));
        state.tickReactions(40);
        assertTrue(state.claimSignatureBottle(SIGNATURE).isEmpty());
        assertEquals(solid, state.claimSignatureResult(SIGNATURE).orElseThrow());
        assertTrue(state.isEmpty());
        assertTrue(state.claimSignatureResult(SIGNATURE).isEmpty());
    }

    @Test
    void malformedCommittedRecordDoesNotDestroyOrdinaryReactions() {
        var state = state();
        assertTrue(state.commitSignatureGroup(SIGNATURE, result()));
        String serialized = state.encode().replaceAll("(?m)^Q\\|[^\\n]*", "Q|invalid|garbage");
        var decoded = AlchemyMixtureState.decode(serialized, 8);
        assertTrue(decoded.signatureProcesses().isEmpty());
        assertEquals(2, decoded.reactions().size());
    }
}
