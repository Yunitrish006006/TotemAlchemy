package dev.totem.alchemy.testing;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First focused JUNIT-00 gate. Must run with the global Jupiter bootstrap
 * extension and the same Fabric Loader JUnit environment as production tests.
 * Do not mock item holders or bypass their deferred default components.
 */
class MinecraftRegistryBootstrapSmokeTest {
    @Test
    void vanillaItemStackHasBoundDefaultComponents() {
        ItemStack glass = new ItemStack(Items.GLASS_BOTTLE);
        ItemStack potion = new ItemStack(Items.POTION);
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        assertFalse(glass.isEmpty());
        assertFalse(potion.isEmpty());
        assertFalse(bucket.isEmpty());
        assertTrue(glass.getComponents() != null);
        assertTrue(potion.getComponents() != null);
        assertTrue(bucket.getComponents() != null);
    }
}
