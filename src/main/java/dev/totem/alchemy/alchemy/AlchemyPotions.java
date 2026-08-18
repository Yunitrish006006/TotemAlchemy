package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.TotemAlchemy;
import dev.totem.alchemy.effect.AlchemyMobEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;

public final class AlchemyPotions {
    public static final Holder.Reference<Potion> SATURATION = register(
            "saturation",
            new Potion("saturation", new MobEffectInstance(MobEffects.SATURATION, 1, 0))
    );
    public static final Holder.Reference<Potion> STRONG_SATURATION = register(
            "strong_saturation",
            new Potion("saturation", new MobEffectInstance(MobEffects.SATURATION, 1, 1))
    );
    public static final Holder.Reference<Potion> RESISTANCE = register(
            "resistance",
            new Potion("resistance", new MobEffectInstance(MobEffects.RESISTANCE, 20 * 180, 0))
    );
    public static final Holder.Reference<Potion> LONG_RESISTANCE = register(
            "long_resistance",
            new Potion("resistance", new MobEffectInstance(MobEffects.RESISTANCE, 20 * 480, 0))
    );
    public static final Holder.Reference<Potion> STRONG_RESISTANCE = register(
            "strong_resistance",
            new Potion("resistance", new MobEffectInstance(MobEffects.RESISTANCE, 20 * 90, 1))
    );
    public static final Holder.Reference<Potion> CHERRY_SWIFTNESS = register(
            "cherry_swiftness",
            new Potion("cherry_swiftness", new MobEffectInstance(AlchemyMobEffects.CHERRY_BLOOM, 20 * 180, 0))
    );
    public static final Holder.Reference<Potion> LONG_CHERRY_SWIFTNESS = register(
            "long_cherry_swiftness",
            new Potion("cherry_swiftness", new MobEffectInstance(AlchemyMobEffects.CHERRY_BLOOM, 20 * 480, 0))
    );
    public static final Holder.Reference<Potion> STRONG_CHERRY_SWIFTNESS = register(
            "strong_cherry_swiftness",
            new Potion("cherry_swiftness", new MobEffectInstance(AlchemyMobEffects.CHERRY_BLOOM, 20 * 90, 1))
    );
    public static final Holder.Reference<Potion> FIREFLY_STRENGTH = register(
            "firefly_strength",
            new Potion("firefly_strength", new MobEffectInstance(AlchemyMobEffects.FIREFLY_STRENGTH, 20 * 180, 0))
    );
    public static final Holder.Reference<Potion> LONG_FIREFLY_STRENGTH = register(
            "long_firefly_strength",
            new Potion("firefly_strength", new MobEffectInstance(AlchemyMobEffects.FIREFLY_STRENGTH, 20 * 480, 0))
    );
    public static final Holder.Reference<Potion> STRONG_FIREFLY_STRENGTH = register(
            "strong_firefly_strength",
            new Potion("firefly_strength", new MobEffectInstance(AlchemyMobEffects.FIREFLY_STRENGTH, 20 * 90, 1))
    );

    private AlchemyPotions() {
    }

    private static Holder.Reference<Potion> register(String name, Potion potion) {
        Identifier id = Identifier.fromNamespaceAndPath("totem", "alchemy/" + name);
        ResourceKey<Potion> key = ResourceKey.create(Registries.POTION, id);
        return Registry.registerForHolder(BuiltInRegistries.POTION, key, potion);
    }

    public static void register() {
        TotemAlchemy.LOGGER.info("正在註冊多結果釀造藥水...");
    }
}
