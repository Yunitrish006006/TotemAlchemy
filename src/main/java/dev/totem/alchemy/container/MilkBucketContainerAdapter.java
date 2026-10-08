package dev.totem.alchemy.container;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Three-unit adapter for the vanilla Milk Bucket.
 *
 * <p>Minecraft milk has no native fluid/potion identity, so the drained mixture uses the explicit
 * {@code minecraft:milk} Alchemy liquid id with no canonical potion id. Only plain Milk can be filled back
 * into a vanilla Milk Bucket; chemistry-bearing Milk is rejected instead of losing state.</p>
 */
public final class MilkBucketContainerAdapter implements LiquidContainerAdapter {
    public static final MilkBucketContainerAdapter INSTANCE = new MilkBucketContainerAdapter();
    public static final int BUCKET_VOLUME_UNITS = AlchemyMixtureState.MAX_VOLUME_UNITS;

    private MilkBucketContainerAdapter() {
    }

    @Override
    public Optional<DrainResult> drain(ItemStack stack, int maxUnits) {
        if (stack == null || !stack.is(Items.MILK_BUCKET) || stack.getCount() != 1
                || maxUnits < BUCKET_VOLUME_UNITS) {
            return Optional.empty();
        }
        return Optional.of(new DrainResult(
                new ItemStack(Items.BUCKET),
                milkState(BUCKET_VOLUME_UNITS),
                BUCKET_VOLUME_UNITS
        ));
    }

    @Override
    public Optional<FillResult> fill(ItemStack stack, AlchemyMixtureState source, int maxUnits) {
        if (stack == null || !stack.is(Items.BUCKET) || stack.getCount() != 1
                || source == null || source.volumeUnits() < BUCKET_VOLUME_UNITS
                || maxUnits < BUCKET_VOLUME_UNITS
                || !isPlainMilk(source)) {
            return Optional.empty();
        }

        AlchemyMixtureState remaining = source.copy();
        AlchemyMixtureState transferred = remaining.extractUnits(BUCKET_VOLUME_UNITS);
        if (transferred.volumeUnits() != BUCKET_VOLUME_UNITS || !isPlainMilk(transferred)) {
            return Optional.empty();
        }

        return Optional.of(new FillResult(
                new ItemStack(Items.MILK_BUCKET),
                remaining,
                BUCKET_VOLUME_UNITS
        ));
    }

    static AlchemyMixtureState milkState(int volumeUnits) {
        AlchemyMixtureState state = new AlchemyMixtureState(volumeUnits);
        state.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.MILK_ID, 1.0D));
        return state;
    }

    static boolean isPlainMilk(AlchemyMixtureState state) {
        if (state == null || state.isEmpty()
                || state.baseActivated()
                || state.deliveryForm() != AlchemyMixtureState.DeliveryForm.DRINKABLE
                || state.canonicalPotionId() != null
                || !state.effects().isEmpty()
                || state.hasPendingReactions()
                || state.hasCompletedStages()
                || state.stability() != AlchemyMixtureState.STABILITY_MAX
                || Math.abs(state.stabilityDamageCarry()) > LiquidComposition.DEFAULT_EPSILON) {
            return false;
        }

        LiquidComposition composition = state.liquidComposition().normalized();
        return composition.components().size() == 1
                && Math.abs(composition.amount(AlchemyLiquids.MILK_ID) - 1.0D)
                <= LiquidComposition.DEFAULT_EPSILON;
    }
}
