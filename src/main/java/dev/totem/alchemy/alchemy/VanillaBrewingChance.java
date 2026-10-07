package dev.totem.alchemy.alchemy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Carries Brewing Stand compatibility metadata that survives potion transformations. */
public final class VanillaBrewingChance {
    private static final String TAG_UNSTABLE_MUSHROOM_BASE = "totem_alchemy_unstable_mushroom_base";

    private VanillaBrewingChance() {}

    public static boolean hasUnstableMushroomBase(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getBooleanOr(TAG_UNSTABLE_MUSHROOM_BASE, false);
    }

    public static void markUnstableMushroomBase(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(TAG_UNSTABLE_MUSHROOM_BASE, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void carryUnstableMushroomBase(ItemStack input, ItemStack output) {
        if (hasUnstableMushroomBase(input)) markUnstableMushroomBase(output);
    }
}
