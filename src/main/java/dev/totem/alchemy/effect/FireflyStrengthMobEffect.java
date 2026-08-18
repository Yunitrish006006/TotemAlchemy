package dev.totem.alchemy.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Strength-equivalent mutation whose visual identity comes from fireflies. */
public final class FireflyStrengthMobEffect extends MobEffect {
    private static final int AMBIENT_PARTICLE_INTERVAL_TICKS = 10;

    public FireflyStrengthMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xD8F26A);
        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                Identifier.fromNamespaceAndPath("deadrecall", "firefly_strength_attack_damage"),
                3.3D,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        level.sendParticles(
                ParticleTypes.FIREFLY,
                entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.65D,
                entity.getZ(),
                3,
                0.65D,
                0.55D,
                0.65D,
                0.01D
        );
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
        return tickCount % AMBIENT_PARTICLE_INTERVAL_TICKS == 0;
    }
}
