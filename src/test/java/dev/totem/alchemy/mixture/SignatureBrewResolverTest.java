package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewResolverTest {
    private static final Identifier MILK = id("minecraft", "milk");
    private static final Identifier WATER = id("minecraft", "water");
    private static final Identifier SUGAR = id("minecraft", "sugar");
    private static final Identifier COCOA = id("minecraft", "cocoa_beans");
    private static final Identifier CHERRY = id("minecraft", "pink_petals");

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static SignatureBrewResolver.Signature signature(
            String path, int priority, Map<Identifier, Double> liquids, Identifier... ingredients
    ) {
        return new SignatureBrewResolver.Signature(
                id("totem", path), priority, liquids, Set.of(ingredients));
    }

    private static AlchemyMixtureState.Reaction reaction(String id, Identifier ingredient, int elapsed, int required) {
        return new AlchemyMixtureState.Reaction(
                id, ingredient.toString(), elapsed, required, 3, null, null, Map.of(), Map.of());
    }

    private static AlchemyMixtureState milkWithSugarAndCocoa() {
        var mixture = new AlchemyMixtureState(3);
        mixture.setLiquidComposition(LiquidComposition.single(MILK, 1.0D));
        mixture.addReaction(reaction("brew:sugar", SUGAR, 80, 200));
        mixture.addReaction(reaction("brew:cocoa", COCOA, 20, 140));
        return mixture;
    }

    @Test
    void bindsPendingSugarAndCocoaIntoOneHotCocoaGroup() {
        var hotCocoa = signature("hot_cocoa", 10, Map.of(MILK, 1.0D), SUGAR, COCOA);
        var mixture = milkWithSugarAndCocoa();

        var group = SignatureBrewResolver.resolve(mixture, List.of(hotCocoa)).orElseThrow();
        assertEquals(hotCocoa.id(), group.signatureId());
        assertEquals(List.of("brew:cocoa", "brew:sugar"), group.memberReactionIds());
        assertTrue(group.owns("brew:sugar"));
        assertFalse(group.owns("brew:unrelated"));
        // Pure planning must not reset individual reaction timers or replace ordinary results yet.
        assertEquals(80, mixture.pendingReactionForIngredient(SUGAR.toString()).elapsedTicks());
        assertEquals(20, mixture.pendingReactionForIngredient(COCOA.toString()).elapsedTicks());
        assertEquals(2, mixture.reactions().size());
    }

    @Test
    void moreHighlyPrioritizedOverlappingGroupOwnsSharedSugarReaction() {
        var milkSugar = signature("milk_sugar", 10, Map.of(MILK, 1.0D), SUGAR);
        var hotCocoa = signature("hot_cocoa", 20, Map.of(MILK, 1.0D), SUGAR, COCOA);

        var planned = SignatureBrewResolver.planGroups(
                milkWithSugarAndCocoa(), List.of(milkSugar, hotCocoa));
        assertEquals(1, planned.size());
        assertEquals(hotCocoa.id(), planned.getFirst().signatureId());
        assertEquals(List.of("brew:cocoa", "brew:sugar"), planned.getFirst().memberReactionIds());
    }

    @Test
    void tieBreaksByIdAndRemainsStableWhenCandidateOrderChanges() {
        var later = signature("z", 5, Map.of(MILK, 1.0D), SUGAR);
        var earlier = signature("a", 5, Map.of(MILK, 1.0D), SUGAR);
        var mixture = milkWithSugarAndCocoa();

        assertEquals(earlier.id(), SignatureBrewResolver.resolve(mixture, List.of(later, earlier))
                .orElseThrow().signatureId());
        assertEquals(earlier.id(), SignatureBrewResolver.resolve(mixture, List.of(earlier, later))
                .orElseThrow().signatureId());
    }

    @Test
    void unrelatedReactionsRemainAvailableForAnotherGroup() {
        var mixture = milkWithSugarAndCocoa();
        mixture.addReaction(reaction("brew:petals", CHERRY, 0, 100));
        var hotCocoa = signature("hot_cocoa", 10, Map.of(MILK, 0.5D), SUGAR, COCOA);
        var flower = signature("flower", 5, Map.of(MILK, 0.5D), CHERRY);

        var groups = SignatureBrewResolver.planGroups(mixture, List.of(flower, hotCocoa));
        assertEquals(2, groups.size());
        assertEquals(List.of("brew:cocoa", "brew:sugar"), groups.get(0).memberReactionIds());
        assertEquals(List.of("brew:petals"), groups.get(1).memberReactionIds());
    }

    @Test
    void rejectsMissingLiquidMissingIngredientOrAlreadyFinishedReaction() {
        var hotCocoa = signature("hot_cocoa", 10, Map.of(MILK, 1.0D), SUGAR, COCOA);
        var mixture = milkWithSugarAndCocoa();
        mixture.setLiquidComposition(LiquidComposition.single(WATER, 1.0D));
        assertTrue(SignatureBrewResolver.resolve(mixture, List.of(hotCocoa)).isEmpty());

        mixture.setLiquidComposition(LiquidComposition.single(MILK, 1.0D));
        var cherryRecipe = signature("cherry", 10, Map.of(MILK, 1.0D), SUGAR, CHERRY);
        assertTrue(SignatureBrewResolver.resolve(mixture, List.of(cherryRecipe)).isEmpty());

        var alreadyFinished = new AlchemyMixtureState(3);
        alreadyFinished.setLiquidComposition(LiquidComposition.single(MILK, 1.0D));
        alreadyFinished.addReaction(reaction("brew:sugar", SUGAR, 200, 200));
        alreadyFinished.addReaction(reaction("brew:cocoa", COCOA, 0, 200));
        assertTrue(SignatureBrewResolver.resolve(alreadyFinished, List.of(hotCocoa)).isEmpty());
    }

    @Test
    void missingInputsAndEmptyMixturesNeverPlan() {
        var candidate = signature("hot_cocoa", 1, Map.of(MILK, 1.0D), SUGAR);
        assertTrue(SignatureBrewResolver.planGroups(null, List.of(candidate)).isEmpty());
        assertTrue(SignatureBrewResolver.planGroups(new AlchemyMixtureState(0), List.of(candidate)).isEmpty());
        assertTrue(SignatureBrewResolver.planGroups(new AlchemyMixtureState(3), List.of(candidate)).isEmpty());
        assertTrue(SignatureBrewResolver.planGroups(new AlchemyMixtureState(3), null).isEmpty());
        assertTrue(SignatureBrewResolver.planGroups(new AlchemyMixtureState(3), List.of()).isEmpty());
    }

    @Test
    void invalidSignatureAndGroupAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                signature("empty", 0, Map.of(MILK, 1.0D)));
        assertThrows(IllegalArgumentException.class, () ->
                signature("invalid", 0, Map.of(MILK, 1.1D), SUGAR));
        assertThrows(IllegalArgumentException.class, () ->
                signature("overfull", 0, Map.of(MILK, 0.8D, WATER, 0.8D), SUGAR));
        assertThrows(IllegalArgumentException.class, () ->
                new SignatureBrewResolver.ReactionGroup(id("totem", "invalid"), List.of("same", "same")));
    }
}
