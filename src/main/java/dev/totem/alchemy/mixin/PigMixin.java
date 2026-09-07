package dev.totem.alchemy.mixin;

import dev.totem.alchemy.entity.ai.PigManureGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Pig.class)
public abstract class PigMixin extends Animal {
    @Unique
    private PigManureGoal totem$manureGoal;

    protected PigMixin(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void totem$addPigManureGoal(CallbackInfo ci) {
        totem$manureGoal = new PigManureGoal((Mob) (Object) this);
        this.goalSelector.addGoal(5, totem$manureGoal);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void totem$queueManureAfterFeeding(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        ItemStack food = player.getItemInHand(hand);
        if (!level().isClientSide()
                && totem$manureGoal != null
                && isFood(food)
                && (canFallInLove() || canAgeUp())) {
            totem$manureGoal.queueAfterFeeding();
        }
    }
}
