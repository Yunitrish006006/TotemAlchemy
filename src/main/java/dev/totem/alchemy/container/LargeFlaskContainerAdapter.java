package dev.totem.alchemy.container;

import dev.totem.alchemy.item.FlaskEnchantments;
import dev.totem.alchemy.mixture.AlchemyCompoundBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Full-mixture adapter for the Large Potion Flask.
 *
 * <p>The flask preserves complete {@link AlchemyMixtureState} snapshots instead of reducing them to a plain
 * liquid identity. Fill capacity is read from {@link FlaskEnchantments#capacity(ItemStack)} on every transfer.
 * Existing over-capacity contents remain drainable if a capacity enchantment is later removed, but cannot be
 * topped up until volume is again within the current capacity.</p>
 */
public final class LargeFlaskContainerAdapter implements LiquidContainerAdapter {
    public static final LargeFlaskContainerAdapter INSTANCE = new LargeFlaskContainerAdapter();

    private LargeFlaskContainerAdapter() {
    }

    @Override
    public Optional<DrainResult> drain(ItemStack stack, int maxUnits) {
        if (!isSingleFlask(stack) || maxUnits <= 0) {
            return Optional.empty();
        }

        AlchemyMixtureState stored = AlchemyMixtureBottle.storedMixture(stack);
        if (stored.isEmpty()) {
            return Optional.empty();
        }

        AlchemyMixtureState remaining = stored.copy();
        AlchemyMixtureState drained = remaining.extractUnits(Math.min(maxUnits, stored.volumeUnits()));
        if (drained.isEmpty()) {
            return Optional.empty();
        }

        ItemStack remainder = stack.copyWithCount(1);
        if (remaining.isEmpty()) {
            AlchemyMixtureBottle.clearState(remainder);
        } else {
            AlchemyMixtureBottle.writeState(remainder, remaining);
        }
        return Optional.of(new DrainResult(remainder, drained, drained.volumeUnits()));
    }

    @Override
    public Optional<FillResult> fill(ItemStack stack, AlchemyMixtureState source, int maxUnits) {
        if (!isSingleFlask(stack) || source == null || source.isEmpty() || maxUnits <= 0) {
            return Optional.empty();
        }

        int capacity = FlaskEnchantments.capacity(stack);
        AlchemyMixtureState stored = AlchemyMixtureBottle.storedMixture(stack);
        int room = capacity - stored.volumeUnits();
        if (room <= 0) {
            return Optional.empty();
        }

        int transferUnits = Math.min(Math.min(room, maxUnits), source.volumeUnits());
        if (transferUnits <= 0) {
            return Optional.empty();
        }

        AlchemyMixtureState sourceRemainder = source.copy();
        AlchemyMixtureState incoming = sourceRemainder.extractUnits(transferUnits);
        if (incoming.isEmpty()) {
            return Optional.empty();
        }

        AlchemyMixtureState filledState;
        if (stored.isEmpty()) {
            filledState = incoming.copy(capacity);
        } else {
            if (!AlchemyCompoundBrewing.canMerge(stored, incoming)) {
                return Optional.empty();
            }
            filledState = stored.copy(capacity);
            if (!filledState.mergeFrom(incoming)) {
                return Optional.empty();
            }
        }

        ItemStack filled = stack.copyWithCount(1);
        AlchemyMixtureBottle.writeState(filled, filledState);
        return Optional.of(new FillResult(filled, sourceRemainder, transferUnits));
    }

    private static boolean isSingleFlask(ItemStack stack) {
        return stack != null
                && stack.is(AlchemyItems.LARGE_POTION_FLASK)
                && stack.getCount() == 1;
    }
}
