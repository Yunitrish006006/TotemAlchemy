package dev.totem.alchemy.mixture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewDataLoaderTest {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");

    private static final String HOT_COCOA = """
            {
              "schema_version": 1,
              "priority": 20,
              "requires_heat": true,
              "liquids": { "minecraft:milk": 1.0 },
              "ingredients": ["minecraft:sugar", "totem:alchemy/cocoa_powder"],
              "result": {
                "type": "bottled_item",
                "item": "totem:alchemy/hot_cocoa",
                "container_item": "minecraft:glass_bottle",
                "potion": "totem:alchemy/saturation",
                "count": 1
              }
            }
            """;

    private static JsonObject json(String source) {
        return JsonParser.parseString(source).getAsJsonObject();
    }

    @Test
    void parsesHotCocoaAsDeclarativeSchedulingDefinition() {
        var definition = SignatureBrewDataLoader.parse(ID, json(HOT_COCOA));
        assertEquals(ID, definition.id());
        assertEquals(20, definition.signature().priority());
        assertTrue(definition.requiresHeat());
        assertEquals(Map.of(Identifier.parse("minecraft:milk"), 1.0D),
                definition.signature().minimumLiquidFractions());
        assertEquals(Set.of(Identifier.parse("minecraft:sugar"),
                Identifier.parse("totem:alchemy/cocoa_powder")),
                definition.signature().requiredIngredients());
        assertEquals(SignatureBrewDefinition.Type.BOTTLED_ITEM, definition.result().type());
        assertEquals(Identifier.parse("totem:alchemy/hot_cocoa"), definition.result().itemId());
        assertEquals(Identifier.parse("minecraft:glass_bottle"), definition.result().containerItemId());
        assertEquals(Identifier.parse("totem:alchemy/saturation"), definition.result().potionId());
        assertEquals(1, definition.result().count());
        // Parsing does not mutate current active registries or create a runtime group.
        assertFalse(SignatureBrewDataLoader.definitions().containsKey(ID));
    }

    @Test
    void supportsDropItemWithoutBottleSpecificFields() {
        JsonObject result = json(HOT_COCOA);
        result.addProperty("requires_heat", false);
        var output = json("{\"type\":\"drop_item\",\"item\":\"minecraft:sugar\"}");
        result.add("result", output);
        var parsed = SignatureBrewDataLoader.parse(ID, result);
        assertEquals(SignatureBrewDefinition.Type.DROP_ITEM, parsed.result().type());
        assertEquals(1, parsed.result().count());
        assertFalse(parsed.requiresHeat());
    }

    @Test
    void rejectsUnknownSchemaVersionAndTypos() {
        var unknownVersion = json(HOT_COCOA);
        unknownVersion.addProperty("schema_version", 2);
        assertThrows(IllegalArgumentException.class, () -> SignatureBrewDataLoader.parse(ID, unknownVersion));

        var typo = json(HOT_COCOA);
        typo.addProperty("requires_haet", true);
        assertThrows(IllegalArgumentException.class, () -> SignatureBrewDataLoader.parse(ID, typo));
    }

    @Test
    void rejectsDuplicateIngredientsOrMissingInputs() {
        var repeated = json(HOT_COCOA);
        repeated.add("ingredients", JsonParser.parseString("[\"minecraft:sugar\",\"minecraft:sugar\"]"));
        assertThrows(IllegalArgumentException.class, () -> SignatureBrewDataLoader.parse(ID, repeated));

        var empty = json(HOT_COCOA);
        empty.add("ingredients", JsonParser.parseString("[]"));
        assertThrows(IllegalArgumentException.class, () -> SignatureBrewDataLoader.parse(ID, empty));
    }

    @Test
    void rejectsImpossibleOrMalformedLiquidRequirements() {
        var overfull = json(HOT_COCOA);
        overfull.add("liquids", json("{\"minecraft:milk\":0.7,\"minecraft:water\":0.6}"));
        assertThrows(IllegalArgumentException.class, () -> SignatureBrewDataLoader.parse(ID, overfull));

        var negative = json(HOT_COCOA);
        negative.add("liquids", json("{\"minecraft:milk\":-0.2}"));
        assertThrows(IllegalArgumentException.class, () -> SignatureBrewDataLoader.parse(ID, negative));
    }

    @Test
    void rejectsInvalidOutputConstraints() {
        var missingBottle = json(HOT_COCOA);
        missingBottle.getAsJsonObject("result").remove("container_item");
        assertThrows(IllegalArgumentException.class,
                () -> SignatureBrewDataLoader.parse(ID, missingBottle));

        var tooMany = json(HOT_COCOA);
        tooMany.getAsJsonObject("result").addProperty("count", 65);
        assertThrows(IllegalArgumentException.class,
                () -> SignatureBrewDataLoader.parse(ID, tooMany));

        var dropWithPotion = json(HOT_COCOA);
        dropWithPotion.getAsJsonObject("result").addProperty("type", "drop_item");
        assertThrows(IllegalArgumentException.class,
                () -> SignatureBrewDataLoader.parse(ID, dropWithPotion));
    }

    @Test
    void rejectsWrongFieldTypesAndFractionalPriority() {
        var invalidHeat = json(HOT_COCOA);
        invalidHeat.addProperty("requires_heat", "yes");
        assertThrows(IllegalArgumentException.class,
                () -> SignatureBrewDataLoader.parse(ID, invalidHeat));

        var invalidPriority = json(HOT_COCOA);
        invalidPriority.addProperty("priority", 2.5);
        assertThrows(IllegalArgumentException.class,
                () -> SignatureBrewDataLoader.parse(ID, invalidPriority));

        var invalidIngredient = json(HOT_COCOA);
        invalidIngredient.add("ingredients", JsonParser.parseString("[15]"));
        assertThrows(IllegalArgumentException.class,
                () -> SignatureBrewDataLoader.parse(ID, invalidIngredient));
    }
}
