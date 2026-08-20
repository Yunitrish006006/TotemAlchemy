package dev.totem.alchemy.alchemy;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.totem.alchemy.TotemAlchemy;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/** Server-data settings for material reaction duration and starter-base behavior. */
public final class BrewingMaterialSettings {
    public static final int DEFAULT_PROCESSING_TICKS = 20 * 20;

    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "totem_alchemy/brewing_material_settings";
    private static volatile Map<String, Setting> settings = Map.of();

    private BrewingMaterialSettings() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath("totem-alchemy", "brewing_material_settings");
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                load(resourceManager);
            }
        });
    }

    public static int processingTicks(Item ingredient) {
        if (ingredient == null) {
            return DEFAULT_PROCESSING_TICKS;
        }
        return processingTicks(BuiltInRegistries.ITEM.getKey(ingredient).toString());
    }

    public static int processingTicks(String ingredientId) {
        Setting setting = settings.get(ingredientId);
        return setting == null ? DEFAULT_PROCESSING_TICKS : setting.processingTicks();
    }

    public static boolean isStarter(Item ingredient) {
        return ingredient != null && isStarter(BuiltInRegistries.ITEM.getKey(ingredient).toString());
    }

    public static boolean isStarter(String ingredientId) {
        Setting setting = settings.get(ingredientId);
        return setting != null && setting.starter();
    }

    public static boolean isConfigured(Item ingredient) {
        return ingredient != null && settings.containsKey(BuiltInRegistries.ITEM.getKey(ingredient).toString());
    }

    private static void load(ResourceManager resourceManager) {
        Map<String, Setting> loaded = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : resourceManager.listResources(
                DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null || !root.has("ingredients") || !root.get("ingredients").isJsonArray()) {
                    continue;
                }
                for (JsonElement element : root.getAsJsonArray("ingredients")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject json = element.getAsJsonObject();
                    if (!json.has("ingredient")) {
                        continue;
                    }
                    String ingredientId = json.get("ingredient").getAsString();
                    int processingTicks = json.has("processing_ticks")
                            ? json.get("processing_ticks").getAsInt()
                            : DEFAULT_PROCESSING_TICKS;
                    boolean starter = json.has("starter") && json.get("starter").getAsBoolean();
                    if (processingTicks <= 0) {
                        TotemAlchemy.LOGGER.warn("Ignoring invalid processing_ticks {} for {} in {}",
                                processingTicks, ingredientId, entry.getKey());
                        continue;
                    }
                    loaded.put(ingredientId, new Setting(processingTicks, starter));
                }
            } catch (Exception exception) {
                TotemAlchemy.LOGGER.warn("Unable to load brewing material settings from {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        }
        settings = Map.copyOf(loaded);
        TotemAlchemy.LOGGER.info("Loaded brewing material settings for {} ingredients", settings.size());
    }

    public record Setting(int processingTicks, boolean starter) {
        public Setting {
            processingTicks = Math.max(1, processingTicks);
        }
    }
}
