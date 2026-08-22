package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.AlchemyCauldronRecipe;
import dev.totem.alchemy.alchemy.AlchemyCauldronRecipes;
import dev.totem.alchemy.alchemy.AlchemyPotions;
import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Verifies that the cauldron state carried through the modular cutover survives saved-world reloads. */
public final class AlchemyCauldronPersistenceGameTest {
    private static final BlockPos CAULDRON_POS = new BlockPos(2, 2, 2);
    private static final Identifier HOT_COCOA = Identifier.fromNamespaceAndPath("deadrecall", "hot_cocoa");

    @GameTest(maxTicks = 40)
    public void independentEffectRollsCanSelectSeveralAndFallbackStillGuaranteesOne(GameTestHelper helper) {
        ItemStack sugar = new ItemStack(Items.SUGAR);
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);

        List<MultiOutcomeBrewing.Outcome> several = MultiOutcomeBrewing.chooseOutcomes(
                sugar, awkward, 0.0F, 0.0F, 0.999F, 0.0F);
        require(helper, several.size() == 2,
                "Independent sugar rolls did not select two simultaneous outcomes");
        require(helper, several.stream().anyMatch(outcome -> outcome.potion().is(Potions.SWIFTNESS))
                        && several.stream().anyMatch(outcome -> outcome.potion().is(Potions.SLOWNESS)),
                "Independent sugar rolls did not preserve the selected swiftness/slowness pair");

