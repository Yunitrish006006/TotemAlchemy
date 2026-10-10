package dev.totem.alchemy.testing;

import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JR-01 checkpoint: distinguish a skipped Jupiter bootstrap callback from
 * deferred Minecraft 26.3 item components that are still unbound afterward.
 * Use this alone via --tests *MinecraftRegistryBootstrapTest before retrying
 * all of the failing container classes.
 */
@WithMinecraftItemComponents
class MinecraftRegistryBootstrapTest {
    @Test
    void globalJupiterExtensionRunsBeforeRegistryDependentTests() {
        assertTrue(MinecraftRegistryBootstrapExtension.hasBootstrappedForTests(),
                "Explicit BeforeAllCallback was not invoked before this test");
    }

    @Test
    void vanillaGlassBottleHasBoundComponents() {
        assertTrue(MinecraftRegistryBootstrapExtension.hasBootstrappedForTests());
        ItemStack glass = assertDoesNotThrow(() -> new ItemStack(Items.GLASS_BOTTLE),
                "Bootstrap succeeded but vanilla ItemStack components are still unbound");
        assertTrue(glass.is(Items.GLASS_BOTTLE));
        assertEquals(1, glass.getCount());
    }

    @Test
    void vanillaPotionAndHoneyContainersHaveBoundComponents() {
        ItemStack potion = assertDoesNotThrow(() -> new ItemStack(Items.POTION),
                "Vanilla potion's deferred components were never bound");
        ItemStack honey = assertDoesNotThrow(() -> new ItemStack(Items.HONEY_BOTTLE),
                "Vanilla honey bottle's deferred components were never bound");
        assertTrue(potion.is(Items.POTION));
        assertTrue(honey.is(Items.HONEY_BOTTLE));
    }

    @Test
    void totemHotCocoaAndLargeFlaskAreInitializedUnderFabricLoader() {
        // Loading the class registers the custom items via AlchemyItemRegistrar.
        AlchemyItems.register();
        ItemStack cocoa = assertDoesNotThrow(() -> new ItemStack(AlchemyItems.HOT_COCOA),
                "Custom Totem item did not receive a component prototype");
        ItemStack flask = assertDoesNotThrow(() -> new ItemStack(AlchemyItems.LARGE_POTION_FLASK),
                "Custom Totem flask did not receive a component prototype");
        assertTrue(cocoa.is(AlchemyItems.HOT_COCOA));
        assertTrue(flask.is(AlchemyItems.LARGE_POTION_FLASK));
    }
}
