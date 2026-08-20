package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
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
            if (action.message() != null) {
                player.sendOverlayMessage(action.message());
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
        BlockState targetState = world.getBlockState(pos);
        BlockState cleanState = AlchemyBlocks.getCleanState(targetState);
        boolean layeredManure = targetState.is(AlchemyBlocks.PIG_MANURE_LAYER);
        if ((!layeredManure && cleanState == null) || !stack.is(ItemTags.SHOVELS)) {
            return InteractionResult.PASS;
        }

        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        int manureCount = layeredManure ? targetState.getValue(SnowLayerBlock.LAYERS) : 1;
        world.setBlock(pos, layeredManure ? Blocks.AIR.defaultBlockState() : cleanState, 3);
        ItemStack manure = new ItemStack(AlchemyItems.PIG_MANURE, manureCount);
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
        if (canApplyMixtureInteraction(level, pos, stack, dropped)) {
            return true;
        }
        if (findExtractionRecipe(level, pos, stack) != null) {
            return true;
        }
        return findIngredientMatch(level, pos, stack, dropped) != null;
    }

    private static boolean canApplyMixtureInteraction(Level level, BlockPos pos, ItemStack stack, boolean dropped) {
        BlockState state = level.getBlockState(pos);
        if (!dropped && AlchemyMixtureBottle.isDrinkablePotion(stack)) {
            if (state.is(Blocks.CAULDRON)) {
                return true;
            }
            if (state.is(Blocks.WATER_CAULDRON)) {
                return state.getValue(LayeredCauldronBlock.LEVEL) < AlchemyMixtureState.MAX_VOLUME_UNITS;
            }
            if (state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                return blockEntity instanceof AlchemyCauldronBlockEntity cauldron
                        && cauldron.hasMixture()
                        && cauldron.mixtureSnapshot().volumeUnits() < AlchemyMixtureState.MAX_VOLUME_UNITS;
            }
            return false;
        }

        if (!dropped && stack.is(Items.GLASS_BOTTLE) && state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof AlchemyCauldronBlockEntity cauldron && cauldron.hasMixture();
        }

        AlchemyMixtureState preview = previewMixture(level, pos);
        return !preview.isEmpty() && AlchemyMixtureBrewing.canReact(level, preview, stack);
    }

    private static CauldronAction tryApplyAlchemyItem(ServerLevel level, BlockPos pos, ItemStack stack, boolean dropped) {
        CauldronAction mixtureAction = tryApplyMixtureInteraction(level, pos, stack, dropped);
        if (mixtureAction != null) {
            return mixtureAction;
        }

        AlchemyCauldronRecipe extractionRecipe = findExtractionRecipe(level, pos, stack);
        if (extractionRecipe != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AlchemyCauldronBlockEntity cauldron
                    && cauldron.extractBottledResult(extractionRecipe, level, pos, level.getBlockState(pos), stack)) {
                playSound(level, pos, extractionRecipe.result().sound(), 1.0F, 1.0F);
                return new CauldronAction(
                        extractionRecipe.createResultStack(),
                        translatedOrNull(extractionRecipe.result().messageKey())
                );
            }
        }

        IngredientMatch match = findIngredientMatch(level, pos, stack, dropped);
        if (match == null) {
            return null;
        }

        if (!match.recipe().isIngredientSuccessful(match.ingredient(), level.getRandom().nextFloat())) {
            showIngredientFailure(level, pos, match.recipe(), match.ingredient());
            return new CauldronAction(match.ingredient().createRemainderStack(), null);
        }

        AlchemyCauldronBlockEntity cauldron = ensureAlchemyCauldron(level, pos, match.recipe());
        if (cauldron == null || !cauldron.addIngredient(match.recipe(), match.ingredient())) {
            return null;
        }

        playSound(level, pos, match.ingredient().soundOrDefault(match.recipe().defaultAddSound()), 0.8F, 1.0F);
        return new CauldronAction(
                match.ingredient().createRemainderStack(),
                Component.translatable(match.ingredient().messageOrDefault(match.recipe().defaultMessageKey()))
                        .append(Component.literal(" "))
                        .append(Component.translatable(
                                "message.deadrecall.alchemy.ingredient_success_chance",
                                chancePercent(match.ingredient().successChance())
                        ))
        );
    }

    private static CauldronAction tryApplyMixtureInteraction(
            ServerLevel level,
            BlockPos pos,
            ItemStack stack,
            boolean dropped
    ) {
        BlockState state = level.getBlockState(pos);

        if (!dropped && AlchemyMixtureBottle.isDrinkablePotion(stack)) {
            AlchemyMixtureState incoming = AlchemyMixtureBottle.fromPotion(stack);
            if (incoming.isEmpty()) {
                return null;
            }
            AlchemyCauldronBlockEntity cauldron = ensureMixtureCauldronForPour(level, pos, incoming);
            if (cauldron == null) {
                return null;
            }
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return new CauldronAction(
                    new ItemStack(Items.GLASS_BOTTLE),
                    Component.translatable("message.deadrecall.alchemy.mixture_poured")
            );
        }

        if (!dropped && stack.is(Items.GLASS_BOTTLE) && state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron) || !cauldron.hasMixture()) {
                return null;
            }
            AlchemyMixtureState bottled = cauldron.extractMixtureBottle();
            if (bottled.isEmpty()) {
                return null;
            }
            ItemStack output = AlchemyMixtureBottle.toPotion(bottled);
            int remaining = cauldron.mixtureSnapshot().volumeUnits();
            if (remaining <= 0) {
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
            } else {
                level.setBlock(pos, level.getBlockState(pos).setValue(LayeredCauldronBlock.LEVEL, remaining), 3);
            }
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return new CauldronAction(output, Component.translatable("message.deadrecall.alchemy.mixture_bottled"));
        }

        AlchemyMixtureState preview = previewMixture(level, pos);
        if (preview.isEmpty() || !AlchemyMixtureBrewing.canReact(level, preview, stack)) {
            return null;
        }
        AlchemyCauldronBlockEntity cauldron = ensureMixtureCauldronForReaction(level, pos);
        if (cauldron == null || !cauldron.scheduleMixtureReaction(level, stack)) {
            return null;
        }
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 0.9F);
        return new CauldronAction(
                ItemStack.EMPTY,
                Component.translatable("message.deadrecall.alchemy.mixture_reaction_started")
        );
    }

    private static AlchemyMixtureState previewMixture(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON)) {
            return AlchemyMixtureBrewing.waterState(state.getValue(LayeredCauldronBlock.LEVEL));
        }
        if (state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AlchemyCauldronBlockEntity cauldron && cauldron.hasMixture()) {
                return cauldron.mixtureSnapshot();
            }
        }
        return AlchemyMixtureState.empty();
    }

    private static AlchemyCauldronBlockEntity ensureMixtureCauldronForPour(
            ServerLevel level,
            BlockPos pos,
            AlchemyMixtureState incoming
    ) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.CAULDRON)) {
            BlockState alchemyState = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, incoming.volumeUnits());
            level.setBlock(pos, alchemyState, 3);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AlchemyCauldronBlockEntity cauldron && cauldron.initializeMixture(incoming)) {
                return cauldron;
            }
            return null;
        }

        if (state.is(Blocks.WATER_CAULDRON)) {
            int waterVolume = state.getValue(LayeredCauldronBlock.LEVEL);
            if (waterVolume + incoming.volumeUnits() > AlchemyMixtureState.MAX_VOLUME_UNITS) {
                return null;
            }
            BlockState alchemyState = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, waterVolume);
            level.setBlock(pos, alchemyState, 3);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron)
                    || !cauldron.initializeMixture(AlchemyMixtureBrewing.waterState(waterVolume))
                    || !cauldron.mergeMixture(incoming)) {
                return null;
            }
            level.setBlock(pos, level.getBlockState(pos).setValue(
                    LayeredCauldronBlock.LEVEL,
                    cauldron.mixtureSnapshot().volumeUnits()
            ), 3);
            return cauldron;
        }

        if (state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AlchemyCauldronBlockEntity cauldron
                    && cauldron.hasMixture()
                    && cauldron.mergeMixture(incoming)) {
                level.setBlock(pos, state.setValue(
                        LayeredCauldronBlock.LEVEL,
                        cauldron.mixtureSnapshot().volumeUnits()
                ), 3);
                return cauldron;
            }
        }
        return null;
    }

    private static AlchemyCauldronBlockEntity ensureMixtureCauldronForReaction(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON)) {
            int volume = state.getValue(LayeredCauldronBlock.LEVEL);
            BlockState alchemyState = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, volume);
            level.setBlock(pos, alchemyState, 3);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AlchemyCauldronBlockEntity cauldron
                    && cauldron.initializeMixture(AlchemyMixtureBrewing.waterState(volume))) {
                return cauldron;
            }
            return null;
        }
        if (state.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof AlchemyCauldronBlockEntity cauldron && cauldron.hasMixture()
                    ? cauldron
                    : null;
        }
        return null;
    }

    private static void showIngredientFailure(
            ServerLevel level,
            BlockPos pos,
            AlchemyCauldronRecipe recipe,
            AlchemyCauldronRecipe.IngredientStep ingredient
    ) {
        playSound(level, pos, recipe.failureSound(), 1.0F, 0.8F);
        level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5D,
                pos.getY() + 1.05D,
                pos.getZ() + 0.5D,
                8,
                0.22D,
                0.12D,
                0.22D,
                0.02D
        );
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D) {
                player.sendOverlayMessage(Component.translatable(
                        recipe.failureMessageKey(),
                        chancePercent(ingredient.successChance())
                ));
            }
        }
    }

    private static Component translatedOrNull(String messageKey) {
        return messageKey == null || messageKey.isBlank() ? null : Component.translatable(messageKey);
    }

    private static int chancePercent(double chance) {
        return (int) Math.round(chance * 100.0D);
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
            if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron)
                    || cauldron.hasMixture()
                    || cauldron.getRecipeId() == null) {
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

    private record CauldronAction(ItemStack output, Component message) {
    }
}
