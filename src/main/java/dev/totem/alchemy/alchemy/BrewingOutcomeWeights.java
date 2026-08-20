package dev.totem.alchemy.alchemy;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.totem.alchemy.TotemAlchemy;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server data-pack overrides for the hidden true distribution of multi-outcome brewing. */
public final class BrewingOutcomeWeights {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "totem_alchemy/brewing_outcome_weights";
    private static volatile Map<String, Map<String, Double>> overrides = Map.of();

    private BrewingOutcomeWeights() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath("totem-alchemy", "brewing_outcome_weights");
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                load(resourceManager);
            }
        });
    }

    public static double weight(Item ingredient, Holder<Potion> potion, double fallback) {
        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient).toString();
        String potionId = BuiltInRegistries.POTION.getKey(potion.value()).toString();
        return overrides.getOrDefault(ingredientId, Map.of()).getOrDefault(potionId, fallback);
    }

    private static void load(ResourceManager resourceManager) {
        Map<String, Map<String, Double>> loaded = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : resourceManager.listResources(
                DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null || !root.has("ingredients") || !root.get("ingredients").isJsonArray()) {
                    continue;
                }
                for (JsonElement element : root.getAsJsonArray("ingredients")) {
                    JsonObject ingredientJson = element.getAsJsonObject();
                    String ingredient = ingredientJson.get("ingredient").getAsString();
                    JsonObject outcomes = ingredientJson.getAsJsonObject("outcomes");
                    Map<String, Double> weights = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> outcome : outcomes.entrySet()) {
                        double weight = outcome.getValue().getAsDouble();
                        if (Double.isFinite(weight) && weight > 0.0D) {
                            weights.put(outcome.getKey(), weight);
                        }
                    }
                    if (!weights.isEmpty()) {
                        loaded.put(ingredient, Map.copyOf(weights));
                    }
                }
            } catch (Exception exception) {
                TotemAlchemy.LOGGER.warn("Unable to load brewing outcome weights from {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        overrides = Map.copyOf(loaded);
        TotemAlchemy.LOGGER.info("Loaded hidden brewing outcome weights for {} ingredients", overrides.size());
    }
}
