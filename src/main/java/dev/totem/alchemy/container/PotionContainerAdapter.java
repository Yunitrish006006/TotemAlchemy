package dev.totem.alchemy.container;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * One-unit compatibility adapter for vanilla potion containers.
 *
 * <p>Drinkable, splash, and lingering potions are imported through the canonical
 * {@link AlchemyMixtureBottle#fromPotion(ItemStack)} conversion so canonical potion ids, effects,
 * delivery form, and stored mixture snapshots are preserved. Export delegates to
 * {@link AlchemyMixtureBottle#toPotion(AlchemyMixtureState)} after extracting exactly one mixture unit.</p>
 *
 * <p>Alchemy signature drink items are intentionally excluded from this adapter and remain owned by
 * their later signature-brew work.</p>
 */
public final class PotionContainerAdapter implements LiquidContainerAdapter {
    public static final PotionContainerAdapter INSTANCE = new PotionContainerAdapter();
    public static final int BOTTLE_VOLUME_UNITS = 1;

    private PotionContainerAdapter() {
    }

    @Override
    public Optional<DrainResult> drain(ItemStack stack, int maxUnits) {
        if (!isVanillaPotionContainer(stack)
                || stack.getCount() != 1
                || maxUnits < BOTTLE_VOLUME_UNITS) {
            return Optional.empty();
        }

        AlchemyMixtureState drained = AlchemyMixtureBottle.fromPotion(stack);
        if (drained.isEmpty() || drained.volumeUnits() != BOTTLE_VOLUME_UNITS) {
            return Optional.empty();
        }

        return Optional.of(new DrainResult(
                new ItemStack(Items.GLASS_BOTTLE),
                drained,
                BOTTLE_VOLUME_UNITS
        ));
    }

    @Override
    public Optional<FillResult> fill(ItemStack stack, AlchemyMixtureState source, int maxUnits) {
        if (stack == null || !stack.is(Items.GLASS_BOTTLE) || stack.getCount() != 1
                || source == null || source.isEmpty()
                || maxUnits < BOTTLE_VOLUME_UNITS) {
            return Optional.empty();
        }

        AlchemyMixtureState sourceRemainder = source.copy();
        AlchemyMixtureState bottled = sourceRemainder.extractUnits(BOTTLE_VOLUME_UNITS);
        if (bottled.volumeUnits() != BOTTLE_VOLUME_UNITS) {
            return Optional.empty();
        }

        ItemStack filled = AlchemyMixtureBottle.toPotion(bottled);
        if (!isVanillaPotionContainer(filled)) {
            return Optional.empty();
        }

        return Optional.of(new FillResult(
                filled,
                sourceRemainder,
                BOTTLE_VOLUME_UNITS
        ));
    }

    static boolean isVanillaPotionContainer(ItemStack stack) {
        return stack != null
                && (stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION));
    }
}
