package dev.totem.alchemy.mixture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.totem.alchemy.TotemAlchemy;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads data/<namespace>/alchemy/signature_brews/*.json into a read-only registry.
 *
 * <p>The registry does not participate in scheduling or settlement yet. Failed
 * reloads reject the entire candidate registry, leaving the prior snapshot
 * intact rather than silently enabling only part of a datapack.</p>
 */
public final class SignatureBrewDataLoader {
    static final String DIRECTORY = "alchemy/signature_brews";
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema_version", "priority", "requires_heat", "liquids", "ingredients", "result");
    private static final Set<String> RESULT_FIELDS = Set.of(
            "type", "item", "count", "container_item", "potion");

    private static volatile Map<Identifier, SignatureBrewDefinition> definitions = Map.of();
    private static volatile long revision;

    private SignatureBrewDataLoader() {
    }

    public static Map<Identifier, SignatureBrewDefinition> definitions() {
        return definitions;
    }

    public static long revision() {
        return revision;
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("totem", "alchemy/signature_brews");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        SignatureBrewDataLoader.reload(resourceManager);
                    }
                });
    }

    private static void reload(ResourceManager manager) {
        Map<Identifier, SignatureBrewDefinition> next = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    Identifier resourceId = entry.getKey();
                    try (BufferedReader reader = entry.getValue().openAsReader()) {
                        String filePath = resourceId.getPath();
                        String relative = filePath.substring(
                                DIRECTORY.length() + 1, filePath.length() - ".json".length());
                        Identifier definitionId = Identifier.fromNamespaceAndPath(
                                resourceId.getNamespace(), "alchemy/" + relative);
                        JsonElement element = com.google.gson.JsonParser.parseReader(reader);
                        if (element == null || !element.isJsonObject()) {
                            throw new IllegalArgumentException("Signature definition root must be an object");
                        }
                        next.put(definitionId, parse(definitionId, element.getAsJsonObject()));
                    } catch (Exception exception) {
                        errors.add(resourceId + ": " + exception.getMessage());
                    }
                });
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid signature brew definitions:\n" + String.join("\n", errors));
        }
        definitions = Map.copyOf(next);
        revision++;
        TotemAlchemy.LOGGER.info("Loaded {} signature brew definitions (no runtime settlement yet)",
                definitions.size());
    }

    static SignatureBrewDefinition parse(Identifier id, JsonObject json) {
        if (id == null || json == null) {
            throw new IllegalArgumentException("Signature ID and root object are required");
        }
        rejectUnknown(json, ROOT_FIELDS, "signature");

        int schema = requiredInt(json, "schema_version");
        if (schema != 1) {
            throw new IllegalArgumentException("Unsupported signature schema_version: " + schema);
        }
        int priority = requiredInt(json, "priority");
        boolean requiresHeat = requiredBoolean(json, "requires_heat");

        JsonObject liquids = requiredObject(json, "liquids");
        Map<Identifier, Double> fractions = new LinkedHashMap<>();
        for (var entry : liquids.entrySet()) {
            Identifier liquid = parseId(entry.getKey(), "liquid");
            double amount = number(entry.getValue(), "liquids." + entry.getKey());
            if (fractions.putIfAbsent(liquid, amount) != null) {
                throw new IllegalArgumentException("Duplicate liquid " + liquid);
            }
        }

        JsonArray ingredients = requiredArray(json, "ingredients");
        Set<Identifier> required = new LinkedHashSet<>();
        for (JsonElement ingredient : ingredients) {
            Identifier ingredientId = parseId(string(ingredient, "ingredient"), "ingredient");
            if (!required.add(ingredientId)) {
                throw new IllegalArgumentException("Duplicate signature ingredient: " + ingredientId);
            }
        }

        JsonObject resultObject = requiredObject(json, "result");
        rejectUnknown(resultObject, RESULT_FIELDS, "signature result");
        String typeName = requiredString(resultObject, "type");
        SignatureBrewDefinition.Type type = switch (typeName) {
            case "bottled_item" -> SignatureBrewDefinition.Type.BOTTLED_ITEM;
            case "drop_item" -> SignatureBrewDefinition.Type.DROP_ITEM;
            default -> throw new IllegalArgumentException("Unsupported signature result type: " + typeName);
        };
        Identifier item = parseId(requiredString(resultObject, "item"), "result.item");
        int count = resultObject.has("count") ? requiredInt(resultObject, "count") : 1;
        Identifier container = optionalId(resultObject, "container_item");
        Identifier potion = optionalId(resultObject, "potion");

        return new SignatureBrewDefinition(
                new SignatureBrewResolver.Signature(id, priority, fractions, required),
                requiresHeat,
                new SignatureBrewDefinition.Result(type, item, count, container, potion));
    }

    private static Identifier optionalId(JsonObject object, String key) {
        return object.has(key) ? parseId(requiredString(object, key), key) : null;
    }

    private static Identifier parseId(String value, String field) {
        try {
            return Identifier.parse(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid identifier in " + field + ": " + value, error);
        }
    }

    private static void rejectUnknown(JsonObject object, Set<String> allowed, String field) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown field in " + field + ": " + key);
            }
        }
    }

    private static JsonObject requiredObject(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String requiredString(JsonObject json, String key) {
        return string(json.get(key), key);
    }

    private static String string(JsonElement element, String key) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()
                || element.getAsString().isBlank()) {
            throw new IllegalArgumentException(key + " must be a nonblank string");
        }
        return element.getAsString();
    }

    private static int requiredInt(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        try {
            return new BigDecimal(element.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IllegalArgumentException(key + " must be a finite integer", error);
        }
    }

    private static boolean requiredBoolean(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static double number(JsonElement element, String key) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        double parsed = element.getAsDouble();
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(key + " must be finite");
        }
        return parsed;
    }
}
