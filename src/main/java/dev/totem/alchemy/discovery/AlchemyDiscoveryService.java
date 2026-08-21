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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Attributes brewing to the player who started observing it and synchronizes discoveries plus research confidence. */
public final class AlchemyDiscoveryService {
    private static final double ATTRIBUTION_DISTANCE_SQUARED = 64.0D;
    private static final String NO_EFFECT_ID = "totem:none";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private AlchemyDiscoveryService() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        PayloadTypeRegistry.clientboundPlay().register(AlchemyDiscoveriesPayload.TYPE, AlchemyDiscoveriesPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AlchemyResearchPayload.TYPE, AlchemyResearchPayload.CODEC);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> send(player));
    }

    public static void recordSuccessfulBrew(ServerLevel level, BlockPos pos, ItemStack ingredient,
                                            List<ItemStack> inputs, List<ItemStack> outputs) {
        recordSuccessfulBrewResults(level, pos, ingredient, inputs, outputs, -1, List.of(), null);
    }

    public static void recordSuccessfulBrew(ServerLevel level, BlockPos pos, ItemStack ingredient,
                                            List<ItemStack> inputs, List<ItemStack> outputs, int processingTicks) {
        recordSuccessfulBrewResults(level, pos, ingredient, inputs, outputs, processingTicks, List.of(), null);
    }

    public static void recordSuccessfulBrew(ServerLevel level, BlockPos pos, ItemStack ingredient,
                                            List<ItemStack> inputs, List<ItemStack> outputs, int processingTicks,
                                            Holder<Potion> explicitResult) {
        recordSuccessfulBrewResults(level, pos, ingredient, inputs, outputs, processingTicks,
                explicitResult == null ? List.of() : List.of(explicitResult), null);
    }

    public static void recordSuccessfulBrew(ServerLevel level, BlockPos pos, ItemStack ingredient,
                                            List<ItemStack> inputs, List<ItemStack> outputs, int processingTicks,
                                            Holder<Potion> explicitResult, UUID researcherId) {
        recordSuccessfulBrewResults(level, pos, ingredient, inputs, outputs, processingTicks,
                explicitResult == null ? List.of() : List.of(explicitResult), researcherId);
    }

    /** Records every independently selected result while counting the completed material batch only once. */
    public static void recordSuccessfulBrewOutcomes(ServerLevel level, BlockPos pos, ItemStack ingredient,
                                                    List<ItemStack> inputs, List<ItemStack> outputs,
                                                    int processingTicks, List<Holder<Potion>> explicitResults,
                                                    UUID researcherId) {
        recordSuccessfulBrewResults(level, pos, ingredient, inputs, outputs, processingTicks,
                explicitResults == null ? List.of() : explicitResults, researcherId);
    }

    private static void recordSuccessfulBrewResults(ServerLevel level, BlockPos pos, ItemStack ingredient,
                                                    List<ItemStack> inputs, List<ItemStack> outputs,
                                                    int processingTicks, List<Holder<Potion>> explicitResults,
                                                    UUID researcherId) {
        UUID subjectId = researcherId;
        ServerPlayer livePlayer = subjectId == null
                ? nearestPlayer(level, pos)
                : level.getServer().getPlayerList().getPlayer(subjectId);
        if (subjectId == null) {
            if (livePlayer == null) return;
            subjectId = livePlayer.getUUID();
        }

        Set<Holder<Potion>> results = new LinkedHashSet<>();
        results.addAll(explicitResults);
        int slotCount = Math.min(inputs.size(), outputs.size());
        for (int index = 0; index < slotCount; index++) {
            ItemStack input = inputs.get(index);
            ItemStack output = outputs.get(index);
            if (output.isEmpty() || ItemStack.matches(input, output)) continue;
            output.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion().ifPresent(results::add);
        }

        AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(level.getServer());
        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        data.recordMaterialSample(subjectId, ingredientId);
        if (processingTicks > 0) data.recordProcessingTime(subjectId, ingredientId, processingTicks);

        if (results.isEmpty()) {
            // A material may process successfully while none of its independent effect rolls hit.
            data.recordResearch(subjectId, ingredientId + ">" + NO_EFFECT_ID);
            if (livePlayer != null) send(livePlayer);
            return;
        }

        for (Holder<Potion> result : results) {
            String key = AlchemyDiscoveryKey.of(ingredient.getItem(), result);
            data.recordResearch(subjectId, key);
            if (data.record(subjectId, key) && livePlayer != null) {
                ItemStack resultStack = PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, result);
                livePlayer.sendOverlayMessage(Component.translatable(
                        "message.deadrecall.alchemy.discovery_recorded", resultStack.getHoverName()));
            }
        }
        if (livePlayer != null) send(livePlayer);
    }

    public static void recordProcessingAttempt(ServerLevel level, BlockPos pos, ItemStack ingredient, int processingTicks) {
        recordProcessingAttempt(level, pos, ingredient, processingTicks, null);
    }

    public static void recordProcessingAttempt(ServerLevel level, BlockPos pos, ItemStack ingredient,
                                               int processingTicks, UUID researcherId) {
        if (processingTicks <= 0) return;
        UUID subjectId = researcherId;
        ServerPlayer livePlayer = subjectId == null
                ? nearestPlayer(level, pos)
                : level.getServer().getPlayerList().getPlayer(subjectId);
        if (subjectId == null) {
            if (livePlayer == null) return;
            subjectId = livePlayer.getUUID();
        }
        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(level.getServer());
        data.recordProcessingTime(subjectId, ingredientId, processingTicks);
        if (livePlayer != null) send(livePlayer);
    }

    public static boolean record(ServerPlayer player, ItemStack ingredient, Holder<Potion> result) {
        String key = AlchemyDiscoveryKey.of(ingredient.getItem(), result);
        AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
        boolean researchChanged = data.ensureResearchSample(player.getUUID(), key);
        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        boolean materialChanged = data.ensureMaterialSample(player.getUUID(), ingredientId);
        boolean discoveryChanged = data.record(player.getUUID(), key);
        if (!researchChanged && !materialChanged && !discoveryChanged) return false;
        send(player);
        if (discoveryChanged) {
            ItemStack resultStack = PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, result);
            player.sendOverlayMessage(Component.translatable(
                    "message.deadrecall.alchemy.discovery_recorded", resultStack.getHoverName()));
        }
        return discoveryChanged;
    }

    public static void send(ServerPlayer player) {
        AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
        if (ServerPlayNetworking.canSend(player, AlchemyResearchPayload.TYPE)) {
            ServerPlayNetworking.send(player, new AlchemyResearchPayload(buildResearchSnapshot(player, data)));
        }
        if (ServerPlayNetworking.canSend(player, AlchemyDiscoveriesPayload.TYPE)) {
            ServerPlayNetworking.send(player, new AlchemyDiscoveriesPayload(
                    data.discoveries(player.getUUID()).stream().sorted().toList()));
        }
    }

    private static List<String> buildResearchSnapshot(ServerPlayer player, AlchemyDiscoverySavedData data) {
        List<String> snapshot = new ArrayList<>();
        Map<String, Integer> research = data.research(player.getUUID());
        Map<String, Integer> samplesByIngredient = data.materialSamples(player.getUUID());
        samplesByIngredient.forEach((ingredientId, samples) -> {
            if (samples > 0) snapshot.add("S|" + ingredientId + "|" + samples);
        });

        for (Map.Entry<String, Integer> entry : research.entrySet()) {
            String key = entry.getKey();
            int split = key.indexOf('>');
            if (split <= 0 || split >= key.length() - 1) continue;
            String ingredientId = key.substring(0, split);
            String potionId = key.substring(split + 1);
            int samples = samplesByIngredient.getOrDefault(ingredientId, 0);
            if (samples <= 0) continue;
            double observed = entry.getValue() / (double) samples;
            double truth;
            String type;
            if (NO_EFFECT_ID.equals(potionId)) {
                truth = MultiOutcomeBrewing.noEffectProbability(ingredientId);
                type = "N|" + ingredientId;
            } else {
                truth = MultiOutcomeBrewing.outcomeProbability(ingredientId, potionId);
                type = "O|" + key;
            }
            if (!Double.isFinite(truth) || truth < 0.0D) continue;
            AlchemyResearchTier tier = AlchemyResearchTier.classify(samples, Math.abs(observed - truth));
            AlchemyObservedFrequency frequency = AlchemyObservedFrequency.classify(observed);
            snapshot.add(type + "|" + samples + "|" + tier.name() + "|" + frequency.name());
        }

        for (Map.Entry<String, AlchemyDiscoverySavedData.ProcessingTimeStats> entry
                : data.processingTimes(player.getUUID()).entrySet()) {
            AlchemyDiscoverySavedData.ProcessingTimeStats timing = entry.getValue();
            AlchemyProcessingTimeEstimate.fromObservations(timing.samples(), timing.averageTicks())
                    .map(estimate -> estimate.encodeSnapshotEntry(entry.getKey()))
                    .ifPresent(snapshot::add);
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
