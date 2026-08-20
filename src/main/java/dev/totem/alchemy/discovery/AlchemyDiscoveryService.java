package dev.totem.alchemy.discovery;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.network.AlchemyDiscoveriesPayload;
import dev.totem.alchemy.network.AlchemyResearchPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Attributes brewing to one nearby player and synchronizes discoveries plus research confidence. */
public final class AlchemyDiscoveryService {
    private static final double ATTRIBUTION_DISTANCE_SQUARED = 64.0D;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private AlchemyDiscoveryService() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(AlchemyDiscoveriesPayload.TYPE, AlchemyDiscoveriesPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AlchemyResearchPayload.TYPE, AlchemyResearchPayload.CODEC);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> send(player));
    }

    /** Backward-compatible entry point for tests/callers that do not have an observed processing duration. */
    public static void recordSuccessfulBrew(
            ServerLevel level,
            BlockPos pos,
            ItemStack ingredient,
            List<ItemStack> inputs,
            List<ItemStack> outputs
    ) {
        recordSuccessfulBrew(level, pos, ingredient, inputs, outputs, -1, null);
    }

    /** One successful batch contributes one outcome observation and one material processing-time sample. */
    public static void recordSuccessfulBrew(
            ServerLevel level,
            BlockPos pos,
            ItemStack ingredient,
            List<ItemStack> inputs,
            List<ItemStack> outputs,
            int processingTicks
    ) {
        recordSuccessfulBrew(level, pos, ingredient, inputs, outputs, processingTicks, null);
    }

    /**
     * Records a completed batch. {@code explicitResult} is used for layered custom mixtures whose
     * PotionContents intentionally has no canonical potion holder after multiple effects are combined.
     */
    public static void recordSuccessfulBrew(
            ServerLevel level,
            BlockPos pos,
            ItemStack ingredient,
            List<ItemStack> inputs,
            List<ItemStack> outputs,
            int processingTicks,
            Holder<Potion> explicitResult
    ) {
        ServerPlayer player = nearestPlayer(level, pos);
        if (player == null) {
            return;
        }
        Set<Holder<Potion>> results = new LinkedHashSet<>();
        if (explicitResult != null) {
            results.add(explicitResult);
        }
        int slotCount = Math.min(inputs.size(), outputs.size());
        for (int index = 0; index < slotCount; index++) {
            ItemStack input = inputs.get(index);
            ItemStack output = outputs.get(index);
            if (output.isEmpty() || ItemStack.matches(input, output)) {
                continue;
            }
            output.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion().ifPresent(results::add);
        }
        if (results.isEmpty()) {
            return;
        }

        AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        if (processingTicks > 0) {
            data.recordProcessingTime(player.getUUID(), ingredientId, processingTicks);
        }

        for (Holder<Potion> result : results) {
            String key = AlchemyDiscoveryKey.of(ingredient.getItem(), result);
            data.recordResearch(player.getUUID(), key);
            if (data.record(player.getUUID(), key)) {
                ItemStack resultStack = PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, result);
                player.sendOverlayMessage(Component.translatable(
                        "message.deadrecall.alchemy.discovery_recorded", resultStack.getHoverName()));
            }
        }
        send(player);
    }

    /** A failed completed brew still teaches the player how long this material takes to process. */
    public static void recordProcessingAttempt(
            ServerLevel level,
            BlockPos pos,
            ItemStack ingredient,
            int processingTicks
    ) {
        if (processingTicks <= 0) {
            return;
        }
        ServerPlayer player = nearestPlayer(level, pos);
        if (player == null) {
            return;
        }
        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
        data.recordProcessingTime(player.getUUID(), ingredientId, processingTicks);
        send(player);
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
                "message.deadrecall.alchemy.discovery_recorded", resultStack.getHoverName()));
        return true;
    }

    public static void send(ServerPlayer player) {
        AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
        if (ServerPlayNetworking.canSend(player, AlchemyDiscoveriesPayload.TYPE)) {
            ServerPlayNetworking.send(player, new AlchemyDiscoveriesPayload(
                    data.discoveries(player.getUUID()).stream().sorted().toList()));
        }
        if (ServerPlayNetworking.canSend(player, AlchemyResearchPayload.TYPE)) {
            ServerPlayNetworking.send(player, new AlchemyResearchPayload(buildResearchSnapshot(player, data)));
        }
    }

    /**
     * Outcome entries contain only sample count + derived labels. Timing entries contain only the player's
     * observed average. Hidden true outcome probabilities never cross the network boundary.
     */
    private static List<String> buildResearchSnapshot(ServerPlayer player, AlchemyDiscoverySavedData data) {
        List<String> snapshot = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : data.research(player.getUUID()).entrySet()) {
            String key = entry.getKey();
            int split = key.indexOf('>');
            if (split <= 0 || split >= key.length() - 1) {
                continue;
            }
            String ingredientId = key.substring(0, split);
            String potionId = key.substring(split + 1);
            int samples = data.researchTotal(player.getUUID(), ingredientId);
            if (samples <= 0) {
                continue;
            }
            double observed = entry.getValue() / (double) samples;
            double truth = MultiOutcomeBrewing.outcomeProbability(ingredientId, potionId);
            if (!Double.isFinite(truth) || truth < 0.0D) {
                continue;
            }
            AlchemyResearchTier tier = AlchemyResearchTier.classify(samples, Math.abs(observed - truth));
            AlchemyObservedFrequency frequency = AlchemyObservedFrequency.classify(observed);
            snapshot.add("O|" + key + "|" + samples + "|" + tier.name() + "|" + frequency.name());
        }

        for (Map.Entry<String, AlchemyDiscoverySavedData.ProcessingTimeStats> entry
                : data.processingTimes(player.getUUID()).entrySet()) {
            AlchemyDiscoverySavedData.ProcessingTimeStats timing = entry.getValue();
            if (timing.samples() > 0 && timing.averageTicks() > 0) {
                snapshot.add("T|" + entry.getKey() + "|" + timing.samples() + "|" + timing.averageTicks());
            }
        }

        snapshot.sort(String::compareTo);
        return List.copyOf(snapshot);
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        return level.players().stream()
                .filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast)
                .filter(player -> !player.isSpectator())
                .filter(player -> player.distanceToSqr(x, y, z) <= ATTRIBUTION_DISTANCE_SQUARED)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(x, y, z)))
                .orElse(null);
    }
}
