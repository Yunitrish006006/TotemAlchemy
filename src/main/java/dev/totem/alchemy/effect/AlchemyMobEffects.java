package dev.totem.alchemy.effect;

import dev.totem.alchemy.TotemAlchemy;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;

public final class AlchemyMobEffects {
    public static final Holder.Reference<MobEffect> STINKY = register("stinky", new StinkyMobEffect());
    public static final Holder.Reference<MobEffect> CHERRY_BLOOM = register("cherry_bloom", new CherryBloomMobEffect());

    private AlchemyMobEffects() {
    }

    private static Holder.Reference<MobEffect> register(String name, MobEffect effect) {
        Identifier id = Identifier.fromNamespaceAndPath("deadrecall", name);
        ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, id);
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, key, effect);
    }

    public static void register() {
        TotemAlchemy.LOGGER.info("正在註冊模組狀態效果...");
    }
}
