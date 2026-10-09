package dev.totem.alchemy.container;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidContainerAdaptersTest {
    @Test
    void unsupportedContainersStillFallThroughBuiltInDispatcher() {
        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(AlchemyItems.HOT_COCOA),
                1
        ).isEmpty());
        assertTrue(LiquidContainerAdapters.drain(
                new ItemStack(Items.APPLE),
                1
        ).isEmpty());
    }

    @Test
    void drainUsesFirstAdapterThatProducesAResult() {
        LiquidContainerAdapter miss = new TestAdapter(false, false);
        LiquidContainerAdapter hit = new TestAdapter(true, false);

        LiquidContainerAdapter.DrainResult result = LiquidContainerAdapters.drain(
                List.of(miss, hit),
                new ItemStack(Items.POTION),
                1
        ).orElseThrow();

        assertEquals(1, result.transferredUnits());
        assertEquals(1, result.drained().volumeUnits());
        assertTrue(result.containerRemainder().is(Items.GLASS_BOTTLE));
    }

    @Test
    void fillUsesFirstAdapterThatProducesAResult() {
        LiquidContainerAdapter miss = new TestAdapter(false, false);
        LiquidContainerAdapter hit = new TestAdapter(false, true);

        LiquidContainerAdapter.FillResult result = LiquidContainerAdapters.fill(
                List.of(miss, hit),
                new ItemStack(Items.GLASS_BOTTLE),
                water(2),
                1
        ).orElseThrow();

        assertEquals(1, result.transferredUnits());
        assertTrue(result.filledContainer().is(Items.POTION));
        assertEquals(1, result.sourceRemainder().volumeUnits());
    }

    @Test
    void dispatcherPassesDefensiveCopiesToAdapters() {
        ItemStack originalStack = new ItemStack(Items.POTION);
        AlchemyMixtureState originalSource = water(2);

        LiquidContainerAdapter mutating = new LiquidContainerAdapter() {
            @Override
            public Optional<DrainResult> drain(ItemStack stack, int maxUnits) {
                stack.shrink(1);
                return Optional.of(new DrainResult(
                        new ItemStack(Items.GLASS_BOTTLE),
                        water(1),
                        1
                ));
            }

            @Override
            public Optional<FillResult> fill(ItemStack stack, AlchemyMixtureState source, int maxUnits) {
                stack.shrink(1);
                source.extractUnits(1);
                return Optional.of(new FillResult(
                        new ItemStack(Items.POTION),
                        source,
                        1
                ));
            }
        };

        assertTrue(LiquidContainerAdapters.drain(
                List.of(mutating), originalStack, 1
        ).isPresent());
        assertEquals(1, originalStack.getCount());

        assertTrue(LiquidContainerAdapters.fill(
                List.of(mutating),
                new ItemStack(Items.GLASS_BOTTLE),
                originalSource,
                1
        ).isPresent());
        assertEquals(2, originalSource.volumeUnits());
    }

    @Test
    void resultRecordsOwnDetachedCopies() {
        ItemStack remainder = new ItemStack(Items.GLASS_BOTTLE);
        AlchemyMixtureState drained = water(1);
        LiquidContainerAdapter.DrainResult drainResult =
                new LiquidContainerAdapter.DrainResult(remainder, drained, 1);

        ItemStack filled = new ItemStack(Items.POTION);
        AlchemyMixtureState sourceRemainder = water(1);
        LiquidContainerAdapter.FillResult fillResult =
                new LiquidContainerAdapter.FillResult(filled, sourceRemainder, 1);

        remainder.shrink(1);
        drained.extractUnits(1);
        filled.shrink(1);
        sourceRemainder.extractUnits(1);

        assertFalse(drainResult.containerRemainder().isEmpty());
        assertEquals(1, drainResult.drained().volumeUnits());
        assertFalse(fillResult.filledContainer().isEmpty());
        assertEquals(1, fillResult.sourceRemainder().volumeUnits());
        assertNotSame(drained, drainResult.drained());
        assertNotSame(sourceRemainder, fillResult.sourceRemainder());
    }

    @Test
    void invalidTransfersAreRejectedBeforeAdapterDispatch() {
        LiquidContainerAdapter adapter = new TestAdapter(true, true);

        assertTrue(LiquidContainerAdapters.drain(
                List.of(adapter), ItemStack.EMPTY, 1
        ).isEmpty());
        assertTrue(LiquidContainerAdapters.drain(
                List.of(adapter), new ItemStack(Items.POTION), 0
        ).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(
                List.of(adapter), new ItemStack(Items.GLASS_BOTTLE), AlchemyMixtureState.empty(), 1
        ).isEmpty());
        assertTrue(LiquidContainerAdapters.fill(
                List.of(adapter), new ItemStack(Items.GLASS_BOTTLE), water(1), 0
        ).isEmpty());
    }

    private static AlchemyMixtureState water(int units) {
        AlchemyMixtureState state = new AlchemyMixtureState(units);
        state.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.WATER_ID, 1.0D));
        return state;
    }

    private record TestAdapter(boolean drains, boolean fills) implements LiquidContainerAdapter {
        @Override
        public Optional<DrainResult> drain(ItemStack stack, int maxUnits) {
            if (!drains || !stack.is(Items.POTION) || maxUnits < 1) {
                return Optional.empty();
            }
            return Optional.of(new DrainResult(
                    new ItemStack(Items.GLASS_BOTTLE),
                    water(1),
                    1
            ));
        }

        @Override
        public Optional<FillResult> fill(ItemStack stack, AlchemyMixtureState source, int maxUnits) {
            if (!fills || !stack.is(Items.GLASS_BOTTLE) || source.isEmpty() || maxUnits < 1) {
                return Optional.empty();
            }
            AlchemyMixtureState remaining = source.copy();
            remaining.extractUnits(1);
            return Optional.of(new FillResult(
                    new ItemStack(Items.POTION),
                    remaining,
                    1
            ));
        }
    }
}
