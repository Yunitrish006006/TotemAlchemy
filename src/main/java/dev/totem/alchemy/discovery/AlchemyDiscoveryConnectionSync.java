package dev.totem.alchemy.discovery;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Re-sends the persisted Alchemy manual snapshot after a play connection is fully established.
 * This is particularly important for integrated single-player servers, where the initial data-pack
 * synchronization can happen before the local client's custom-payload receivers are ready.
 */
public final class AlchemyDiscoveryConnectionSync {
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private AlchemyDiscoveryConnectionSync() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            synchronized (PENDING) {
                PENDING.put(handler.player.getUUID(), 5);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            synchronized (PENDING) {
                PENDING.remove(handler.player.getUUID());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            synchronized (PENDING) {
                var iterator = PENDING.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    int ticks = entry.getValue() - 1;
                    if (ticks > 0) {
                        entry.setValue(ticks);
                        continue;
                    }
                    var player = server.getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        AlchemyDiscoveryService.send(player);
                    }
                    iterator.remove();
                }
            }
        });
    }
}
