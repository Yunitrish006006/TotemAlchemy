package dev.totem.alchemy.mixture;

import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds delayed cauldron reactions and preserves layered mixtures through a vanilla Brewing Stand. */
public final class AlchemyMixtureBrewing {
    private AlchemyMixtureBrewing() {
    }

    public static boolean canReact(Level level, AlchemyMixtureState state, ItemStack ingredient) {
        if (level == null || state == null || state.isEmpty() || ingredient == null || ingredient.isEmpty()) {
            return false;
        }
        if (state.hasPendingReactions()) {
            return false;
        }
        if (ingredient.is(Items.REDSTONE) || ingredient.is(Items.GLOWSTONE_DUST)) {
            return !state.effects().isEmpty();
        }
        if (ingredient.is(Items.GUNPOWDER)) {
            return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.DRINKABLE;
        }
        if (ingredient.is(Items.DRAGON_BREATH)) {
            return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.SPLASH;
        }
        if (BrewingMaterialSettings.isStarter(ingredient.getItem()) && !state.baseActivated()) {
            return true;
        }
        if (MultiOutcomeBrewing.isOutcomeIngredient(ingredient)) {
            return true;
        }
        ItemStack input = canonicalInput(state);
        return !input.isEmpty() && level.potionBrewing().hasMix(input, ingredient);
    }

    public static boolean schedule(Level level, AlchemyMixtureState state, ItemStack ingredient) {
        if (!canReact(level, state, ingredient)) {
            return false;
        }

        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        String sourcePotion = state.canonicalPotionId();
        String targetPotion = null;
        Map<String, AlchemyMixtureState.EffectDose> source = Map.of();
        Map<String, AlchemyMixtureState.EffectDose> target = Map.of();

        boolean startingBase = BrewingMaterialSettings.isStarter(ingredient.getItem()) && !state.baseActivated();
        if (startingBase) {
            if (state.effects().isEmpty()) {
                targetPotion = "minecraft:awkward";
            }
        } else if (ingredient.is(Items.REDSTONE)) {
            source = state.effects();
            AlchemyMixtureState targetState = state.copy();
            targetState.applyRedstoneModifier();
            target = targetState.effects();
        } else if (ingredient.is(Items.GLOWSTONE_DUST)) {
            source = state.effects();
            AlchemyMixtureState targetState = state.copy();
            targetState.applyGlowstoneModifier();
            target = targetState.effects();
        } else if (ingredient.is(Items.GUNPOWDER) || ingredient.is(Items.DRAGON_BREATH)) {
            targetPotion = sourcePotion;
        } else if (MultiOutcomeBrewing.isOutcomeIngredient(ingredient)) {
            if (!state.baseActivated()) {
                state.setStability(0);
                state.addProvenance("unstable:no_starter");
            }
            MultiOutcomeBrewing.Outcome chosen = MultiOutcomeBrewing.chooseOutcome(
                    ingredient, level.getRandom().nextFloat());
            if (chosen == null) {
                return false;
            }
            target = scaleEffects(effectsForPotion(chosen.potion()), state.volumeUnits());
        } else {
            ItemStack input = canonicalInput(state);
            if (input.isEmpty()) {
                return false;
            }
            ItemStack output = level.potionBrewing().mix(ingredient, input);
            AlchemyMixtureState targetState = AlchemyMixtureBottle.fromPotion(output);
            if (targetState.isEmpty()) {
                return false;
            }
            source = state.effects();
            target = scaleEffects(targetState.effects(), state.volumeUnits());
            targetPotion = targetState.canonicalPotionId();
        }

        String id = "brew:" + (sourcePotion == null ? "mixed" : sourcePotion)
                + ">" + ingredientId + ">" + (targetPotion == null ? "mixed" : targetPotion);
        state.addReaction(new AlchemyMixtureState.Reaction(
                id,
                ingredientId,
                0,
                BrewingMaterialSettings.processingTicks(ingredient.getItem()),
                state.volumeUnits(),
                sourcePotion,
                targetPotion,
                source,
                target
        ));
        return true;
    }

    /** Lets PotionBrewing.hasMix accept layered/custom mixtures that vanilla cannot classify as one canonical potion. */
    public static boolean canApplyBrewingStandIngredient(ItemStack input, ItemStack ingredient) {
        if (!AlchemyMixtureBottle.isPotionContainer(input) || ingredient == null || ingredient.isEmpty()) {
            return false;
        }
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(input);
        if (state.isEmpty()) {
            return false;
        }
        if (ingredient.is(Items.REDSTONE) || ingredient.is(Items.GLOWSTONE_DUST)) {
            return !state.effects().isEmpty();
        }
        if (ingredient.is(Items.GUNPOWDER)) {
            return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.DRINKABLE;
        }
        if (ingredient.is(Items.DRAGON_BREATH)) {
            return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.SPLASH;
        }
        if (BrewingMaterialSettings.isStarter(ingredient.getItem()) && !state.baseActivated()) {
            return true;
        }
        return MultiOutcomeBrewing.isOutcomeIngredient(ingredient);
    }

