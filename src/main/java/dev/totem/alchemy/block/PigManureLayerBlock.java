package dev.totem.alchemy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A manure pile with the same one-to-eight layer geometry as snow. Unlike
 * snow, it never melts during random ticks.
 */
public final class PigManureLayerBlock extends SnowLayerBlock {
    public PigManureLayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Pig manure persists until it is collected with a shovel.
    }
}
