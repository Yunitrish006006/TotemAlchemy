package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.effect.AlchemyMobEffects;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Server-side visual feedback for the firefly mutation of Strength. */
public final class FireflyStrengthInteractions {
    private static final int AMBIENT_PARTICLE_INTERVAL_TICKS = 10;

    private FireflyStrengthInteractions() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FireflyStrengthInteractions::tickAmbientParticles);
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

    private static void tickAmbientParticles(MinecraftServer server) {
        if (server.getTickCount() % AMBIENT_PARTICLE_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && living.isAlive()
                        && living.hasEffect(AlchemyMobEffects.FIREFLY_STRENGTH)) {
                    level.sendParticles(
                            ParticleTypes.FIREFLY,
                            living.getX(),
                            living.getY() + living.getBbHeight() * 0.65D,
                            living.getZ(),
                            3,
                            0.65D,
                            0.55D,
                            0.65D,
                            0.01D
                    );
                }
            }
        }
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
