package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.AlchemyBrewing;
import dev.totem.alchemy.alchemy.AlchemyPotions;
import dev.totem.alchemy.alchemy.BrewingModifierPolicy;
import dev.totem.alchemy.alchemy.BrewingStationPolicy;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.mixin.BrewingStandBlockEntityAccessor;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.List;

/** End-to-end regressions for the PotionBrewing mixin registration and actual outputs. */
public final class PotionBrewingRegressionGameTest {
    @GameTest(maxTicks = 40)
    public void vanillaWaterToAwkwardStillWorks(GameTestHelper helper) {
        ItemStack water = potion(Potions.WATER);
        ItemStack ingredient = new ItemStack(Items.NETHER_WART);
        require(helper, dev.totem.alchemy.alchemy.AlchemyBrewing.hasMix(helper.getLevel(), water, ingredient),
                "Nether wart stopped brewing water into awkward potion");
        assertPotion(helper, dev.totem.alchemy.alchemy.AlchemyBrewing.mix(helper.getLevel(), ingredient, water), Potions.AWKWARD,
                "Nether wart did not produce awkward potion");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void vanillaAwkwardToSwiftnessStillWorks(GameTestHelper helper) {
        assertNativeVanillaStandMix(
                helper,
                Potions.AWKWARD,
                new ItemStack(Items.SUGAR),
                Potions.SWIFTNESS,
                "Sugar stopped brewing awkward potion into swiftness"
        );
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void vanillaSwiftnessModifiersStillWork(GameTestHelper helper) {
        assertMix(helper, Potions.SWIFTNESS, new ItemStack(Items.REDSTONE), Potions.LONG_SWIFTNESS,
                "Redstone stopped extending swiftness");
        assertMix(helper, Potions.SWIFTNESS, new ItemStack(Items.GLOWSTONE_DUST), Potions.STRONG_SWIFTNESS,
                "Glowstone stopped amplifying swiftness");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void vanillaModifierPolicyIsDeterministicAndGuaranteed(GameTestHelper helper) {
        ItemStack swiftness = potion(Potions.SWIFTNESS);
        ItemStack redstone = new ItemStack(Items.REDSTONE);

        require(helper, BrewingModifierPolicy.isModifierIngredient(redstone),
                "Redstone was not classified as a Brewing Stand modifier");
        BrewingStationPolicy.Decision decision =
                BrewingStationPolicy.evaluate(helper.getLevel(), redstone, List.of(swiftness));
        require(helper, decision.deterministicModifier(),
                "Vanilla redstone modifier was not classified as deterministic");
        require(helper, Math.abs(decision.effectiveChance() - 1.0D) < 0.000_001D,
                "Vanilla redstone modifier did not reach 100% success");

        BrewingStandBlockEntity stand =
                completeNativeVanillaAtLegacyFailureRoll(helper, swiftness, redstone);
        for (int slot = 0; slot < 3; slot++) {
            assertPotion(helper, stand.getItem(slot), Potions.LONG_SWIFTNESS,
                    "Deterministic redstone modifier failed under a legacy failure roll");
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void storedMixtureModifierPreservesMixtureState(GameTestHelper helper) {
        ItemStack input = potion(Potions.SWIFTNESS);
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(input);
        state.addProvenance("test:stored-mixture");
        AlchemyMixtureBottle.writeState(input, state);

        ItemStack output = AlchemyBrewing.mix(
                helper.getLevel(), new ItemStack(Items.REDSTONE), input);
        require(helper, AlchemyMixtureBottle.hasStoredMixture(output),
                "Deterministic redstone modifier dropped stored mixture state");
        AlchemyMixtureState modified = AlchemyMixtureBottle.fromPotion(output);
        require(helper, modified.hasProvenance("test:stored-mixture"),
                "Modifier transform discarded existing mixture provenance");
        require(helper, modified.canonicalPotionId() == null,
                "Stored-mixture redstone transform did not leave canonical fixed-potion state");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void vanillaDeliveryModifiersStillWork(GameTestHelper helper) {
        ItemStack drinkable = potion(Potions.SWIFTNESS);
        ItemStack splash = dev.totem.alchemy.alchemy.AlchemyBrewing.mix(
                helper.getLevel(), new ItemStack(Items.GUNPOWDER), drinkable);
        assertPotionContainer(helper, splash, Items.SPLASH_POTION, Potions.SWIFTNESS,
                "Gunpowder stopped converting drinkable swiftness into a splash potion");

        ItemStack lingering = dev.totem.alchemy.alchemy.AlchemyBrewing.mix(
                helper.getLevel(), new ItemStack(Items.DRAGON_BREATH), splash);
        assertPotionContainer(helper, lingering, Items.LINGERING_POTION, Potions.SWIFTNESS,
                "Dragon breath stopped converting splash swiftness into a lingering potion");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void nativeVanillaStandDoesNotRandomlyFailRegardlessOfAlchemyPackState(GameTestHelper helper) {
        ItemStack water = potion(Potions.WATER);
        ItemStack netherWart = new ItemStack(Items.NETHER_WART);
        require(helper, AlchemyBrewing.shouldGuaranteeVanillaSuccess(
                        helper.getLevel(), netherWart, List.of(water)),
                "Native vanilla brew was not classified for guaranteed success");

        BrewingStandBlockEntity stand =
                completeNativeVanillaAtLegacyFailureRoll(helper, water, netherWart);
        for (int slot = 0; slot < 3; slot++) {
            assertPotion(helper, stand.getItem(slot), Potions.AWKWARD,
                    "Native vanilla brew failed under a roll that used to fail");
        }
        require(helper, stand.getItem(3).isEmpty(),
                "Guaranteed vanilla brew did not consume exactly one reagent");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void nativeVanillaSafetyAggregateRegardlessOfAlchemyPackState(GameTestHelper helper) {
        assertMix(helper, Potions.WATER, new ItemStack(Items.NETHER_WART), Potions.AWKWARD,
                "Native vanilla safety broke water to awkward");
        assertNativeVanillaStandMix(
                helper,
                Potions.AWKWARD,
                new ItemStack(Items.SUGAR),
                Potions.SWIFTNESS,
                "Native vanilla safety broke awkward to swiftness"
        );
        assertMix(helper, Potions.SWIFTNESS, new ItemStack(Items.REDSTONE), Potions.LONG_SWIFTNESS,
                "Native vanilla safety broke redstone duration modifier");
        assertMix(helper, Potions.SWIFTNESS, new ItemStack(Items.GLOWSTONE_DUST), Potions.STRONG_SWIFTNESS,
                "Native vanilla safety broke glowstone potency modifier");

        ItemStack drinkable = potion(Potions.SWIFTNESS);
        ItemStack splash = AlchemyBrewing.mix(
                helper.getLevel(), new ItemStack(Items.GUNPOWDER), drinkable);
        assertPotionContainer(helper, splash, Items.SPLASH_POTION, Potions.SWIFTNESS,
                "Native vanilla safety broke gunpowder delivery conversion");

        ItemStack lingering = AlchemyBrewing.mix(
                helper.getLevel(), new ItemStack(Items.DRAGON_BREATH), splash);
        assertPotionContainer(helper, lingering, Items.LINGERING_POTION, Potions.SWIFTNESS,
                "Native vanilla safety broke dragon-breath delivery conversion");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void fireflyStrengthAllInputTiersProduceMatchingVariants(GameTestHelper helper) {
        ItemStack fireflyBush = new ItemStack(Items.FIREFLY_BUSH);
        assertMix(helper, Potions.STRENGTH, fireflyBush, AlchemyPotions.FIREFLY_STRENGTH,
                "Base strength did not produce firefly strength");
        assertMix(helper, Potions.LONG_STRENGTH, fireflyBush, AlchemyPotions.LONG_FIREFLY_STRENGTH,
                "Long strength did not produce long firefly strength");
        assertMix(helper, Potions.STRONG_STRENGTH, fireflyBush, AlchemyPotions.STRONG_FIREFLY_STRENGTH,
                "Strong strength did not produce strong firefly strength");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void fireflyStrengthModifiersProduceActualLongAndStrongOutputs(GameTestHelper helper) {
        ItemStack base = potion(AlchemyPotions.FIREFLY_STRENGTH);
        assertMix(helper, AlchemyPotions.FIREFLY_STRENGTH, new ItemStack(Items.REDSTONE),
                AlchemyPotions.LONG_FIREFLY_STRENGTH,
                "Redstone did not extend firefly strength");
        assertMix(helper, AlchemyPotions.FIREFLY_STRENGTH, new ItemStack(Items.GLOWSTONE_DUST),
                AlchemyPotions.STRONG_FIREFLY_STRENGTH,
                "Glowstone did not amplify firefly strength");
        require(helper, base.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                        .is(AlchemyPotions.FIREFLY_STRENGTH),
                "Modifier test mutated the source potion stack");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void unrelatedIngredientDoesNotBecomeABrewingRecipe(GameTestHelper helper) {
        ItemStack awkward = potion(Potions.AWKWARD);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        require(helper, !dev.totem.alchemy.alchemy.AlchemyBrewing.hasMix(helper.getLevel(), awkward, diamond),
                "Unrelated ingredient unexpectedly became a potion mix");
        helper.succeed();
    }

    private static BrewingStandBlockEntity completeNativeVanillaAtLegacyFailureRoll(
            GameTestHelper helper,
            ItemStack input,
            ItemStack reagent
    ) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        var blockState = Blocks.BREWING_STAND.defaultBlockState();
        var stand = new BrewingStandBlockEntity(pos, blockState);
        stand.setLevel(level);
        for (int slot = 0; slot < 3; slot++) {
            stand.setItem(slot, input.copy());
        }
        stand.setItem(3, reagent.copy());
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));

        BrewingStandBlockEntity.serverTick(level, pos, blockState, stand);
        var accessor = (BrewingStandBlockEntityAccessor) (Object) stand;
        require(helper, accessor.totemAlchemy$getBrewTime() > 0,
                "Vanilla brewing stand did not start");

        for (int tick = 0; tick < 1000 && accessor.totemAlchemy$getBrewTime() > 1; tick++) {
            BrewingStandBlockEntity.serverTick(level, pos, blockState, stand);
        }
        require(helper, accessor.totemAlchemy$getBrewTime() == 1,
                "Vanilla brewing stand did not reach completion");

        RandomSource random = level.getRandom();
        BrewingStationPolicy.Decision decision =
                BrewingStationPolicy.evaluate(level, reagent, List.of(input));
        require(helper, decision.nativeVanillaRecipe() || decision.deterministicModifier(),
                "Guaranteed Brewing Stand fixture was not classified as native vanilla or deterministic modifier");
        boolean foundBaseChanceFailureRoll = false;
        for (long seed = 0; seed < 100000; seed++) {
            random.setSeed(seed);
            float roll = random.nextFloat();
            if (roll >= decision.baseChance()) {
                require(helper, BrewingStationPolicy.succeeds(decision, roll),
                        "Guaranteed Brewing Stand policy did not override the lower reaction base chance");
                random.setSeed(seed);
                foundBaseChanceFailureRoll = true;
                break;
            }
        }
        require(helper, foundBaseChanceFailureRoll,
                "Could not find a deterministic roll above the reaction base chance");

        BrewingStandBlockEntity.serverTick(level, pos, blockState, stand);
        require(helper, accessor.totemAlchemy$getBrewTime() == 0,
                "Guaranteed vanilla brewing cycle did not complete");
        return stand;
    }

    private static void assertNativeVanillaStandMix(
            GameTestHelper helper,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> inputPotion,
            ItemStack ingredient,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> expectedPotion,
            String message
    ) {
        BrewingStandBlockEntity stand =
                completeNativeVanillaAtLegacyFailureRoll(helper, potion(inputPotion), ingredient);
        for (int slot = 0; slot < 3; slot++) {
            assertPotion(helper, stand.getItem(slot), expectedPotion, message);
        }
    }

    private static void assertMix(
            GameTestHelper helper,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> inputPotion,
            ItemStack ingredient,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> expectedPotion,
            String message
    ) {
        ItemStack input = potion(inputPotion);
        require(helper, dev.totem.alchemy.alchemy.AlchemyBrewing.hasMix(helper.getLevel(), input, ingredient), message + " (recipe missing)");
        assertPotion(helper, dev.totem.alchemy.alchemy.AlchemyBrewing.mix(helper.getLevel(), ingredient, input), expectedPotion, message);
    }

    private static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static void assertPotion(
            GameTestHelper helper,
            ItemStack stack,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> expected,
            String message
    ) {
        require(helper, stack.is(Items.POTION), message + " (container changed)");
        require(helper, stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(expected), message);
    }

    private static void assertPotionContainer(
            GameTestHelper helper,
            ItemStack stack,
            Item expectedItem,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> expectedPotion,
            String message
    ) {
        require(helper, stack.is(expectedItem), message + " (container mismatch)");
        require(helper, stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(expectedPotion), message);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
