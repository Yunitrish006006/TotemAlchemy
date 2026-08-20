package dev.totem.alchemy.client.mixture;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.mixture.AlchemyMixtureColor;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Colors only the vanilla water-tinted faces of the Alchemy Cauldron model. */
public final class AlchemyCauldronColorProvider {
    private AlchemyCauldronColorProvider() {
    }

    public static void register() {
        BlockColorRegistry.register(List.of(new BlockTintSource() {
            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof AlchemyCauldronBlockEntity cauldron && cauldron.hasMixture()) {
                    return ARGB.opaque(cauldron.mixtureColorRgb());
                }
                return ARGB.opaque(AlchemyMixtureColor.WATER_RGB);
            }

            @Override
            public int color(BlockState state) {
                return ARGB.opaque(AlchemyMixtureColor.WATER_RGB);
            }
        }), AlchemyBlocks.ALCHEMY_CAULDRON);
    }
}
