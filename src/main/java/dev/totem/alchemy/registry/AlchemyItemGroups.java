package dev.totem.alchemy.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Adds Alchemy items to DeadRecall's legacy tab or creates that tab standalone. */
public final class AlchemyItemGroups {
    private static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("deadrecall", "main")
    );

    private AlchemyItemGroups() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("deadrecall")
                && BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(TAB_KEY).isEmpty()) {
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY,
                    FabricCreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.deadrecall.main"))
                            .icon(() -> new ItemStack(AlchemyItems.CHERRY_BREW))
                            .build());
        }
        CreativeModeTabEvents.modifyOutputEvent(TAB_KEY).register(AlchemyItemGroups::addItems);
    }

    private static void addItems(FabricCreativeModeTabOutput output) {
        output.accept(AlchemyItems.SALTPETER);
        output.accept(AlchemyItems.PIG_MANURE);
        output.accept(AlchemyItems.WOOD_ASH);
        output.accept(AlchemyItems.COCOA_POWDER);
        output.accept(AlchemyItems.HOT_COCOA);
        output.accept(AlchemyItems.CHERRY_BREW);
        output.accept(AlchemyItems.STONE_BOWL);
        output.accept(AlchemyItems.SULFUR_BOWL);
        output.accept(AlchemyItems.LARGE_POTION_FLASK);
    }
}
