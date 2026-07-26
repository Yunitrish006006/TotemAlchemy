package dev.totem.alchemy.recipe;

import dev.totem.alchemy.registry.AlchemyItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class CocoaPowderRecipe extends CustomRecipe {
    private static final CocoaPowderRecipe INSTANCE = new CocoaPowderRecipe();

    public static final MapCodec<CocoaPowderRecipe> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, CocoaPowderRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<CocoaPowderRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 3) {
            return false;
        }

        boolean hasCocoaBeans = false;
        boolean hasSugar = false;
        boolean hasBowl = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.COCOA_BEANS)) {
                hasCocoaBeans = true;
                continue;
            }
            if (stack.is(Items.SUGAR)) {
                hasSugar = true;
                continue;
            }
            if (stack.is(AlchemyItems.STONE_BOWL)) {
                hasBowl = true;
                continue;
            }
            return false;
        }

        return hasCocoaBeans && hasSugar && hasBowl;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return new ItemStack(AlchemyItems.COCOA_POWDER);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }
}
