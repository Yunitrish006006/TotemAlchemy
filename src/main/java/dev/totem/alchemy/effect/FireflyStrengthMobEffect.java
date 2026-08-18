package dev.totem.alchemy.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Strength-equivalent mutation whose visual identity comes from fireflies. */
public final class FireflyStrengthMobEffect extends MobEffect {
    public FireflyStrengthMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xD8F26A);
        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                Identifier.fromNamespaceAndPath("deadrecall", "firefly_strength_attack_damage"),
                3.3D,
                AttributeModifier.Operation.ADD_VALUE
        );
    }
}
