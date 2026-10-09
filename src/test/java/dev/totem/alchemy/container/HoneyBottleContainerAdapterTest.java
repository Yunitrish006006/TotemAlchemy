package dev.totem.alchemy.container;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoneyBottleContainerAdapterTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void honeyBottleDrainsToOneHoneyUnitAndGlassBottle() {
        ItemStack honeyBottle = new ItemStack(Items.HONEY_BOTTLE);

        LiquidContainerAdapter.DrainResult result =
                LiquidContainerAdapters.drain(honeyBottle, 1).orElseThrow();

        assertEquals(1, result.transferredUnits());
        assertTrue(result.containerRemainder().is(Items.GLASS_BOTTLE));
        assertEquals(1, result.drained().volumeUnits());
        assertNull(result.drained().canonicalPotionId());
        assertEquals(
                Map.of(AlchemyLiquids.HONEY_ID, 1.0D),
                result.drained().liquidComposition().components()
        );
        assertEquals(1, honeyBottle.getCount());
    }

    @Test
    void glassBottleFillsFromOneUnitOfPlainHoney() {
        AlchemyMixtureState source = HoneyBottleContainerAdapter.honeyState(2);

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(Items.GLASS_BOTTLE),
                source,
                1
        ).orElseThrow();

        assertEquals(1, result.transferredUnits());
        assertTrue(result.filledContainer().is(Items.HONEY_BOTTLE));
        assertEquals(1, result.sourceRemainder().volumeUnits());
        assertEquals(2, source.volumeUnits());
    }

    @Test
    void oneUnitFillLeavesEmptySourceRemainder() {
        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(Items.GLASS_BOTTLE),
                HoneyBottleContainerAdapter.honeyState(1),
                1
        ).orElseThrow();

        assertTrue(result.sourceRemainder().isEmpty());
    }

    @Test
    void fillRejectsWaterMilkMixedOrStatefulHoney() {
        AlchemyMixtureState water = AlchemyMixtureBrewing.waterState(1);
        AlchemyMixtureState milk = MilkBucketContainerAdapter.milkState(1);

        AlchemyMixtureState mixed = new AlchemyMixtureState(1);
        mixed.setLiquidComposition(LiquidComposition.of(Map.of(
                AlchemyLiquids.HONEY_ID, 0.5D,
                AlchemyLiquids.WATER_ID, 0.5D
        )));

        AlchemyMixtureState activatedHoney = HoneyBottleContainerAdapter.honeyState(1);
        activatedHoney.setBaseActivated(true);

        AlchemyMixtureState effectfulHoney = HoneyBottleContainerAdapter.honeyState(1);
        effectfulHoney.putEffect("minecraft:speed", 200.0D, 0);

        AlchemyMixtureState damagedHoney = HoneyBottleContainerAdapter.honeyState(1);
        damagedHoney.setStability(90);

        ItemStack glass = new ItemStack(Items.GLASS_BOTTLE);
        assertTrue(LiquidContainerAdapters.fill(glass, water, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, milk, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, mixed, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, activatedHoney, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, effectfulHoney, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(glass, damagedHoney, 1).isEmpty());
    }

    @Test
    void adapterRequiresSingleBottleStackAndPositiveTransferLimit() {
        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(Items.HONEY_BOTTLE, 2),
                1
        ).isEmpty());

        assertTrue(LiquidContainerAdapters.fill(
                new ItemStack(Items.GLASS_BOTTLE, 2),
                HoneyBottleContainerAdapter.honeyState(1),
                1
        ).isEmpty());

        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(Items.HONEY_BOTTLE),
                0
        ).isEmpty());
    }

    @Test
    void plainHoneyPredicateRejectsCanonicalPotionIdentity() {
        AlchemyMixtureState honey = HoneyBottleContainerAdapter.honeyState(1);
        honey.setCanonicalPotionId("minecraft:water");

        assertTrue(!HoneyBottleContainerAdapter.isPlainHoney(honey));
    }
}
