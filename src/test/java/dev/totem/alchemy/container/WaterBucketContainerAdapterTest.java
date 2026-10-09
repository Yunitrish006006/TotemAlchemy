package dev.totem.alchemy.container;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
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
    void nonPlainThreeUnitMixtureUsesStoredWaterBucketWithoutLosingState() {
        AlchemyMixtureState mixed = new AlchemyMixtureState(3);
        mixed.setLiquidComposition(LiquidComposition.of(Map.of(
                AlchemyLiquids.WATER_ID, 0.5D,
                AlchemyLiquids.MILK_ID, 0.5D
        )));
        mixed.setBaseActivated(true);
        mixed.putEffect("minecraft:speed", 600.0D, 0);
        mixed.setStability(83);
        mixed.addProvenance("test:mixed_bucket");
        mixed.lockHeatIfFinished();

        LiquidContainerAdapter.FillResult filled = LiquidContainerAdapters.fill(
                new ItemStack(Items.BUCKET),
                mixed,
                3
        ).orElseThrow();

        assertTrue(filled.filledContainer().is(Items.WATER_BUCKET));
        assertTrue(AlchemyMixtureBottle.hasStoredMixture(filled.filledContainer()));
        assertEquals(
                mixed.encode(),
                AlchemyMixtureBottle.storedMixture(filled.filledContainer()).encode()
        );

        LiquidContainerAdapter.DrainResult drained =
                LiquidContainerAdapters.drain(filled.filledContainer(), 3).orElseThrow();
        assertEquals(mixed.encode(), drained.drained().encode());
        assertTrue(drained.containerRemainder().is(Items.BUCKET));
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
