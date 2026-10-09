package dev.totem.alchemy.reaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotemReactionExtensionPackTest {
    private static final String PREFIX =
            "resourcepacks/totem_alchemy/data/totem_alchemy/alchemy/ingredient_reaction_extensions/minecraft/";

    @Test
    void allTwentyFiveTotemExtensionsTargetMinecraftCoreReactions() throws Exception {
        Map<String, List<ExpectedOutcome>> expected = expected();
        assertEquals(25, expected.size());

        ClassLoader loader = TotemReactionExtensionPackTest.class.getClassLoader();
        for (var entry : expected.entrySet()) {
            String name = entry.getKey();
            String path = PREFIX + name + ".json";
            try (InputStream stream = loader.getResourceAsStream(path)) {
                assertNotNull(stream, "Missing Totem reaction extension " + name);
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                    assertEquals(
                            Identifier.fromNamespaceAndPath("minecraft_alchemy", "vanilla/" + name),
                            Identifier.parse(json.get("target").getAsString()),
                            name
                    );
                    for (String forbidden : List.of(
                            "base",
                            "ingredient",
                            "success_chance",
                            "effect_yield",
                            "processing_ticks",
                            "max_dose",
                            "brewing_stand"
                    )) {
                        assertFalse(json.has(forbidden), name + " illegally overrides " + forbidden);
                    }

                    IngredientReactionExtension extension =
                            AlchemyReactionDataLoader.parseIngredientReactionExtension(
                                    Identifier.fromNamespaceAndPath("totem_alchemy", "minecraft/" + name),
                                    json
                            );
                    assertEquals(entry.getValue().size(), extension.outcomes().size(), name);
                    for (int i = 0; i < entry.getValue().size(); i++) {
                        ExpectedOutcome expectedOutcome = entry.getValue().get(i);
                        ReactionOutcome actual = extension.outcomes().get(i);
                        assertEquals(expectedOutcome.potion(), actual.resultPotionId(), name);
                        assertEquals(expectedOutcome.chance(), actual.chance(), name);
                        assertEquals(expectedOutcome.priority(), actual.priority(), name);
                        assertTrue("totem".equals(actual.resultPotionId().getNamespace()), name);
                    }
                }
            }
        }
    }

    @Test
    void goldenAppleExtensionKeepsBothTotemSideOutcomes() throws Exception {
        ClassLoader loader = TotemReactionExtensionPackTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(PREFIX + "golden_apple.json")) {
            assertNotNull(stream);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                IngredientReactionExtension extension =
                        AlchemyReactionDataLoader.parseIngredientReactionExtension(
                                Identifier.fromNamespaceAndPath("totem_alchemy", "minecraft/golden_apple"),
                                JsonParser.parseReader(reader).getAsJsonObject()
                        );
                assertEquals(List.of(
                        Identifier.fromNamespaceAndPath("totem", "alchemy/resistance"),
                        Identifier.fromNamespaceAndPath("totem", "alchemy/saturation")
                ), extension.outcomes().stream().map(ReactionOutcome::resultPotionId).toList());
            }
        }
    }

    private static Map<String, List<ExpectedOutcome>> expected() {
        Map<String, List<ExpectedOutcome>> result = new LinkedHashMap<>();
        saturation(result, "apple", 0.97D, 2);
        saturation(result, "brown_mushroom", 0.78D, 3);
        resistance(result, "cactus", 0.07D, 2);
        saturation(result, "cocoa_beans", 0.12D, 2);
        saturation(result, "egg", 0.78D, 3);
        resistance(result, "enchanted_golden_apple", 0.50D, 2);
        resistance(result, "glistering_melon_slice", 0.04D, 1);
        result.put("golden_apple", List.of(
                new ExpectedOutcome(id("totem", "alchemy/resistance"), 0.25D, 2),
                new ExpectedOutcome(id("totem", "alchemy/saturation"), 0.15D, 1)
        ));
        saturation(result, "honey_block", 0.86D, 3);
        saturation(result, "honey_bottle", 0.90D, 3);
        resistance(result, "honeycomb", 0.80D, 3);
        saturation(result, "kelp", 0.10D, 1);
        resistance(result, "leather", 0.82D, 2);
        resistance(result, "magma_cream", 0.04D, 2);
        saturation(result, "melon_slice", 0.96D, 2);
        resistance(result, "nautilus_shell", 0.06D, 1);
        resistance(result, "rabbit_hide", 0.08D, 2);
        saturation(result, "red_mushroom", 0.07D, 1);
        resistance(result, "resin_clump", 0.82D, 3);
        resistance(result, "shulker_shell", 0.84D, 2);
        resistance(result, "stone", 0.06D, 2);
        saturation(result, "sugar", 0.03D, 1);
        saturation(result, "sugar_cane", 0.08D, 1);
        resistance(result, "turtle_helmet", 0.03D, 1);
        saturation(result, "wheat", 0.84D, 2);
        return Map.copyOf(result);
    }

    private static void saturation(Map<String, List<ExpectedOutcome>> map, String name, double chance, int priority) {
        map.put(name, List.of(new ExpectedOutcome(id("totem", "alchemy/saturation"), chance, priority)));
    }

    private static void resistance(Map<String, List<ExpectedOutcome>> map, String name, double chance, int priority) {
        map.put(name, List.of(new ExpectedOutcome(id("totem", "alchemy/resistance"), chance, priority)));
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private record ExpectedOutcome(Identifier potion, double chance, int priority) {}
}
