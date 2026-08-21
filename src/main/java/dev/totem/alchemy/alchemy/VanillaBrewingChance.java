package dev.totem.alchemy.alchemy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;

/** Ingredient-sensitive completion roll for recipes executed by a vanilla brewing stand. */
public final class VanillaBrewingChance {
    public static final double DEFAULT_SUCCESS_CHANCE = 0.8D;
    public static final double UNSTABLE_BASE_PENALTY = 0.2D;
    private static final String TAG_UNSTABLE_MUSHROOM_BASE = "totem_alchemy_unstable_mushroom_base";
    private static final Map<Item, Double> INGREDIENT_CHANCES = Map.ofEntries(
            Map.entry(Items.REDSTONE, 0.92D),
            Map.entry(Items.SUGAR, 0.90D),
            Map.entry(Items.MELON_SLICE, 0.90D),
            Map.entry(Items.APPLE, 0.90D),
            Map.entry(Items.NETHER_WART, 0.88D),
            Map.entry(Items.SWEET_BERRIES, 0.88D),
            Map.entry(Items.STONE, 0.87D),
            Map.entry(Items.GOLDEN_CARROT, 0.86D),
            Map.entry(Items.SPIDER_EYE, 0.85D),
            Map.entry(Items.GLOW_BERRIES, 0.85D),
            Map.entry(Items.MAGMA_CREAM, 0.84D),
            Map.entry(Items.GLISTERING_MELON_SLICE, 0.83D),
            Map.entry(Items.HONEY_BOTTLE, 0.82D),
            Map.entry(Items.BLAZE_POWDER, 0.82D),
            Map.entry(Items.FIREFLY_BUSH, 0.82D),
            Map.entry(Items.SLIME_BLOCK, 0.81D),
            Map.entry(Items.PUFFERFISH, 0.80D),
            Map.entry(Items.CHERRY_LEAVES, 0.80D),
            Map.entry(Items.RABBIT_FOOT, 0.79D),
            Map.entry(Items.GHAST_TEAR, 0.78D),
            Map.entry(Items.FERMENTED_SPIDER_EYE, 0.77D),
            Map.entry(Items.PHANTOM_MEMBRANE, 0.76D),
            Map.entry(Items.GLOWSTONE_DUST, 0.75D),
            Map.entry(Items.GUNPOWDER, 0.74D),
            Map.entry(Items.COBWEB, 0.73D),
            Map.entry(Items.BREEZE_ROD, 0.72D),
            Map.entry(Items.TURTLE_HELMET, 0.71D),
            Map.entry(Items.DRAGON_BREATH, 0.68D),
            Map.entry(Items.RED_MUSHROOM, 0.65D),
            // Expensive restorative ingredients are reliable to process; their effect rolls remain independent.
            Map.entry(Items.GOLDEN_APPLE, 0.94D),
            Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 0.99D)
    );

    private VanillaBrewingChance() {}

    public static double chanceFor(ItemStack ingredient) {
        return INGREDIENT_CHANCES.getOrDefault(ingredient.getItem(), DEFAULT_SUCCESS_CHANCE);
    }

    public static double chanceFor(ItemStack ingredient, Iterable<ItemStack> potionInputs) {
        double chance = chanceFor(ingredient);
        for (ItemStack potionInput : potionInputs) {
            if (hasUnstableMushroomBase(potionInput)) return Math.max(0.0D, chance - UNSTABLE_BASE_PENALTY);
        }
        return chance;
    }

    public static boolean hasDesignedChance(ItemStack ingredient) {
        return INGREDIENT_CHANCES.containsKey(ingredient.getItem());
    }

    public static boolean isSuccessful(ItemStack ingredient, float randomRoll) {
        return randomRoll >= 0.0F && randomRoll < chanceFor(ingredient);
    }

    public static boolean isSuccessful(ItemStack ingredient, Iterable<ItemStack> potionInputs, float randomRoll) {
        return randomRoll >= 0.0F && randomRoll < chanceFor(ingredient, potionInputs);
    }

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
