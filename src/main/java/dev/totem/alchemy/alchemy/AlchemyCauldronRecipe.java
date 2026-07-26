package dev.totem.alchemy.alchemy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public record AlchemyCauldronRecipe(
        Identifier id,
        StartState startState,
        int initialLevel,
        boolean requiresLitCampfire,
        CookMode cookMode,
        int cookTicks,
        boolean consumeLevelPerCook,
        List<IngredientStep> ingredients,
        Result result,
        String defaultMessageKey,
        Identifier defaultAddSound,
        Identifier completeSound
) {
    public boolean canStartFrom(BlockState state) {
        return switch (startState) {
            case EMPTY_CAULDRON -> state.is(Blocks.CAULDRON);
            case FULL_WATER_CAULDRON -> state.is(Blocks.WATER_CAULDRON)
                    && state.getValue(LayeredCauldronBlock.LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL;
        };
    }

    public IngredientStep findIngredient(ItemStack stack, boolean dropped) {
        for (IngredientStep ingredient : ingredients) {
            if (ingredient.matches(stack) && ingredient.isAllowed(dropped)) {
                return ingredient;
            }
        }
        return null;
    }

    public boolean hasIngredient(String ingredientId) {
        for (IngredientStep ingredient : ingredients) {
            if (ingredient.id().equals(ingredientId)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack createResultStack() {
        return result.createStack();
    }

    public enum StartState {
        EMPTY_CAULDRON,
        FULL_WATER_CAULDRON
    }

    public enum CookMode {
        PER_INGREDIENT,
        AFTER_ALL_INPUTS
    }

    public enum ResultType {
        DROP_ITEM,
        BOTTLED_ITEM
    }

    public record IngredientStep(
            String id,
            List<Item> items,
            Item remainder,
            boolean allowRightClick,
            boolean allowDropped,
            boolean canStartRecipe,
            String messageKey,
            Identifier sound
    ) {
        public boolean matches(ItemStack stack) {
            for (Item item : items) {
                if (stack.is(item)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isAllowed(boolean dropped) {
            return dropped ? allowDropped : allowRightClick;
        }

        public boolean canStartRecipe() {
            return canStartRecipe;
        }

        public ItemStack createRemainderStack() {
            return remainder == null || remainder == Items.AIR ? ItemStack.EMPTY : new ItemStack(remainder);
        }

        public String messageOrDefault(String fallback) {
            return messageKey == null || messageKey.isBlank() ? fallback : messageKey;
        }

        public Identifier soundOrDefault(Identifier fallback) {
            return sound == null ? fallback : sound;
        }
    }

    public record Result(
            ResultType type,
            Item item,
            int count,
            Item containerItem,
            String messageKey,
            Identifier sound
    ) {
        public boolean matchesContainer(ItemStack stack) {
            return containerItem != null && stack.is(containerItem);
        }

        public ItemStack createStack() {
            int safeCount = Math.max(1, count);
            Item resultItem = item == null ? BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:air")) : item;
            if (resultItem == null || resultItem == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(resultItem, safeCount);
        }
    }
}
