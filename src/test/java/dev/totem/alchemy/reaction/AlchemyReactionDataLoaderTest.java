package dev.totem.alchemy.reaction;

import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlchemyReactionDataLoaderTest {
    @Test
    void resourcePathProducesStableReactionId() {
        assertEquals(
                id("totem", "vanilla/sugar"),
                AlchemyReactionDataLoader.reactionId(
                        id("totem", "alchemy/ingredient_reactions/vanilla/sugar.json"),
                        AlchemyReactionDataLoader.INGREDIENT_REACTION_DIRECTORY
                )
        );
    }

    @Test
    void parsesTaggedBaseStarter() {
        BaseReaction reaction = AlchemyReactionDataLoader.parseBaseReaction(
                id("totem", "mushroom_starter"),
                JsonParser.parseString("""
                        {
                          "liquids": {"minecraft:water": 1.0},
                          "starter": {"tag": "c:mushrooms"},
                          "result_base": "totem:alchemy/unstable_mushroom",
                          "success_chance": 0.65,
                          "activation_yield": 1.0,
                          "brewing_stand": true,
                          "priority": 5
                        }
                        """).getAsJsonObject()
        );

        assertEquals(ReactionIngredient.Kind.TAG, reaction.starter().kind());
        assertEquals(id("c", "mushrooms"), reaction.starter().id());
        assertEquals(0.65D, reaction.successChance());
        assertEquals(5, reaction.priority());
    }

    @Test
    void parsesIngredientReactionOutcomes() {
        IngredientReaction reaction = AlchemyReactionDataLoader.parseIngredientReaction(
                id("totem", "sugar"),
                JsonParser.parseString("""
                        {
                          "base": "totem:alchemy/awkward",
                          "ingredient": "minecraft:sugar",
                          "success_chance": 0.9,
                          "effect_yield": 1.0,
                          "max_dose": 3,
                          "brewing_stand": true,
                          "outcomes": [
                            {"potion": "minecraft:swiftness", "chance": 0.94, "priority": 10},
                            {"potion": "minecraft:slowness", "chance": 0.03}
                          ]
                        }
                        """).getAsJsonObject()
        );

        assertEquals(id("totem", "alchemy/awkward"), reaction.baseId());
        assertEquals(ReactionIngredient.item(id("minecraft", "sugar")), reaction.ingredient());
        assertEquals(3, reaction.maxDose());
        assertEquals(List.of(
                id("minecraft", "swiftness"),
                id("minecraft", "slowness")
        ), reaction.outcomes().stream().map(ReactionOutcome::resultPotionId).toList());
    }

    @Test
    void rejectsStringEncodedNumericFields() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                AlchemyReactionDataLoader.parseIngredientReaction(
                        id("totem", "bad_chance"),
                        JsonParser.parseString("""
                                {
                                  "base": "totem:alchemy/awkward",
                                  "ingredient": "minecraft:sugar",
                                  "success_chance": "0.9"
                                }
                                """).getAsJsonObject()
                )
        );

        assertTrue(exception.getMessage().contains("success_chance must be a number"));
    }

    @Test
    void rejectsFractionalIntegerFieldsInsteadOfTruncatingThem() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                AlchemyReactionDataLoader.parseIngredientReaction(
                        id("totem", "bad_dose"),
                        JsonParser.parseString("""
                                {
                                  "base": "totem:alchemy/awkward",
                                  "ingredient": "minecraft:sugar",
                                  "max_dose": 1.5
                                }
                                """).getAsJsonObject()
                )
        );

        assertTrue(exception.getMessage().contains("max_dose must be an integer"));
    }

    @Test
    void outcomeErrorsIdentifyTheirArrayIndex() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                AlchemyReactionDataLoader.parseIngredientReaction(
                        id("totem", "bad_outcome"),
                        JsonParser.parseString("""
                                {
                                  "base": "totem:alchemy/awkward",
                                  "ingredient": "minecraft:sugar",
                                  "outcomes": ["minecraft:swiftness"]
                                }
                                """).getAsJsonObject()
                )
        );

        assertTrue(exception.getMessage().contains("outcomes[0] must be an object"));
    }

    @Test
    void reloadErrorSummaryNamesEveryInvalidResourceDeterministically() {
        String message = AlchemyReactionDataLoader.formatReloadErrors(List.of(
                new AlchemyReactionDataLoader.LoadError(
                        "ingredient reaction",
                        id("totem", "alchemy/ingredient_reactions/z_bad.json"),
                        "success_chance must be a number"
                ),
                new AlchemyReactionDataLoader.LoadError(
                        "base reaction",
                        id("example", "alchemy/base_reactions/a_bad.json"),
                        "starter must contain exactly one of item or tag"
                )
        ));

        int first = message.indexOf("example:alchemy/base_reactions/a_bad.json");
        int second = message.indexOf("totem:alchemy/ingredient_reactions/z_bad.json");
        assertTrue(message.startsWith("Alchemy reaction reload rejected because 2 resources are invalid:"));
        assertTrue(first >= 0 && second > first, "Reload errors were not sorted by resource id");
        assertTrue(message.contains("[base reaction]: starter must contain exactly one of item or tag"));
        assertTrue(message.contains("[ingredient reaction]: success_chance must be a number"));
    }

    @Test
    void ingredientSelectorRejectsAmbiguousItemAndTag() {
        assertThrows(IllegalArgumentException.class, () ->
                AlchemyReactionDataLoader.parseIngredient(
                        JsonParser.parseString("""
                                {"item":"minecraft:red_mushroom","tag":"c:mushrooms"}
                                """),
                        "ingredient"
                )
        );
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
