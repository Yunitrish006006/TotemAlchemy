package dev.totem.alchemy.discovery;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

/** Stable material-to-potion identifier stored in each player's research journal. */
public final class AlchemyDiscoveryKey {
    private AlchemyDiscoveryKey() {
    }

    public static String of(Item ingredient, Holder<Potion> potion) {
        return BuiltInRegistries.ITEM.getKey(ingredient)
                + ">"
                + BuiltInRegistries.POTION.getKey(potion.value());
    }
}
