package dev.totem.alchemy.container;

import dev.totem.alchemy.testing.WithMinecraftItemComponents;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithMinecraftItemComponents
class WaterBottleContainerAdapterTest {
    @Test
    void plainWaterBottleDrainsToOneWaterUnitAndGlassBottle() {
        ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);

        LiquidContainerAdapter.DrainResult result =
                LiquidContainerAdapters.drain(waterBottle, 1).orElseThrow();

        assertEquals(1, result.transferredUnits());
        assertTrue(result.containerRemainder().is(Items.GLASS_BOTTLE));
        assertEquals(1, result.drained().volumeUnits());
        assertEquals("minecraft:water", result.drained().canonicalPotionId());
        assertEquals(
                Map.of(AlchemyLiquids.WATER_ID, 1.0D),
                result.drained().liquidComposition().components()
        );
        assertEquals(1, waterBottle.getCount());
    }

    @Test
    void glassBottleFillsFromOneUnitOfPlainWater() {
        AlchemyMixtureState source = AlchemyMixtureBrewing.waterState(2);

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(Items.GLASS_BOTTLE),
                source,
                1
        ).orElseThrow();

        assertEquals(1, result.transferredUnits());
        assertTrue(result.filledContainer().is(Items.POTION));
        assertTrue(result.filledContainer()
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .is(Potions.WATER));
        assertEquals(1, result.sourceRemainder().volumeUnits());
        assertEquals(2, source.volumeUnits());
    }

    @Test
    void oneUnitFillLeavesEmptySourceRemainder() {
        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(Items.GLASS_BOTTLE),
                AlchemyMixtureBrewing.waterState(1),
                1
        ).orElseThrow();

        assertTrue(result.sourceRemainder().isEmpty());
    }

    @Test
    void nonWaterAndNonDrinkablePotionContainersDoNotDrainHere() {
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        ItemStack splashWater = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER);

        assertTrue(LiquidContainerAdapters.drain(awkward, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.drain(splashWater, 1).isEmpty());
    }

    @Test
    void storedMixtureWaterBottleIsReservedForGenericPotionCompatibility() {
        ItemStack stored = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        AlchemyMixtureBottle.writeState(stored, AlchemyMixtureBrewing.waterState(1));

        assertTrue(AlchemyMixtureBottle.hasStoredMixture(stored));
        assertTrue(LiquidContainerAdapters.drain(stored, 1).isEmpty());
    }

    @Test
    void fillRejectsWaterLikeMixturesThatWouldLoseChemistryState() {
        AlchemyMixtureState activated = AlchemyMixtureBrewing.waterState(1);
        activated.setBaseActivated(true);

        AlchemyMixtureState effectful = AlchemyMixtureBrewing.waterState(1);
        effectful.putEffect("minecraft:speed", 200.0D, 0);

        AlchemyMixtureState mixed = new AlchemyMixtureState(1);
        mixed.setLiquidComposition(LiquidComposition.of(Map.of(
                AlchemyLiquids.WATER_ID, 0.5D,
                AlchemyLiquids.MILK_ID, 0.5D
        )));

        AlchemyMixtureState damaged = AlchemyMixtureBrewing.waterState(1);
        damaged.setStability(90);

        AlchemyMixtureState mundane = AlchemyMixtureBrewing.waterState(1);
        mundane.setCanonicalPotionId("minecraft:mundane");

        AlchemyMixtureState splashWater = AlchemyMixtureBrewing.waterState(1);
        splashWater.setDeliveryForm(AlchemyMixtureState.DeliveryForm.SPLASH);

        ItemStack glass = new ItemStack(Items.GLASS_BOTTLE);
        assertTrue(LiquidContainerAdapters.fill(glass, activated, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, effectful, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, mixed, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, damaged, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, mundane, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, splashWater, 1).isEmpty());
    }

    @Test
    void fillRequiresSingleGlassBottleAndAtLeastOneTransferUnit() {
        ItemStack stackedGlass = new ItemStack(Items.GLASS_BOTTLE, 2);
        AlchemyMixtureState water = AlchemyMixtureBrewing.waterState(1);

        assertTrue(LiquidContainerAdapters.fill(stackedGlass, water, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(
                new ItemStack(Items.GLASS_BOTTLE), water, 0
        ).isEmpty());
        assertTrue(WaterBottleContainerAdapter.isPlainWater(water));
    }
}
