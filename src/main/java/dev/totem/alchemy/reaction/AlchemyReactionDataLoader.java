package dev.totem.alchemy.reaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.totem.alchemy.TotemAlchemy;
import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.math.BigDecimal;
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
 *
 * <p>Reloads are atomic. A malformed reaction rejects the complete next-generation reaction reload
 * instead of silently dropping only the invalid resource and leaving a partial registry active.</p>
 */
public final class AlchemyReactionDataLoader {
    static final String BASE_REACTION_DIRECTORY = "alchemy/base_reactions";
    static final String INGREDIENT_REACTION_DIRECTORY = "alchemy/ingredient_reactions";

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
        List<LoadError> errors = new ArrayList<>();
        List<BaseReaction> loadedBases = loadBaseReactions(resourceManager, errors);
        List<IngredientReaction> loadedIngredients = loadIngredientReactions(resourceManager, errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException(formatReloadErrors(errors));
        }

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

    private static List<BaseReaction> loadBaseReactions(ResourceManager resourceManager, List<LoadError> errors) {
        List<BaseReaction> loaded = new ArrayList<>();
        resourceManager.listResources(BASE_REACTION_DIRECTORY, AlchemyReactionDataLoader::isJson)
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    try (BufferedReader reader = entry.getValue().openAsReader()) {
                        JsonObject json = readRootObject(reader, "base reaction");
                        loaded.add(parseBaseReaction(reactionId(entry.getKey(), BASE_REACTION_DIRECTORY), json));
                    } catch (Exception exception) {
                        errors.add(new LoadError(
                                "base reaction",
                                entry.getKey(),
                                usefulMessage(exception)
                        ));
                    }
                });
        return loaded;
    }

    private static List<IngredientReaction> loadIngredientReactions(
            ResourceManager resourceManager,
            List<LoadError> errors
    ) {
        List<IngredientReaction> loaded = new ArrayList<>();
        resourceManager.listResources(INGREDIENT_REACTION_DIRECTORY, AlchemyReactionDataLoader::isJson)
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    try (BufferedReader reader = entry.getValue().openAsReader()) {
                        JsonObject json = readRootObject(reader, "ingredient reaction");
                        loaded.add(parseIngredientReaction(
                                reactionId(entry.getKey(), INGREDIENT_REACTION_DIRECTORY),
                                json
                        ));
                    } catch (Exception exception) {
                        errors.add(new LoadError(
                                "ingredient reaction",
                                entry.getKey(),
                                usefulMessage(exception)
                        ));
                    }
                });
        return loaded;
    }

    private static JsonObject readRootObject(BufferedReader reader, String description) {
        JsonElement root = JsonParser.parseReader(reader);
        if (root == null || root.isJsonNull() || !root.isJsonObject()) {
            throw new IllegalArgumentException(description + " root must be a JSON object");
        }
        return root.getAsJsonObject();
    }

    static BaseReaction parseBaseReaction(Identifier id, JsonObject json) {
        requireObject(json, "base reaction");

        Map<Identifier, Double> liquids = new LinkedHashMap<>();
        JsonObject liquidJson = requiredObject(json, "liquids");
        for (Map.Entry<String, JsonElement> entry : liquidJson.entrySet()) {
            liquids.put(
                    requiredId(entry.getKey(), "liquid"),
                    requiredDouble(entry.getValue(), "liquids." + entry.getKey())
            );
        }

        return new BaseReaction(
                id,
                liquids,
                parseIngredient(required(json, "starter"), "starter"),
                requiredId(requiredString(json, "result_base"), "result_base"),
                optionalDouble(json, "success_chance", 1.0D),
                optionalDouble(json, "activation_yield", 1.0D),
                optionalInt(json, "processing_ticks", BrewingMaterialSettings.DEFAULT_PROCESSING_TICKS),
                optionalBoolean(json, "brewing_stand", false),
                optionalInt(json, "priority", 0)
        );
    }

    static IngredientReaction parseIngredientReaction(Identifier id, JsonObject json) {
        requireObject(json, "ingredient reaction");

        List<ReactionOutcome> outcomes = new ArrayList<>();
        if (json.has("outcomes")) {
            JsonElement outcomesElement = nonNull(json.get("outcomes"), "outcomes");
            if (!outcomesElement.isJsonArray()) {
                throw new IllegalArgumentException("outcomes must be an array");
            }
            int index = 0;
            for (JsonElement element : outcomesElement.getAsJsonArray()) {
                String prefix = "outcomes[" + index + "]";
                if (element == null || element.isJsonNull() || !element.isJsonObject()) {
                    throw new IllegalArgumentException(prefix + " must be an object");
                }
                JsonObject outcome = element.getAsJsonObject();
                try {
                    outcomes.add(new ReactionOutcome(
                            requiredId(requiredString(outcome, "potion"), prefix + ".potion"),
                            optionalDouble(outcome, "chance", 1.0D),
                            optionalInt(outcome, "priority", 0)
                    ));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException(prefix + ": " + exception.getMessage(), exception);
                }
                index++;
            }
        }

        return new IngredientReaction(
                id,
                requiredId(requiredString(json, "base"), "base"),
                parseIngredient(required(json, "ingredient"), "ingredient"),
                optionalDouble(json, "success_chance", 1.0D),
                optionalDouble(json, "effect_yield", 1.0D),
                optionalInt(json, "processing_ticks", BrewingMaterialSettings.DEFAULT_PROCESSING_TICKS),
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
        nonNull(element, fieldName);
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
                ? ReactionIngredient.item(requiredId(requiredString(object, "item"), fieldName + ".item"))
                : ReactionIngredient.tag(requiredId(requiredString(object, "tag"), fieldName + ".tag"));
    }

    static String formatReloadErrors(List<LoadError> errors) {
        StringBuilder message = new StringBuilder(
                "Alchemy reaction reload rejected because "
                        + errors.size()
                        + " resource"
                        + (errors.size() == 1 ? " is" : "s are")
                        + " invalid:"
        );
        errors.stream()
                .sorted(Comparator
                        .comparing((LoadError error) -> error.resourceId().toString())
                        .thenComparing(LoadError::reactionType))
                .forEach(error -> message
                        .append("\n - ")
                        .append(error.resourceId())
                        .append(" [")
                        .append(error.reactionType())
                        .append("]: ")
                        .append(error.reason()));
        return message.toString();
    }

    private static String usefulMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
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
        return nonNull(object.get(field), field);
    }

    private static JsonElement nonNull(JsonElement element, String field) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException(field + " cannot be null");
        }
        return element;
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
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    private static Identifier requiredId(String value, String field) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid identifier for " + field + ": " + value);
        }
        return id;
    }

    private static double requiredDouble(JsonElement element, String field) {
        nonNull(element, field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return value;
    }

    private static double optionalDouble(JsonObject object, String field, double fallback) {
        return object.has(field) ? requiredDouble(object.get(field), field) : fallback;
    }

    private static int optionalInt(JsonObject object, String field, int fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        JsonElement element = nonNull(object.get(field), field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        try {
            BigDecimal value = primitive.getAsBigDecimal();
            return value.intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an integer", exception);
        }
    }

    private static boolean optionalBoolean(JsonObject object, String field, boolean fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        JsonElement element = nonNull(object.get(field), field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    record LoadError(String reactionType, Identifier resourceId, String reason) {
    }
}
