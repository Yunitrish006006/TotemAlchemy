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
        require(helper, dev.totem.alchemy.alchemy.AlchemyBrewing.hasMix(helper.getLevel(), water, ingredient),
                "Nether wart stopped brewing water into awkward potion");
        assertPotion(helper, dev.totem.alchemy.alchemy.AlchemyBrewing.mix(helper.getLevel(), ingredient, water), Potions.AWKWARD,
                "Nether wart did not produce awkward potion");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void vanillaAwkwardToSwiftnessStillWorks(GameTestHelper helper) {
        assertMix(helper, Potions.AWKWARD, new ItemStack(Items.SUGAR), Potions.SWIFTNESS,
                "Sugar stopped brewing awkward potion into swiftness");
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
