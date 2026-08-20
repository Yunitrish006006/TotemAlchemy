package dev.totem.alchemy.item;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

/** Three-dose portable Alchemy mixture container. */
public final class LargePotionFlaskItem extends Item {
    public LargePotionFlaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        AlchemyMixtureState state = AlchemyMixtureBottle.storedMixture(stack);
        if (state.isEmpty()) {
            return InteractionResult.PASS;
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 32;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide()) {
            return stack;
        }
        AlchemyMixtureState stored = AlchemyMixtureBottle.storedMixture(stack);
        if (stored.isEmpty()) {
            return stack;
        }

        AlchemyMixtureState working = stored.copy();
        AlchemyMixtureState dose = working.extractUnits(1);
        ItemStack potionView = AlchemyMixtureBottle.toPotion(dose);
        PotionContents contents = potionView.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (level instanceof ServerLevel serverLevel) {
            for (MobEffectInstance effect : contents.getAllEffects()) {
                if (effect.getEffect().value().isInstantenous()) {
                    effect.getEffect().value().applyInstantenousEffect(
                            serverLevel, entity, entity, entity, effect.getAmplifier(), 1.0D);
                } else {
                    entity.addEffect(new MobEffectInstance(
                            effect.getEffect(), effect.getDuration(), effect.getAmplifier()));
                }
            }
        }

        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_DRINK, entity.getSoundSource(), 0.5F, 1.0F);

        if (!(entity instanceof Player player) || !player.getAbilities().instabuild) {
            if (working.isEmpty()) {
                AlchemyMixtureBottle.clearState(stack);
            } else {
                AlchemyMixtureBottle.writeState(stack, working);
            }
        }
        return stack;
    }
}
