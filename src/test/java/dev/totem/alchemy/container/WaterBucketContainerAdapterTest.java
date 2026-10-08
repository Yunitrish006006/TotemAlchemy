package dev.totem.alchemy.container;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterBucketContainerAdapterTest {
    @Test
    void waterBucketDrainsToThreeWaterUnitsAndEmptyBucket() {
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);

        LiquidContainerAdapter.DrainResult result =
                LiquidContainerAdapters.drain(waterBucket, 3).orElseThrow();

        assertEquals(3, result.transferredUnits());
        assertTrue(result.containerRemainder().is(Items.BUCKET));
        assertEquals(3, result.drained().volumeUnits());
        assertEquals("minecraft:water", result.drained().canonicalPotionId());
        assertEquals(
                Map.of(AlchemyLiquids.WATER_ID, 1.0D),
                result.drained().liquidComposition().components()
        );
        assertEquals(1, waterBucket.getCount());
    }

    @Test
    void emptyBucketFillsFromThreeUnitsOfPlainWater() {
        AlchemyMixtureState source = AlchemyMixtureBrewing.waterState(3);

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(Items.BUCKET),
                source,
                3
        ).orElseThrow();

        assertEquals(3, result.transferredUnits());
        assertTrue(result.filledContainer().is(Items.WATER_BUCKET));
        assertTrue(result.sourceRemainder().isEmpty());
        assertEquals(3, source.volumeUnits());
    }

    @Test
    void bucketRequiresFullThreeUnitTransfer() {
        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(Items.WATER_BUCKET),
                2
        ).isEmpty());

        assertTrue(LiquidContainerAdapters.fill(
                new ItemStack(Items.BUCKET),
                AlchemyMixtureBrewing.waterState(2),
                3
        ).isEmpty());

        assertTrue(LiquidContainerAdapters.fill(
                new ItemStack(Items.BUCKET),
                AlchemyMixtureBrewing.waterState(3),
                2
        ).isEmpty());
    }

    @Test
    void fillRejectsNonWaterOrStatefulMixtures() {
        AlchemyMixtureState milk = new AlchemyMixtureState(3);
        milk.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.MILK_ID, 1.0D));

        AlchemyMixtureState activatedWater = AlchemyMixtureBrewing.waterState(3);
        activatedWater.setBaseActivated(true);

        AlchemyMixtureState effectfulWater = AlchemyMixtureBrewing.waterState(3);
        effectfulWater.putEffect("minecraft:speed", 600.0D, 0);

        ItemStack bucket = new ItemStack(Items.BUCKET);
        assertTrue(LiquidContainerAdapters.fill(bucket, milk, 3).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(bucket, activatedWater, 3).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(bucket, effectfulWater, 3).isEmpty());
    }

    @Test
    void adapterRequiresSingleBucketStack() {
        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(Items.WATER_BUCKET, 2),
                3
        ).isEmpty());

        assertTrue(LiquidContainerAdapters.fill(
                new ItemStack(Items.BUCKET, 2),
                AlchemyMixtureBrewing.waterState(3),
                3
        ).isEmpty());
    }
}
