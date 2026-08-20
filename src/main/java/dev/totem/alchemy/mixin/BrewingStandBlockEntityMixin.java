package dev.totem.alchemy.mixin;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
    private static final int INGREDIENT_SLOT = 3;
    private static final double ATTRIBUTION_DISTANCE_SQUARED = 64.0D;
    private static final ThreadLocal<SuccessfulBrewContext> SUCCESSFUL_BREW = new ThreadLocal<>();
    private static final Map<Level, Map<Long, ProcessingTimer>> PROCESSING_TIMERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Counts only ticks while the stand is actively brewing and captures the nearby researcher when the
     * cycle begins. Moving away before completion therefore no longer drops the player's journal update.
     */
    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void totemAlchemy$trackProcessingTime(
            Level level,
            BlockPos pos,
            BlockState state,
            BrewingStandBlockEntity entity,
            CallbackInfo ci
    ) {
        int brewTime = ((BrewingStandBlockEntityAccessor) (Object) entity).totemAlchemy$getBrewTime();
        synchronized (PROCESSING_TIMERS) {
            Map<Long, ProcessingTimer> timers = PROCESSING_TIMERS.computeIfAbsent(level, ignored -> new HashMap<>());
            long key = pos.asLong();
            if (brewTime > 0) {
                Item ingredient = entity.getItem(INGREDIENT_SLOT).getItem();
                ProcessingTimer timer = timers.get(key);
                if (timer == null || timer.ingredient() != ingredient) {
                    timers.put(key, new ProcessingTimer(ingredient, 0, nearestResearcher(level, pos)));
                } else {
                    timers.put(key, new ProcessingTimer(
                            ingredient,
                            timer.elapsedTicks() + 1,
                            timer.researcherId()
                    ));
                }
            } else {
                timers.remove(key);
                if (timers.isEmpty()) {
                    PROCESSING_TIMERS.remove(level);
                }
            }
        }
    }

    @Inject(method = "doBrew", at = @At("HEAD"), cancellable = true)
    private static void totemAlchemy$rollIngredientSuccess(
            Level level,
            BlockPos pos,
            NonNullList<ItemStack> slots,
            CallbackInfo ci
    ) {
        SUCCESSFUL_BREW.remove();
        MultiOutcomeBrewing.clearBatch();

        ItemStack ingredient = slots.get(INGREDIENT_SLOT);
        List<ItemStack> potionInputs = slots.subList(0, INGREDIENT_SLOT);
        ProcessingTimer processing = currentProcessing(level, pos, ingredient);
        int processingTicks = processing == null ? -1 : processing.elapsedTicks() + 1;
        UUID researcherId = processing == null ? null : processing.researcherId();
        double successChance = VanillaBrewingChance.chanceFor(ingredient, potionInputs);
        int chancePercent = (int) Math.round(successChance * 100.0D);
        if (VanillaBrewingChance.isSuccessful(ingredient, potionInputs, level.getRandom().nextFloat())) {
            MultiOutcomeBrewing.beginBatch(level.getRandom(), ingredient, potionInputs);
            SUCCESSFUL_BREW.set(new SuccessfulBrewContext(
                    ingredient.copy(),
                    potionInputs.stream().map(ItemStack::copy).toList(),
                    chancePercent,
                    processingTicks,
                    MultiOutcomeBrewing.activeOutcomes(),
                    researcherId
            ));
            return;
        }

        if (level instanceof ServerLevel serverLevel) {
            AlchemyDiscoveryService.recordProcessingAttempt(
                    serverLevel, pos, ingredient, processingTicks, researcherId);
        }
        consumeIngredient(level, pos, slots);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
        notifyNearbyPlayers(level, pos, "message.deadrecall.alchemy.vanilla_brew_failure", chancePercent);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.85D,
                    pos.getZ() + 0.5D,
                    8,
                    0.22D,
                    0.18D,
                    0.22D,
                    0.015D
            );
        }
        ci.cancel();
    }

    @Inject(method = "doBrew", at = @At("RETURN"))
    private static void totemAlchemy$recordCompletedBrew(
            Level level,
            BlockPos pos,
            NonNullList<ItemStack> slots,
            CallbackInfo ci
    ) {
        SuccessfulBrewContext context = SUCCESSFUL_BREW.get();
        try {
            if (context == null) {
                return;
            }
            if (level instanceof ServerLevel serverLevel) {
                AlchemyDiscoveryService.recordSuccessfulBrewOutcomes(
                        serverLevel,
                        pos,
                        context.ingredient(),
                        context.inputs(),
                        slots.subList(0, INGREDIENT_SLOT).stream().map(ItemStack::copy).toList(),
                        context.processingTicks(),
                        context.outcomes().stream().map(MultiOutcomeBrewing.Outcome::potion).toList(),
                        context.researcherId()
                );
            }
            if (context.outcomes().isEmpty()) {
                notifyNearbyPlayers(
                        level,
                        pos,
                        "message.deadrecall.alchemy.vanilla_brew_success",
                        context.chancePercent()
                );
            } else {
                notifyNearbyPlayers(
                        level,
                        pos,
                        "message.deadrecall.alchemy.multi_outcome_success",
                        context.chancePercent(),
                        outcomeSetName(context.outcomes())
                );
            }
        } finally {
            SUCCESSFUL_BREW.remove();
            MultiOutcomeBrewing.clearBatch();
        }
    }

    private static ProcessingTimer currentProcessing(Level level, BlockPos pos, ItemStack ingredient) {
        synchronized (PROCESSING_TIMERS) {
            Map<Long, ProcessingTimer> timers = PROCESSING_TIMERS.get(level);
            if (timers == null) {
                return null;
            }
            ProcessingTimer timer = timers.get(pos.asLong());
            return timer == null || timer.ingredient() != ingredient.getItem() ? null : timer;
        }
    }

    private static UUID nearestResearcher(Level level, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        return level.players().stream()
                .filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast)
                .filter(player -> !player.isSpectator())
                .filter(player -> player.distanceToSqr(x, y, z) <= ATTRIBUTION_DISTANCE_SQUARED)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(x, y, z)))
                .map(ServerPlayer::getUUID)
                .orElse(null);
    }

    private static void consumeIngredient(Level level, BlockPos pos, NonNullList<ItemStack> slots) {
        ItemStack ingredient = slots.get(INGREDIENT_SLOT);
        ItemStackTemplate remainder = ingredient.getItem().getCraftingRemainder();
        ingredient.shrink(1);
        if (remainder != null) {
            if (ingredient.isEmpty()) {
                ingredient = remainder.create();
            } else {
                Containers.dropItemStack(
                        level,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        remainder.create()
                );
            }
        }
        slots.set(INGREDIENT_SLOT, ingredient);
    }

    private static void notifyNearbyPlayers(
            Level level,
            BlockPos pos,
            String messageKey,
            Object... arguments
    ) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        for (net.minecraft.world.entity.player.Player player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= 64.0D) {
                player.sendOverlayMessage(Component.translatable(messageKey, arguments));
            }
        }
    }

    private static Component outcomeSetName(List<MultiOutcomeBrewing.Outcome> outcomes) {
        var name = Component.empty();
        for (int index = 0; index < outcomes.size(); index++) {
            if (index > 0) {
                name.append(Component.literal(", "));
            }
            name.append(Component.translatable(outcomes.get(index).messageKey()));
        }
        return name;
    }

    private record ProcessingTimer(Item ingredient, int elapsedTicks, UUID researcherId) {
    }

    private record SuccessfulBrewContext(
            ItemStack ingredient,
            List<ItemStack> inputs,
            int chancePercent,
            int processingTicks,
            List<MultiOutcomeBrewing.Outcome> outcomes,
            UUID researcherId
    ) {
        private SuccessfulBrewContext {
            ingredient = ingredient.copy();
            inputs = inputs.stream().map(ItemStack::copy).toList();
            outcomes = List.copyOf(outcomes);
        }
    }
}
