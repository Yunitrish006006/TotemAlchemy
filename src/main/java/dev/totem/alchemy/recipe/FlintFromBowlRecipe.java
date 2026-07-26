package dev.totem.alchemy.recipe;

import com.mojang.serialization.MapCodec;
import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Converts gravel to flint while returning the Alchemy-owned Stone Bowl. */
public final class FlintFromBowlRecipe extends CustomRecipe {
    private static final FlintFromBowlRecipe INSTANCE = new FlintFromBowlRecipe();

    public static final MapCodec<FlintFromBowlRecipe> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, FlintFromBowlRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<FlintFromBowlRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 2) {
            return false;
        }

        boolean hasGravel = false;
        boolean hasStoneBowl = false;
        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.GRAVEL)) {
                hasGravel = true;
                continue;
            }
            if (stack.is(AlchemyItems.STONE_BOWL)) {
                hasStoneBowl = true;
                continue;
            }
            return false;
        }

        return hasGravel && hasStoneBowl;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return new ItemStack(Items.FLINT);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int index = 0; index < input.size(); index++) {
            if (input.getItem(index).is(AlchemyItems.STONE_BOWL)) {
                remaining.set(index, new ItemStack(AlchemyItems.STONE_BOWL));
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }
}
