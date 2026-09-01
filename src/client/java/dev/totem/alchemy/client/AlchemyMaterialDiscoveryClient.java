package dev.totem.alchemy.client;

import dev.totem.alchemy.manual.AlchemyMaterialCatalog;
import dev.totem.alchemy.network.AlchemyMaterialDiscoveredPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Plays the vanilla Totem-of-Undying item activation renderer for a discovered material. */
public final class AlchemyMaterialDiscoveryClient {
    private AlchemyMaterialDiscoveryClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                AlchemyMaterialDiscoveredPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    Item material = BuiltInRegistries.ITEM.getValue(payload.material());
                    if (material != null && material != Items.AIR && AlchemyMaterialCatalog.contains(material)) {
                        context.client().gameRenderer.displayItemActivation(new ItemStack(material));
                    }
                })
        );
    }
}
