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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilkBucketContainerAdapterTest {
    @Test
    void milkBucketDrainsToThreeMilkUnitsAndEmptyBucket() {
        ItemStack milkBucket = new ItemStack(Items.MILK_BUCKET);

        LiquidContainerAdapter.DrainResult result =
                LiquidContainerAdapters.drain(milkBucket, 3).orElseThrow();

        assertEquals(3, result.transferredUnits());
        assertTrue(result.containerRemainder().is(Items.BUCKET));
        assertEquals(3, result.drained().volumeUnits());
        assertNull(result.drained().canonicalPotionId());
        assertEquals(
                Map.of(AlchemyLiquids.MILK_ID, 1.0D),
                result.drained().liquidComposition().components()
        );
        assertEquals(1, milkBucket.getCount());
    }

    @Test
    void emptyBucketFillsFromThreeUnitsOfPlainMilk() {
        AlchemyMixtureState source = MilkBucketContainerAdapter.milkState(3);

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(Items.BUCKET),
                source,
                3
        ).orElseThrow();

        assertEquals(3, result.transferredUnits());
        assertTrue(result.filledContainer().is(Items.MILK_BUCKET));
        assertTrue(result.sourceRemainder().isEmpty());
        assertEquals(3, source.volumeUnits());
    }

    @Test
    void milkBucketRequiresFullThreeUnitTransfer() {
        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(Items.MILK_BUCKET),
                2
        ).isEmpty());

        assertTrue(LiquidContainerAdapters.fill(
                new ItemStack(Items.BUCKET),
                MilkBucketContainerAdapter.milkState(2),
                3
        ).isEmpty());

        assertTrue(LiquidContainerAdapters.fill(
                new ItemStack(Items.BUCKET),
                MilkBucketContainerAdapter.milkState(3),
                2
        ).isEmpty());
    }

    @Test
    void fillRejectsWaterMixedOrStatefulMilk() {
        AlchemyMixtureState water = AlchemyMixtureBrewing.waterState(3);

        AlchemyMixtureState mixed = new AlchemyMixtureState(3);
        mixed.setLiquidComposition(LiquidComposition.of(Map.of(
                AlchemyLiquids.MILK_ID, 0.5D,
                AlchemyLiquids.WATER_ID, 0.5D
        )));

        AlchemyMixtureState activatedMilk = MilkBucketContainerAdapter.milkState(3);
        activatedMilk.setBaseActivated(true);

        AlchemyMixtureState effectfulMilk = MilkBucketContainerAdapter.milkState(3);
        effectfulMilk.putEffect("minecraft:speed", 600.0D, 0);

        AlchemyMixtureState damagedMilk = MilkBucketContainerAdapter.milkState(3);
        damagedMilk.setStability(90);

        ItemStack bucket = new ItemStack(Items.BUCKET);
        assertTrue(LiquidContainerAdapters.fill(bucket, water, 3).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(bucket, mixed, 3).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(bucket, activatedMilk, 3).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(bucket, effectfulMilk, 3).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(bucket, damagedMilk, 3).isEmpty());
    }

    @Test
    void adapterRequiresSingleBucketStack() {
        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(Items.MILK_BUCKET, 2),
                3
        ).isEmpty());

        assertTrue(LiquidContainerAdapters.fill(
                new ItemStack(Items.BUCKET, 2),
                MilkBucketContainerAdapter.milkState(3),
                3
        ).isEmpty());
    }

    @Test
    void plainMilkPredicateRejectsCanonicalPotionIdentity() {
        AlchemyMixtureState milk = MilkBucketContainerAdapter.milkState(3);
        milk.setCanonicalPotionId("minecraft:water");

        assertTrue(!MilkBucketContainerAdapter.isPlainMilk(milk));
    }
}
