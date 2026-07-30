package dev.totem.alchemy.mixin;

import dev.totem.alchemy.alchemy.PigManureInteractions;
import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Snowball.class)
public abstract class SnowballMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void deadrecall$applyPigManureStink(EntityHitResult hitResult, CallbackInfo ci) {
        Snowball snowball = (Snowball) (Object) this;
        if (!AlchemyItems.isPigManure(snowball.getItem())) {
            return;
        }

        if (snowball.level() instanceof ServerLevel serverLevel
                && hitResult.getEntity() instanceof LivingEntity target) {
            PigManureInteractions.applyStink(serverLevel, target, snowball.getOwner());
        }
        ci.cancel();
    }

    @Inject(method = "onHit", at = @At("HEAD"))
    private void deadrecall$spreadPigManureOnGrass(HitResult hitResult, CallbackInfo ci) {
        Snowball snowball = (Snowball) (Object) this;
        if (!AlchemyItems.isPigManure(snowball.getItem())
                || !(snowball.level() instanceof ServerLevel serverLevel)
                || !(hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        PigManureInteractions.spreadToGrass(serverLevel, blockHitResult);
    }
}
