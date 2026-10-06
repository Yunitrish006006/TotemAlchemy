package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactionModelTest {
    @Test
    void outcomeRejectsInvalidChance() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReactionOutcome(id("minecraft", "swiftness"), -0.01D, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ReactionOutcome(id("minecraft", "swiftness"), 1.01D, 0));
    }

    @Test
    void ingredientReactionDefensivelyCopiesOutcomes() {
        List<ReactionOutcome> source = new ArrayList<>();
        source.add(new ReactionOutcome(id("minecraft", "swiftness"), 0.94D, 10));

        IngredientReaction reaction = new IngredientReaction(
                id("totem", "alchemy/reaction/sugar"),
                id("totem", "alchemy/awkward"),
                ReactionIngredient.item(id("minecraft", "sugar")),
                0.90D,
                1.0D,
                240,
                3,
                true,
                source
        );

        source.clear();
        assertEquals(1, reaction.outcomes().size());
        assertThrows(UnsupportedOperationException.class,
                () -> reaction.outcomes().add(new ReactionOutcome(id("minecraft", "slowness"), 0.03D, 0)));
    }

    @Test
    void baseReactionSortsAndFreezesLiquidRequirements() {
        Map<Identifier, Double> requirements = new LinkedHashMap<>();
        requirements.put(id("totem", "alchemy/milk"), 0.25D);
        requirements.put(id("minecraft", "water"), 0.50D);

        BaseReaction reaction = new BaseReaction(
                id("totem", "alchemy/base/mixed_starter"),
                requirements,
                ReactionIngredient.tag(id("c", "mushrooms")),
                id("totem", "alchemy/unstable_mushroom"),
                0.65D,
                1.0D,
                320,
                true,
                0
        );

        assertEquals(List.of(id("minecraft", "water"), id("totem", "alchemy/milk")),
                List.copyOf(reaction.minimumLiquidFractions().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> reaction.minimumLiquidFractions().put(id("minecraft", "honey_block"), 0.1D));
    }

    @Test
    void baseReactionRejectsImpossibleMinimumComposition() {
        assertThrows(IllegalArgumentException.class, () -> new BaseReaction(
                id("totem", "alchemy/base/invalid"),
                Map.of(
                        id("minecraft", "water"), 0.75D,
                        id("totem", "alchemy/milk"), 0.50D
                ),
                ReactionIngredient.item(id("minecraft", "nether_wart")),
                id("totem", "alchemy/awkward"),
                1.0D,
                1.0D,
                400,
                true,
                0
        ));
    }

    @Test
    void reactionsRejectNonPositiveProcessingTime() {
        assertThrows(IllegalArgumentException.class, () -> new IngredientReaction(
                id("totem", "alchemy/reaction/invalid_time"),
                id("totem", "alchemy/awkward"),
                ReactionIngredient.item(id("minecraft", "sugar")),
                1.0D,
                1.0D,
                0,
                1,
                true,
                List.of()
        ));

        assertThrows(IllegalArgumentException.class, () -> new BaseReaction(
                id("totem", "alchemy/base/invalid_time"),
                Map.of(id("minecraft", "water"), 1.0D),
                ReactionIngredient.item(id("minecraft", "nether_wart")),
                id("totem", "alchemy/awkward"),
                1.0D,
                1.0D,
                0,
                true,
                0
        ));
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
