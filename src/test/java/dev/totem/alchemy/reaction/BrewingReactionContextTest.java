package dev.totem.alchemy.reaction;

import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrewingReactionContextTest {
    private static final Identifier AWKWARD = id("totem", "alchemy/awkward");
    private static final Identifier SUGAR = id("minecraft", "sugar");

    @Test
    void activatedLegacyMixtureMapsToCompatibilityBase() {
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.setBaseActivated(true);

        assertEquals(AWKWARD, BrewingReactionContext.baseId(state).orElseThrow());
    }

    @Test
    void inactiveMixtureDoesNotClaimAnActivatedBase() {
        AlchemyMixtureState state = new AlchemyMixtureState(1);

        assertTrue(BrewingReactionContext.baseId(state).isEmpty());
    }

    @Test
    void reactionLookupUsesBaseDerivedFromMixtureContext() {
        IngredientReaction sugar = new IngredientReaction(
                id("totem", "reaction/sugar"),
                AWKWARD,
                ReactionIngredient.item(SUGAR),
                0.90D,
                1.0D,
                300,
                1,
                true,
                List.of(new ReactionOutcome(id("minecraft", "swiftness"), 0.94D, 3))
        );
        AlchemyReactionIndex index = AlchemyReactionIndex.build(List.of(), List.of(sugar));
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.setBaseActivated(true);

        BrewingReactionContext context = BrewingReactionContext.resolve(
                index,
                state,
                SUGAR,
                ignored -> false
        ).orElseThrow();

        assertEquals(AWKWARD, context.baseId());
        assertEquals(sugar, context.reaction());
    }

    @Test
    void inactiveMixtureCannotResolveActivatedBaseReaction() {
        IngredientReaction sugar = new IngredientReaction(
                id("totem", "reaction/sugar"),
                AWKWARD,
                ReactionIngredient.item(SUGAR),
                0.90D,
                1.0D,
                300,
                1,
                true,
                List.of()
        );
        AlchemyReactionIndex index = AlchemyReactionIndex.build(List.of(), List.of(sugar));
        AlchemyMixtureState state = new AlchemyMixtureState(1);

        assertTrue(BrewingReactionContext.resolve(
                index,
                state,
                SUGAR,
                ignored -> false
        ).isEmpty());
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
