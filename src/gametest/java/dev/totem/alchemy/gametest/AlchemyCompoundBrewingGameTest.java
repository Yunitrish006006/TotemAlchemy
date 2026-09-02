package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.AlchemyCauldronRecipe;
import dev.totem.alchemy.alchemy.AlchemyCauldronRecipes;
import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.effect.AlchemyMobEffects;
import dev.totem.alchemy.mixture.AlchemyCompoundBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Regression coverage for named food/solid recipes running on the shared mixture state machine. */
public final class AlchemyCompoundBrewingGameTest {
    private static final Identifier SALTPETER = Identifier.fromNamespaceAndPath("deadrecall", "saltpeter");
    private static final Identifier HOT_COCOA = Identifier.fromNamespaceAndPath("deadrecall", "hot_cocoa");
    private static final Identifier CHERRY_BREW = Identifier.fromNamespaceAndPath("deadrecall", "cherry_brew");

    @GameTest(maxTicks = 40)
    public void unfinishedHotCocoaBottleRetainsEveryReactionTimer(GameTestHelper helper) {
        AlchemyCauldronRecipe recipe = recipe(helper, HOT_COCOA);
        AlchemyMixtureState cauldron = AlchemyCompoundBrewing.initialState(recipe);
        AlchemyCauldronRecipe.IngredientStep milk = recipe.ingredients().getFirst();
        require(helper, AlchemyCompoundBrewing.schedule(
                        cauldron, recipe, milk, new ItemStack(Items.MILK_BUCKET)),
                "Milk did not enter the shared mixture reaction state");
        cauldron.tickReactions(73);

        AlchemyMixtureState dose = cauldron.extractUnits(1);
        ItemStack bottle = AlchemyMixtureBottle.toPotion(dose);
        require(helper, bottle.is(Items.POTION),
                "An unfinished hot-cocoa mixture did not use a resumable potion bottle");
        AlchemyMixtureState restored = AlchemyMixtureBottle.fromPotion(bottle);
        AlchemyMixtureState.Reaction restoredMilk = restored.reactions().stream().findFirst().orElse(null);
        require(helper, restoredMilk != null && restoredMilk.elapsedTicks() == 73,
                "Bottling hot cocoa lost its current milk reaction timer");
        require(helper, HOT_COCOA.equals(AlchemyCompoundBrewing.activeRecipeId(restored)),
                "Bottling hot cocoa lost its named recipe identity");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void finishedHotCocoaUsesItsOldItemWithModernMixtureData(GameTestHelper helper) {
        AlchemyCauldronRecipe recipe = recipe(helper, HOT_COCOA);
        AlchemyMixtureState state = complete(helper, recipe);
        ItemStack bottle = AlchemyMixtureBottle.toPotion(state.extractUnits(1));

        require(helper, bottle.is(AlchemyItems.HOT_COCOA),
                "Finished hot cocoa did not retain its established item and texture");
        require(helper, AlchemyMixtureBottle.hasStoredMixture(bottle),
                "Finished hot cocoa did not carry modern mixture data");
        AlchemyMixtureState restored = AlchemyMixtureBottle.fromPotion(bottle);
        require(helper, restored.isHeatLockedAfterBottling() && AlchemyCompoundBrewing.isReady(restored),
                "Finished hot cocoa did not become a stable repourable result");
        require(helper, restored.provenance().stream().noneMatch(marker ->
                        marker.startsWith("compound:input:") || marker.startsWith("compound:base:")),
                "Finished hot cocoa still stored its obsolete ingredient history");
        require(helper, restored.effects().containsKey("minecraft:saturation"),
                "Hot cocoa did not expose its saturation result to the shared potion UI");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void finishedCherryBrewCarriesCherryEffectAndCanBeRepoured(GameTestHelper helper) {
        AlchemyCauldronRecipe recipe = recipe(helper, CHERRY_BREW);
        AlchemyMixtureState state = complete(helper, recipe);
        ItemStack bottle = AlchemyMixtureBottle.toPotion(state.extractUnits(1));

        require(helper, bottle.is(AlchemyItems.CHERRY_BREW),
                "Finished cherry brew did not retain its established item and texture");
        AlchemyMixtureState restored = AlchemyMixtureBottle.fromPotion(bottle);
        require(helper, restored.effects().containsKey("deadrecall:cherry_bloom"),
                "Cherry brew did not expose Cherry Bloom through the shared mixture effect data");
        require(helper, AlchemyMixtureBottle.isDrinkablePotion(bottle)
                        && CHERRY_BREW.equals(AlchemyCompoundBrewing.activeRecipeId(restored))
                        && AlchemyCompoundBrewing.canMerge(state, restored),
                "Cherry brew could not be poured back through the normal potion path");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void saltpeterUsesSharedReactionsButNeverBecomesADrink(GameTestHelper helper) {
        AlchemyCauldronRecipe recipe = recipe(helper, SALTPETER);
        AlchemyMixtureState state = complete(helper, recipe);

        require(helper, AlchemyCompoundBrewing.isSolidProcess(state),
                "Saltpeter was not marked as a solid-result mixture process");
        require(helper, AlchemyCompoundBrewing.bottledResult(state).isEmpty(),
                "Saltpeter incorrectly became a drinkable bottle");
        require(helper, recipe.createResultStack().is(AlchemyItems.SALTPETER),
                "The unified saltpeter process lost its solid output");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void legacyDrinkItemsUpgradeToReadyMixturesOnFirstUse(GameTestHelper helper) {
        AlchemyMixtureState cocoa = AlchemyMixtureBottle.fromPotion(new ItemStack(AlchemyItems.HOT_COCOA));
        AlchemyMixtureState cherry = AlchemyMixtureBottle.fromPotion(new ItemStack(AlchemyItems.CHERRY_BREW));

        require(helper, HOT_COCOA.equals(AlchemyCompoundBrewing.activeRecipeId(cocoa))
                        && AlchemyCompoundBrewing.isReady(cocoa),
                "Legacy hot cocoa did not upgrade to its ready mixture recipe");
        require(helper, CHERRY_BREW.equals(AlchemyCompoundBrewing.activeRecipeId(cherry))
                        && AlchemyCompoundBrewing.isReady(cherry),
                "Legacy cherry brew did not upgrade to its ready mixture recipe");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void legacyCauldronRecipeFieldsEnterTheMixtureStateOnNextTick(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3);
        helper.setBlock(relative, state);
        ServerLevel level = helper.getLevel();
        BlockEntity original = level.getBlockEntity(pos);
        if (!(original instanceof AlchemyCauldronBlockEntity cauldron)) {
            throw helper.assertionException("Test cauldron did not create its block entity");
        }

        CompoundTag oldData = cauldron.saveWithFullMetadata(level.registryAccess());
        oldData.putString("recipe_id", HOT_COCOA.toString());
        oldData.putString("added_ingredients", "milk,cocoa");
        oldData.putString("cooked_ingredients", "");
        oldData.putInt("cook_time", 60);
        BlockEntity loaded = BlockEntity.loadStatic(pos, state, oldData, level.registryAccess());
        if (!(loaded instanceof AlchemyCauldronBlockEntity restored)) {
            throw helper.assertionException("Legacy cauldron data did not reload");
        }

        AlchemyCauldronBlockEntity.serverTick(level, pos, state, restored);
        require(helper, restored.getRecipeId() == null && restored.hasMixture(),
                "Legacy parallel recipe state was not replaced by a mixture state");
        require(helper, HOT_COCOA.equals(AlchemyCompoundBrewing.activeRecipeId(restored.mixtureSnapshot())),
                "Migrated cauldron lost its hot-cocoa recipe identity");
        require(helper, restored.mixtureSnapshot().reactions().size() == 2,
                "Migrated cauldron did not preserve both already-added ingredient reactions");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void cherryDrinkAppliesItsStoredMixtureInsteadOfAStaticEffect(GameTestHelper helper) {
        AlchemyMixtureState state = complete(helper, recipe(helper, CHERRY_BREW)).extractUnits(1);
        state.setCanonicalPotionId(null);
        state.putEffect("minecraft:poison", 20.0D * 10.0D, 0);
        ItemStack bottle = AlchemyMixtureBottle.toPotion(state);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        try {
            ItemStack remainder = bottle.finishUsingItem(helper.getLevel(), player);
            require(helper, remainder.is(Items.GLASS_BOTTLE),
                    "Drinking a completed cherry brew did not return its own glass bottle");
            require(helper, player.hasEffect(AlchemyMobEffects.CHERRY_BLOOM)
                            && player.hasEffect(MobEffects.POISON),
                    "Cherry brew ignored the effects stored by the shared mixture system");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    private static AlchemyMixtureState complete(GameTestHelper helper, AlchemyCauldronRecipe recipe) {
        AlchemyMixtureState state = AlchemyCompoundBrewing.initialState(recipe);
        for (AlchemyCauldronRecipe.IngredientStep ingredient : recipe.ingredients()) {
            ItemStack actual = new ItemStack(ingredient.items().getFirst());
            require(helper, AlchemyCompoundBrewing.schedule(state, recipe, ingredient, actual),
                    "Named recipe rejected ingredient " + ingredient.id());
        }
        state.tickReactions(Integer.MAX_VALUE);
        require(helper, AlchemyCompoundBrewing.completeIfReady(state) == recipe,
                "Named recipe did not complete after all independent reactions finished");
        return state;
    }

    private static AlchemyCauldronRecipe recipe(GameTestHelper helper, Identifier id) {
        AlchemyCauldronRecipe recipe = AlchemyCauldronRecipes.get(id);
        if (recipe == null || !recipe.usesMixtureSystem()) {
            throw helper.assertionException("Unified cauldron recipe was unavailable: " + id);
        }
        return recipe;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
