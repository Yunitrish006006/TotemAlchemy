package dev.totem.alchemy.container;

import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Optional;

/**
 * One-unit adapter for the vanilla drinkable Water Bottle.
 *
 * <p>This adapter deliberately handles only the plain vanilla water-potion representation. Stored Totem
 * mixture bottles and all non-water potion contents remain for the generic potion compatibility adapter in
 * M9-T07.</p>
 */
public final class WaterBottleContainerAdapter implements LiquidContainerAdapter {
    public static final WaterBottleContainerAdapter INSTANCE = new WaterBottleContainerAdapter();

    private WaterBottleContainerAdapter() {
    }

    @Override
    public Optional<DrainResult> drain(ItemStack stack, int maxUnits) {
        if (!isPlainWaterBottle(stack) || maxUnits < 1) {
            return Optional.empty();
        }
        return Optional.of(new DrainResult(
                new ItemStack(Items.GLASS_BOTTLE),
                AlchemyMixtureBrewing.waterState(1),
                1
        ));
    }

    @Override
    public Optional<FillResult> fill(ItemStack stack, AlchemyMixtureState source, int maxUnits) {
        if (stack == null || !stack.is(Items.GLASS_BOTTLE) || stack.getCount() != 1
                || source == null || source.volumeUnits() < 1 || maxUnits < 1
                || !isPlainWater(source)) {
            return Optional.empty();
        }

        AlchemyMixtureState remaining = source.copy();
        AlchemyMixtureState transferred = remaining.extractUnits(1);
        if (transferred.volumeUnits() != 1 || !isPlainWater(transferred)) {
            return Optional.empty();
        }

        return Optional.of(new FillResult(
                PotionContents.createItemStack(Items.POTION, Potions.WATER),
                remaining,
                1
        ));
    }

    static boolean isPlainWaterBottle(ItemStack stack) {
        return stack != null
                && stack.is(Items.POTION)
                && stack.getCount() == 1
                && !AlchemyMixtureBottle.hasStoredMixture(stack)
                && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                        .is(Potions.WATER);
    }

    static boolean isPlainWater(AlchemyMixtureState state) {
        if (state == null || state.isEmpty()
                || state.baseActivated()
                || state.deliveryForm() != AlchemyMixtureState.DeliveryForm.DRINKABLE
                || state.canonicalPotionId() != null
                    && !"minecraft:water".equals(state.canonicalPotionId())
                || !state.effects().isEmpty()
                || state.hasPendingReactions()
                || state.hasCompletedStages()
                || state.stability() != AlchemyMixtureState.STABILITY_MAX
                || Math.abs(state.stabilityDamageCarry()) > LiquidComposition.DEFAULT_EPSILON) {
            return false;
        }

        LiquidComposition composition = state.liquidComposition().normalized();
        return composition.components().size() == 1
                && Math.abs(composition.amount(AlchemyLiquids.WATER_ID) - 1.0D)
                <= LiquidComposition.DEFAULT_EPSILON;
    }
}
