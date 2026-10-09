package dev.totem.alchemy.reaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TotemBrewingRecipePackTest {
    private static final String PACK_PREFIX =
            "resourcepacks/totem_alchemy/data/totem/recipe/brewing/";
    private static final String LEGACY_PREFIX =
            "data/totem/recipe/brewing/";
    private static final List<String> RECIPES = List.of(
            "lingering_potion_awkward_fermented_spider_eye.json",
            "lingering_potion_awkward_red_mushroom.json",
            "lingering_potion_cherry_swiftness_glowstone_dust.json",
            "lingering_potion_cherry_swiftness_redstone.json",
            "lingering_potion_firefly_strength_glowstone_dust.json",
            "lingering_potion_firefly_strength_redstone.json",
            "lingering_potion_long_strength_firefly_bush.json",
            "lingering_potion_long_swiftness_cherry_leaves.json",
            "lingering_potion_resistance_glowstone_dust.json",
            "lingering_potion_resistance_redstone.json",
            "lingering_potion_saturation_glowstone_dust.json",
            "lingering_potion_strength_firefly_bush.json",
            "lingering_potion_strong_strength_firefly_bush.json",
            "lingering_potion_strong_swiftness_cherry_leaves.json",
            "lingering_potion_swiftness_cherry_leaves.json",
            "lingering_potion_water_red_mushroom.json",
            "potion_awkward_fermented_spider_eye.json",
            "potion_awkward_red_mushroom.json",
            "potion_cherry_swiftness_glowstone_dust.json",
            "potion_cherry_swiftness_redstone.json",
            "potion_firefly_strength_glowstone_dust.json",
            "potion_firefly_strength_redstone.json",
            "potion_long_strength_firefly_bush.json",
            "potion_long_swiftness_cherry_leaves.json",
            "potion_resistance_glowstone_dust.json",
            "potion_resistance_redstone.json",
            "potion_saturation_glowstone_dust.json",
            "potion_strength_firefly_bush.json",
            "potion_strong_strength_firefly_bush.json",
            "potion_strong_swiftness_cherry_leaves.json",
            "potion_swiftness_cherry_leaves.json",
            "potion_water_red_mushroom.json",
            "splash_potion_awkward_fermented_spider_eye.json",
            "splash_potion_awkward_red_mushroom.json",
            "splash_potion_cherry_swiftness_glowstone_dust.json",
            "splash_potion_cherry_swiftness_redstone.json",
            "splash_potion_firefly_strength_glowstone_dust.json",
            "splash_potion_firefly_strength_redstone.json",
            "splash_potion_long_strength_firefly_bush.json",
            "splash_potion_long_swiftness_cherry_leaves.json",
            "splash_potion_resistance_glowstone_dust.json",
            "splash_potion_resistance_redstone.json",
            "splash_potion_saturation_glowstone_dust.json",
            "splash_potion_strength_firefly_bush.json",
            "splash_potion_strong_strength_firefly_bush.json",
            "splash_potion_strong_swiftness_cherry_leaves.json",
            "splash_potion_swiftness_cherry_leaves.json",
            "splash_potion_water_red_mushroom.json"
    );

    @Test
    void allFortyEightFixedRecipesBelongToTotemAlchemyPack() throws Exception {
        assertEquals(48, RECIPES.size());
        ClassLoader loader = TotemBrewingRecipePackTest.class.getClassLoader();

        for (String recipe : RECIPES) {
            try (InputStream stream = loader.getResourceAsStream(PACK_PREFIX + recipe)) {
                assertNotNull(stream, "Missing Totem Alchemy Brewing recipe " + recipe);
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    assertEquals("minecraft:brewing", json.get("type").getAsString(), recipe);
                    assertNotNull(json.getAsJsonObject("input"), recipe);
                    assertNotNull(json.getAsJsonObject("reagent"), recipe);
                    assertNotNull(json.getAsJsonObject("output"), recipe);
                }
            }

            assertNull(
                    loader.getResource(LEGACY_PREFIX + recipe),
                    "Always-on legacy Brewing recipe remains for " + recipe
            );
        }
    }
}
