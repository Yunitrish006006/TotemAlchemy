package dev.totem.alchemy.recipe;

import com.mojang.serialization.MapCodec;
import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.List;

/** Converts gravel to flint while returning the Alchemy-owned Stone Bowl. */
public final class FlintFromBowlRecipe extends NormalCraftingRecipe {
    private static final Ingredient GRAVEL = Ingredient.of(Items.GRAVEL);
    private static final Ingredient STONE_BOWL = Ingredient.of(AlchemyItems.STONE_BOWL);
    private static final FlintFromBowlRecipe INSTANCE = new FlintFromBowlRecipe();

    public static final MapCodec<FlintFromBowlRecipe> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, FlintFromBowlRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<FlintFromBowlRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private FlintFromBowlRecipe() {
        super(
                new Recipe.CommonInfo(true),
                new CraftingBookInfo(CraftingBookCategory.MISC, "")
        );
    }

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
            if (AlchemyItems.isStoneBowl(stack)) {
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
            if (AlchemyItems.isStoneBowl(input.getItem(index))) {
                remaining.set(index, new ItemStack(AlchemyItems.STONE_BOWL));
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<FlintFromBowlRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.create(List.of(GRAVEL, STONE_BOWL));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(
                List.of(GRAVEL.display(), STONE_BOWL.display()),
                new SlotDisplay.ItemSlotDisplay(Items.FLINT),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
        ));
    }
}
