package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlchemyReactionIndexTest {
    @Test
    void exactIngredientLookupUsesBaseAndItem() {
        IngredientReaction sugar = ingredient(
                "sugar",
                "awkward",
                ReactionIngredient.item(id("minecraft", "sugar"))
        );
        IngredientReaction milkSugar = ingredient(
                "milk_sugar",
                "fermented_milk",
                ReactionIngredient.item(id("minecraft", "sugar"))
        );

        AlchemyReactionIndex index = AlchemyReactionIndex.build(List.of(), List.of(sugar, milkSugar));

        assertEquals(sugar, index.exactIngredient(
                id("totem", "alchemy/awkward"), id("minecraft", "sugar")).orElseThrow());
        assertEquals(milkSugar, index.exactIngredient(
                id("totem", "alchemy/fermented_milk"), id("minecraft", "sugar")).orElseThrow());
        assertTrue(index.exactIngredient(
                id("totem", "alchemy/awkward"), id("minecraft", "diamond")).isEmpty());
    }

    @Test
    void tagIngredientCandidatesStaySeparateFromExactLookup() {
        IngredientReaction mushroom = ingredient(
                "mushroom",
                "awkward",
                ReactionIngredient.tag(id("c", "mushrooms"))
        );

        AlchemyReactionIndex index = AlchemyReactionIndex.build(List.of(), List.of(mushroom));

        assertTrue(index.exactIngredient(
                id("totem", "alchemy/awkward"), id("minecraft", "red_mushroom")).isEmpty());
        assertEquals(List.of(mushroom),
                index.taggedIngredientCandidates(id("totem", "alchemy/awkward")));
    }

    @Test
    void duplicateExactIngredientKeysAreRejected() {
        IngredientReaction first = ingredient(
                "first",
                "awkward",
                ReactionIngredient.item(id("minecraft", "sugar"))
        );
        IngredientReaction second = ingredient(
                "second",
                "awkward",
                ReactionIngredient.item(id("minecraft", "sugar"))
        );

        assertThrows(IllegalArgumentException.class,
                () -> AlchemyReactionIndex.build(List.of(), List.of(first, second)));
    }

    @Test
    void baseStarterCandidatesPreferPriorityThenResourceId() {
        BaseReaction low = base("z_low", 0);
        BaseReaction highB = base("b_high", 5);
        BaseReaction highA = base("a_high", 5);

        AlchemyReactionIndex index = AlchemyReactionIndex.build(
                List.of(low, highB, highA),
                List.of()
        );

        assertEquals(
                List.of(highA, highB, low),
                index.exactBaseStarterCandidates(id("minecraft", "nether_wart"))
        );
    }

    private static IngredientReaction ingredient(
            String idPath,
            String basePath,
            ReactionIngredient ingredient
    ) {
        return new IngredientReaction(
                id("totem", "reaction/" + idPath),
                id("totem", "alchemy/" + basePath),
                ingredient,
                0.9D,
                1.0D,
                240,
                3,
                true,
                List.of(new ReactionOutcome(id("minecraft", "swiftness"), 0.94D, 10))
        );
    }

    private static BaseReaction base(String idPath, int priority) {
        return new BaseReaction(
                id("totem", "base/" + idPath),
                Map.of(id("minecraft", "water"), 1.0D),
                ReactionIngredient.item(id("minecraft", "nether_wart")),
                id("totem", "alchemy/awkward"),
                1.0D,
                1.0D,
                400,
                true,
                priority
        );
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
