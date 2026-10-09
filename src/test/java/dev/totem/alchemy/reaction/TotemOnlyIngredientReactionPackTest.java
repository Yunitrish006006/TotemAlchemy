package dev.totem.alchemy.reaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotemOnlyIngredientReactionPackTest {
    private static final String PACK_PREFIX =
            "resourcepacks/totem_alchemy/data/totem_alchemy/alchemy/ingredient_reactions/totem/";
    private static final String LEGACY_PREFIX =
            "data/totem/alchemy/ingredient_reactions/legacy/totem/alchemy/";
    private static final List<String> NAMES = List.of("cocoa_powder", "pig_manure", "wood_ash");

    @Test
    void allTotemOnlyIngredientReactionsBelongToTotemAlchemyPack() throws Exception {
        ClassLoader loader = TotemOnlyIngredientReactionPackTest.class.getClassLoader();
        for (String name : NAMES) {
            String packPath = PACK_PREFIX + name + ".json";
            try (InputStream stream = loader.getResourceAsStream(packPath)) {
                assertNotNull(stream, "Missing Totem Alchemy reaction " + name);
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    IngredientReaction reaction = AlchemyReactionDataLoader.parseIngredientReaction(
                            Identifier.fromNamespaceAndPath("totem_alchemy", "totem/" + name),
                            json
                    );
                    assertEquals("totem", reaction.ingredient().id().getNamespace(), name);
                    assertEquals(
                            Identifier.fromNamespaceAndPath("totem", "alchemy/awkward"),
                            reaction.baseId(),
                            name
                    );
                    assertEquals(0.8D, reaction.successChance(), name);
                    assertEquals(1.0D, reaction.effectYield(), name);
                    assertEquals(1, reaction.maxDose(), name);
                    assertTrue(reaction.brewingStandCompatible(), name);
                    assertEquals(3, reaction.outcomes().size(), name);
                }
            }
            assertNull(
                    loader.getResource(LEGACY_PREFIX + name + ".json"),
                    "Always-on legacy Totem reaction remains for " + name
            );
        }
    }

    @Test
    void cocoaPowderKeepsTotemOwnedSaturationOutcome() throws Exception {
        ClassLoader loader = TotemOnlyIngredientReactionPackTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(PACK_PREFIX + "cocoa_powder.json")) {
            assertNotNull(stream);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                IngredientReaction reaction = AlchemyReactionDataLoader.parseIngredientReaction(
                        Identifier.fromNamespaceAndPath("totem_alchemy", "totem/cocoa_powder"),
                        JsonParser.parseReader(reader).getAsJsonObject()
                );
                assertTrue(reaction.outcomes().stream().anyMatch(outcome ->
                        outcome.resultPotionId().equals(
                                Identifier.fromNamespaceAndPath("totem", "alchemy/saturation")
                        ) && Math.abs(outcome.chance() - 0.12D) < 0.000_001D
                ));
                assertFalse(reaction.outcomes().isEmpty());
            }
        }
    }

    @Test
    void processingTimesRemainUnchanged() throws Exception {
        ClassLoader loader = TotemOnlyIngredientReactionPackTest.class.getClassLoader();
        assertEquals(320, readReaction(loader, "cocoa_powder").processingTicks());
        assertEquals(400, readReaction(loader, "pig_manure").processingTicks());
        assertEquals(380, readReaction(loader, "wood_ash").processingTicks());
    }

    private static IngredientReaction readReaction(ClassLoader loader, String name) throws Exception {
        try (InputStream stream = loader.getResourceAsStream(PACK_PREFIX + name + ".json")) {
            assertNotNull(stream);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return AlchemyReactionDataLoader.parseIngredientReaction(
                        Identifier.fromNamespaceAndPath("totem_alchemy", "totem/" + name),
                        JsonParser.parseReader(reader).getAsJsonObject()
                );
            }
        }
    }
}
