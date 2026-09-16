package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.AlchemyBrewing;
import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.mixin.BrewingStandBlockEntityAccessor;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.List;

public final class BrewingMigrationGameTest {
    @GameTest(maxTicks = 40)
    public void fixedRecipesLoadAllContainerVariants(GameTestHelper helper) {
        int count = 0;
        for (var holder : helper.getLevel().recipeAccess().getRecipes()) {
            if (holder.value().getType() != RecipeType.BREWING
                    || !holder.id().identifier().getNamespace().equals("totem")
                    || !holder.id().identifier().getPath().startsWith("brewing/")) continue;
            var recipe = (net.minecraft.world.item.crafting.BrewingRecipe) holder.value();
            ItemStack output = recipe.getOutput().create();
            require(helper, output.has(DataComponents.POTION_CONTENTS), "Fixed recipe lost potion contents");
            require(helper, recipe.getInput().ingredient().test(output), "Fixed recipe changed container");
            count++;
        }
        require(helper, count == 48, "Expected all 48 fixed container recipes, got " + count);
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void customOnlyIngredientUsesSharedOutcomesThroughRealStand(GameTestHelper helper) {
        ItemStack input = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        ItemStack ingredient = new ItemStack(Items.BROWN_MUSHROOM);
        require(helper, AlchemyBrewing.recipe(helper.getLevel(), input, ingredient).isEmpty(),
                "Test ingredient unexpectedly has a fixed recipe");
        BrewingStandBlockEntity stand = complete(helper, input, ingredient, true, 2);
        String expected = AlchemyMixtureBottle.fromPotion(stand.getItem(0)).encode();
        require(helper, AlchemyMixtureBottle.fromPotion(stand.getItem(0)).effects().size() >= 2,
                "Independent effect set was lost");
        for (int slot = 1; slot < 3; slot++) {
            require(helper, expected.equals(AlchemyMixtureBottle.fromPotion(stand.getItem(slot)).encode()),
                    "Bottles did not share one outcome set");
        }
        require(helper, stand.getItem(3).isEmpty(), "Successful batch did not consume exactly one reagent");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void noEffectRetainsLayersAndFailureRetainsExactBottles(GameTestHelper helper) {
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(
                PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
        state.setCanonicalPotionId(null);
        ItemStack input = AlchemyMixtureBottle.toPotion(state);
        ItemStack reagent = new ItemStack(Items.HONEY_BOTTLE);
        for (boolean successful : List.of(true, false)) {
            BrewingStandBlockEntity stand = complete(helper, input, reagent, successful, 0);
            for (int slot = 0; slot < 3; slot++) {
                require(helper, state.effects().equals(AlchemyMixtureBottle.fromPotion(stand.getItem(slot)).effects()),
                        "No-effect or failed brew destroyed existing layers");
                if (!successful) require(helper, ItemStack.matches(input, stand.getItem(slot)),
                        "Failed fusion mutated a bottle");
            }
            require(helper, stand.getItem(3).is(Items.GLASS_BOTTLE) && stand.getItem(3).getCount() == 1,
                    "Brew did not preserve the reagent remainder exactly once");
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void layeredModifiersAndDeliverySurviveRealStand(GameTestHelper helper) {
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(
                PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
        state.setCanonicalPotionId(null);
        ItemStack input = AlchemyMixtureBottle.toPotion(state);
        for (var item : List.of(Items.GLOWSTONE_DUST, Items.REDSTONE, Items.GUNPOWDER, Items.DRAGON_BREATH)) {
            AlchemyMixtureState expected = AlchemyMixtureBottle.fromPotion(input);
            if (item == Items.GLOWSTONE_DUST) expected.applyGlowstoneModifier();
            if (item == Items.REDSTONE) expected.applyRedstoneModifier();
            if (item == Items.GUNPOWDER) expected.setDeliveryForm(AlchemyMixtureState.DeliveryForm.SPLASH);
            if (item == Items.DRAGON_BREATH) expected.setDeliveryForm(AlchemyMixtureState.DeliveryForm.LINGERING);
            BrewingStandBlockEntity stand = complete(helper, input, new ItemStack(item), true, 0);
            input = stand.getItem(0).copy();
            AlchemyMixtureState actual = AlchemyMixtureBottle.fromPotion(input);
            require(helper, actual.effects().equals(expected.effects()), "Modifier lost conserved effect quantity");
            require(helper, actual.deliveryForm() == expected.deliveryForm(), "Delivery transformation failed");
            require(helper, actual.volumeUnits() == state.volumeUnits(), "Brew changed mixture volume");
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void mushroomStarterAndUnrelatedSlotsRemainCorrect(GameTestHelper helper) {
        ItemStack water = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER);
        BrewingStandBlockEntity stand = complete(helper, water, new ItemStack(Items.RED_MUSHROOM), true, -1);
        ItemStack output = stand.getItem(0);
        require(helper, output.is(Items.SPLASH_POTION), "Starter changed the container");
        require(helper, output.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD),
                "Mushroom starter did not activate the base");
        require(helper, VanillaBrewingChance.hasUnstableMushroomBase(output), "Mushroom instability was lost");
        require(helper, !AlchemyBrewing.hasMix(helper.getLevel(), water, new ItemStack(Items.DIAMOND)),
                "Unrelated ingredient became brewable");
        require(helper, ItemStack.matches(water, AlchemyBrewing.mix(
                helper.getLevel(), new ItemStack(Items.DIAMOND), water)), "Unrelated mix mutated input");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void customReagentsReachMenuHoppersAndSynchronization(GameTestHelper helper) {
        var level = helper.getLevel();
        var stand = new BrewingStandBlockEntity(helper.absolutePos(BlockPos.ZERO), Blocks.BREWING_STAND.defaultBlockState());
        stand.setLevel(level);
        var player = helper.makeMockServerPlayerInLevel();
        try {
            var menu = new BrewingStandMenu(0, player.getInventory(), stand, new SimpleContainerData(4));
            for (var item : List.of(Items.BROWN_MUSHROOM, Items.BLAZE_ROD, Items.FEATHER, Items.HONEY_BOTTLE)) {
                ItemStack reagent = new ItemStack(item);
                require(helper, stand.canPlaceItemThroughFace(3, reagent, Direction.UP), "Hopper rejected custom reagent");
                require(helper, menu.getSlot(3).mayPlace(reagent), "Menu rejected custom reagent");
                require(helper, level.recipeAccess().getSynchronizedItemProperties()
                        .get(RecipePropertySet.BREWING_REAGENTS).test(reagent), "Client reagent synchronization omitted material");
            }
            require(helper, !menu.getSlot(3).mayPlace(new ItemStack(Items.DIAMOND)), "Menu admitted unrelated material");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    private static BrewingStandBlockEntity complete(GameTestHelper helper, ItemStack input, ItemStack reagent,
                                                     boolean success, int minimumOutcomes) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        var blockState = Blocks.BREWING_STAND.defaultBlockState();
        var stand = new BrewingStandBlockEntity(pos, blockState);
        stand.setLevel(level);
        for (int slot = 0; slot < 3; slot++) stand.setItem(slot, input.copy());
        stand.setItem(3, reagent.copy());
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        BrewingStandBlockEntity.serverTick(level, pos, blockState, stand);
        var accessor = (BrewingStandBlockEntityAccessor) (Object) stand;
        require(helper, accessor.totemAlchemy$getBrewTime() > 0, "Stand did not start custom brewing");
        for (int tick = 0; tick < 1000 && accessor.totemAlchemy$getBrewTime() > 1; tick++) {
            BrewingStandBlockEntity.serverTick(level, pos, blockState, stand);
        }
        require(helper, accessor.totemAlchemy$getBrewTime() == 1, "Stand failed to reach completion");
        RandomSource random = level.getRandom();
        boolean found = false;
        for (long seed = 0; seed < 100000; seed++) {
            random.setSeed(seed);
            boolean rolledSuccess = VanillaBrewingChance.isSuccessful(reagent, List.of(input), random.nextFloat());
            if (rolledSuccess != success) continue;
            int outcomes = !success || minimumOutcomes < 0 ? minimumOutcomes
                    : MultiOutcomeBrewing.chooseOutcomes(reagent, random).size();
            if (minimumOutcomes == 0 && outcomes != 0 || minimumOutcomes > 0 && outcomes < minimumOutcomes) continue;
            random.setSeed(seed);
            found = true;
            break;
        }
        require(helper, found, "Could not select deterministic brewing rolls");
        BrewingStandBlockEntity.serverTick(level, pos, blockState, stand);
        require(helper, accessor.totemAlchemy$getBrewTime() == 0, "Stand did not complete");
        require(helper, MultiOutcomeBrewing.activeOutcomes().isEmpty(), "Completed brew leaked batch context");
        return stand;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
