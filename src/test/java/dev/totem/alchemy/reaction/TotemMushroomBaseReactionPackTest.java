package dev.totem.alchemy.reaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotemMushroomBaseReactionPackTest {
    private static final String RESOURCE =
            "resourcepacks/totem_alchemy/data/totem_alchemy/alchemy/base_reactions/mushroom/red_mushroom.json";

    @Test
    void redMushroomBaseBelongsToOptionalTotemAlchemyPack() throws Exception {
        ClassLoader loader = TotemMushroomBaseReactionPackTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(RESOURCE)) {
            assertNotNull(stream, "Missing Totem mushroom base reaction");
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                BaseReaction reaction = AlchemyReactionDataLoader.parseBaseReaction(
                        Identifier.fromNamespaceAndPath("totem_alchemy", "mushroom/red_mushroom"), json);

                assertEquals(Identifier.fromNamespaceAndPath("minecraft", "red_mushroom"),
                        reaction.starter().id());
                assertEquals(Identifier.fromNamespaceAndPath("totem", "alchemy/awkward"),
                        reaction.resultBaseId());
                assertEquals(1.0D,
                        reaction.minimumLiquidFractions().get(Identifier.fromNamespaceAndPath("minecraft", "water")));
                assertEquals(0.8D, reaction.successChance());
                assertEquals(3.0D, reaction.activationYield());
                assertEquals(360, reaction.processingTicks());
                assertTrue(reaction.brewingStandCompatible());
                assertEquals(0, reaction.priority());
            }
        }
    }

    @Test
    void legacySettingsKeepTimingButNotTotemStarterOwnership() throws Exception {
        ClassLoader loader = TotemMushroomBaseReactionPackTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(
                "data/totem/alchemy/brewing_material_settings/defaults.json")) {
            assertNotNull(stream);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject mushroom = null;
                for (var element : root.getAsJsonArray("ingredients")) {
                    JsonObject candidate = element.getAsJsonObject();
                    if ("minecraft:red_mushroom".equals(candidate.get("ingredient").getAsString())) {
                        mushroom = candidate;
                        break;
                    }
                }
                assertNotNull(mushroom);
                assertEquals(360, mushroom.get("processing_ticks").getAsInt());
                assertFalse(mushroom.has("starter") && mushroom.get("starter").getAsBoolean());
            }
        }
    }
}
