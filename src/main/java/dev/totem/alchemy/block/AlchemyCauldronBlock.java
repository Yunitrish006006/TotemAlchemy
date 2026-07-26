package dev.totem.alchemy.block;

import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.block.entity.AlchemyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class AlchemyCauldronBlock extends LayeredCauldronBlock implements EntityBlock {
    public AlchemyCauldronBlock(Properties properties) {
        super(Biome.Precipitation.RAIN, CauldronInteractions.WATER, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlchemyCauldronBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != AlchemyBlockEntities.ALCHEMY_CAULDRON) {
            return null;
        }
        return (world, pos, blockState, blockEntity) ->
                AlchemyCauldronBlockEntity.serverTick(world, pos, blockState, (AlchemyCauldronBlockEntity) blockEntity);
    }
}
