package dev.totem.alchemy.mixture;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;

/** Conversion between potion containers and persistent Alchemy mixture snapshots. */
public final class AlchemyMixtureBottle {
    public static final String TAG_MIXTURE_STATE = "totem_alchemy_mixture_state";

    private AlchemyMixtureBottle() {
    }

    public static boolean isPotionContainer(ItemStack stack) {
        return stack != null && (stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION));
    }

    public static boolean isDrinkablePotion(ItemStack stack) {
        return stack != null && stack.is(Items.POTION);
    }

    public static boolean hasStoredMixture(ItemStack stack) {
        return !storedStateString(stack).isBlank();
    }

    public static AlchemyMixtureState storedMixture(ItemStack stack) {
        String encoded = storedStateString(stack);
        return encoded.isBlank() ? AlchemyMixtureState.empty() : AlchemyMixtureState.decode(encoded);
    }

    public static AlchemyMixtureState fromPotion(ItemStack stack) {
        if (!isPotionContainer(stack)) {
            return AlchemyMixtureState.empty();
        }
        if (hasStoredMixture(stack)) {
            return AlchemyMixtureState.decode(storedStateString(stack));
        }

        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.setDeliveryForm(deliveryForm(stack));
        contents.potion()
                .flatMap(Holder::unwrapKey)
                .ifPresent(key -> state.setCanonicalPotionId(key.identifier().toString()));
        for (MobEffectInstance effect : contents.getAllEffects()) {
            String effectId = effect.getEffect().unwrapKey()
                    .map(key -> key.identifier().toString())
                    .orElse(null);
            if (effectId != null) {
                state.putEffect(effectId,
                        AlchemyMixtureState.EffectDose.fromDuration(effect.getDuration(), effect.getAmplifier())
                                .potencyTicks(),
                        effect.getAmplifier());
            }
        }
        state.setBaseActivated(isActivatedPotion(state.canonicalPotionId(), state.effects().isEmpty()));
        state.addProvenance("potion:" + (state.canonicalPotionId() == null ? "custom" : state.canonicalPotionId()));
        return state;
    }

    private static boolean isActivatedPotion(String potionId, boolean hasNoEffects) {
        if (potionId == null) {
            return !hasNoEffects;
        }
        return !"minecraft:water".equals(potionId)
                && !"minecraft:mundane".equals(potionId)
                && !"minecraft:thick".equals(potionId);
    }

    public static ItemStack toPotion(AlchemyMixtureState state) {
        if (state == null || state.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = canonicalStack(state);
        if (stack.isEmpty()) {
            stack = new ItemStack(deliveryItem(state.deliveryForm()));
            PotionContents contents = PotionContents.EMPTY;
            for (Map.Entry<String, AlchemyMixtureState.EffectDose> entry : state.effects().entrySet()) {
                Holder<MobEffect> effect = effectHolder(entry.getKey());
                if (effect == null) {
                    continue;
                }
                AlchemyMixtureState.EffectDose dose = entry.getValue();
                contents = contents.withEffectAdded(new MobEffectInstance(
                        effect,
                        dose.durationForVolume(Math.max(1, state.volumeUnits())),
                        dose.amplifierCap()
                ));
            }
            if (state.stability() < 50 && state.stability() > 0) {
                int nauseaTicks = 20 * Math.max(2, (50 - state.stability()) / 5);
                contents = contents.withEffectAdded(new MobEffectInstance(MobEffects.NAUSEA, nauseaTicks, 0));
            }
            stack.set(DataComponents.POTION_CONTENTS, contents);
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.totem.alchemy.mixture"));
        }

        writeState(stack, state);
        return stack;
    }

    public static void writeState(ItemStack stack, AlchemyMixtureState state) {
        if (stack == null || stack.isEmpty() || state == null) {
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (state.isEmpty()) {
            tag.remove(TAG_MIXTURE_STATE);
        } else {
            tag.putString(TAG_MIXTURE_STATE, state.encode());
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearState(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_MIXTURE_STATE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static ItemStack canonicalStack(AlchemyMixtureState state) {
        if (state.hasPendingReactions() || state.canonicalPotionId() == null) {
            return ItemStack.EMPTY;
        }
        Holder<Potion> holder = potionHolder(state.canonicalPotionId());
        return holder == null ? ItemStack.EMPTY : PotionContents.createItemStack(deliveryItem(state.deliveryForm()), holder);
    }

    private static Item deliveryItem(AlchemyMixtureState.DeliveryForm form) {
        return switch (form == null ? AlchemyMixtureState.DeliveryForm.DRINKABLE : form) {
            case SPLASH -> Items.SPLASH_POTION;
            case LINGERING -> Items.LINGERING_POTION;
            case DRINKABLE -> Items.POTION;
        };
    }

    private static AlchemyMixtureState.DeliveryForm deliveryForm(ItemStack stack) {
        if (stack.is(Items.SPLASH_POTION)) {
            return AlchemyMixtureState.DeliveryForm.SPLASH;
        }
        if (stack.is(Items.LINGERING_POTION)) {
            return AlchemyMixtureState.DeliveryForm.LINGERING;
        }
        return AlchemyMixtureState.DeliveryForm.DRINKABLE;
    }

    private static String storedStateString(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getStringOr(TAG_MIXTURE_STATE, "");
    }

    public static Holder<Potion> potionHolder(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return null;
        }
        Potion potion = BuiltInRegistries.POTION.getValue(identifier);
        return potion == null ? null : BuiltInRegistries.POTION.wrapAsHolder(potion);
    }

    public static Holder<MobEffect> effectHolder(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return null;
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(identifier);
        return effect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }
}
