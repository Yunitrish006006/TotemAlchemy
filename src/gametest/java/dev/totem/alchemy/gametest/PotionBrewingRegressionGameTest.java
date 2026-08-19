package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.AlchemyPotions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** End-to-end regressions for the PotionBrewing mixin registration and actual outputs. */
public final class PotionBrewingRegressionGameTest {
    @GameTest(maxTicks = 40)
    public void vanillaWaterToAwkwardStillWorks(GameTestHelper helper) {
        ItemStack water = potion(Potions.WATER);
        ItemStack ingredient = new ItemStack(Items.NETHER_WART);
        require(helper, helper.getLevel().potionBrewing().hasMix(water, ingredient),
                "Nether wart stopped brewing water into awkward potion");
        assertPotion(helper, helper.getLevel().potionBrewing().mix(ingredient, water), Potions.AWKWARD,
                "Nether wart did not produce awkward potion");
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
        require(helper, !helper.getLevel().potionBrewing().hasMix(awkward, diamond),
                "Unrelated ingredient unexpectedly became a potion mix");
        helper.succeed();
    }

    private static void assertMix(
            GameTestHelper helper,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> inputPotion,
            ItemStack ingredient,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> expectedPotion,
            String message
    ) {
        ItemStack input = potion(inputPotion);
        require(helper, helper.getLevel().potionBrewing().hasMix(input, ingredient), message + " (recipe missing)");
        assertPotion(helper, helper.getLevel().potionBrewing().mix(ingredient, input), expectedPotion, message);
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

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
