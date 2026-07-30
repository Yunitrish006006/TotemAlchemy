package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.AlchemyCauldronRecipe;
import dev.totem.alchemy.alchemy.AlchemyCauldronRecipes;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

public final class AlchemyItemIdMigrationGameTest {
    @GameTest(maxTicks = 20)
    public void canonicalAndLegacyIdsAreAllRegistered(GameTestHelper helper) {
        for (Mapping mapping : mappings()) {
            require(helper, mapping.canonicalId().equals(
                            BuiltInRegistries.ITEM.getKey(mapping.canonical()).toString()),
                    "Wrong canonical ID: " + mapping.canonicalId());
            require(helper, mapping.legacyId().equals(
                            BuiltInRegistries.ITEM.getKey(mapping.legacy()).toString()),
                    "Legacy ID is no longer registered: " + mapping.legacyId());
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void migrationPreservesFullComponentPatch(GameTestHelper helper) {
        Component name = Component.literal("legacy alchemy");
        CompoundTag tag = new CompoundTag();
        tag.putString("owner", "totem-alchemy");
        CustomData customData = CustomData.of(tag);

        for (Mapping mapping : mappings()) {
            ItemStack legacy = new ItemStack(mapping.legacy());
            legacy.set(DataComponents.CUSTOM_NAME, name);
            legacy.set(DataComponents.CUSTOM_DATA, customData);
            ItemStack migrated = AlchemyItems.migrateLegacy(legacy);

            require(helper, migrated.is(mapping.canonical()),
                    "Legacy item did not migrate: " + mapping.legacyId());
            require(helper, name.equals(migrated.get(DataComponents.CUSTOM_NAME)),
                    "Migration changed custom name: " + mapping.legacyId());
            require(helper, customData.equals(migrated.get(DataComponents.CUSTOM_DATA)),
                    "Migration changed custom data: " + mapping.legacyId());
            require(helper, legacy.is(mapping.legacy()),
                    "Migration mutated source stack: " + mapping.legacyId());
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void customRecipesAcceptLegacyBowlsAndProduceCanonicalItems(GameTestHelper helper) {
        CraftingInput cocoaInput = CraftingInput.of(3, 1, List.of(
                new ItemStack(Items.COCOA_BEANS),
                new ItemStack(Items.SUGAR),
                new ItemStack(AlchemyItems.LEGACY_STONE_BOWL)
        ));
        RecipeHolder<CraftingRecipe> cocoaRecipe = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, cocoaInput, helper.getLevel())
                .orElseThrow(() -> helper.assertionException("Cocoa recipe rejected legacy stone bowl"));
        require(helper, cocoaRecipe.value().assemble(cocoaInput).is(AlchemyItems.COCOA_POWDER),
                "Cocoa recipe did not output canonical cocoa powder");

        CraftingInput flintInput = CraftingInput.of(2, 1, List.of(
                new ItemStack(Items.GRAVEL),
                new ItemStack(AlchemyItems.LEGACY_STONE_BOWL)
        ));
        RecipeHolder<CraftingRecipe> flintRecipe = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, flintInput, helper.getLevel())
                .orElseThrow(() -> helper.assertionException("Flint recipe rejected legacy stone bowl"));
        require(helper, flintRecipe.value().assemble(flintInput).is(Items.FLINT),
                "Flint recipe output changed");
        require(helper, flintRecipe.value().getRemainingItems(flintInput).get(1).is(AlchemyItems.STONE_BOWL),
                "Flint recipe did not migrate returned bowl to canonical ID");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void cauldronRecipesBridgeLegacyIngredientsToCanonicalResults(GameTestHelper helper) {
        AlchemyCauldronRecipe cocoa = requireRecipe(helper, "hot_cocoa");
        AlchemyCauldronRecipe.IngredientStep cocoaStep = cocoa.findIngredient(
                new ItemStack(AlchemyItems.LEGACY_COCOA_POWDER), false);
        require(helper, cocoaStep != null, "Hot cocoa rejected legacy cocoa powder");
        require(helper, cocoaStep.createRemainderStack().is(AlchemyItems.STONE_BOWL),
                "Hot cocoa did not return canonical stone bowl");
        require(helper, cocoa.createResultStack().is(AlchemyItems.HOT_COCOA),
                "Hot cocoa result is not canonical");

        AlchemyCauldronRecipe saltpeter = requireRecipe(helper, "saltpeter");
        require(helper, saltpeter.findIngredient(new ItemStack(AlchemyItems.LEGACY_WOOD_ASH), false) != null,
                "Saltpeter rejected legacy wood ash");
        require(helper, saltpeter.findIngredient(new ItemStack(AlchemyItems.LEGACY_PIG_MANURE), false) != null,
                "Saltpeter rejected legacy pig manure");
        require(helper, saltpeter.createResultStack().is(AlchemyItems.SALTPETER),
                "Saltpeter result is not canonical");

        require(helper, requireRecipe(helper, "cherry_brew").createResultStack().is(AlchemyItems.CHERRY_BREW),
                "Cherry brew result is not canonical");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void gunpowderRecipeAcceptsBothIdGenerations(GameTestHelper helper) {
        for (boolean legacy : List.of(false, true)) {
            CraftingInput input = CraftingInput.of(3, 1, List.of(
                    new ItemStack(legacy ? AlchemyItems.LEGACY_SULFUR_BOWL : AlchemyItems.SULFUR_BOWL),
                    new ItemStack(legacy ? AlchemyItems.LEGACY_SALTPETER : AlchemyItems.SALTPETER),
                    new ItemStack(Items.COAL)
            ));
            RecipeHolder<CraftingRecipe> recipe = helper.getLevel().recipeAccess()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                    .orElseThrow(() -> helper.assertionException(
                            "Gunpowder recipe rejected " + (legacy ? "legacy" : "canonical") + " items"));
            ItemStack result = recipe.value().assemble(input);
            require(helper, result.is(Items.GUNPOWDER) && result.getCount() == 4,
                    "Gunpowder recipe result changed");
            require(helper, recipe.value().getRemainingItems(input).get(0).is(AlchemyItems.STONE_BOWL),
                    "Sulfur bowl did not return canonical stone bowl");
        }
        helper.succeed();
    }

    private static AlchemyCauldronRecipe requireRecipe(GameTestHelper helper, String path) {
        AlchemyCauldronRecipe recipe = AlchemyCauldronRecipes.get(
                Identifier.fromNamespaceAndPath("deadrecall", path));
        if (recipe == null) throw helper.assertionException("Missing cauldron recipe: " + path);
        return recipe;
    }

    private static List<Mapping> mappings() {
        return List.of(
                mapping(AlchemyItems.SALTPETER, AlchemyItems.LEGACY_SALTPETER, "saltpeter"),
                mapping(AlchemyItems.PIG_MANURE, AlchemyItems.LEGACY_PIG_MANURE, "pig_manure"),
                mapping(AlchemyItems.WOOD_ASH, AlchemyItems.LEGACY_WOOD_ASH, "wood_ash"),
                mapping(AlchemyItems.COCOA_POWDER, AlchemyItems.LEGACY_COCOA_POWDER, "cocoa_powder"),
                mapping(AlchemyItems.HOT_COCOA, AlchemyItems.LEGACY_HOT_COCOA, "hot_cocoa"),
                mapping(AlchemyItems.CHERRY_BREW, AlchemyItems.LEGACY_CHERRY_BREW, "cherry_brew"),
                mapping(AlchemyItems.STONE_BOWL, AlchemyItems.LEGACY_STONE_BOWL, "stone_bowl"),
                mapping(AlchemyItems.SULFUR_BOWL, AlchemyItems.LEGACY_SULFUR_BOWL, "sulfur_bowl")
        );
    }

    private static Mapping mapping(Item canonical, Item legacy, String path) {
        return new Mapping(canonical, legacy, "totem:alchemy/" + path, "deadrecall:" + path);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw helper.assertionException(message);
    }

    private record Mapping(Item canonical, Item legacy, String canonicalId, String legacyId) {
    }
}
