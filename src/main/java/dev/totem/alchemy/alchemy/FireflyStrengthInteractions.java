package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.effect.AlchemyMobEffects;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Server-side combat feedback for the firefly mutation of Strength. */
public final class FireflyStrengthInteractions {
    private FireflyStrengthInteractions() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0.0F) {
                return;
            }
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity livingAttacker
                    && livingAttacker.hasEffect(AlchemyMobEffects.FIREFLY_STRENGTH)
                    && entity.level() instanceof ServerLevel level) {
                spawnHitParticles(level, entity);
            }
        });
    }

    private static void spawnHitParticles(ServerLevel level, LivingEntity target) {
        level.sendParticles(
                ParticleTypes.FIREFLY,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.6D,
                target.getZ(),
                18,
                0.5D,
                0.55D,
                0.5D,
                0.04D
        );
    }
}
