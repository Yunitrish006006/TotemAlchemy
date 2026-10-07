package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlchemyReactionReaderTest {
    private static final double EPSILON = 0.000_001D;

    @Test
    void mergedExtensionOutcomesDriveReaderTruth() {
        IngredientReaction base = new IngredientReaction(
                id("minecraft_alchemy", "vanilla/sugar"),
                id("totem", "alchemy/awkward"),
                ReactionIngredient.item(id("minecraft", "sugar")),
                0.90D,
                1.0D,
                300,
                1,
                true,
                List.of(new ReactionOutcome(id("minecraft", "swiftness"), 0.94D, 10))
        );
        IngredientReactionExtension extension = new IngredientReactionExtension(
                id("totem", "sugar_side_effects"),
                base.id(),
                List.of(new ReactionOutcome(id("totem", "alchemy/saturation"), 0.03D, 0))
        );

        IngredientReaction merged = AlchemyReactionDataLoader.applyIngredientExtensions(
                List.of(base),
                List.of(extension)
        ).getFirst();

        assertEquals(0.94D,
                AlchemyReactionReader.outcomeProbability(merged, id("minecraft", "swiftness")),
                EPSILON);
        assertEquals(0.03D,
                AlchemyReactionReader.outcomeProbability(merged, id("totem", "alchemy/saturation")),
                EPSILON);
        assertEquals((1.0D - 0.94D) * (1.0D - 0.03D),
                AlchemyReactionReader.noEffectProbability(merged),
                EPSILON);
    }

    @Test
    void missingOutcomeRemainsUnknownToReader() {
        IngredientReaction base = new IngredientReaction(
                id("minecraft_alchemy", "vanilla/sugar"),
                id("totem", "alchemy/awkward"),
                ReactionIngredient.item(id("minecraft", "sugar")),
                0.90D,
                1.0D,
                300,
                1,
                true,
                List.of(new ReactionOutcome(id("minecraft", "swiftness"), 0.94D, 10))
        );

        assertEquals(-1.0D,
                AlchemyReactionReader.outcomeProbability(base, id("minecraft", "healing")),
                EPSILON);
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
