package dev.totem.alchemy.container;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Three-unit adapter for the vanilla Water Bucket.
 *
 * <p>One bucket corresponds to a full vanilla cauldron volume: three bottle-equivalent mixture units.
 * Plain Water stays a normal vanilla Water Bucket. Non-plain three-unit mixtures use the existing stored-mixture
 * Water Bucket representation so mixed liquids and chemistry state round-trip without loss.</p>
 */
public final class WaterBucketContainerAdapter implements LiquidContainerAdapter {
    public static final WaterBucketContainerAdapter INSTANCE = new WaterBucketContainerAdapter();
    public static final int BUCKET_VOLUME_UNITS = AlchemyMixtureState.MAX_VOLUME_UNITS;

    private WaterBucketContainerAdapter() {
    }

    @Override
    public Optional<DrainResult> drain(ItemStack stack, int maxUnits) {
        if (stack == null || !stack.is(Items.WATER_BUCKET) || stack.getCount() != 1
                || maxUnits < BUCKET_VOLUME_UNITS) {
            return Optional.empty();
        }
        AlchemyMixtureState drained = AlchemyMixtureBottle.hasStoredMixture(stack)
                ? AlchemyMixtureBottle.storedMixture(stack)
                : AlchemyMixtureBrewing.waterState(BUCKET_VOLUME_UNITS);
        if (drained.volumeUnits() != BUCKET_VOLUME_UNITS) {
            return Optional.empty();
        }
        return Optional.of(new DrainResult(
                new ItemStack(Items.BUCKET),
                drained,
                BUCKET_VOLUME_UNITS
        ));
    }

    @Override
    public Optional<FillResult> fill(ItemStack stack, AlchemyMixtureState source, int maxUnits) {
        if (stack == null || !stack.is(Items.BUCKET) || stack.getCount() != 1
                || source == null || source.volumeUnits() < BUCKET_VOLUME_UNITS
                || maxUnits < BUCKET_VOLUME_UNITS) {
            return Optional.empty();
        }

        AlchemyMixtureState remaining = source.copy();
        AlchemyMixtureState transferred = remaining.extractUnits(BUCKET_VOLUME_UNITS);
        if (transferred.volumeUnits() != BUCKET_VOLUME_UNITS) {
            return Optional.empty();
        }

        ItemStack filled = new ItemStack(Items.WATER_BUCKET);
        if (!WaterBottleContainerAdapter.isPlainWater(transferred)) {
            AlchemyMixtureBottle.writeState(filled, transferred);
        }
        return Optional.of(new FillResult(
                filled,
                remaining,
                BUCKET_VOLUME_UNITS
        ));
    }
}
