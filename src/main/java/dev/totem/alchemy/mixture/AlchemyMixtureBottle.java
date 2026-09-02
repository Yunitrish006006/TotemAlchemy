package dev.totem.alchemy.mixture;

import dev.totem.alchemy.item.LargePotionFlaskItem;
import dev.totem.alchemy.registry.AlchemyItems;
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
import java.util.Objects;

/** Conversion between potion containers and persistent Alchemy mixture snapshots. */
public final class AlchemyMixtureBottle {
    public static final String TAG_MIXTURE_STATE = "totem_alchemy_mixture_state";

    private AlchemyMixtureBottle() {
    }

    public static boolean isPotionContainer(ItemStack stack) {
        return stack != null && (stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION)
                || stack.is(AlchemyItems.HOT_COCOA)
                || stack.is(AlchemyItems.CHERRY_BREW));
    }

    public static boolean isDrinkablePotion(ItemStack stack) {
        return stack != null && (stack.is(Items.POTION)
                || stack.is(AlchemyItems.HOT_COCOA)
                || stack.is(AlchemyItems.CHERRY_BREW));
    }

    public static boolean hasStoredMixture(ItemStack stack) {
        return !storedStateString(stack).isBlank();
    }

    public static AlchemyMixtureState storedMixture(ItemStack stack) {
        String encoded = storedStateString(stack);
        if (encoded.isBlank()) {
            return AlchemyMixtureState.empty();
        }
        AlchemyMixtureState state = AlchemyMixtureState.decode(encoded);
        // Migrate finished portable mixtures made before the heat-lock marker existed.
        state.lockHeatIfFinished();
        return state;
    }

    public static AlchemyMixtureState fromPotion(ItemStack stack) {
        if (hasStoredMixture(stack)) {
            return storedMixture(stack);
        }
        if (!isPotionContainer(stack)) {
            return AlchemyMixtureState.empty();
        }

        AlchemyMixtureState legacyDrink = AlchemyCompoundBrewing.legacyDrinkState(stack);
        if (!legacyDrink.isEmpty()) {
            return legacyDrink;
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
                AlchemyMixtureState.EffectDose dose =
                        AlchemyMixtureState.EffectDose.fromDuration(effect.getDuration(), effect.getAmplifier());
                state.putEffect(effectId, dose.potencyTicks(), effect.getAmplifier());
            }
        }
        state.setBaseActivated(isActivatedPotion(state.canonicalPotionId(), state.effects().isEmpty()));
        state.addProvenance("potion:" + (state.canonicalPotionId() == null ? "custom" : state.canonicalPotionId()));
        state.lockHeatIfFinished();
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
        ItemStack compoundResult = AlchemyCompoundBrewing.bottledResult(state);
        if (!compoundResult.isEmpty()) {
            return compoundResult;
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
        AlchemyMixtureState stored = state.copy();
        stored.lockHeatIfFinished();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (stored.isEmpty()) {
            tag.remove(TAG_MIXTURE_STATE);
        } else {
            tag.putString(TAG_MIXTURE_STATE, stored.encode());
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (stored.isEmpty()) {
            stack.remove(DataComponents.POTION_CONTENTS);
        } else {
            stack.set(DataComponents.POTION_CONTENTS, potionContents(stored));
        }
        if (stack.getItem() instanceof LargePotionFlaskItem) {
            stack.remove(DataComponents.USE_REMAINDER);
        }
    }

    public static void clearState(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_MIXTURE_STATE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.remove(DataComponents.POTION_CONTENTS);
        if (stack.getItem() instanceof LargePotionFlaskItem) {
            stack.remove(DataComponents.USE_REMAINDER);
        }
    }

    /** Repairs presentation components on portable mixtures created by earlier module versions. */
    public static boolean refreshPortablePresentation(ItemStack stack) {
        if (!hasStoredMixture(stack)) {
            return false;
        }
        AlchemyMixtureState stored = storedMixture(stack);
        PotionContents expected = potionContents(stored);
        boolean staleState = !stored.encode().equals(storedStateString(stack));
        boolean staleEffects = !Objects.equals(stack.get(DataComponents.POTION_CONTENTS), expected);
        boolean staleRemainder = stack.getItem() instanceof LargePotionFlaskItem
                && stack.has(DataComponents.USE_REMAINDER);
        if (!staleState && !staleEffects && !staleRemainder) {
            return false;
        }
        writeState(stack, stored);
        return true;
    }

    /** Builds the native per-dose potion view used by item tooltips and read-only cauldron UI. */
    public static PotionContents potionContents(AlchemyMixtureState state) {
        if (state == null || state.isEmpty()) {
            return PotionContents.EMPTY;
        }
        PotionContents contents = PotionContents.EMPTY;
        Holder<Potion> canonical = state.hasPendingReactions() || state.canonicalPotionId() == null
                ? null
                : potionHolder(state.canonicalPotionId());
        if (canonical != null) {
            contents = new PotionContents(canonical);
        } else {
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
        }
        return contents;
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
