package dev.totem.alchemy.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class CherryBloomMobEffect extends MobEffect {
    public CherryBloomMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF6A6C8);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath("deadrecall", "cherry_bloom_movement_speed"),
                0.22D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }
}
