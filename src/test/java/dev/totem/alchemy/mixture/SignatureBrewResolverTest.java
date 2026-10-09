package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewResolverTest {
    private static SignatureBrewResolver.Signature signature(String path, int priority, String marker) {
        return new SignatureBrewResolver.Signature(
                Identifier.fromNamespaceAndPath("totem", path), priority, marker);
    }

    @Test
    void highestPriorityMatchingSignatureWinsRegardlessOfInputOrder() {
        var low = signature("low", 1, "ingredient:cocoa");
        var high = signature("high", 10, "ingredient:cocoa");
        var state = new AlchemyMixtureState(3);
        state.addProvenance("ingredient:cocoa");

        assertEquals(high, SignatureBrewResolver.resolve(state, List.of(low, high)).orElseThrow());
        assertEquals(high, SignatureBrewResolver.resolve(state, List.of(high, low)).orElseThrow());
    }

    @Test
    void equalPriorityUsesStableIdentifierOrdering() {
        var z = signature("z", 5, "ingredient:cocoa");
        var a = signature("a", 5, "ingredient:cocoa");
        var state = new AlchemyMixtureState(3);
        state.addProvenance("ingredient:cocoa");

        assertEquals(a, SignatureBrewResolver.resolve(state, List.of(z, a)).orElseThrow());
    }

    @Test
    void nonMatchingSignaturesAreExcludedEvenWithHigherPriority() {
        var wrong = signature("wrong", 100, "ingredient:cherry");
        var matching = signature("matching", 1, "ingredient:cocoa");
        var state = new AlchemyMixtureState(3);
        state.addProvenance("ingredient:cocoa");

        assertEquals(matching, SignatureBrewResolver.resolve(state, List.of(wrong, matching)).orElseThrow());
    }

    @Test
    void missingInputsAndEmptyMixturesNeverResolve() {
        var candidate = signature("cocoa", 1, "ingredient:cocoa");
        assertTrue(SignatureBrewResolver.resolve(null, List.of(candidate)).isEmpty());
        assertTrue(SignatureBrewResolver.resolve(new AlchemyMixtureState(0), List.of(candidate)).isEmpty());
        assertTrue(SignatureBrewResolver.resolve(new AlchemyMixtureState(3), List.of(candidate)).isEmpty());
        assertTrue(SignatureBrewResolver.resolve(new AlchemyMixtureState(3), null).isEmpty());
        assertTrue(SignatureBrewResolver.resolve(new AlchemyMixtureState(3), List.of()).isEmpty());
    }

    @Test
    void signatureRequiresNonBlankProvenance() {
        assertThrows(IllegalArgumentException.class, () -> signature("invalid", 0, ""));
        assertThrows(IllegalArgumentException.class, () -> signature("invalid", 0, "  "));
    }
}
