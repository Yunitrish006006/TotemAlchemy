package dev.totem.alchemy.item;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/** A food-like bottled drink whose modern stacks apply the actual stored mixture effects. */
public final class AlchemyDrinkItem extends Item {
    private final Supplier<Holder<Potion>> legacyPotion;
    private final String foodEquivalentEffect;

    public AlchemyDrinkItem(
            Properties properties,
            Supplier<Holder<Potion>> legacyPotion,
            String foodEquivalentEffect
    ) {
        super(properties);
        this.legacyPotion = legacyPotion;
        this.foodEquivalentEffect = foodEquivalentEffect;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        AlchemyMixtureState stored = AlchemyMixtureBottle.hasStoredMixture(stack)
                ? AlchemyMixtureBottle.storedMixture(stack)
                : AlchemyMixtureState.empty();
        PotionContents contents;
        if (!stored.isEmpty()) {
            contents = AlchemyMixtureBottle.potionContents(stored);
        } else if (legacyPotion != null && legacyPotion.get() != null) {
            contents = new PotionContents(legacyPotion.get());
        } else {
            contents = PotionContents.EMPTY;
        }

        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (level instanceof ServerLevel serverLevel) {
            for (MobEffectInstance effect : contents.getAllEffects()) {
                String effectId = effect.getEffect().unwrapKey()
                        .map(key -> key.identifier().toString())
                        .orElse("");
                // Hot cocoa's normal food component already supplies its saturation value.
                if (!stored.isEmpty() && effectId.equals(foodEquivalentEffect)) {
                    continue;
                }
                if (effect.getEffect().value().isInstantaneous()) {
                    effect.getEffect().value().applyInstantaneousEffect(
                            serverLevel, entity, entity, entity, effect.getAmplifier(), 1.0D);
                } else {
                    entity.addEffect(new MobEffectInstance(
                            effect.getEffect(), effect.getDuration(), effect.getAmplifier()));
                }
            }
        }
        return result;
    }
}
