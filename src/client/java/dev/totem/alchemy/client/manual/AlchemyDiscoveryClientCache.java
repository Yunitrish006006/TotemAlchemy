package dev.totem.alchemy.client.manual;

import dev.totem.alchemy.discovery.AlchemyDiscoveryKey;
import dev.totem.alchemy.network.AlchemyDiscoveriesPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

import java.util.Set;

/** Client-side snapshot used by the live Alchemy manual renderer. */
public final class AlchemyDiscoveryClientCache {
    private static volatile Set<String> discoveries = Set.of();

    private AlchemyDiscoveryClientCache() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                AlchemyDiscoveriesPayload.TYPE,
                (payload, context) -> context.client().execute(() -> replace(payload.discoveries()))
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> replace(Set.of()));
    }

    public static boolean has(Item ingredient, Holder<Potion> potion) {
        String key = AlchemyDiscoveryKey.of(ingredient, potion);
        return discoveries.contains(key) || AlchemyResearchClientCache.hasOutcome(ingredient, potion);
    }

    static void replace(Iterable<String> updatedDiscoveries) {
        java.util.HashSet<String> copy = new java.util.HashSet<>();
        updatedDiscoveries.forEach(copy::add);
        discoveries = Set.copyOf(copy);
    }
}
