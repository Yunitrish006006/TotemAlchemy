package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewReservationTest {
    private static final Identifier MILK = Identifier.fromNamespaceAndPath("minecraft", "milk");
    private static final Identifier SUGAR = Identifier.fromNamespaceAndPath("minecraft", "sugar");
    private static final Identifier COCOA = Identifier.fromNamespaceAndPath("minecraft", "cocoa_beans");

    private static SignatureBrewResolver.ReactionGroup group(String name, String... members) {
        return new SignatureBrewResolver.ReactionGroup(
                Identifier.fromNamespaceAndPath("totem", name), List.of(members));
    }

    private static AlchemyMixtureState.Reaction reaction(String id, Identifier ingredient, int elapsed) {
        return new AlchemyMixtureState.Reaction(id, ingredient.toString(), elapsed, 100, 3,
                null, null, Map.of(), Map.of());
    }

    private static AlchemyMixtureState pendingMixture() {
        var state = new AlchemyMixtureState(3, 8);
        state.setLiquidComposition(LiquidComposition.single(MILK, 1.0D));
        state.addReaction(reaction("brew:sugar", SUGAR, 35));
        state.addReaction(reaction("brew:cocoa", COCOA, 12));
        return state;
    }

    @Test
    void reservationSurvivesSaveLoadAndCopyWithoutChangingProgress() {
        var state = pendingMixture();
        var hotCocoa = group("hot_cocoa", "brew:sugar", "brew:cocoa");

        assertTrue(state.replaceSignatureGroups(List.of(hotCocoa)));
        assertEquals(List.of(hotCocoa), List.copyOf(state.signatureGroups()));
        assertEquals(List.of(hotCocoa), List.copyOf(state.copy().signatureGroups()));

        var restored = AlchemyMixtureState.decode(state.encode());
        assertEquals(List.of(hotCocoa), List.copyOf(restored.signatureGroups()));
        assertEquals(35, restored.pendingReactionForIngredient(SUGAR.toString()).elapsedTicks());
        assertEquals(12, restored.pendingReactionForIngredient(COCOA.toString()).elapsedTicks());
        assertEquals(state.encode(), restored.encode());
    }

    @Test
    void overlappingReservationIsRejectedAtomically() {
        var state = pendingMixture();
        var original = group("first", "brew:sugar");
        assertTrue(state.replaceSignatureGroups(List.of(original)));
        assertFalse(state.replaceSignatureGroups(List.of(
                group("hot_cocoa", "brew:sugar", "brew:cocoa"),
                group("milk_sugar", "brew:sugar"))));
        assertEquals(List.of(original), List.copyOf(state.signatureGroups()));
    }

    @Test
    void invalidMemberAndAlreadyCompletedMemberAreRejected() {
        var state = pendingMixture();
        assertFalse(state.replaceSignatureGroups(List.of(group("missing", "brew:nonexistent"))));
        assertTrue(state.signatureGroups().isEmpty());

        var completed = new AlchemyMixtureState(3);
        completed.addReaction(reaction("brew:sugar", SUGAR, 100));
        assertFalse(completed.replaceSignatureGroups(List.of(group("finished", "brew:sugar"))));
    }

    @Test
    void reassignmentBeforeCompletionRetainsElapsedTicks() {
        var state = pendingMixture();
        assertTrue(state.replaceSignatureGroups(List.of(group("milk_sugar", "brew:sugar"))));
        var hotCocoa = group("hot_cocoa", "brew:sugar", "brew:cocoa");
        assertTrue(state.replaceSignatureGroups(List.of(hotCocoa)));
        assertEquals(List.of(hotCocoa), List.copyOf(state.signatureGroups()));
        assertEquals(35, state.pendingReactionForIngredient(SUGAR.toString()).elapsedTicks());
        assertEquals(12, state.pendingReactionForIngredient(COCOA.toString()).elapsedTicks());
    }

    @Test
    void pendingGroupsArePreservedAcrossMixtureExtraction() {
        var state = pendingMixture();
        var hotCocoa = group("hot_cocoa", "brew:sugar", "brew:cocoa");
        assertTrue(state.replaceSignatureGroups(List.of(hotCocoa)));

        var extracted = state.extractUnits(1);
        assertEquals(List.of(hotCocoa), List.copyOf(extracted.signatureGroups()));
        assertEquals(List.of(hotCocoa), List.copyOf(state.signatureGroups()));
        assertEquals(1, extracted.volumeUnits());
        assertEquals(2, state.volumeUnits());
    }

    @Test
    void mergingReservedMixturesIsConservativelyRejected() {
        var reserved = pendingMixture();
        assertTrue(reserved.replaceSignatureGroups(List.of(group("hot_cocoa", "brew:sugar", "brew:cocoa"))));
        var incoming = new AlchemyMixtureState(1, 8);
        String before = reserved.encode();

        assertFalse(reserved.mergeFrom(incoming));
        assertEquals(before, reserved.encode());
        assertFalse(incoming.mergeFrom(reserved));
    }

    @Test
    void malformedOrStaleSerializedReservationsAreDropped() {
        var state = pendingMixture();
        String serialized = state.encode();
        // A malformed raw G marker must not prevent the rest of the mixture from loading.
        var corrupted = AlchemyMixtureState.decode(serialized + "G|garbage\n");
        assertTrue(corrupted.signatureGroups().isEmpty());
        assertEquals(2, corrupted.reactions().size());

        var valid = group("hot_cocoa", "brew:sugar", "brew:cocoa");
        assertTrue(state.replaceSignatureGroups(List.of(valid)));
        String withReservation = state.encode();
        String withoutReactions = withReservation.lines()
                .filter(line -> !line.startsWith("R|"))
                .reduce("", (a, b) -> a + b + "\n");
        assertTrue(AlchemyMixtureState.decode(withoutReactions).signatureGroups().isEmpty());
    }
}
