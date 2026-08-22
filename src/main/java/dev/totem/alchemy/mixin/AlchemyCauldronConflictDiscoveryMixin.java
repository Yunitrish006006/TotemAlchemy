package dev.totem.alchemy.mixin;

import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records player research when directly combining liquids neutralizes opposing effects. */
@Mixin(AlchemyCauldronBlockEntity.class)
public abstract class AlchemyCauldronConflictDiscoveryMixin {
    @Unique
    private AlchemyMixtureState totemAlchemy$conflictBefore;
    @Unique
    private AlchemyMixtureState totemAlchemy$conflictIncoming;

    @Inject(method = "mergeMixture", at = @At("HEAD"))
    private void totemAlchemy$captureConflictInputs(
            AlchemyMixtureState incoming,
            CallbackInfoReturnable<Boolean> cir
    ) {
        AlchemyCauldronBlockEntity self = (AlchemyCauldronBlockEntity) (Object) this;
        totemAlchemy$conflictBefore = self.mixtureSnapshot();
        totemAlchemy$conflictIncoming = incoming == null ? AlchemyMixtureState.empty() : incoming.copy();
    }

    @Inject(method = "mergeMixture", at = @At("RETURN"))
    private void totemAlchemy$recordMixedConflict(
            AlchemyMixtureState incoming,
            CallbackInfoReturnable<Boolean> cir
    ) {
        try {
            if (!Boolean.TRUE.equals(cir.getReturnValue())) {
                return;
            }
            AlchemyCauldronBlockEntity self = (AlchemyCauldronBlockEntity) (Object) this;
            if (self.getLevel() instanceof ServerLevel serverLevel) {
                AlchemyDiscoveryService.recordMixtureConflict(
                        serverLevel,
                        self.getBlockPos(),
                        totemAlchemy$conflictBefore,
                        totemAlchemy$conflictIncoming,
                        self.mixtureSnapshot()
                );
            }
        } finally {
            totemAlchemy$conflictBefore = null;
            totemAlchemy$conflictIncoming = null;
        }
    }
}
