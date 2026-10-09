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

class MinecraftIngredientReactionPackTest {
    private static final String NEW_PREFIX =
            "resourcepacks/minecraft_alchemy/data/minecraft_alchemy/alchemy/ingredient_reactions/vanilla/";
    private static final String OLD_PREFIX =
            "data/totem/alchemy/ingredient_reactions/legacy/minecraft/";
    private static final List<String> MINECRAFT_REACTIONS = List.of(
            "apple",
            "bamboo",
            "blaze_powder",
            "blaze_rod",
            "bone_meal",
            "breeze_rod",
            "brown_mushroom",
            "cactus",
            "charcoal",
            "chorus_fruit",
            "cobweb",
            "cocoa_beans",
            "egg",
            "enchanted_golden_apple",
            "ender_pearl",
            "feather",
            "fermented_spider_eye",
            "ghast_tear",
            "glistering_melon_slice",
            "glow_berries",
            "glow_ink_sac",
            "golden_apple",
            "golden_carrot",
            "honey_block",
            "honey_bottle",
            "honeycomb",
            "ink_sac",
            "kelp",
            "leather",
            "magma_cream",
            "melon_slice",
            "nautilus_shell",
            "phantom_membrane",
            "pufferfish",
            "rabbit_foot",
            "rabbit_hide",
            "red_mushroom",
            "resin_clump",
            "rotten_flesh",
            "shulker_shell",
            "slime_ball",
            "slime_block",
            "spider_eye",
            "stone",
            "string",
            "sugar",
            "sugar_cane",
            "sweet_berries",
            "turtle_helmet",
            "wheat"
    );

    @Test
    void allMinecraftIngredientReactionsBelongToMinecraftAlchemyCore() throws Exception {
        assertEquals(50, MINECRAFT_REACTIONS.size());

        ClassLoader loader = MinecraftIngredientReactionPackTest.class.getClassLoader();
        for (String name : MINECRAFT_REACTIONS) {
            String newPath = NEW_PREFIX + name + ".json";
            JsonObject json = readJson(loader, newPath);
            IngredientReaction reaction = AlchemyReactionDataLoader.parseIngredientReaction(
                    Identifier.fromNamespaceAndPath("minecraft_alchemy", "vanilla/" + name),
                    json
            );

            assertEquals("minecraft", reaction.ingredient().id().getNamespace(), name);
            assertEquals(
                    Identifier.fromNamespaceAndPath("totem", "alchemy/awkward"),
                    reaction.baseId(),
                    name
            );
            assertFalse(reaction.outcomes().isEmpty(), name);
            assertTrue(
                    reaction.outcomes().stream()
                            .allMatch(outcome -> "minecraft".equals(outcome.resultPotionId().getNamespace())),
                    name + " still contains a Totem-owned outcome"
            );

            assertNull(
                    loader.getResource(OLD_PREFIX + name + ".json"),
                    "Legacy always-on Minecraft reaction still exists for " + name
            );
        }
    }

    @Test
    void sugarCoreKeepsApprovedScalarPolicyAndOnlyMinecraftOutcomes() throws Exception {
        IngredientReaction sugar = AlchemyReactionDataLoader.parseIngredientReaction(
                Identifier.fromNamespaceAndPath("minecraft_alchemy", "vanilla/sugar"),
                readJson(MinecraftIngredientReactionPackTest.class.getClassLoader(), NEW_PREFIX + "sugar.json")
        );

        assertEquals(0.90D, sugar.successChance());
        assertEquals(1.0D, sugar.effectYield());
        assertEquals(300, sugar.processingTicks());
        assertEquals(1, sugar.maxDose());
        assertTrue(sugar.brewingStandCompatible());
        assertEquals(
                List.of(
                        Identifier.fromNamespaceAndPath("minecraft", "swiftness"),
                        Identifier.fromNamespaceAndPath("minecraft", "slowness")
                ),
                sugar.outcomes().stream().map(ReactionOutcome::resultPotionId).toList()
        );
    }

    private static JsonObject readJson(ClassLoader loader, String path) throws Exception {
        try (InputStream stream = loader.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing reaction resource " + path);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
