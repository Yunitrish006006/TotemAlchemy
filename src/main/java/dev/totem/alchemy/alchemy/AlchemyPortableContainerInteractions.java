package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Portable three-unit containers for Alchemy mixtures. */
public final class AlchemyPortableContainerInteractions {
    private AlchemyPortableContainerInteractions() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            BlockPos pos = hit.getBlockPos();
            if (!canHandle(level, pos, stack)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return apply((ServerLevel) level, pos, player, hand, stack)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        });
    }

    private static boolean canHandle(Level level, BlockPos pos, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        boolean cauldron = state.is(Blocks.CAULDRON)
                || state.is(Blocks.WATER_CAULDRON)
                || state.is(AlchemyBlocks.ALCHEMY_CAULDRON);
        if (!cauldron) {
            return false;
        }
        if (stack.is(AlchemyItems.LARGE_POTION_FLASK)) {
            return true;
        }
        if (stack.is(Items.BUCKET) && state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof AlchemyCauldronBlockEntity cauldronEntity
                    && cauldronEntity.hasMixture()
                    && cauldronEntity.mixtureSnapshot().volumeUnits() == AlchemyMixtureState.MAX_VOLUME_UNITS;
        }
        return stack.is(Items.WATER_BUCKET) && AlchemyMixtureBottle.hasStoredMixture(stack);
    }

    private static boolean apply(
            ServerLevel level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            ItemStack stack
    ) {
        if (stack.is(AlchemyItems.LARGE_POTION_FLASK)) {
            AlchemyMixtureState stored = AlchemyMixtureBottle.storedMixture(stack);
            if (stored.isEmpty()) {
                AlchemyMixtureState extracted = extractForFlask(level, pos);
                if (extracted.isEmpty()) {
                    return false;
                }
                ItemStack filled = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
                AlchemyMixtureBottle.writeState(filled, extracted);
                replaceHeld(player, hand, stack, filled);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.85F);
                player.sendOverlayMessage(Component.translatable(
                        "message.deadrecall.alchemy.large_flask_filled",
                        extracted.volumeUnits()
                ));
                return true;
            }

            if (!pourMixture(level, pos, stored)) {
                return false;
            }
            ItemStack emptied = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
            replaceHeld(player, hand, stack, emptied);
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 0.85F);
            player.sendOverlayMessage(Component.translatable("message.deadrecall.alchemy.large_flask_poured"));
            return true;
        }

        if (stack.is(Items.BUCKET)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron)
                    || !cauldron.hasMixture()
                    || cauldron.mixtureSnapshot().volumeUnits() != AlchemyMixtureState.MAX_VOLUME_UNITS) {
                return false;
            }
            AlchemyMixtureState extracted = cauldron.extractMixtureUnits(AlchemyMixtureState.MAX_VOLUME_UNITS);
            if (extracted.volumeUnits() != AlchemyMixtureState.MAX_VOLUME_UNITS) {
                return false;
            }
            ItemStack filledBucket = new ItemStack(Items.WATER_BUCKET);
            AlchemyMixtureBottle.writeState(filledBucket, extracted);
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
            replaceHeld(player, hand, stack, filledBucket);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.translatable("message.deadrecall.alchemy.mixture_bucket_filled"));
            return true;
        }

        if (stack.is(Items.WATER_BUCKET) && AlchemyMixtureBottle.hasStoredMixture(stack)) {
            AlchemyMixtureState stored = AlchemyMixtureBottle.storedMixture(stack);
            if (stored.isEmpty() || !pourMixture(level, pos, stored)) {
                return false;
            }
            replaceHeld(player, hand, stack, new ItemStack(Items.BUCKET));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.translatable("message.deadrecall.alchemy.mixture_bucket_poured"));
            return true;
        }

        return false;
    }

    private static AlchemyMixtureState extractForFlask(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON)) {
            int volume = state.getValue(LayeredCauldronBlock.LEVEL);
            if (volume <= 0) {
                return AlchemyMixtureState.empty();
            }
            AlchemyMixtureState water = AlchemyMixtureBrewing.waterState(volume);
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
            return water;
        }
        if (!state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            return AlchemyMixtureState.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron) || !cauldron.hasMixture()) {
            return AlchemyMixtureState.empty();
        }
        AlchemyMixtureState extracted = cauldron.extractMixtureUnits(AlchemyMixtureState.MAX_VOLUME_UNITS);
        updateLevelAfterExtraction(level, pos, cauldron);
        return extracted;
    }

    private static void updateLevelAfterExtraction(
            ServerLevel level,
            BlockPos pos,
            AlchemyCauldronBlockEntity cauldron
    ) {
        int remaining = cauldron.mixtureSnapshot().volumeUnits();
        if (remaining <= 0) {
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, remaining), 3);
        }
    }

    private static boolean pourMixture(ServerLevel level, BlockPos pos, AlchemyMixtureState incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.CAULDRON)) {
            BlockState alchemyState = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, incoming.volumeUnits());
            level.setBlock(pos, alchemyState, 3);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof AlchemyCauldronBlockEntity cauldron
                    && cauldron.initializeMixture(incoming);
        }

        if (state.is(Blocks.WATER_CAULDRON)) {
            int waterVolume = state.getValue(LayeredCauldronBlock.LEVEL);
            if (waterVolume + incoming.volumeUnits() > AlchemyMixtureState.MAX_VOLUME_UNITS) {
                return false;
            }
            BlockState alchemyState = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, waterVolume);
            level.setBlock(pos, alchemyState, 3);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron)
                    || !cauldron.initializeMixture(AlchemyMixtureBrewing.waterState(waterVolume))
                    || !cauldron.mergeMixture(incoming)) {
                return false;
            }
            level.setBlock(pos, level.getBlockState(pos).setValue(
                    LayeredCauldronBlock.LEVEL,
                    cauldron.mixtureSnapshot().volumeUnits()
            ), 3);
            return true;
        }

        if (state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron)) {
                return false;
            }
            boolean accepted = cauldron.hasMixture()
                    ? cauldron.mergeMixture(incoming)
                    : cauldron.initializeMixture(incoming);
            if (!accepted) {
                return false;
            }
            level.setBlock(pos, state.setValue(
                    LayeredCauldronBlock.LEVEL,
                    cauldron.mixtureSnapshot().volumeUnits()
            ), 3);
            return true;
        }
        return false;
    }

    private static void replaceHeld(Player player, InteractionHand hand, ItemStack current, ItemStack replacement) {
        if (player.getAbilities().instabuild) {
            ItemStack copy = replacement.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
            return;
        }
        current.shrink(1);
        if (current.isEmpty()) {
            player.setItemInHand(hand, replacement);
        } else if (!replacement.isEmpty() && !player.getInventory().add(replacement.copy())) {
            player.drop(replacement.copy(), false);
        }
    }
}
