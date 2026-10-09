package dev.totem.alchemy.container;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * One-unit adapter for the vanilla Honey Bottle.
 *
 * <p>Minecraft honey has no native potion/fluid identity, so the drained mixture uses the explicit
 * {@code minecraft:honey} Alchemy liquid id with no canonical potion id. Only plain Honey can be filled
 * back into a vanilla Honey Bottle; chemistry-bearing Honey is rejected instead of losing state.</p>
 */
public final class HoneyBottleContainerAdapter implements LiquidContainerAdapter {
    public static final HoneyBottleContainerAdapter INSTANCE = new HoneyBottleContainerAdapter();
    public static final int BOTTLE_VOLUME_UNITS = 1;

    private HoneyBottleContainerAdapter() {
    }

    @Override
    public Optional<DrainResult> drain(ItemStack stack, int maxUnits) {
        if (stack == null || !stack.is(Items.HONEY_BOTTLE) || stack.getCount() != 1
                || maxUnits < BOTTLE_VOLUME_UNITS) {
            return Optional.empty();
        }
        return Optional.of(new DrainResult(
                new ItemStack(Items.GLASS_BOTTLE),
                honeyState(BOTTLE_VOLUME_UNITS),
                BOTTLE_VOLUME_UNITS
        ));
    }

    @Override
    public Optional<FillResult> fill(ItemStack stack, AlchemyMixtureState source, int maxUnits) {
        if (stack == null || !stack.is(Items.GLASS_BOTTLE) || stack.getCount() != 1
                || source == null || source.volumeUnits() < BOTTLE_VOLUME_UNITS
                || maxUnits < BOTTLE_VOLUME_UNITS
                || !isPlainHoney(source)) {
            return Optional.empty();
        }

        AlchemyMixtureState remaining = source.copy();
        AlchemyMixtureState transferred = remaining.extractUnits(BOTTLE_VOLUME_UNITS);
        if (transferred.volumeUnits() != BOTTLE_VOLUME_UNITS || !isPlainHoney(transferred)) {
            return Optional.empty();
        }

        return Optional.of(new FillResult(
                new ItemStack(Items.HONEY_BOTTLE),
                remaining,
                BOTTLE_VOLUME_UNITS
        ));
    }

    static AlchemyMixtureState honeyState(int volumeUnits) {
        AlchemyMixtureState state = new AlchemyMixtureState(volumeUnits);
        state.setLiquidComposition(LiquidComposition.single(AlchemyLiquids.HONEY_ID, 1.0D));
        return state;
    }

    static boolean isPlainHoney(AlchemyMixtureState state) {
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
                && Math.abs(composition.amount(AlchemyLiquids.HONEY_ID) - 1.0D)
                <= LiquidComposition.DEFAULT_EPSILON;
    }
}
