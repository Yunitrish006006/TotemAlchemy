package dev.totem.alchemy.reaction;

import dev.totem.alchemy.mixture.ActivatedBaseComposition;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlchemyReactionResolverTest {
    private static final Identifier AWKWARD = id("totem", "alchemy/awkward");
    private static final Identifier SUGAR = id("minecraft", "sugar");
    private static final Identifier NETHER_WART = id("minecraft", "nether_wart");
    private static final Identifier WATER = id("minecraft", "water");
    private static final Identifier MILK = id("minecraft", "milk");

    @Test
    void baseStarterResolutionUsesOnlyUnactivatedUnits() {
        BaseReaction starter = baseReaction(
                "nether_wart",
                Map.of(WATER, 1.0D),
                ReactionIngredient.item(NETHER_WART),
                1.0D,
                0
        );
        AlchemyReactionIndex index = AlchemyReactionIndex.build(List.of(starter), List.of());

        AlchemyMixtureState state = new AlchemyMixtureState(3);
        state.setLiquidComposition(LiquidComposition.single(WATER, 1.0D));
        state.setActivatedBaseComposition(ActivatedBaseComposition.single(AWKWARD, 1.25D));

        AlchemyReactionResolver.BaseReactionResolution resolved =
                AlchemyReactionResolver.resolveBaseReaction(
                        index,
                        state,
                        NETHER_WART,
                        tag -> false
                ).orElseThrow();

        assertEquals(starter, resolved.reaction());
        assertEquals(1.75D, resolved.unactivatedUnits(), 1.0E-9D);
        assertEquals(1.0D, resolved.activationUnits(), 1.0E-9D);

        state.setActivatedBaseComposition(ActivatedBaseComposition.single(AWKWARD, 3.0D));
        assertTrue(AlchemyReactionResolver.resolveBaseReaction(
                index,
                state,
                NETHER_WART,
                tag -> false
        ).isEmpty());
    }

    @Test
    void baseStarterResolutionHonorsLiquidRequirementsAndCapsYieldToAvailableUnits() {
        BaseReaction incompatibleHighPriority = baseReaction(
                "milk_first",
                Map.of(MILK, 0.5D),
                ReactionIngredient.item(NETHER_WART),
                1.0D,
                10
        );
        BaseReaction waterFallback = baseReaction(
                "water_fallback",
                Map.of(WATER, 1.0D),
                ReactionIngredient.item(NETHER_WART),
                2.0D,
                0
        );
        AlchemyReactionIndex index = AlchemyReactionIndex.build(
                List.of(waterFallback, incompatibleHighPriority),
                List.of()
        );

        AlchemyMixtureState state = new AlchemyMixtureState(3);
        state.setLiquidComposition(LiquidComposition.single(WATER, 1.0D));
        state.setActivatedBaseComposition(ActivatedBaseComposition.single(AWKWARD, 2.5D));

        AlchemyReactionResolver.BaseReactionResolution resolved =
                AlchemyReactionResolver.resolveBaseReaction(
                        index,
                        state,
                        NETHER_WART,
                        tag -> false
                ).orElseThrow();

        assertEquals(waterFallback, resolved.reaction());
        assertEquals(0.5D, resolved.unactivatedUnits(), 1.0E-9D);
        assertEquals(0.5D, resolved.activationUnits(), 1.0E-9D);
    }

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

    private static BaseReaction baseReaction(
            String idPath,
            Map<Identifier, Double> liquids,
            ReactionIngredient starter,
            double activationYield,
            int priority
    ) {
        return new BaseReaction(
                id("totem", "base/" + idPath),
                liquids,
                starter,
                AWKWARD,
                1.0D,
                activationYield,
                400,
                true,
                priority
        );
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
