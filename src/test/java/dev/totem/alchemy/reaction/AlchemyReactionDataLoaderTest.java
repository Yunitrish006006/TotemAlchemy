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
                          "processing_ticks": 320,
                          "brewing_stand": true,
                          "priority": 5
                        }
                        """).getAsJsonObject()
        );

        assertEquals(ReactionIngredient.Kind.TAG, reaction.starter().kind());
        assertEquals(id("c", "mushrooms"), reaction.starter().id());
        assertEquals(0.65D, reaction.successChance());
        assertEquals(320, reaction.processingTicks());
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
                          "processing_ticks": 240,
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
        assertEquals(240, reaction.processingTicks());
        assertEquals(3, reaction.maxDose());
        assertEquals(List.of(
                id("minecraft", "swiftness"),
                id("minecraft", "slowness")
        ), reaction.outcomes().stream().map(ReactionOutcome::resultPotionId).toList());
    }

    @Test
    void parsesAdditiveIngredientReactionExtension() {
        IngredientReactionExtension extension = AlchemyReactionDataLoader.parseIngredientReactionExtension(
                id("totem", "sugar_totem_additions"),
                JsonParser.parseString("""
                        {
                          "target": "minecraft_alchemy:vanilla/sugar",
                          "outcomes": [
                            {"potion": "totem:alchemy/saturation", "chance": 0.03, "priority": 1}
                          ]
                        }
                        """).getAsJsonObject()
        );

        assertEquals(id("minecraft_alchemy", "vanilla/sugar"), extension.targetReactionId());
        assertEquals(List.of(id("totem", "alchemy/saturation")),
                extension.outcomes().stream().map(ReactionOutcome::resultPotionId).toList());
    }

    @Test
    void extensionsAppendOutcomesWithoutOverridingReactionPolicy() {
        IngredientReaction base = AlchemyReactionDataLoader.parseIngredientReaction(
                id("minecraft_alchemy", "vanilla/sugar"),
                JsonParser.parseString("""
                        {
                          "base": "totem:alchemy/awkward",
                          "ingredient": "minecraft:sugar",
                          "success_chance": 0.9,
                          "effect_yield": 0.75,
                          "processing_ticks": 300,
                          "max_dose": 2,
                          "brewing_stand": true,
                          "outcomes": [
                            {"potion": "minecraft:swiftness", "chance": 0.94, "priority": 10}
                          ]
                        }
                        """).getAsJsonObject()
        );
        IngredientReactionExtension extension = AlchemyReactionDataLoader.parseIngredientReactionExtension(
                id("totem", "sugar_side_effects"),
                JsonParser.parseString("""
                        {
                          "target": "minecraft_alchemy:vanilla/sugar",
                          "outcomes": [
                            {"potion": "totem:alchemy/saturation", "chance": 0.03}
                          ]
                        }
                        """).getAsJsonObject()
        );

        IngredientReaction merged = AlchemyReactionDataLoader.applyIngredientExtensions(
                List.of(base), List.of(extension)).getFirst();

        assertEquals(base.id(), merged.id());
        assertEquals(base.baseId(), merged.baseId());
        assertEquals(base.ingredient(), merged.ingredient());
        assertEquals(base.successChance(), merged.successChance());
        assertEquals(base.effectYield(), merged.effectYield());
        assertEquals(base.processingTicks(), merged.processingTicks());
        assertEquals(base.maxDose(), merged.maxDose());
        assertEquals(base.brewingStandCompatible(), merged.brewingStandCompatible());
        assertEquals(List.of(
                id("minecraft", "swiftness"),
                id("totem", "alchemy/saturation")
        ), merged.outcomes().stream().map(ReactionOutcome::resultPotionId).toList());
    }

    @Test
    void extensionRejectsScalarReactionOverrides() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                AlchemyReactionDataLoader.parseIngredientReactionExtension(
                        id("totem", "bad_override"),
                        JsonParser.parseString("""
                                {
                                  "target": "minecraft_alchemy:vanilla/sugar",
                                  "success_chance": 1.0,
                                  "outcomes": [
                                    {"potion": "totem:alchemy/saturation", "chance": 0.03}
                                  ]
                                }
                                """).getAsJsonObject()
                )
        );

        assertTrue(exception.getMessage().contains("may only add outcomes"));
    }

    @Test
    void extensionSkipsMissingOptionalTargetButRejectsDuplicateOutcome() {
        IngredientReaction base = AlchemyReactionDataLoader.parseIngredientReaction(
                id("minecraft_alchemy", "vanilla/sugar"),
                JsonParser.parseString("""
                        {
                          "base": "totem:alchemy/awkward",
                          "ingredient": "minecraft:sugar",
                          "outcomes": [
                            {"potion": "minecraft:swiftness", "chance": 0.94}
                          ]
                        }
                        """).getAsJsonObject()
        );
        IngredientReactionExtension unknown = AlchemyReactionDataLoader.parseIngredientReactionExtension(
                id("totem", "unknown_target"),
                JsonParser.parseString("""
                        {
                          "target": "minecraft_alchemy:missing",
                          "outcomes": [
                            {"potion": "totem:alchemy/saturation", "chance": 0.03}
                          ]
                        }
                        """).getAsJsonObject()
        );
        IngredientReactionExtension duplicate = AlchemyReactionDataLoader.parseIngredientReactionExtension(
                id("totem", "duplicate_swiftness"),
                JsonParser.parseString("""
                        {
                          "target": "minecraft_alchemy:vanilla/sugar",
                          "outcomes": [
                            {"potion": "minecraft:swiftness", "chance": 0.01}
                          ]
                        }
                        """).getAsJsonObject()
        );

        List<IngredientReaction> missingTargetResult =
                AlchemyReactionDataLoader.applyIngredientExtensions(List.of(base), List.of(unknown));
        assertEquals(List.of(base), missingTargetResult,
                "Optional extension target absence must leave loaded core reactions unchanged");

        assertTrue(assertThrows(IllegalArgumentException.class, () ->
                AlchemyReactionDataLoader.applyIngredientExtensions(List.of(base), List.of(duplicate))
        ).getMessage().contains("duplicates outcome minecraft:swiftness"));
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
