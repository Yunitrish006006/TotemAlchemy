package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AlchemyHandler {
    private static final int DROPPED_ITEM_SCAN_INTERVAL_TICKS = 5;

    private AlchemyHandler() {
    }

    public static void register() {
        AlchemyCauldronRecipes.registerReloadListener();

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);
            return tryHarvestPigManure(player, world, hand, stack, pos);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) {
                return InteractionResult.PASS;
            }

            if (world.isClientSide()) {
                return canApplyAlchemyItem(world, pos, stack, false)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.PASS;
            }

            CauldronAction action = tryApplyAlchemyItem((ServerLevel) world, pos, stack, false);
            if (action == null) {
                return InteractionResult.PASS;
            }

            replaceConsumedItem(player, hand, stack, action.output());
            if (action.messageKey() != null && !action.messageKey().isBlank()) {
                player.sendOverlayMessage(Component.translatable(action.messageKey()));
            }
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(AlchemyHandler::tickDroppedIngredients);
    }

    public static boolean hasLitCampfireBelow(Level level, BlockPos cauldronPos) {
        BlockState below = level.getBlockState(cauldronPos.below());
        return CampfireBlock.isLitCampfire(below);
    }

    private static InteractionResult tryHarvestPigManure(Player player, Level world, net.minecraft.world.InteractionHand hand,
                                                        ItemStack stack, BlockPos pos) {
        BlockState cleanState = AlchemyBlocks.getCleanState(world.getBlockState(pos));
        if (cleanState == null || !stack.is(ItemTags.SHOVELS)) {
            return InteractionResult.PASS;
        }

        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        world.setBlock(pos, cleanState, 3);
        ItemStack manure = new ItemStack(AlchemyItems.PIG_MANURE);
        if (!player.addItem(manure)) {
            player.drop(manure, false);
        }
        if (!player.getAbilities().instabuild) {
            stack.hurtAndBreak(1, player, hand);
        }
        world.playSound(null, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    private static void tickDroppedIngredients(MinecraftServer server) {
        if (server.getTickCount() % DROPPED_ITEM_SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) {
                    continue;
                }

                ItemStack stack = itemEntity.getItem();
                if (stack.isEmpty()) {
                    continue;
                }

                BlockPos cauldronPos = findCauldronForDroppedItem(level, itemEntity, stack);
                if (cauldronPos == null) {
                    continue;
                }

                CauldronAction action = tryApplyAlchemyItem(level, cauldronPos, stack, true);
                if (action == null) {
                    continue;
                }

                stack.shrink(1);
                if (stack.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(stack);
                    itemEntity.setPickUpDelay(20);
                }

                if (!action.output().isEmpty()) {
                    spawnItem(level, cauldronPos, action.output());
                }
            }
        }
    }

    private static BlockPos findCauldronForDroppedItem(ServerLevel level, ItemEntity itemEntity, ItemStack stack) {
        BlockPos pos = itemEntity.blockPosition();
        if (canApplyAlchemyItem(level, pos, stack, true)) {
            return pos;
        }

        BlockPos below = pos.below();
        if (canApplyAlchemyItem(level, below, stack, true)) {
            return below;
        }
        return null;
    }

    private static boolean canApplyAlchemyItem(Level level, BlockPos pos, ItemStack stack, boolean dropped) {
        if (findExtractionRecipe(level, pos, stack) != null) {
            return true;
        }
        return findIngredientMatch(level, pos, stack, dropped) != null;
    }

    private static CauldronAction tryApplyAlchemyItem(ServerLevel level, BlockPos pos, ItemStack stack, boolean dropped) {
        AlchemyCauldronRecipe extractionRecipe = findExtractionRecipe(level, pos, stack);
        if (extractionRecipe != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AlchemyCauldronBlockEntity cauldron
                    && cauldron.extractBottledResult(extractionRecipe, level, pos, level.getBlockState(pos), stack)) {
                playSound(level, pos, extractionRecipe.result().sound(), 1.0F, 1.0F);
                return new CauldronAction(extractionRecipe.createResultStack(), extractionRecipe.result().messageKey());
            }
        }

        IngredientMatch match = findIngredientMatch(level, pos, stack, dropped);
        if (match == null) {
            return null;
        }

        AlchemyCauldronBlockEntity cauldron = ensureAlchemyCauldron(level, pos, match.recipe());
        if (cauldron == null || !cauldron.addIngredient(match.recipe(), match.ingredient())) {
            return null;
        }

        playSound(level, pos, match.ingredient().soundOrDefault(match.recipe().defaultAddSound()), 0.8F, 1.0F);
        return new CauldronAction(
                match.ingredient().createRemainderStack(),
                match.ingredient().messageOrDefault(match.recipe().defaultMessageKey())
        );
    }

    private static AlchemyCauldronBlockEntity ensureAlchemyCauldron(ServerLevel level, BlockPos pos, AlchemyCauldronRecipe recipe) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            if (!recipe.canStartFrom(state)) {
                return null;
            }
            BlockState alchemyState = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, recipe.initialLevel());
            level.setBlock(pos, alchemyState, 3);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof AlchemyCauldronBlockEntity cauldron ? cauldron : null;
    }

    private static AlchemyCauldronRecipe findExtractionRecipe(Level level, BlockPos pos, ItemStack stack) {
        if (!level.getBlockState(pos).is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron) || cauldron.getRecipeId() == null) {
            return null;
        }

        AlchemyCauldronRecipe recipe = AlchemyCauldronRecipes.get(cauldron.getRecipeId());
        if (cauldron.canExtractBottledResult(recipe, stack)) {
            return recipe;
        }
        return null;
    }

    private static IngredientMatch findIngredientMatch(Level level, BlockPos pos, ItemStack stack, boolean dropped) {
        BlockState state = level.getBlockState(pos);
        if (state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron) || cauldron.getRecipeId() == null) {
                return null;
            }

            AlchemyCauldronRecipe recipe = AlchemyCauldronRecipes.get(cauldron.getRecipeId());
            if (recipe == null) {
                return null;
            }

            AlchemyCauldronRecipe.IngredientStep ingredient = recipe.findIngredient(stack, dropped);
            if (cauldron.canAddIngredient(recipe, ingredient)) {
                return new IngredientMatch(recipe, ingredient);
            }
            return null;
        }

        for (AlchemyCauldronRecipe recipe : AlchemyCauldronRecipes.all()) {
            if (recipe.requiresLitCampfire() && !hasLitCampfireBelow(level, pos)) {
                continue;
            }
            if (!recipe.canStartFrom(state)) {
                continue;
            }
            AlchemyCauldronRecipe.IngredientStep ingredient = recipe.findIngredient(stack, dropped);
            if (ingredient != null && ingredient.canStartRecipe()) {
                return new IngredientMatch(recipe, ingredient);
            }
        }
        return null;
    }

    private static void replaceConsumedItem(Player player, InteractionHand hand, ItemStack consumedStack, ItemStack replacement) {
        if (player.getAbilities().instabuild) {
            if (!replacement.isEmpty()) {
                ItemStack copy = replacement.copy();
                if (!player.getInventory().add(copy)) {
                    player.drop(copy, false);
                }
            }
            return;
        }

        consumedStack.shrink(1);
        if (consumedStack.isEmpty()) {
            player.setItemInHand(hand, replacement);
            return;
        }

        if (!replacement.isEmpty()) {
            ItemStack copy = replacement.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        }
    }

    private static void spawnItem(ServerLevel level, BlockPos pos, ItemStack stack) {
        ItemEntity output = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D, stack.copy());
        output.setDeltaMovement(0.0D, 0.05D, 0.0D);
        level.addFreshEntity(output);
    }

    private static void playSound(ServerLevel level, BlockPos pos, Identifier soundId, float volume, float pitch) {
        SoundEvent sound = AlchemyCauldronRecipes.getSound(soundId);
        if (sound != null) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    private record IngredientMatch(AlchemyCauldronRecipe recipe, AlchemyCauldronRecipe.IngredientStep ingredient) {
    }

    private record CauldronAction(ItemStack output, String messageKey) {
    }
}
