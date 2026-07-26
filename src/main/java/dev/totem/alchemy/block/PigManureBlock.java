package dev.totem.alchemy.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class PigManureBlock extends Block {
    private final BlockState cleanState;

    public PigManureBlock(BlockState cleanState, Properties properties) {
        super(properties);
        this.cleanState = cleanState;
    }

    public BlockState getCleanState() {
        return cleanState;
    }
}
