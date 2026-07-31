package dev.totem.alchemy.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.function.Function;

final class AlchemyItemRegistrar {
    private AlchemyItemRegistrar() {
    }

    static Item register(String namespace, String path, Function<Item.Properties, Item> itemFactory) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Item.Properties props = new Item.Properties().setId(itemKey);
        Item item = itemFactory.apply(props);
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.getValue(id);
        }
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }
}
