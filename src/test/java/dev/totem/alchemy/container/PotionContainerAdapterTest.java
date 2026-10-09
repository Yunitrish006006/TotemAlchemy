package dev.totem.alchemy.container;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionContainerAdapterTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void awkwardPotionImportsCanonicalOneUnitMixture() {
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);

        LiquidContainerAdapter.DrainResult result =
                LiquidContainerAdapters.drain(awkward, 1).orElseThrow();

        assertEquals(1, result.transferredUnits());
        assertTrue(result.containerRemainder().is(Items.GLASS_BOTTLE));
        assertEquals(1, result.drained().volumeUnits());
        assertEquals("minecraft:awkward", result.drained().canonicalPotionId());
        assertEquals(AlchemyMixtureState.DeliveryForm.DRINKABLE, result.drained().deliveryForm());
        assertEquals(
                Map.of(AlchemyLiquids.WATER_ID, 1.0D),
                result.drained().liquidComposition().components()
        );
        assertTrue(result.drained().baseActivated());
        assertEquals(1, awkward.getCount());
    }

    @Test
    void splashAndLingeringImportsPreserveDeliveryFormAndEffects() {
        ItemStack splash = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SWIFTNESS);
        ItemStack lingering = PotionContents.createItemStack(Items.LINGERING_POTION, Potions.STRENGTH);

        AlchemyMixtureState splashState =
                LiquidContainerAdapters.drain(splash, 1).orElseThrow().drained();
        AlchemyMixtureState lingeringState =
                LiquidContainerAdapters.drain(lingering, 1).orElseThrow().drained();

        assertEquals(AlchemyMixtureState.DeliveryForm.SPLASH, splashState.deliveryForm());
        assertTrue(splashState.effects().containsKey("minecraft:speed"));
        assertEquals(AlchemyMixtureState.DeliveryForm.LINGERING, lingeringState.deliveryForm());
        assertTrue(lingeringState.effects().containsKey("minecraft:strength"));
    }

    @Test
    void exportUsesStoredDeliveryFormAndPreservesFullMixtureSnapshot() {
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.setLiquidComposition(LiquidComposition.of(Map.of(
                AlchemyLiquids.WATER_ID, 0.75D,
                AlchemyLiquids.MILK_ID, 0.25D
        )));
        state.setDeliveryForm(AlchemyMixtureState.DeliveryForm.LINGERING);
        state.putEffect("minecraft:speed", 600.0D, 1);
        state.setStability(80);
        state.addProvenance("test:potion_adapter");
        state.lockHeatIfFinished();

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(Items.GLASS_BOTTLE),
                state,
                1
        ).orElseThrow();

        assertTrue(result.filledContainer().is(Items.LINGERING_POTION));
        assertTrue(AlchemyMixtureBottle.hasStoredMixture(result.filledContainer()));
        assertTrue(result.sourceRemainder().isEmpty());
        assertEquals(state.encode(), AlchemyMixtureBottle.storedMixture(result.filledContainer()).encode());
        assertEquals(1, state.volumeUnits());
    }

    @Test
    void multiUnitExportExtractsOneDoseAndConservesEffectQuantity() {
        AlchemyMixtureState source = new AlchemyMixtureState(3);
        source.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.WATER_ID, 1.0D));
        source.putEffect("minecraft:speed", 1800.0D, 0);
        source.lockHeatIfFinished();

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(Items.GLASS_BOTTLE),
                source,
                1
        ).orElseThrow();

        AlchemyMixtureState bottled = AlchemyMixtureBottle.storedMixture(result.filledContainer());
        assertEquals(1, result.transferredUnits());
        assertEquals(1, bottled.volumeUnits());
        assertEquals(2, result.sourceRemainder().volumeUnits());
        assertEquals(
                source.effects().get("minecraft:speed").quantity(),
                bottled.effects().get("minecraft:speed").quantity()
                        + result.sourceRemainder().effects().get("minecraft:speed").quantity(),
                EPSILON
        );
        assertEquals(3, source.volumeUnits());
    }

    @Test
    void storedPendingPotionImportsWithoutLosingReactionState() {
        AlchemyMixtureState pending = new AlchemyMixtureState(1);
        pending.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.WATER_ID, 1.0D));
        pending.addReaction(new AlchemyMixtureState.Reaction(
                "test:pending",
                "minecraft:sugar",
                20,
                100,
                1,
                2,
                null,
                null,
                Map.of(),
                Map.of("minecraft:speed", new AlchemyMixtureState.EffectDose(600.0D, 0))
        ));

        ItemStack potion = AlchemyMixtureBottle.toPotion(pending);
        AlchemyMixtureState expected = AlchemyMixtureBottle.storedMixture(potion);

        AlchemyMixtureState imported =
                LiquidContainerAdapters.drain(potion, 1).orElseThrow().drained();

        assertEquals(expected.encode(), imported.encode());
        assertEquals(1, imported.reactions().size());
        assertFalse(imported.hasCompletedStages());
    }

    @Test
    void signatureDrinksRemainOutsideGenericPotionAdapter() {
        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(AlchemyItems.HOT_COCOA),
                1
        ).isEmpty());
        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(AlchemyItems.CHERRY_BREW),
                1
        ).isEmpty());
    }

    @Test
    void adapterRequiresSingleContainerAndPositiveTransferLimit() {
        ItemStack splash = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SWIFTNESS);
        splash.setCount(2);

        assertTrue(LiquidContainerAdapters.drain(splash, 1).isEmpty());
        assertTrue(LiquidContainerAdapters.drain(
                PotionContents.createItemStack(Items.POTION, Potions.AWKWARD),
                0
        ).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(
                new ItemStack(Items.GLASS_BOTTLE, 2),
                AlchemyMixtureBottle.fromPotion(
                        PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS)
                ),
                1
        ).isEmpty());
    }
}
