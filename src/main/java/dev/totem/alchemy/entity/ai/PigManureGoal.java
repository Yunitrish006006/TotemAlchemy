package dev.totem.alchemy.entity.ai;

import dev.totem.alchemy.block.AlchemyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.EnumSet;

public class PigManureGoal extends Goal {
    private static final int EAT_ANIMATION_TICKS = 40;

    private final Mob mob;
    private final Level level;
    private int eatAnimationTick;

    public PigManureGoal(Mob mob) {
        this.mob = mob;
        this.level = mob.level();
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        int chance = mob.isBaby() ? 50 : 1000;
        if (mob.getRandom().nextInt(adjustedTickDelay(chance)) != 0) {
            return false;
        }

        BlockPos pos = mob.blockPosition();
        if (level.getBlockState(pos).is(BlockTags.EDIBLE_FOR_SHEEP)) {
            return true;
        }
        return AlchemyBlocks.getPigManureState(level.getBlockState(pos.below())) != null;
    }

    @Override
    public void start() {
        eatAnimationTick = adjustedTickDelay(EAT_ANIMATION_TICKS);
        level.broadcastEntityEvent(mob, (byte) 10);
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        eatAnimationTick = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return eatAnimationTick > 0;
    }

    @Override
    public void tick() {
        eatAnimationTick = Math.max(0, eatAnimationTick - 1);
        if (eatAnimationTick != adjustedTickDelay(4)) {
            return;
        }

        BlockPos pos = mob.blockPosition();
        BlockState currentState = level.getBlockState(pos);
        boolean ate = false;
        if (currentState.is(BlockTags.EDIBLE_FOR_SHEEP)) {
            if (mobGriefing()) {
                level.destroyBlock(pos, false);
            }
            ate = true;
        }

        BlockPos groundPos = pos.below();
        BlockState groundState = level.getBlockState(groundPos);
        BlockState manureState = AlchemyBlocks.getPigManureState(groundState);
        if (manureState != null) {
            if (mobGriefing()) {
                level.levelEvent(2001, groundPos, Block.getId(groundState));
                level.setBlock(groundPos, manureState, 2);
            }
            ate = true;
        }

        if (ate) {
            mob.ate();
        }
    }

    private boolean mobGriefing() {
        return (Boolean) getServerLevel(mob).getGameRules().get(GameRules.MOB_GRIEFING);
    }
}
