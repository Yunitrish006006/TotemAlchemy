package dev.totem.alchemy.block;

import dev.totem.alchemy.TotemAlchemy;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiFunction;

public final class AlchemyBlocks {
    public static final Block PIG_MANURE_DIRT = registerPigManureBlock("pig_manure_dirt", Blocks.DIRT);
    public static final Block PIG_MANURE_GRASS_BLOCK = registerPigManureBlock("pig_manure_grass_block", Blocks.GRASS_BLOCK);
    public static final Block PIG_MANURE_COARSE_DIRT = registerPigManureBlock("pig_manure_coarse_dirt", Blocks.COARSE_DIRT);
    public static final Block PIG_MANURE_ROOTED_DIRT = registerPigManureBlock("pig_manure_rooted_dirt", Blocks.ROOTED_DIRT);
    public static final Block PIG_MANURE_PODZOL = registerPigManureBlock("pig_manure_podzol", Blocks.PODZOL);
    public static final Block PIG_MANURE_MYCELIUM = registerPigManureBlock("pig_manure_mycelium", Blocks.MYCELIUM);
    public static final Block PIG_MANURE_MUD = registerPigManureBlock("pig_manure_mud", Blocks.MUD);

    public static final Block ALCHEMY_CAULDRON = registerBlock("alchemy_cauldron", Blocks.CAULDRON,
            (base, properties) -> new AlchemyCauldronBlock(properties));

    private AlchemyBlocks() {
    }

    private static Block registerPigManureBlock(String name, Block cleanBlock) {
        return registerBlock(name, cleanBlock,
                (base, properties) -> new PigManureBlock(base.defaultBlockState(), properties));
    }

    private static Block registerBlock(String name, Block baseBlock, BiFunction<Block, BlockBehaviour.Properties, Block> factory) {
        Identifier id = Identifier.fromNamespaceAndPath("deadrecall", name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(baseBlock).setId(blockKey);
        Block block = factory.apply(baseBlock, properties);
        return Registry.register(BuiltInRegistries.BLOCK, id, block);
    }

    public static BlockState getPigManureState(BlockState cleanState) {
        if (cleanState.is(Blocks.DIRT)) {
            return PIG_MANURE_DIRT.defaultBlockState();
        }
        if (cleanState.is(Blocks.GRASS_BLOCK)) {
            return PIG_MANURE_GRASS_BLOCK.defaultBlockState();
        }
        if (cleanState.is(Blocks.COARSE_DIRT)) {
            return PIG_MANURE_COARSE_DIRT.defaultBlockState();
        }
        if (cleanState.is(Blocks.ROOTED_DIRT)) {
            return PIG_MANURE_ROOTED_DIRT.defaultBlockState();
        }
        if (cleanState.is(Blocks.PODZOL)) {
            return PIG_MANURE_PODZOL.defaultBlockState();
        }
        if (cleanState.is(Blocks.MYCELIUM)) {
            return PIG_MANURE_MYCELIUM.defaultBlockState();
        }
        if (cleanState.is(Blocks.MUD)) {
            return PIG_MANURE_MUD.defaultBlockState();
        }
        return null;
    }

    public static BlockState getCleanState(BlockState manureState) {
        if (manureState.getBlock() instanceof PigManureBlock pigManureBlock) {
            return pigManureBlock.getCleanState();
        }
        return null;
    }

    public static void register() {
        TotemAlchemy.LOGGER.info("正在註冊模組方塊...");
    }
}
