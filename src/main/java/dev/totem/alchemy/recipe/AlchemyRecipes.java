package dev.totem.alchemy.recipe;

import dev.totem.alchemy.TotemAlchemy;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class AlchemyRecipes {
    public static final RecipeSerializer<CocoaPowderRecipe> COCOA_POWDER =
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Identifier.fromNamespaceAndPath("deadrecall", "cocoa_powder"),
            CocoaPowderRecipe.SERIALIZER);

    public static final RecipeSerializer<FlintFromBowlRecipe> FLINT_FROM_BOWL =
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
            Identifier.fromNamespaceAndPath("deadrecall", "flint_from_bowl"),
            FlintFromBowlRecipe.SERIALIZER);

    private AlchemyRecipes() {
    }

    public static void register() {
        TotemAlchemy.LOGGER.info("正在註冊模組配方...");
    }
}
