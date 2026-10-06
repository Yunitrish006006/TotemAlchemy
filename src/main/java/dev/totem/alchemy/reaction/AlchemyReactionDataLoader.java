package dev.totem.alchemy.reaction;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.totem.alchemy.TotemAlchemy;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads next-generation alchemy reaction definitions without wiring them into gameplay yet.
 *
 * <p>This loader intentionally targets new resource directories so the migration can be staged while
 * existing brewing data remains authoritative until the resolver tasks are complete.</p>
 */
public final class AlchemyReactionDataLoader {
    static final String BASE_REACTION_DIRECTORY = "alchemy/base_reactions";
    static final String INGREDIENT_REACTION_DIRECTORY = "alchemy/ingredient_reactions";

    private static final Gson GSON = new Gson();

    private static volatile List<BaseReaction> baseReactions = List.of();
    private static volatile List<IngredientReaction> ingredientReactions = List.of();
    private static volatile AlchemyReactionIndex index = AlchemyReactionIndex.empty();
    private static volatile long revision;

    private AlchemyReactionDataLoader() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("totem", "alchemy/reaction_data");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        AlchemyReactionDataLoader.reload(resourceManager);
                    }
                });
    }

    public static List<BaseReaction> baseReactions() {
        return baseReactions;
    }

    public static List<IngredientReaction> ingredientReactions() {
        return ingredientReactions;
    }

    public static AlchemyReactionIndex index() {
        return index;
    }

    public static long revision() {
        return revision;
    }

    private static void reload(ResourceManager resourceManager) {
        List<BaseReaction> loadedBases = loadBaseReactions(resourceManager);
        List<IngredientReaction> loadedIngredients = loadIngredientReactions(resourceManager);

        AlchemyReactionIndex nextIndex = AlchemyReactionIndex.build(loadedBases, loadedIngredients);

        baseReactions = List.copyOf(loadedBases);
        ingredientReactions = List.copyOf(loadedIngredients);
        index = nextIndex;
        revision++;

        TotemAlchemy.LOGGER.info(
                "Loaded {} base reactions and {} ingredient reactions",
                baseReactions.size(),
                ingredientReactions.size()
        );
    }

    private static List<BaseReaction> loadBaseReactions(ResourceManager resourceManager) {
        List<BaseReaction> loaded = new ArrayList<>();
        resourceManager.listResources(BASE_REACTION_DIRECTORY, AlchemyReactionDataLoader::isJson)
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    try (BufferedReader reader = entry.getValue().openAsReader()) {
                        JsonObject json = GSON.fromJson(reader, JsonObject.class);
                        loaded.add(parseBaseReaction(reactionId(entry.getKey(), BASE_REACTION_DIRECTORY), json));
                    } catch (Exception exception) {
                        TotemAlchemy.LOGGER.warn(
                                "Unable to load base reaction from {}: {}",
                                entry.getKey(),
                                exception.getMessage()
                        );
                    }
                });
        return loaded;
    }

    private static List<IngredientReaction> loadIngredientReactions(ResourceManager resourceManager) {
        List<IngredientReaction> loaded = new ArrayList<>();
        resourceManager.listResources(INGREDIENT_REACTION_DIRECTORY, AlchemyReactionDataLoader::isJson)
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    try (BufferedReader reader = entry.getValue().openAsReader()) {
                        JsonObject json = GSON.fromJson(reader, JsonObject.class);
                        loaded.add(parseIngredientReaction(
                                reactionId(entry.getKey(), INGREDIENT_REACTION_DIRECTORY),
                                json
                        ));
                    } catch (Exception exception) {
                        TotemAlchemy.LOGGER.warn(
                                "Unable to load ingredient reaction from {}: {}",
                                entry.getKey(),
                                exception.getMessage()
                        );
                    }
                });
        return loaded;
    }

    static BaseReaction parseBaseReaction(Identifier id, JsonObject json) {
        requireObject(json, "base reaction");

        Map<Identifier, Double> liquids = new LinkedHashMap<>();
        JsonObject liquidJson = requiredObject(json, "liquids");
        for (Map.Entry<String, JsonElement> entry : liquidJson.entrySet()) {
            liquids.put(requiredId(entry.getKey(), "liquid"), entry.getValue().getAsDouble());
        }

        return new BaseReaction(
                id,
                liquids,
                parseIngredient(required(json, "starter"), "starter"),
                requiredId(requiredString(json, "result_base"), "result_base"),
                optionalDouble(json, "success_chance", 1.0D),
                optionalDouble(json, "activation_yield", 1.0D),
                optionalBoolean(json, "brewing_stand", false),
                optionalInt(json, "priority", 0)
        );
    }

    static IngredientReaction parseIngredientReaction(Identifier id, JsonObject json) {
        requireObject(json, "ingredient reaction");

        List<ReactionOutcome> outcomes = new ArrayList<>();
        if (json.has("outcomes")) {
            if (!json.get("outcomes").isJsonArray()) {
                throw new IllegalArgumentException("outcomes must be an array");
            }
            for (JsonElement element : json.getAsJsonArray("outcomes")) {
                JsonObject outcome = element.getAsJsonObject();
                outcomes.add(new ReactionOutcome(
                        requiredId(requiredString(outcome, "potion"), "outcome potion"),
                        optionalDouble(outcome, "chance", 1.0D),
                        optionalInt(outcome, "priority", 0)
                ));
            }
        }

        return new IngredientReaction(
                id,
                requiredId(requiredString(json, "base"), "base"),
                parseIngredient(required(json, "ingredient"), "ingredient"),
                optionalDouble(json, "success_chance", 1.0D),
                optionalDouble(json, "effect_yield", 1.0D),
                optionalInt(json, "max_dose", 1),
                optionalBoolean(json, "brewing_stand", false),
                outcomes
        );
    }

    static Identifier reactionId(Identifier resourceId, String directory) {
        String prefix = directory + "/";
        String path = resourceId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            throw new IllegalArgumentException("Resource is outside " + directory + ": " + resourceId);
        }
        String relative = path.substring(prefix.length(), path.length() - ".json".length());
        if (relative.isBlank()) {
            throw new IllegalArgumentException("Reaction resource has no relative id: " + resourceId);
        }
        return Identifier.fromNamespaceAndPath(resourceId.getNamespace(), relative);
    }

    static ReactionIngredient parseIngredient(JsonElement element, String fieldName) {
        if (element == null) {
            throw new IllegalArgumentException("Missing " + fieldName);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return ReactionIngredient.item(requiredId(element.getAsString(), fieldName));
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(fieldName + " must be an item id or selector object");
        }

        JsonObject object = element.getAsJsonObject();
        boolean hasItem = object.has("item");
        boolean hasTag = object.has("tag");
        if (hasItem == hasTag) {
            throw new IllegalArgumentException(fieldName + " must contain exactly one of item or tag");
        }
        return hasItem
                ? ReactionIngredient.item(requiredId(object.get("item").getAsString(), fieldName + ".item"))
                : ReactionIngredient.tag(requiredId(object.get("tag").getAsString(), fieldName + ".tag"));
    }

    private static boolean isJson(Identifier id) {
        return id.getPath().endsWith(".json");
    }

    private static void requireObject(JsonObject object, String description) {
        if (object == null) {
            throw new IllegalArgumentException(description + " must be a JSON object");
        }
    }

    private static JsonElement required(JsonObject object, String field) {
        if (!object.has(field)) {
            throw new IllegalArgumentException("Missing required field " + field);
        }
        return object.get(field);
    }

    private static JsonObject requiredObject(JsonObject object, String field) {
        JsonElement element = required(object, field);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String field) {
        JsonElement element = required(object, field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return element.getAsString();
    }

    private static Identifier requiredId(String value, String field) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid identifier for " + field + ": " + value);
        }
        return id;
    }

    private static double optionalDouble(JsonObject object, String field, double fallback) {
        return object.has(field) ? object.get(field).getAsDouble() : fallback;
    }

    private static int optionalInt(JsonObject object, String field, int fallback) {
        return object.has(field) ? object.get(field).getAsInt() : fallback;
    }

    private static boolean optionalBoolean(JsonObject object, String field, boolean fallback) {
        return object.has(field) ? object.get(field).getAsBoolean() : fallback;
    }
}
