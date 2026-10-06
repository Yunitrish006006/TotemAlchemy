package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.reaction.IngredientReaction;
import dev.totem.alchemy.reaction.ReactionIngredient;
import dev.totem.alchemy.reaction.ReactionOutcome;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiOutcomeBrewingRegistryTest {
    @Test
    void registryChanceOverridesLegacyOutcomeWeight() {
        IngredientReaction reaction = new IngredientReaction(
                id("totem", "reaction/test_sugar"),
                id("totem", "alchemy/awkward"),
                ReactionIngredient.item(id("minecraft", "sugar")),
                1.0D,
                1.0D,
                240,
                3,
                true,
                List.of(
                        new ReactionOutcome(id("minecraft", "swiftness"), 0.10D, 10),
                        new ReactionOutcome(id("minecraft", "slowness"), 0.80D, 0)
                )
        );

        List<MultiOutcomeBrewing.Outcome> selected =
                MultiOutcomeBrewing.chooseRegistryOutcomes(reaction, 0.20F, 0.20F);

        assertEquals(
                List.of("minecraft:slowness"),
                selected.stream()
                        .map(outcome -> BuiltInRegistries.POTION.getKey(outcome.potion().value()).toString())
                        .toList()
        );
        assertEquals(
                0.10D,
                MultiOutcomeBrewing.registryOutcomeProbability(reaction, "minecraft:swiftness")
        );
        assertEquals(
                0.80D,
                MultiOutcomeBrewing.registryOutcomeProbability(reaction, "minecraft:slowness")
        );
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