    /** Apply one completed Brewing Stand ingredient without discarding effects already stored in the mixture. */
    public static ItemStack applyBrewingStandIngredient(
            ItemStack ingredient,
            ItemStack input,
            ItemStack vanillaOutput,
            MultiOutcomeBrewing.Outcome chosenOutcome
    ) {
        if (!AlchemyMixtureBottle.isPotionContainer(input) || ingredient == null || ingredient.isEmpty()) {
            return vanillaOutput;
        }
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(input);
        if (state.isEmpty()) {
            return vanillaOutput;
        }

        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        boolean startingBase = BrewingMaterialSettings.isStarter(ingredient.getItem()) && !state.baseActivated();
        if (startingBase) {
            state.setBaseActivated(true);
            if (state.effects().isEmpty()) {
                state.setCanonicalPotionId("minecraft:awkward");
            }
            state.addProvenance("reaction:" + ingredientId);
            return AlchemyMixtureBottle.toPotion(state);
        }

        if (!state.baseActivated()
                && !ingredient.is(Items.REDSTONE)
                && !ingredient.is(Items.GLOWSTONE_DUST)
                && !ingredient.is(Items.GUNPOWDER)
                && !ingredient.is(Items.DRAGON_BREATH)) {
            state.setStability(0);
            state.addProvenance("unstable:no_starter");
        }

        if (ingredient.is(Items.REDSTONE)) {
            state.applyRedstoneModifier();
        } else if (ingredient.is(Items.GLOWSTONE_DUST)) {
            state.applyGlowstoneModifier();
        } else if (ingredient.is(Items.GUNPOWDER)) {
            state.setDeliveryForm(AlchemyMixtureState.DeliveryForm.SPLASH);
            state.addProvenance("modifier:minecraft:gunpowder");
        } else if (ingredient.is(Items.DRAGON_BREATH)) {
            state.setDeliveryForm(AlchemyMixtureState.DeliveryForm.LINGERING);
            state.addProvenance("modifier:minecraft:dragon_breath");
        } else if (MultiOutcomeBrewing.isOutcomeIngredient(ingredient)) {
            Map<String, AlchemyMixtureState.EffectDose> additions;
            if (chosenOutcome != null) {
                additions = effectsForPotion(chosenOutcome.potion());
            } else if (AlchemyMixtureBottle.isPotionContainer(vanillaOutput)) {
                additions = AlchemyMixtureBottle.fromPotion(vanillaOutput).effects();
            } else {
                additions = Map.of();
            }
            state.addEffects(scaleEffects(additions, state.volumeUnits()));
            state.setCanonicalPotionId(null);
            state.addProvenance("reaction:" + ingredientId);
        } else if (AlchemyMixtureBottle.isPotionContainer(vanillaOutput)) {
            AlchemyMixtureState target = AlchemyMixtureBottle.fromPotion(vanillaOutput);
            state.replaceEffects(scaleEffects(target.effects(), state.volumeUnits()));
            state.setDeliveryForm(target.deliveryForm());
            state.setCanonicalPotionId(target.canonicalPotionId());
            state.addProvenance("reaction:" + ingredientId);
        } else {
            return vanillaOutput;
        }
        return AlchemyMixtureBottle.toPotion(state);
    }

    public static AlchemyMixtureState waterState(int volumeUnits) {
        AlchemyMixtureState state = new AlchemyMixtureState(volumeUnits);
        state.setCanonicalPotionId("minecraft:water");
        state.setBaseActivated(false);
        state.addProvenance("cauldron:minecraft:water");
        return state;
    }

    private static ItemStack canonicalInput(AlchemyMixtureState state) {
        String id = state.canonicalPotionId();
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Holder<Potion> potion = AlchemyMixtureBottle.potionHolder(id);
        return potion == null ? ItemStack.EMPTY : PotionContents.createItemStack(Items.POTION, potion);
    }

    private static Map<String, AlchemyMixtureState.EffectDose> effectsForPotion(Holder<Potion> potion) {
        if (potion == null) {
            return Map.of();
        }
        ItemStack stack = PotionContents.createItemStack(Items.POTION, potion);
        return AlchemyMixtureBottle.fromPotion(stack).effects();
    }

    private static Map<String, AlchemyMixtureState.EffectDose> scaleEffects(
            Map<String, AlchemyMixtureState.EffectDose> effects,
            int factor
    ) {
        Map<String, AlchemyMixtureState.EffectDose> result = new LinkedHashMap<>();
        effects.forEach((id, dose) -> result.put(id, dose.scale(Math.max(1, factor))));
        return result;
    }
}
