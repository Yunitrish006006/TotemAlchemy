package dev.totem.alchemy.discovery;

import dev.totem.alchemy.network.AlchemyDiscoveriesPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Attributes successful nearby brewing to one player and synchronizes their journal. */
public final class AlchemyDiscoveryService {
    private static final double ATTRIBUTION_DISTANCE_SQUARED = 64.0D;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private AlchemyDiscoveryService() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(
                AlchemyDiscoveriesPayload.TYPE,
                AlchemyDiscoveriesPayload.CODEC
        );
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> send(player));
    }

    /** Records only potion results that actually changed during the completed brewing batch. */
    public static void recordSuccessfulBrew(
            ServerLevel level,
            BlockPos pos,
            ItemStack ingredient,
            List<ItemStack> inputs,
            List<ItemStack> outputs
    ) {
        ServerPlayer player = nearestPlayer(level, pos);
        if (player == null) {
            return;
        }

        Set<Holder<Potion>> results = new LinkedHashSet<>();
        int slotCount = Math.min(inputs.size(), outputs.size());
        for (int index = 0; index < slotCount; index++) {
            ItemStack input = inputs.get(index);
            ItemStack output = outputs.get(index);
            if (output.isEmpty() || ItemStack.matches(input, output)) {
                continue;
            }
            output.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                    .potion()
                    .ifPresent(results::add);
        }

        for (Holder<Potion> result : results) {
            record(player, ingredient, result);
        }
    }

    public static boolean record(ServerPlayer player, ItemStack ingredient, Holder<Potion> result) {
        String key = AlchemyDiscoveryKey.of(ingredient.getItem(), result);
        AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
        if (!data.record(player.getUUID(), key)) {
            return false;
        }
        send(player);
        ItemStack resultStack = PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, result);
        player.sendOverlayMessage(Component.translatable(
                "message.deadrecall.alchemy.discovery_recorded",
                resultStack.getHoverName()
        ));
        return true;
    }

    public static void send(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, AlchemyDiscoveriesPayload.TYPE)) {
            return;
        }
        List<String> discoveries = AlchemyDiscoverySavedData.get(player.level().getServer())
                .discoveries(player.getUUID())
                .stream()
                .sorted()
                .toList();
        ServerPlayNetworking.send(player, new AlchemyDiscoveriesPayload(discoveries));
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        return level.players().stream()
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast)
                .filter(player -> !player.isSpectator())
                .filter(player -> player.distanceToSqr(x, y, z) <= ATTRIBUTION_DISTANCE_SQUARED)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(x, y, z)))
                .orElse(null);
    }
}
