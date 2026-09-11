package dev.totem.alchemy.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Alchemy-owned enchantment behavior; native table and anvil handle acquisition and combination. */
public final class FlaskEnchantments {
    public static final ResourceKey<Enchantment> BOTTOMLESS = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("totem", "alchemy/bottomless"));
    public static final ResourceKey<Enchantment> CAPACITY = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("totem", "alchemy/capacity"));
    private FlaskEnchantments() { }
    public static int capacity(ItemStack stack) { return 3 + Math.min(5, level(stack, CAPACITY)); }
    public static boolean isBottomless(ItemStack stack) { return level(stack, BOTTOMLESS) > 0; }
    private static int level(ItemStack stack, ResourceKey<Enchantment> key) {
        if (!(stack.getItem() instanceof LargePotionFlaskItem)) return 0;
        for (var entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            if (entry.getKey().is(key)) return Math.max(0, entry.getIntValue());
        }
        return 0;
    }
}
