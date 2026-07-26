package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.effect.AlchemyMobEffects;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CherryBrewInteractions {
    private static final int AMBIENT_PARTICLE_INTERVAL_TICKS = 12;

    private CherryBrewInteractions() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CherryBrewInteractions::tickCherryBloomParticles);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0.0F) {
                return;
            }
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity livingAttacker && livingAttacker.hasEffect(AlchemyMobEffects.CHERRY_BLOOM)
                    && entity.level() instanceof ServerLevel level) {
                spawnHitParticles(level, entity);
            }
        });
    }

    private static void tickCherryBloomParticles(MinecraftServer server) {
        if (server.getTickCount() % AMBIENT_PARTICLE_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && living.isAlive() && living.hasEffect(AlchemyMobEffects.CHERRY_BLOOM)) {
                    spawnAmbientParticles(level, living);
                }
            }
        }
    }

    private static void spawnAmbientParticles(ServerLevel level, LivingEntity entity) {
        level.sendParticles(
                ParticleTypes.CHERRY_LEAVES,
                entity.getX(),
                entity.getY() + entity.getBbHeight() + 0.35D,
                entity.getZ(),
                3,
                0.75D,
                0.15D,
                0.75D,
                0.01D
        );
    }

    private static void spawnHitParticles(ServerLevel level, LivingEntity target) {
        level.sendParticles(
                ParticleTypes.CHERRY_LEAVES,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.6D,
                target.getZ(),
                16,
                0.45D,
                0.55D,
                0.45D,
                0.03D
        );
    }
}
