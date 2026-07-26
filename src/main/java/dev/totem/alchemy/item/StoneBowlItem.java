package dev.totem.alchemy.item;

import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class StoneBowlItem extends Item {
    public StoneBowlItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);

        if (!state.is(Blocks.SULFUR)) {
            return InteractionResult.PASS;
        }

        if (!world.isClientSide()) {
            Player player = context.getPlayer();
            ItemStack inHand = context.getItemInHand();
            ItemStack sulfurBowl = new ItemStack(AlchemyItems.SULFUR_BOWL);

            world.removeBlock(pos, false);

            if (player != null) {
                if (player.getAbilities().instabuild) {
                    player.setItemInHand(context.getHand(), sulfurBowl);
                } else {
                    inHand.shrink(1);
                    if (inHand.isEmpty()) {
                        player.setItemInHand(context.getHand(), sulfurBowl);
                    } else if (!player.getInventory().add(sulfurBowl)) {
                        player.drop(sulfurBowl, false);
                    }
                }
            }
        }

        return InteractionResult.SUCCESS;
    }
}