        List<MultiOutcomeBrewing.Outcome> fallback = MultiOutcomeBrewing.chooseOutcomes(
                sugar, awkward, 0.999F, 0.999F, 0.999F, 0.60F);
        require(helper, fallback.size() == 1,
                "An all-miss independent roll did not choose exactly one weighted fallback");
        double fallbackSwiftnessTruth = MultiOutcomeBrewing.outcomeProbability(
                "minecraft:sugar", "minecraft:swiftness");
        double fallbackSlownessTruth = MultiOutcomeBrewing.outcomeProbability(
                "minecraft:sugar", "minecraft:slowness");
        require(helper, fallbackSwiftnessTruth > 0.94D && fallbackSwiftnessTruth <= 1.0D,
                "Research truth omitted the all-miss fallback contribution for swiftness");
        require(helper, fallbackSlownessTruth > 0.03D && fallbackSlownessTruth < 0.05D,
                "Research truth omitted the all-miss fallback contribution for rare slowness");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void brewingBatchSharesExactOpposingEffectSetAcrossAllBottles(GameTestHelper helper) {
        ItemStack sugar = new ItemStack(Items.SUGAR);
        List<ItemStack> inputs = List.of(
                PotionContents.createItemStack(Items.POTION, Potions.AWKWARD),
                PotionContents.createItemStack(Items.POTION, Potions.AWKWARD),
                PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)
        );
        MultiOutcomeBrewing.beginBatch(sugar, inputs, 0.0F, 0.0F, 0.999F, 0.0F);
        try {
            require(helper, MultiOutcomeBrewing.activeOutcomes().size() == 2,
                    "Brewing batch did not retain its two selected outcomes");
            List<ItemStack> outputs = inputs.stream()
                    .map(input -> helper.getLevel().potionBrewing().mix(sugar, input))
                    .toList();
            String expectedState = null;
            for (ItemStack output : outputs) {
                require(helper, output.is(Items.POTION),
                        "Independent outcome set changed the bottle container type");
                AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(output);
                require(helper, state.effects().containsKey("minecraft:speed")
                                && state.effects().containsKey("minecraft:slowness"),
                        "Produced bottle collapsed an independently selected opposing-effect pair");
                require(helper, state.preservesIndependentOutcomes(),
                        "Produced bottle did not persist its independent outcome-set marker");
                if (expectedState == null) {
                    expectedState = state.encode();
                } else {
                    require(helper, expectedState.equals(state.encode()),
                            "Bottles in one Brewing Stand batch received different effect sets");
                }
            }

            AlchemyMixtureState modified = AlchemyMixtureState.decode(expectedState);
            modified.applyRedstoneModifier();
            modified.applyGlowstoneModifier();
            ItemStack roundTrip = AlchemyMixtureBottle.toPotion(modified);
            AlchemyMixtureState restored = AlchemyMixtureBottle.fromPotion(roundTrip);
            require(helper, restored.effects().containsKey("minecraft:speed")
                            && restored.effects().containsKey("minecraft:slowness"),
                    "Potion round-trip or duration/potency modifiers collapsed selected opposing effects");
            helper.succeed();
        } finally {
            MultiOutcomeBrewing.clearBatch();
        }
    }

    @GameTest(maxTicks = 40)
    public void cauldronCanScheduleSeveralIndependentEffectsInOneReaction(GameTestHelper helper) {
        ItemStack sugar = new ItemStack(Items.SUGAR);
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        List<MultiOutcomeBrewing.Outcome> selected = MultiOutcomeBrewing.chooseOutcomes(
                sugar, awkward, 0.0F, 0.0F, 0.999F, 0.0F);
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(awkward);
        require(helper, AlchemyMixtureBrewing.scheduleOutcomeSet(
                        helper.getLevel(), state, sugar, selected),
                "Cauldron refused a selected independent outcome set");
        state.tickReactions(Integer.MAX_VALUE);
        require(helper, state.effects().containsKey("minecraft:speed")
                        && state.effects().containsKey("minecraft:slowness")
                        && state.preservesIndependentOutcomes(),
                "Cauldron reaction did not retain a simultaneous swiftness/slowness outcome set");

        require(helper, AlchemyMixtureBrewing.schedule(
                        helper.getLevel(), state, new ItemStack(Items.REDSTONE)),
                "Cauldron refused redstone for an independent outcome set");
        state.tickReactions(Integer.MAX_VALUE);
        require(helper, state.effects().containsKey("minecraft:speed")
                        && state.effects().containsKey("minecraft:slowness")
                        && state.preservesIndependentOutcomes(),
                "Delayed cauldron redstone reaction collapsed selected opposing effects");

        require(helper, AlchemyMixtureBrewing.schedule(
                        helper.getLevel(), state, new ItemStack(Items.GUNPOWDER)),
                "Cauldron refused gunpowder for an independent outcome set");
        state.tickReactions(Integer.MAX_VALUE);
        require(helper, state.deliveryForm() == AlchemyMixtureState.DeliveryForm.SPLASH
                        && state.effects().containsKey("minecraft:speed")
                        && state.effects().containsKey("minecraft:slowness")
                        && state.preservesIndependentOutcomes(),
                "Delayed delivery-form reaction collapsed selected opposing effects");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void everyPrimaryBrewingIngredientHasMultipleReachableOutcomes(GameTestHelper helper) {
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        Map<Item, Integer> expectedPools = Map.ofEntries(
                Map.entry(Items.SPIDER_EYE, 2),
                Map.entry(Items.RED_MUSHROOM, 2),
                Map.entry(Items.GLISTERING_MELON_SLICE, 2),
                Map.entry(Items.SUGAR, 3),
                Map.entry(Items.RABBIT_FOOT, 3),
                Map.entry(Items.MAGMA_CREAM, 3),
                Map.entry(Items.GOLDEN_CARROT, 3),
                Map.entry(Items.BLAZE_POWDER, 3),
                Map.entry(Items.GHAST_TEAR, 3),
                Map.entry(Items.PUFFERFISH, 3),
                Map.entry(Items.TURTLE_HELMET, 3),
                Map.entry(Items.PHANTOM_MEMBRANE, 3),
                Map.entry(Items.BREEZE_ROD, 3),
                Map.entry(Items.SLIME_BLOCK, 3),
                Map.entry(Items.STONE, 3),
                Map.entry(Items.COBWEB, 3),
                Map.entry(Items.FERMENTED_SPIDER_EYE, 3)
        );

        for (Map.Entry<Item, Integer> entry : expectedPools.entrySet()) {
            ItemStack ingredient = new ItemStack(entry.getKey());
            require(helper, helper.getLevel().potionBrewing().hasMix(awkward, ingredient),
                    "Effect-pool ingredient was not brewable from awkward potion: " + ingredient);
            require(helper, MultiOutcomeBrewing.outcomeCount(ingredient, awkward) == entry.getValue(),
                    "Effect-pool ingredient had the wrong number of outcomes: " + ingredient);

            Set<Object> reached = new HashSet<>();
            for (int sample = 0; sample <= 1000 && reached.size() < entry.getValue(); sample++) {
                float roll = sample / 1000.0F;
                MultiOutcomeBrewing.Outcome outcome = MultiOutcomeBrewing.chooseOutcome(ingredient, awkward, roll);
                if (outcome != null) {
                    reached.add(outcome.potion());
                }
            }
            require(helper, reached.size() == entry.getValue(),
                    "Not every configured outcome was reachable across the full roll range: " + ingredient);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void coreBrewingIngredientsKeepVanillaPrimaryAndReachableSecondaryBranches(GameTestHelper helper) {
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        ItemStack spiderEye = new ItemStack(Items.SPIDER_EYE);
        ItemStack redMushroom = new ItemStack(Items.RED_MUSHROOM);
        ItemStack melon = new ItemStack(Items.GLISTERING_MELON_SLICE);

        require(helper, MultiOutcomeBrewing.outcomeCount(spiderEye, awkward) == 2,
                "Spider eye did not expose two brewing outcomes");
        require(helper, MultiOutcomeBrewing.chooseOutcome(spiderEye, awkward, 0.1F).potion().is(Potions.POISON),
                "Spider eye first branch was not poison");
        require(helper, MultiOutcomeBrewing.chooseOutcome(spiderEye, awkward, 0.99F).potion().is(Potions.WEAKNESS),
                "Spider eye second branch was not weakness");

        require(helper, MultiOutcomeBrewing.outcomeCount(redMushroom, awkward) == 2,
                "Red mushroom did not expose two brewing outcomes");
        require(helper, MultiOutcomeBrewing.chooseOutcome(redMushroom, awkward, 0.1F).potion().is(Potions.POISON),
                "Red mushroom first branch was not poison");
        require(helper, MultiOutcomeBrewing.chooseOutcome(redMushroom, awkward, 0.99F).potion().is(AlchemyPotions.SATURATION),
                "Red mushroom second branch was not saturation");

        require(helper, MultiOutcomeBrewing.outcomeCount(melon, awkward) == 2,
                "Glistering melon did not expose two brewing outcomes");
        require(helper, MultiOutcomeBrewing.chooseOutcome(melon, awkward, 0.1F).potion().is(Potions.HEALING),
                "Glistering melon first branch was not healing");
        require(helper, MultiOutcomeBrewing.chooseOutcome(melon, awkward, 0.99F).potion().is(AlchemyPotions.RESISTANCE),
                "Glistering melon second branch was not resistance");

        ItemStack saturation = PotionContents.createItemStack(Items.POTION, AlchemyPotions.SATURATION);
        ItemStack resistance = PotionContents.createItemStack(Items.POTION, AlchemyPotions.RESISTANCE);
        require(helper, helper.getLevel().potionBrewing().hasMix(
                        saturation,
                        new ItemStack(Items.GLOWSTONE_DUST)
                ),
                "Saturation potion could not be strengthened");
        require(helper, helper.getLevel().potionBrewing().hasMix(
                        resistance,
                        new ItemStack(Items.REDSTONE)
                ),
                "Resistance potion could not be extended");
        require(helper, helper.getLevel().potionBrewing().hasMix(
                        resistance,
                        new ItemStack(Items.GLOWSTONE_DUST)
                ),
                "Resistance potion could not be strengthened");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void cherryLeavesTurnEverySwiftnessTierIntoItsCherryVariant(GameTestHelper helper) {
        ItemStack cherryLeaves = new ItemStack(Items.CHERRY_LEAVES);
        assertPotionMix(helper, Potions.SWIFTNESS, cherryLeaves, AlchemyPotions.CHERRY_SWIFTNESS);
        assertPotionMix(helper, Potions.LONG_SWIFTNESS, cherryLeaves, AlchemyPotions.LONG_CHERRY_SWIFTNESS);
        assertPotionMix(helper, Potions.STRONG_SWIFTNESS, cherryLeaves, AlchemyPotions.STRONG_CHERRY_SWIFTNESS);

        ItemStack baseCherry = PotionContents.createItemStack(Items.POTION, AlchemyPotions.CHERRY_SWIFTNESS);
        require(helper, helper.getLevel().potionBrewing().hasMix(baseCherry, new ItemStack(Items.REDSTONE)),
                "Cherry swiftness potion could not be extended");
        require(helper, helper.getLevel().potionBrewing().hasMix(baseCherry, new ItemStack(Items.GLOWSTONE_DUST)),
                "Cherry swiftness potion could not be strengthened");
        require(helper, Math.abs(VanillaBrewingChance.chanceFor(cherryLeaves) - 0.80D) < 0.000_001D,
                "Cherry leaves did not use the designed 80% brewing chance");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void fireflyBushTurnsEveryStrengthTierIntoItsFireflyVariant(GameTestHelper helper) {
        ItemStack fireflyBush = new ItemStack(Items.FIREFLY_BUSH);
        assertPotionMix(helper, Potions.STRENGTH, fireflyBush, AlchemyPotions.FIREFLY_STRENGTH);
        assertPotionMix(helper, Potions.LONG_STRENGTH, fireflyBush, AlchemyPotions.LONG_FIREFLY_STRENGTH);
        assertPotionMix(helper, Potions.STRONG_STRENGTH, fireflyBush, AlchemyPotions.STRONG_FIREFLY_STRENGTH);

        ItemStack baseFirefly = PotionContents.createItemStack(Items.POTION, AlchemyPotions.FIREFLY_STRENGTH);
        require(helper, helper.getLevel().potionBrewing().hasMix(baseFirefly, new ItemStack(Items.REDSTONE)),
                "Firefly strength potion could not be extended");
        require(helper, helper.getLevel().potionBrewing().hasMix(baseFirefly, new ItemStack(Items.GLOWSTONE_DUST)),
                "Firefly strength potion could not be strengthened");
        require(helper, Math.abs(VanillaBrewingChance.chanceFor(fireflyBush) - 0.82D) < 0.000_001D,
                "Firefly bush did not use the designed 82% brewing chance");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void redMushroomCanReplaceNetherWartForAwkwardPotion(GameTestHelper helper) {
        ItemStack waterPotion = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        ItemStack redMushroom = new ItemStack(Items.RED_MUSHROOM);
        require(helper, helper.getLevel().potionBrewing().hasMix(waterPotion, redMushroom),
                "Red mushroom was not registered as an alternative awkward-potion ingredient");
        ItemStack result = helper.getLevel().potionBrewing().mix(redMushroom, waterPotion);
        require(helper, result.is(Items.POTION),
                "Red mushroom replacement did not preserve the potion container");
        require(helper, result.getOrDefault(
                        net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        PotionContents.EMPTY
                ).is(Potions.AWKWARD),
                "Red mushroom replacement did not turn water into an awkward potion");
        require(helper, VanillaBrewingChance.hasUnstableMushroomBase(result),
                "Red mushroom awkward potion did not retain its unstable-base marker");
        require(helper, Math.abs(VanillaBrewingChance.chanceFor(redMushroom) - 0.65D) < 0.000_001D,
                "Red mushroom did not use its designed 65% success chance");
        ItemStack sugar = new ItemStack(Items.SUGAR);
        require(helper, Math.abs(VanillaBrewingChance.chanceFor(sugar, List.of(result)) - 0.70D) < 0.000_001D,
                "Unstable mushroom base did not subtract 20 points from later brewing steps");
        ItemStack swiftness = helper.getLevel().potionBrewing().mix(sugar, result);
        require(helper, VanillaBrewingChance.hasUnstableMushroomBase(swiftness),
                "Unstable mushroom base did not propagate to the next potion result");
        require(helper, Math.abs(VanillaBrewingChance.chanceFor(
                        new ItemStack(Items.REDSTONE),
                        List.of(swiftness)
                ) - 0.72D) < 0.000_001D,
                "Unstable mushroom penalty did not remain on later modifier steps");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void vanillaBrewingChanceChangesWithEachIngredient(GameTestHelper helper) {
        ItemStack netherWart = new ItemStack(Items.NETHER_WART);
        ItemStack glowstone = new ItemStack(Items.GLOWSTONE_DUST);
        require(helper, Math.abs(VanillaBrewingChance.chanceFor(netherWart) - 0.88D) < 0.000_001D,
                "Nether wart did not use its 88% brewing chance");
        require(helper, Math.abs(VanillaBrewingChance.chanceFor(glowstone) - 0.75D) < 0.000_001D,
                "Glowstone dust did not use its 75% brewing chance");
        require(helper, VanillaBrewingChance.isSuccessful(netherWart, 0.87F),
                "Nether wart should accept a roll below its own chance");
        require(helper, !VanillaBrewingChance.isSuccessful(glowstone, 0.75F),
                "Glowstone dust should reject a roll at its own chance");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void everyVanillaBrewingIngredientHasADistinctDesignedChance(GameTestHelper helper) {
        List<ItemStack> ingredients = List.of(
                new ItemStack(Items.REDSTONE),
                new ItemStack(Items.SUGAR),
                new ItemStack(Items.NETHER_WART),
                new ItemStack(Items.STONE),
                new ItemStack(Items.GOLDEN_CARROT),
                new ItemStack(Items.SPIDER_EYE),
                new ItemStack(Items.MAGMA_CREAM),
                new ItemStack(Items.GLISTERING_MELON_SLICE),
                new ItemStack(Items.BLAZE_POWDER),
                new ItemStack(Items.SLIME_BLOCK),
                new ItemStack(Items.PUFFERFISH),
                new ItemStack(Items.RABBIT_FOOT),
                new ItemStack(Items.GHAST_TEAR),
                new ItemStack(Items.FERMENTED_SPIDER_EYE),
                new ItemStack(Items.PHANTOM_MEMBRANE),
                new ItemStack(Items.GLOWSTONE_DUST),
                new ItemStack(Items.GUNPOWDER),
                new ItemStack(Items.COBWEB),
                new ItemStack(Items.BREEZE_ROD),
                new ItemStack(Items.TURTLE_HELMET),
                new ItemStack(Items.DRAGON_BREATH),
                new ItemStack(Items.RED_MUSHROOM)
        );
        Set<Double> distinctChances = new HashSet<>();
        for (ItemStack ingredient : ingredients) {
            require(helper, VanillaBrewingChance.hasDesignedChance(ingredient),
                    "A vanilla brewing ingredient fell back to the generic modded chance: " + ingredient);
            double chance = VanillaBrewingChance.chanceFor(ingredient);
            require(helper, chance >= 0.65D && chance <= 0.92D,
                    "A vanilla brewing ingredient chance fell outside the designed range: " + ingredient);
            distinctChances.add(chance);
        }
        require(helper, distinctChances.size() == ingredients.size(),
                "Vanilla brewing ingredients did not all receive distinct success rates");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void cauldronRecipesRollTheConfiguredChanceForEveryIngredient(GameTestHelper helper) {
        AlchemyCauldronRecipe recipe = requireRecipe(helper);
        require(helper, Math.abs(recipe.successChance() - 0.8D) < 0.000_001D,
                "Hot-cocoa recipe did not retain its default 80% ingredient chance");
        AlchemyCauldronRecipe.IngredientStep milk = recipe.ingredients().getFirst();
        AlchemyCauldronRecipe.IngredientStep cocoa = recipe.ingredients().get(1);
        require(helper, Math.abs(milk.successChance() - 0.9D) < 0.000_001D,
                "Milk did not load its 90% ingredient chance");
        require(helper, Math.abs(cocoa.successChance() - 0.8D) < 0.000_001D,
                "Cocoa did not load its 80% ingredient chance");
        require(helper, recipe.isIngredientSuccessful(milk, 0.89F),
                "Milk should accept a roll below its own chance");
        require(helper, !recipe.isIngredientSuccessful(cocoa, 0.8F),
                "Cocoa should reject a roll at its own chance");
        helper.succeed();
    }

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

    private static void assertPotionMix(
            GameTestHelper helper,
            net.minecraft.core.Holder<Potion> inputPotion,
            ItemStack ingredient,
            net.minecraft.core.Holder<Potion> expectedPotion
    ) {
        ItemStack input = PotionContents.createItemStack(Items.POTION, inputPotion);
        require(helper, helper.getLevel().potionBrewing().hasMix(input, ingredient),
                "Expected potion mix was not registered: " + inputPotion);
        ItemStack result = helper.getLevel().potionBrewing().mix(ingredient, input);
        require(helper, result.getOrDefault(
                        net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        PotionContents.EMPTY
                ).is(expectedPotion),
                "Potion mix produced the wrong cherry swiftness tier: " + inputPotion);
    }
}
