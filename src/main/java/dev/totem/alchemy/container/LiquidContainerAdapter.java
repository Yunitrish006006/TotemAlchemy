package dev.totem.alchemy.container;

import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Pure conversion boundary between portable ItemStacks and persistent Alchemy mixture state.
 *
 * <p>Adapters must not mutate the supplied ItemStack or AlchemyMixtureState. World changes, inventory
 * replacement, sounds, player messages, and cauldron commits belong to interaction code outside this boundary.</p>
 *
 * <p>A drain converts up to {@code maxUnits} from a filled/partially-filled container into mixture state.
 * A fill converts up to {@code maxUnits} from a source mixture into a filled/partially-filled container.
 * Unsupported or impossible transfers return {@link Optional#empty()}.</p>
 */
public interface LiquidContainerAdapter {
    Optional<DrainResult> drain(ItemStack stack, int maxUnits);

    Optional<FillResult> fill(ItemStack stack, AlchemyMixtureState source, int maxUnits);

    record DrainResult(
            ItemStack containerRemainder,
            AlchemyMixtureState drained,
            int transferredUnits
    ) {
        public DrainResult {
            containerRemainder = containerRemainder == null ? ItemStack.EMPTY : containerRemainder.copy();
            if (drained == null || drained.isEmpty()) {
                throw new IllegalArgumentException("drained mixture must be non-empty");
            }
            drained = drained.copy();
            transferredUnits = Math.max(1, transferredUnits);
            if (transferredUnits != drained.volumeUnits()) {
                throw new IllegalArgumentException("transferredUnits must equal drained mixture volume");
            }
        }
    }

    record FillResult(
            ItemStack filledContainer,
            AlchemyMixtureState sourceRemainder,
            int transferredUnits
    ) {
        public FillResult {
            if (filledContainer == null || filledContainer.isEmpty()) {
                throw new IllegalArgumentException("filledContainer must be non-empty");
            }
            filledContainer = filledContainer.copy();
            sourceRemainder = sourceRemainder == null
                    ? AlchemyMixtureState.empty()
                    : sourceRemainder.copy();
            transferredUnits = Math.max(1, transferredUnits);
        }
    }
}
