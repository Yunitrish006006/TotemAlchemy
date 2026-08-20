package dev.totem.alchemy.client.manual;

import dev.totem.alchemy.discovery.AlchemyDiscoveryKey;
import dev.totem.alchemy.network.AlchemyResearchPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Client snapshot containing only sample counts, derived labels, and observed processing time. */
public final class AlchemyResearchClientCache {
    private static volatile Map<String, ResearchEntry> entries = Map.of();
    private static volatile Map<String, TimingEntry> timings = Map.of();

    private AlchemyResearchClientCache() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                AlchemyResearchPayload.TYPE,
                (payload, context) -> context.client().execute(() -> replace(payload.entries()))
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            entries = Map.of();
            timings = Map.of();
        });
    }

    public static int samples(Item ingredient) {
        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient).toString();
        String prefix = ingredientId + ">";
        int outcomeSamples = entries.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .mapToInt(entry -> entry.getValue().samples())
                .max().orElse(0);
        TimingEntry timing = timings.get(ingredientId);
        return Math.max(outcomeSamples, timing == null ? 0 : timing.samples());
    }

    public static int processingTicks(Item ingredient) {
        TimingEntry timing = timings.get(BuiltInRegistries.ITEM.getKey(ingredient).toString());
        return timing == null ? 0 : timing.averageTicks();
    }

    public static String tierKey(Item ingredient, Holder<Potion> potion) {
        ResearchEntry entry = entries.get(AlchemyDiscoveryKey.of(ingredient, potion));
        String tier = entry == null ? "novice" : entry.tier().toLowerCase(Locale.ROOT);
        return "book.totem_alchemy.research.tier." + tier;
    }

    public static String frequencyKey(Item ingredient, Holder<Potion> potion) {
        ResearchEntry entry = entries.get(AlchemyDiscoveryKey.of(ingredient, potion));
        String frequency = entry == null ? "very_rare" : entry.frequency().toLowerCase(Locale.ROOT);
        return "book.totem_alchemy.research.frequency." + frequency;
    }

    private static void replace(Iterable<String> encodedEntries) {
        Map<String, ResearchEntry> researchCopy = new HashMap<>();
        Map<String, TimingEntry> timingCopy = new HashMap<>();
        for (String encoded : encodedEntries) {
            if (encoded.startsWith("O|")) {
                String[] parts = encoded.split("\\|", 5);
                if (parts.length != 5) {
                    continue;
                }
                try {
                    researchCopy.put(parts[1], new ResearchEntry(Integer.parseInt(parts[2]), parts[3], parts[4]));
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            if (encoded.startsWith("T|")) {
                String[] parts = encoded.split("\\|", 4);
                if (parts.length != 4) {
                    continue;
                }
                try {
                    timingCopy.put(parts[1], new TimingEntry(Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
                } catch (NumberFormatException ignored) {
                }
                continue;
            }

            // Compatibility with the first research snapshot format used before typed entries were introduced.
            String[] legacy = encoded.split("\\|", 4);
            if (legacy.length == 4) {
                try {
                    researchCopy.put(legacy[0], new ResearchEntry(
                            Integer.parseInt(legacy[1]), legacy[2], legacy[3]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        entries = Map.copyOf(researchCopy);
        timings = Map.copyOf(timingCopy);
    }

    private record ResearchEntry(int samples, String tier, String frequency) {
    }

    private record TimingEntry(int samples, int averageTicks) {
    }
}
