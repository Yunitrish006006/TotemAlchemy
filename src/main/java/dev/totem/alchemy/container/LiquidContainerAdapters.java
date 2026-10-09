package dev.totem.alchemy.container;

import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Deterministic adapter dispatcher for portable liquid/mixture containers.
 *
 * <p>Concrete adapters are added one M9 task at a time. The first adapter that can perform a requested
 * transfer wins, making adapter precedence explicit and stable.</p>
 */
public final class LiquidContainerAdapters {
    private static final List<LiquidContainerAdapter> BUILT_INS = List.of(
            WaterBottleContainerAdapter.INSTANCE,
            WaterBucketContainerAdapter.INSTANCE,
            MilkBucketContainerAdapter.INSTANCE,
            HoneyBottleContainerAdapter.INSTANCE
    );

    private LiquidContainerAdapters() {
    }

    public static Optional<LiquidContainerAdapter.DrainResult> drain(ItemStack stack, int maxUnits) {
        return drain(BUILT_INS, stack, maxUnits);
    }

    public static Optional<LiquidContainerAdapter.FillResult> fill(
            ItemStack stack,
            AlchemyMixtureState source,
            int maxUnits
    ) {
        return fill(BUILT_INS, stack, source, maxUnits);
    }

    static Optional<LiquidContainerAdapter.DrainResult> drain(
            List<LiquidContainerAdapter> adapters,
            ItemStack stack,
            int maxUnits
    ) {
        if (adapters == null || adapters.isEmpty()
                || stack == null || stack.isEmpty()
                || maxUnits <= 0) {
            return Optional.empty();
        }
        for (LiquidContainerAdapter adapter : adapters) {
            if (adapter == null) {
                continue;
            }
            Optional<LiquidContainerAdapter.DrainResult> result = adapter.drain(stack.copy(), maxUnits);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    static Optional<LiquidContainerAdapter.FillResult> fill(
            List<LiquidContainerAdapter> adapters,
            ItemStack stack,
            AlchemyMixtureState source,
            int maxUnits
    ) {
        if (adapters == null || adapters.isEmpty()
                || stack == null || stack.isEmpty()
                || source == null || source.isEmpty()
                || maxUnits <= 0) {
            return Optional.empty();
        }
        for (LiquidContainerAdapter adapter : adapters) {
            if (adapter == null) {
                continue;
            }
            Optional<LiquidContainerAdapter.FillResult> result =
                    adapter.fill(stack.copy(), source.copy(), maxUnits);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
