package dev.totem.alchemy.client.manual;

import dev.totem.alchemy.discovery.AlchemyDiscoveryKey;
import dev.totem.alchemy.discovery.AlchemyProcessingTimeEstimate;
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
import java.util.Optional;

/** Client snapshot containing only sample counts, derived labels, and server-approved processing-time estimates. */
public final class AlchemyResearchClientCache {
    private static volatile Map<String, ResearchEntry> entries = Map.of();
    private static volatile Map<String, ResearchEntry> noEffectEntries = Map.of();
    private static volatile Map<String, Integer> samplesByIngredient = Map.of();
    private static volatile Map<String, AlchemyProcessingTimeEstimate> timings = Map.of();

    private AlchemyResearchClientCache() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                AlchemyResearchPayload.TYPE,
                (payload, context) -> context.client().execute(() -> replace(payload.entries()))
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            entries = Map.of();
            noEffectEntries = Map.of();
            samplesByIngredient = Map.of();
            timings = Map.of();
        });
    }

    public static int samples(Item ingredient) {
        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient).toString();
        Integer samples = samplesByIngredient.get(ingredientId);
        if (samples != null) return samples;
        String prefix = ingredientId + ">";
        int outcomeSamples = entries.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .mapToInt(entry -> entry.getValue().samples())
                .max().orElse(0);
        ResearchEntry noEffect = noEffectEntries.get(ingredientId);
        int noEffectSamples = noEffect == null ? 0 : noEffect.samples();
        AlchemyProcessingTimeEstimate timing = timings.get(ingredientId);
        return Math.max(Math.max(outcomeSamples, noEffectSamples), timing == null ? 0 : timing.samples());
    }

    public static Optional<AlchemyProcessingTimeEstimate> timeEstimate(Item ingredient) {
        return Optional.ofNullable(timings.get(BuiltInRegistries.ITEM.getKey(ingredient).toString()));
    }

    public static boolean hasOutcome(Item ingredient, Holder<Potion> potion) {
        return entries.containsKey(AlchemyDiscoveryKey.of(ingredient, potion));
    }

    public static boolean hasNoEffectObservation(Item ingredient) {
        return noEffectEntries.containsKey(BuiltInRegistries.ITEM.getKey(ingredient).toString());
    }

    public static String tierKey(Item ingredient, Holder<Potion> potion) {
        return tierKey(entries.get(AlchemyDiscoveryKey.of(ingredient, potion)));
    }

    public static String frequencyKey(Item ingredient, Holder<Potion> potion) {
        return frequencyKey(entries.get(AlchemyDiscoveryKey.of(ingredient, potion)));
    }

    public static String noEffectTierKey(Item ingredient) {
        return tierKey(noEffectEntries.get(BuiltInRegistries.ITEM.getKey(ingredient).toString()));
    }

    public static String noEffectFrequencyKey(Item ingredient) {
        return frequencyKey(noEffectEntries.get(BuiltInRegistries.ITEM.getKey(ingredient).toString()));
    }

    private static String tierKey(ResearchEntry entry) {
        String tier = entry == null ? "novice" : entry.tier().toLowerCase(Locale.ROOT);
        return "book.totem_alchemy.research.tier." + tier;
    }

    private static String frequencyKey(ResearchEntry entry) {
        String frequency = entry == null ? "very_rare" : entry.frequency().toLowerCase(Locale.ROOT);
        return "book.totem_alchemy.research.frequency." + frequency;
    }

    private static void replace(Iterable<String> encodedEntries) {
        Map<String, ResearchEntry> researchCopy = new HashMap<>();
        Map<String, ResearchEntry> noEffectCopy = new HashMap<>();
        Map<String, Integer> sampleCopy = new HashMap<>();
        Map<String, AlchemyProcessingTimeEstimate> timingCopy = new HashMap<>();
        for (String encoded : encodedEntries) {
            if (encoded.startsWith("O|") || encoded.startsWith("N|")) {
                String[] parts = encoded.split("\\|", 5);
                if (parts.length != 5) continue;
                try {
                    ResearchEntry entry = new ResearchEntry(Integer.parseInt(parts[2]), parts[3], parts[4]);
                    if (encoded.startsWith("N|")) noEffectCopy.put(parts[1], entry);
                    else researchCopy.put(parts[1], entry);
                } catch (NumberFormatException ignored) {}
                continue;
            }
            if (encoded.startsWith("S|")) {
                String[] parts = encoded.split("\\|", 3);
                if (parts.length != 3) continue;
                try {
                    int samples = Integer.parseInt(parts[2]);
                    if (samples > 0) sampleCopy.put(parts[1], samples);
                } catch (NumberFormatException ignored) {}
                continue;
            }
            if (encoded.startsWith("T|")) {
                AlchemyProcessingTimeEstimate.parseSnapshotEntry(encoded).ifPresent(entry ->
                        timingCopy.put(entry.ingredientId(), entry.estimate()));
                continue;
            }

            String[] legacy = encoded.split("\\|", 4);
            if (legacy.length == 4) {
                try {
                    researchCopy.put(legacy[0], new ResearchEntry(Integer.parseInt(legacy[1]), legacy[2], legacy[3]));
                } catch (NumberFormatException ignored) {}
            }
        }
        entries = Map.copyOf(researchCopy);
        noEffectEntries = Map.copyOf(noEffectCopy);
        samplesByIngredient = Map.copyOf(sampleCopy);
        timings = Map.copyOf(timingCopy);
    }

    private record ResearchEntry(int samples, String tier, String frequency) {}
}
