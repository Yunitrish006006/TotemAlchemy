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

/** Client snapshot containing only sample counts, research tiers, and observed frequency bands. */
public final class AlchemyResearchClientCache {
    private static volatile Map<String, ResearchEntry> entries = Map.of();

    private AlchemyResearchClientCache() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                AlchemyResearchPayload.TYPE,
                (payload, context) -> context.client().execute(() -> replace(payload.entries()))
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> entries = Map.of());
    }

    public static int samples(Item ingredient) {
        String prefix = BuiltInRegistries.ITEM.getKey(ingredient) + ">";
        return entries.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .mapToInt(entry -> entry.getValue().samples())
                .max().orElse(0);
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
        Map<String, ResearchEntry> copy = new HashMap<>();
        for (String encoded : encodedEntries) {
            String[] parts = encoded.split("\\|", 4);
            if (parts.length != 4) {
                continue;
            }
            try {
                copy.put(parts[0], new ResearchEntry(Integer.parseInt(parts[1]), parts[2], parts[3]));
            } catch (NumberFormatException ignored) {
            }
        }
        entries = Map.copyOf(copy);
    }

    private record ResearchEntry(int samples, String tier, String frequency) {
    }
}
