package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.AlchemyCauldronRecipe;
import dev.totem.alchemy.alchemy.AlchemyCauldronRecipes;
import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTestHelper;

/** Verifies that the cauldron state carried through the modular cutover survives saved-world reloads. */
public final class AlchemyCauldronPersistenceGameTest {
    private static final BlockPos CAULDRON_POS = new BlockPos(2, 2, 2);
    private static final Identifier HOT_COCOA = Identifier.fromNamespaceAndPath("deadrecall", "hot_cocoa");

    @GameTest(maxTicks = 40)
    public void currentCauldronStateRoundTripsThroughBlockEntityNbt(GameTestHelper helper) {
        AlchemyCauldronRecipe recipe = requireRecipe(helper);
        if (recipe == null) {
            return;
        }

        AlchemyCauldronBlockEntity cauldron = placeCauldron(helper);
        if (cauldron == null) {
            return;
        }

        AlchemyCauldronRecipe.IngredientStep firstIngredient = recipe.ingredients().getFirst();
        require(helper, cauldron.addIngredient(recipe, firstIngredient),
                "Fresh cauldron rejected the first hot-cocoa ingredient");

        AlchemyCauldronBlockEntity restored = reload(helper, cauldron);
        if (restored == null) {
            return;
        }

        require(helper, HOT_COCOA.equals(restored.getRecipeId()),
                "Reloaded cauldron did not retain its hot-cocoa recipe id");
        require(helper, !restored.canAddIngredient(recipe, firstIngredient),
                "Reloaded cauldron forgot an ingredient that was already added");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void legacyHotCocoaFieldsMigrateWhenTheCauldronReloads(GameTestHelper helper) {
        AlchemyCauldronRecipe recipe = requireRecipe(helper);
        if (recipe == null) {
            return;
        }

        AlchemyCauldronBlockEntity cauldron = placeCauldron(helper);
        if (cauldron == null) {
            return;
        }

        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CAULDRON_POS);
        BlockState state = level.getBlockState(pos);
        CompoundTag legacyState = cauldron.saveWithFullMetadata(level.registryAccess());
        legacyState.remove("recipe_id");
        legacyState.remove("added_ingredients");
        legacyState.remove("cooked_ingredients");
        legacyState.remove("ready_for_extraction");
        legacyState.remove("cook_time");
        legacyState.putString("recipe_mode", "HOT_COCOA");
        legacyState.putBoolean("cocoa_added", true);
        legacyState.putBoolean("hot_cocoa_ready", false);

        BlockEntity reloaded = BlockEntity.loadStatic(pos, state, legacyState, level.registryAccess());
        if (!(reloaded instanceof AlchemyCauldronBlockEntity restored)) {
            throw helper.assertionException("Legacy cauldron state did not reload as an alchemy cauldron");
        }

        require(helper, HOT_COCOA.equals(restored.getRecipeId()),
                "Legacy HOT_COCOA state did not migrate to the hot-cocoa recipe id");
        require(helper, !restored.canAddIngredient(recipe, recipe.ingredients().get(0)),
                "Legacy HOT_COCOA state forgot its milk ingredient");
        require(helper, !restored.canAddIngredient(recipe, recipe.ingredients().get(1)),
                "Legacy HOT_COCOA state forgot its cocoa ingredient");
        require(helper, restored.canAddIngredient(recipe, recipe.ingredients().get(2)),
                "Legacy HOT_COCOA state did not leave the remaining sugar ingredient available");
        helper.succeed();
    }

    private static AlchemyCauldronRecipe requireRecipe(GameTestHelper helper) {
        AlchemyCauldronRecipe recipe = AlchemyCauldronRecipes.get(HOT_COCOA);
        if (recipe == null) {
            throw helper.assertionException("Hot-cocoa cauldron recipe was not available after data-pack reload");
        }
        return recipe;
    }

    private static AlchemyCauldronBlockEntity placeCauldron(GameTestHelper helper) {
        helper.setBlock(CAULDRON_POS, AlchemyBlocks.ALCHEMY_CAULDRON);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(CAULDRON_POS));
        if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron)) {
            throw helper.assertionException("Placed alchemy cauldron did not create its block entity");
        }
        return cauldron;
    }

    private static AlchemyCauldronBlockEntity reload(GameTestHelper helper, AlchemyCauldronBlockEntity cauldron) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CAULDRON_POS);
        BlockEntity reloaded = BlockEntity.loadStatic(
                pos,
                level.getBlockState(pos),
                cauldron.saveWithFullMetadata(level.registryAccess()),
                level.registryAccess()
        );
        if (!(reloaded instanceof AlchemyCauldronBlockEntity restored)) {
            throw helper.assertionException("Saved cauldron state did not reload as an alchemy cauldron");
        }
        return restored;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
