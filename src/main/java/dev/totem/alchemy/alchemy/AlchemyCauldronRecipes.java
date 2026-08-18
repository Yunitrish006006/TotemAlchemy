package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.TotemAlchemy;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AlchemyCauldronRecipes {
    private static final Gson GSON = new Gson();
    private static final String RECIPE_DIRECTORY = "deadrecall/cauldron_recipes";
    private static final Identifier RELOAD_LISTENER_ID =
            Identifier.fromNamespaceAndPath("deadrecall", "alchemy_cauldron_recipes");

    private static Map<Identifier, AlchemyCauldronRecipe> recipes = Map.of();

    private AlchemyCauldronRecipes() {
    }

    public static void registerReloadListener() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return RELOAD_LISTENER_ID;
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                load(resourceManager);
            }
        });
    }

    public static Collection<AlchemyCauldronRecipe> all() {
        return recipes.values();
    }

    public static AlchemyCauldronRecipe get(Identifier id) {
        return recipes.get(id);
    }

    public static SoundEvent getSound(Identifier id) {
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.getValue(id);
    }

    private static void load(ResourceManager resourceManager) {
        Map<Identifier, AlchemyCauldronRecipe> loaded = new LinkedHashMap<>();
        Map<Identifier, Resource> resources = resourceManager.listResources(
                RECIPE_DIRECTORY,
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier resourceId = entry.getKey();
            Identifier recipeId = toRecipeId(resourceId);
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                loaded.put(recipeId, parseRecipe(recipeId, json));
            } catch (IOException | RuntimeException exception) {
                TotemAlchemy.LOGGER.warn("無法載入煉藥鍋配方 {}：{}", resourceId, exception.getMessage());
            }
        }

        recipes = Map.copyOf(loaded);
        TotemAlchemy.LOGGER.info("已載入 {} 個煉藥鍋配方", recipes.size());
    }

    private static Identifier toRecipeId(Identifier resourceId) {
        String path = resourceId.getPath();
        String recipePath = path.substring(RECIPE_DIRECTORY.length() + 1, path.length() - ".json".length());
        return Identifier.fromNamespaceAndPath(resourceId.getNamespace(), recipePath);
    }

    private static AlchemyCauldronRecipe parseRecipe(Identifier id, JsonObject json) {
        if (json == null) {
            throw new JsonParseException("recipe json is empty");
        }

        AlchemyCauldronRecipe.StartState startState = parseStartState(getString(json, "start"));
        int initialLevel = getInt(json, "initial_level", 3);
        boolean requiresLitCampfire = getBoolean(json, "requires_lit_campfire", true);
        AlchemyCauldronRecipe.CookMode cookMode = parseCookMode(getString(json, "cook_mode"));
        int cookTicks = getInt(json, "cook_ticks", 200);
        boolean consumeLevelPerCook = getBoolean(json, "consume_level_per_cook", false);
        double successChance = getDouble(json, "success_chance", 0.8D);
        if (!Double.isFinite(successChance) || successChance < 0.0D || successChance > 1.0D) {
            throw new JsonParseException("success_chance must be between 0.0 and 1.0");
        }
        String successMessageKey = getString(
                json,
                "success_message",
                "message.deadrecall.alchemy.brew_success"
        );
        String failureMessageKey = getString(
                json,
                "failure_message",
                "message.deadrecall.alchemy.brew_failure"
        );
        Identifier failureSound = json.has("failure_sound")
                ? getOptionalIdentifier(json, "failure_sound")
                : Identifier.parse("minecraft:block.fire.extinguish");
        String defaultMessageKey = getString(json, "ingredient_message", "message.deadrecall.alchemy.ingredient_added");
        Identifier defaultAddSound = getOptionalIdentifier(json, "ingredient_sound");
        Identifier completeSound = getOptionalIdentifier(json, "complete_sound");

        JsonArray ingredientsJson = getArray(json, "ingredients");
        List<AlchemyCauldronRecipe.IngredientStep> ingredients = new ArrayList<>();
        for (JsonElement element : ingredientsJson) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("ingredient must be an object");
            }
            ingredients.add(parseIngredient(element.getAsJsonObject(), successChance));
        }
        if (ingredients.isEmpty()) {
            throw new JsonParseException("ingredients must not be empty");
        }

        AlchemyCauldronRecipe.Result result = parseResult(getObject(json, "result"));
        return new AlchemyCauldronRecipe(
                id,
                startState,
                initialLevel,
                requiresLitCampfire,
                cookMode,
                cookTicks,
                consumeLevelPerCook,
                successChance,
                successMessageKey,
                failureMessageKey,
                failureSound,
                List.copyOf(ingredients),
                result,
                defaultMessageKey,
                defaultAddSound,
                completeSound
        );
    }

    private static AlchemyCauldronRecipe.IngredientStep parseIngredient(JsonObject json, double defaultSuccessChance) {
        String id = getString(json, "id");
        List<Item> items = parseItems(json);
        Item remainder = getOptionalItem(json, "remainder");
        boolean allowRightClick = getBoolean(json, "allow_right_click", true);
        boolean allowDropped = getBoolean(json, "allow_dropped", true);
        boolean canStartRecipe = getBoolean(json, "can_start", true);
        double ingredientSuccessChance = getDouble(json, "success_chance", defaultSuccessChance);
        if (!Double.isFinite(ingredientSuccessChance)
                || ingredientSuccessChance < 0.0D
                || ingredientSuccessChance > 1.0D) {
            throw new JsonParseException("ingredient " + id + " success_chance must be between 0.0 and 1.0");
        }
        String messageKey = getString(json, "message", null);
        Identifier sound = getOptionalIdentifier(json, "sound");

        if (items.isEmpty()) {
            throw new JsonParseException("ingredient " + id + " has no valid items");
        }

        return new AlchemyCauldronRecipe.IngredientStep(
                id,
                List.copyOf(items),
                remainder,
                allowRightClick,
                allowDropped,
                canStartRecipe,
                ingredientSuccessChance,
                messageKey,
                sound
        );
    }

    private static List<Item> parseItems(JsonObject json) {
        List<Item> items = new ArrayList<>();
        if (json.has("item")) {
            items.add(requireItem(json.get("item").getAsString()));
        }
        if (json.has("items")) {
            JsonArray array = getArray(json, "items");
            for (JsonElement element : array) {
                items.add(requireItem(element.getAsString()));
            }
        }
        return items;
    }

    private static AlchemyCauldronRecipe.Result parseResult(JsonObject json) {
        AlchemyCauldronRecipe.ResultType type = parseResultType(getString(json, "type"));
        Item item = getOptionalItem(json, "item");
        int count = getInt(json, "count", 1);
        Item containerItem = getOptionalItem(json, "container_item");
        String messageKey = getString(json, "message", null);
        Identifier sound = getOptionalIdentifier(json, "sound");

        if (type == AlchemyCauldronRecipe.ResultType.DROP_ITEM && (item == null || item == Items.AIR)) {
            throw new JsonParseException("drop_item result requires item");
        }
        if (type == AlchemyCauldronRecipe.ResultType.BOTTLED_ITEM) {
            if (item == null || item == Items.AIR) {
                throw new JsonParseException("bottled_item result requires item");
            }
            if (containerItem == null || containerItem == Items.AIR) {
                throw new JsonParseException("bottled_item result requires container_item");
            }
        }

        return new AlchemyCauldronRecipe.Result(type, item, count, containerItem, messageKey, sound);
    }

    private static AlchemyCauldronRecipe.StartState parseStartState(String value) {
        return switch (value) {
            case "empty_cauldron" -> AlchemyCauldronRecipe.StartState.EMPTY_CAULDRON;
            case "full_water_cauldron" -> AlchemyCauldronRecipe.StartState.FULL_WATER_CAULDRON;
            default -> throw new JsonParseException("unknown start state: " + value);
        };
    }

    private static AlchemyCauldronRecipe.CookMode parseCookMode(String value) {
        return switch (value) {
            case "per_ingredient" -> AlchemyCauldronRecipe.CookMode.PER_INGREDIENT;
            case "after_all_inputs" -> AlchemyCauldronRecipe.CookMode.AFTER_ALL_INPUTS;
            default -> throw new JsonParseException("unknown cook mode: " + value);
        };
    }

    private static AlchemyCauldronRecipe.ResultType parseResultType(String value) {
        return switch (value) {
            case "drop_item" -> AlchemyCauldronRecipe.ResultType.DROP_ITEM;
            case "bottled_item" -> AlchemyCauldronRecipe.ResultType.BOTTLED_ITEM;
            default -> throw new JsonParseException("unknown result type: " + value);
        };
    }

    private static Item requireItem(String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            throw new JsonParseException("unknown item: " + rawId);
        }
        return BuiltInRegistries.ITEM.getValue(id);
    }

    private static Item getOptionalItem(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        return requireItem(json.get(key).getAsString());
    }

    private static Identifier getOptionalIdentifier(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        String rawId = json.get(key).getAsString();
        Identifier id = Identifier.tryParse(rawId);
        if (id == null) {
            throw new JsonParseException("invalid identifier for " + key + ": " + rawId);
        }
        return id;
    }

    private static JsonObject getObject(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            throw new JsonParseException("missing object: " + key);
        }
        return json.getAsJsonObject(key);
    }

    private static JsonArray getArray(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            throw new JsonParseException("missing array: " + key);
        }
        return json.getAsJsonArray(key);
    }

    private static String getString(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new JsonParseException("missing string: " + key);
        }
        return json.get(key).getAsString();
    }

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
}
