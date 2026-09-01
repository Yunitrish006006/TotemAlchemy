package dev.totem.alchemy.discovery;

import dev.totem.alchemy.manual.AlchemyMaterialCatalog;
import dev.totem.alchemy.network.AlchemyMaterialDiscoveredPayload;
import dev.totem.alchemy.registry.AlchemyGameRules;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records each brewable material the first time it enters a player's inventory. */
public final class AlchemyMaterialAcquisitionDiscovery {
    private static final int ACTIVATION_INTERVAL_TICKS = 40;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<UUID, ArrayDeque<Identifier>> ACTIVATION_QUEUES = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVATION_COOLDOWNS = new HashMap<>();

    private AlchemyMaterialAcquisitionDiscovery() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(
                AlchemyMaterialDiscoveredPayload.TYPE,
                AlchemyMaterialDiscoveredPayload.CODEC
        );
        ServerTickEvents.END_SERVER_TICK.register(AlchemyMaterialAcquisitionDiscovery::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player.getUUID()));
    }

    /**
     * Performs one server-authoritative inventory scan and returns only newly recorded materials.
     * This intentionally does not create a research sample, timing observation, or brewing outcome.
     */
    public static List<ItemStack> discoverInventoryMaterials(ServerPlayer player) {
        if (player == null || player.isSpectator()
                || !AlchemyGameRules.autoRecordBrewingMaterials(player.level())) {
            return List.of();
        }

        Set<Item> inventoryMaterials = new LinkedHashSet<>();
        for (ItemStack stack : player.getInventory()) {
            if (!stack.isEmpty() && AlchemyMaterialCatalog.contains(stack.getItem())) {
                inventoryMaterials.add(stack.getItem());
            }
        }

        AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
        List<ItemStack> discovered = new ArrayList<>();
        for (Item item : inventoryMaterials) {
            String ingredientId = BuiltInRegistries.ITEM.getKey(item).toString();
            if (data.recordKnownMaterial(player.getUUID(), ingredientId)) {
                discovered.add(new ItemStack(item));
            }
        }
        if (!discovered.isEmpty()) {
            AlchemyDiscoveryService.send(player);
        }
        return List.copyOf(discovered);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !AlchemyGameRules.autoRecordBrewingMaterials(player.level())) {
                clear(player.getUUID());
                continue;
            }
            List<ItemStack> discovered = discoverInventoryMaterials(player);
            if (ServerPlayNetworking.canSend(player, AlchemyMaterialDiscoveredPayload.TYPE)) {
                ArrayDeque<Identifier> queue = ACTIVATION_QUEUES.computeIfAbsent(
                        player.getUUID(), ignored -> new ArrayDeque<>());
                discovered.stream()
                        .map(ItemStack::getItem)
                        .map(BuiltInRegistries.ITEM::getKey)
                        .forEach(queue::addLast);
                playNextActivation(player, queue);
            }
        }
    }

    private static void playNextActivation(ServerPlayer player, ArrayDeque<Identifier> queue) {
        UUID playerId = player.getUUID();
        int cooldown = ACTIVATION_COOLDOWNS.getOrDefault(playerId, 0);
        if (cooldown > 0) {
            ACTIVATION_COOLDOWNS.put(playerId, cooldown - 1);
            return;
        }
        Identifier materialId = queue.pollFirst();
        if (materialId == null) {
            ACTIVATION_QUEUES.remove(playerId);
            ACTIVATION_COOLDOWNS.remove(playerId);
            return;
        }

        Item material = BuiltInRegistries.ITEM.getValue(materialId);
        ServerPlayNetworking.send(player, new AlchemyMaterialDiscoveredPayload(materialId));
        player.sendOverlayMessage(Component.translatable(
                "message.deadrecall.alchemy.material_recorded",
                new ItemStack(material).getHoverName()
        ));
        ACTIVATION_COOLDOWNS.put(playerId, ACTIVATION_INTERVAL_TICKS);
    }

    private static void clear(UUID playerId) {
        ACTIVATION_QUEUES.remove(playerId);
        ACTIVATION_COOLDOWNS.remove(playerId);
    }
}
