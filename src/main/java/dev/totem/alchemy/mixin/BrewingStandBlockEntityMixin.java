package dev.totem.alchemy.mixin;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
    private static final int INGREDIENT_SLOT = 3;
    private static final ThreadLocal<SuccessfulBrewContext> SUCCESSFUL_BREW = new ThreadLocal<>();

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
        double successChance = VanillaBrewingChance.chanceFor(ingredient, potionInputs);
        int chancePercent = (int) Math.round(successChance * 100.0D);
        if (VanillaBrewingChance.isSuccessful(ingredient, potionInputs, level.getRandom().nextFloat())) {
            MultiOutcomeBrewing.beginBatch(level.getRandom(), ingredient, potionInputs);
            SUCCESSFUL_BREW.set(new SuccessfulBrewContext(
                    ingredient.copy(),
                    potionInputs.stream().map(ItemStack::copy).toList(),
                    chancePercent,
                    MultiOutcomeBrewing.activeOutcome()
            ));
            return;
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
                AlchemyDiscoveryService.recordSuccessfulBrew(
                        serverLevel,
                        pos,
                        context.ingredient(),
                        context.inputs(),
                        slots.subList(0, INGREDIENT_SLOT).stream().map(ItemStack::copy).toList()
                );
            }
            if (context.outcome() == null) {
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
                        Component.translatable(context.outcome().messageKey())
                );
            }
        } finally {
            SUCCESSFUL_BREW.remove();
            MultiOutcomeBrewing.clearBatch();
        }
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

    private record SuccessfulBrewContext(
            ItemStack ingredient,
            List<ItemStack> inputs,
            int chancePercent,
            MultiOutcomeBrewing.Outcome outcome
    ) {
        private SuccessfulBrewContext {
            ingredient = ingredient.copy();
            inputs = inputs.stream().map(ItemStack::copy).toList();
        }
    }
}
