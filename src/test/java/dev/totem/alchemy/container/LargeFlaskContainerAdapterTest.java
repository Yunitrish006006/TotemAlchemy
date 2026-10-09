package dev.totem.alchemy.container;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.ActivatedBaseComposition;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LargeFlaskContainerAdapterTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void emptyFlaskCapturesFullPendingMixtureState() {
        AlchemyMixtureState source = complexPendingMixture();
        String before = source.encode();

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(AlchemyItems.LARGE_POTION_FLASK),
                source,
                3
        ).orElseThrow();

        assertEquals(3, result.transferredUnits());
        assertTrue(result.sourceRemainder().isEmpty());
        assertEquals(before, source.encode());

        AlchemyMixtureState stored = AlchemyMixtureBottle.storedMixture(result.filledContainer());
        assertEquals(before, stored.encode());
    }

    @Test
    void partialDrainConservesFullMixtureQuantitiesAndLeavesFlaskFilled() {
        AlchemyMixtureState source = complexPendingMixture();
        double effectBefore = source.effects().get("minecraft:speed").quantity();
        double baseBefore = source.activatedBaseUnits();

        ItemStack flask = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
        AlchemyMixtureBottle.writeState(flask, source);

        LiquidContainerAdapter.DrainResult result =
                LiquidContainerAdapters.drain(flask, 1).orElseThrow();

        AlchemyMixtureState remaining =
                AlchemyMixtureBottle.storedMixture(result.containerRemainder());
        assertEquals(1, result.transferredUnits());
        assertEquals(1, result.drained().volumeUnits());
        assertEquals(2, remaining.volumeUnits());
        assertEquals(source.liquidComposition(), result.drained().liquidComposition());
        assertEquals(source.liquidComposition(), remaining.liquidComposition());
        assertEquals(
                effectBefore,
                result.drained().effects().get("minecraft:speed").quantity()
                        + remaining.effects().get("minecraft:speed").quantity(),
                EPSILON
        );
        assertEquals(
                baseBefore,
                result.drained().activatedBaseUnits() + remaining.activatedBaseUnits(),
                EPSILON
        );
        assertEquals(1, result.drained().reactions().size());
        assertEquals(1, remaining.reactions().size());
    }

    @Test
    void baseFlaskCapacityLimitsTransferToThreeUnits() {
        AlchemyMixtureState source = new AlchemyMixtureState(8, 8);
        source.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.WATER_ID, 1.0D));

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                new ItemStack(AlchemyItems.LARGE_POTION_FLASK),
                source,
                8
        ).orElseThrow();

        assertEquals(3, result.transferredUnits());
        assertEquals(3, AlchemyMixtureBottle.storedMixture(result.filledContainer()).volumeUnits());
        assertEquals(5, result.sourceRemainder().volumeUnits());
        assertEquals(8, source.volumeUnits());
    }

    @Test
    void partialTopUpRespectsMaxUnitsAndDoesNotMutateSource() {
        ItemStack flask = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
        AlchemyMixtureState stored = new AlchemyMixtureState(1);
        stored.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.WATER_ID, 1.0D));
        AlchemyMixtureBottle.writeState(flask, stored);

        AlchemyMixtureState source = new AlchemyMixtureState(3);
        source.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.WATER_ID, 1.0D));

        LiquidContainerAdapter.FillResult result =
                LiquidContainerAdapters.fill(flask, source, 1).orElseThrow();

        assertEquals(1, result.transferredUnits());
        assertEquals(2, AlchemyMixtureBottle.storedMixture(result.filledContainer()).volumeUnits());
        assertEquals(2, result.sourceRemainder().volumeUnits());
        assertEquals(3, source.volumeUnits());
    }

    @Test
    void emptyOrStackedFlasksDoNotTransfer() {
        ItemStack empty = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
        assertTrue(LiquidContainerAdapters.drain(empty, 1).isEmpty());

        ItemStack stacked = new ItemStack(AlchemyItems.LARGE_POTION_FLASK, 2);
        assertTrue(LiquidContainerAdapters.fill(stacked, complexPendingMixture(), 1).isEmpty());
    }

    private static AlchemyMixtureState complexPendingMixture() {
        AlchemyMixtureState state = new AlchemyMixtureState(3);
        state.setLiquidComposition(LiquidComposition.of(Map.of(
                AlchemyLiquids.WATER_ID, 2.0D,
                AlchemyLiquids.MILK_ID, 1.0D
        )));
        state.setActivatedBaseComposition(ActivatedBaseComposition.single(
                Identifier.fromNamespaceAndPath("totem", "alchemy/test_base"),
                1.5D
        ));
        state.putEffect("minecraft:speed", 900.0D, 1);
        state.addProvenance("test:large_flask");
        state.addReaction(new AlchemyMixtureState.Reaction(
                "test:pending",
                "minecraft:sugar",
                25,
                100,
                3,
                2,
                null,
                null,
                Map.of("minecraft:speed", new AlchemyMixtureState.EffectDose(900.0D, 1)),
                Map.of("minecraft:speed", new AlchemyMixtureState.EffectDose(1200.0D, 1))
        ));
        return state;
    }
}
