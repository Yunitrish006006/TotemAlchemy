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

class MinecraftBaseReactionPackTest {
    private static final String NETHER_WART_RESOURCE =
            "resourcepacks/minecraft_alchemy/data/minecraft_alchemy/alchemy/base_reactions/vanilla/nether_wart.json";
    private static final String LEGACY_SETTINGS_RESOURCE =
            "data/totem/alchemy/brewing_material_settings/defaults.json";

    @Test
    void minecraftAlchemyOwnsNetherWartBaseReaction() throws Exception {
        JsonObject json = readJson(NETHER_WART_RESOURCE);
        BaseReaction reaction = AlchemyReactionDataLoader.parseBaseReaction(
                Identifier.fromNamespaceAndPath("minecraft_alchemy", "vanilla/nether_wart"),
                json
        );

        assertEquals(
                Identifier.fromNamespaceAndPath("minecraft", "nether_wart"),
                reaction.starter().id()
        );
        assertEquals(
                Identifier.fromNamespaceAndPath("totem", "alchemy/awkward"),
                reaction.resultBaseId()
        );
        assertEquals(
                1.0D,
                reaction.minimumLiquidFractions().get(
                        Identifier.fromNamespaceAndPath("minecraft", "water")
                )
        );
        assertEquals(1.0D, reaction.successChance());
        assertEquals(3.0D, reaction.activationYield());
        assertEquals(400, reaction.processingTicks());
        assertTrue(reaction.brewingStandCompatible());
    }

    @Test
    void legacyMaterialSettingsNoLongerOwnMinecraftStarterFlag() throws Exception {
        JsonObject root = readJson(LEGACY_SETTINGS_RESOURCE);
        boolean foundNetherWart = false;
        boolean netherWartStarter = false;
        boolean redMushroomStarter = false;

        for (var element : root.getAsJsonArray("ingredients")) {
            JsonObject entry = element.getAsJsonObject();
            String ingredient = entry.get("ingredient").getAsString();
            if ("minecraft:nether_wart".equals(ingredient)) {
                foundNetherWart = true;
                netherWartStarter = entry.has("starter") && entry.get("starter").getAsBoolean();
                assertEquals(400, entry.get("processing_ticks").getAsInt());
            } else if ("minecraft:red_mushroom".equals(ingredient)) {
                redMushroomStarter = entry.has("starter") && entry.get("starter").getAsBoolean();
            }
        }

        assertTrue(foundNetherWart);
        assertFalse(netherWartStarter);
        assertTrue(redMushroomStarter, "M10-T03 must remain responsible for Totem mushroom starter migration");
    }

    private static JsonObject readJson(String path) throws Exception {
        ClassLoader loader = MinecraftBaseReactionPackTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing test resource " + path);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
