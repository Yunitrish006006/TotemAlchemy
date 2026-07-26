package dev.totem.alchemy.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class StinkyMobEffect extends MobEffect {
    public StinkyMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B5A2A);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath("deadrecall", "stinky_movement_speed"),
                -0.25D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }
}
