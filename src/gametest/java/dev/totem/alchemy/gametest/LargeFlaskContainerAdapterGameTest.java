package dev.totem.alchemy.gametest;

import dev.totem.alchemy.container.LiquidContainerAdapter;
import dev.totem.alchemy.container.LiquidContainerAdapters;
import dev.totem.alchemy.item.FlaskEnchantments;
import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

public final class LargeFlaskContainerAdapterGameTest {
    @GameTest(maxTicks = 40)
    public void capacityEnchantmentAllowsEightUnitAdapterFill(GameTestHelper helper) {
        ItemStack flask = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
        flask.enchant(
                helper.getLevel().registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(FlaskEnchantments.CAPACITY),
                5
        );

        AlchemyMixtureState source = new AlchemyMixtureState(8, 8);
        source.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.WATER_ID, 1.0D));

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(flask, source, 8)
                .orElseThrow(() -> helper.assertionException("Capacity V flask rejected eight-unit adapter fill"));

        require(helper, result.transferredUnits() == 8,
                "Capacity V adapter fill did not transfer all eight units");
        require(helper, result.sourceRemainder().isEmpty(),
                "Capacity V adapter fill left source volume behind");
        require(helper, AlchemyMixtureBottle.storedMixture(result.filledContainer()).volumeUnits() == 8,
                "Capacity V adapter fill truncated stored mixture");
        require(helper, FlaskEnchantments.capacity(result.filledContainer()) == 8,
                "Adapter fill did not preserve flask capacity enchantment");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
