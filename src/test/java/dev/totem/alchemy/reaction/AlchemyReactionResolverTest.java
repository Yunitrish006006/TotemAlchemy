package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlchemyReactionResolverTest {
    private static final Identifier AWKWARD = id("totem", "alchemy/awkward");
    private static final Identifier SUGAR = id("minecraft", "sugar");

    @Test
    void exactItemReactionWinsBeforeMatchingTagCandidates() {
        IngredientReaction tagged = reaction(
                "tagged",
                ReactionIngredient.tag(id("c", "sweet_ingredients")),
                0.55D
        );
        IngredientReaction exact = reaction(
                "exact",
                ReactionIngredient.item(SUGAR),
                0.90D
        );
        AlchemyReactionIndex index = AlchemyReactionIndex.build(
                List.of(),
                List.of(tagged, exact)
        );

        IngredientReaction resolved = AlchemyReactionResolver.resolveIngredientReaction(
                index,
                AWKWARD,
                SUGAR,
                tag -> true
        ).orElseThrow();

        assertEquals(exact, resolved);
    }

    @Test
    void overlappingTagCandidatesUseDeterministicReactionIdOrder() {
        IngredientReaction later = reaction(
                "z_later",
                ReactionIngredient.tag(id("c", "sweet_ingredients")),
                0.60D
        );
        IngredientReaction earlier = reaction(
                "a_earlier",
                ReactionIngredient.tag(id("c", "foods")),
                0.72D
        );
        AlchemyReactionIndex index = AlchemyReactionIndex.build(
                List.of(),
                List.of(later, earlier)
        );

        IngredientReaction resolved = AlchemyReactionResolver.resolveIngredientReaction(
                index,
                AWKWARD,
                SUGAR,
                tag -> true
        ).orElseThrow();

        assertEquals(earlier, resolved);
        assertEquals(
                0.72D,
                AlchemyReactionResolver.successChance(index, AWKWARD, SUGAR, tag -> true).orElseThrow()
        );
    }

    @Test
    void outcomeChanceComesFromResolvedReactionData() {
        IngredientReaction reaction = new IngredientReaction(
                id("totem", "reaction/sugar"),
                AWKWARD,
                ReactionIngredient.item(SUGAR),
                0.90D,
                1.0D,
                240,
                3,
                true,
                List.of(
                        new ReactionOutcome(id("minecraft", "swiftness"), 0.50D, 10),
                        new ReactionOutcome(id("minecraft", "slowness"), 0.30D, 0)
                )
        );
        AlchemyReactionIndex index = AlchemyReactionIndex.build(
                List.of(),
                List.of(reaction)
        );

        assertEquals(
                List.of(
                        new ReactionOutcome(id("minecraft", "swiftness"), 0.50D, 10),
                        new ReactionOutcome(id("minecraft", "slowness"), 0.30D, 0)
                ),
                AlchemyReactionResolver.outcomes(index, AWKWARD, SUGAR, tag -> false)
        );
        assertEquals(
                0.50D,
                AlchemyReactionResolver.outcomeChance(
                        index,
                        AWKWARD,
                        SUGAR,
                        tag -> false,
                        id("minecraft", "swiftness")
                ).orElseThrow()
        );
        assertTrue(AlchemyReactionResolver.outcomeChance(
                index,
                AWKWARD,
                SUGAR,
                tag -> false,
                id("minecraft", "healing")
        ).isEmpty());
    }

    @Test
    void unmatchedTagCandidatesReturnNoReactionOrChance() {
        IngredientReaction tagged = reaction(
                "tagged",
                ReactionIngredient.tag(id("c", "mushrooms")),
                0.65D
        );
        AlchemyReactionIndex index = AlchemyReactionIndex.build(
                List.of(),
                List.of(tagged)
        );

        assertTrue(AlchemyReactionResolver.resolveIngredientReaction(
                index,
                AWKWARD,
                SUGAR,
                tag -> false
        ).isEmpty());
        assertTrue(AlchemyReactionResolver.successChance(
                index,
                AWKWARD,
                SUGAR,
                tag -> false
        ).isEmpty());
    }

    private static IngredientReaction reaction(
            String idPath,
            ReactionIngredient ingredient,
            double successChance
    ) {
        return new IngredientReaction(
                id("totem", "reaction/" + idPath),
                AWKWARD,
                ingredient,
                successChance,
                1.0D,
                240,
                3,
                true,
                List.of()
        );
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
