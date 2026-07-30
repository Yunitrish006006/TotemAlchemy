package dev.totem.alchemy.entity.ai;

import dev.totem.alchemy.block.AlchemyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Queues one delayed manure deposit for every successful player feeding. The
 * queue spaces multiple deposits apart so a pile builds one snow-like layer at
 * a time instead of appearing as a full block.
 */
public final class PigManureGoal extends Goal {
    private static final int FIRST_DEPOSIT_DELAY_TICKS = 40;
    private static final int FOLLOW_UP_DEPOSIT_DELAY_TICKS = 20;

    private final Mob mob;
    private final Level level;
    private int pendingDeposits;
    private int depositDelayTicks;

    public PigManureGoal(Mob mob) {
        this.mob = mob;
        this.level = mob.level();
    }

    public void queueAfterFeeding() {
        pendingDeposits++;
        if (depositDelayTicks == 0) {
            depositDelayTicks = adjustedTickDelay(FIRST_DEPOSIT_DELAY_TICKS);
        }
    }

    @Override
    public boolean canUse() {
        return pendingDeposits > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return pendingDeposits > 0;
    }

    @Override
    public void tick() {
        if (--depositDelayTicks > 0) {
            return;
        }

        if (!mobGriefing() || AlchemyBlocks.addPigManureLayer(level, mob.blockPosition())) {
            pendingDeposits--;
            if (mobGriefing()) {
                BlockPos pos = mob.blockPosition();
                level.playSound(null, pos, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.7F, 0.8F);
            }
            depositDelayTicks = pendingDeposits > 0
                    ? adjustedTickDelay(FOLLOW_UP_DEPOSIT_DELAY_TICKS)
                    : 0;
        } else {
            // The pig is standing in an unsuitable block (for example water).
            // Keep the already-earned deposit queued and try again shortly.
            depositDelayTicks = adjustedTickDelay(FOLLOW_UP_DEPOSIT_DELAY_TICKS);
        }
    }

    private boolean mobGriefing() {
        return (Boolean) getServerLevel(mob).getGameRules().get(GameRules.MOB_GRIEFING);
    }
}
