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
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds delayed cauldron reactions and preserves layered mixtures through a vanilla Brewing Stand. */
public final class AlchemyMixtureBrewing {
    private AlchemyMixtureBrewing() {}

    public static boolean canReact(Level level, AlchemyMixtureState state, ItemStack ingredient) {
        if (level == null || state == null || state.isEmpty() || ingredient == null || ingredient.isEmpty()) return false;
        if (state.hasPendingReactions()) return false;
        if (ingredient.is(Items.REDSTONE) || ingredient.is(Items.GLOWSTONE_DUST)) return !state.effects().isEmpty();
        if (ingredient.is(Items.GUNPOWDER)) return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.DRINKABLE;
        if (ingredient.is(Items.DRAGON_BREATH)) return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.SPLASH;
        if (BrewingMaterialSettings.isStarter(ingredient.getItem()) && !state.baseActivated()) return true;
        if (MultiOutcomeBrewing.isOutcomeIngredient(ingredient)) return true;
        ItemStack input = canonicalInput(state);
        return !input.isEmpty() && level.potionBrewing().hasMix(input, ingredient);
    }

    public static boolean schedule(Level level, AlchemyMixtureState state, ItemStack ingredient) {
        return scheduleDetailed(level, state, ingredient).scheduled();
    }

    public static boolean schedule(Level level, AlchemyMixtureState state, ItemStack ingredient, RandomSource random) {
        return scheduleInternal(level, state, ingredient, random, null).scheduled();
    }

    public static boolean scheduleOutcomeSet(Level level, AlchemyMixtureState state, ItemStack ingredient,
                                             List<MultiOutcomeBrewing.Outcome> selectedOutcomes) {
        return scheduleOutcomeSetDetailed(level, state, ingredient, selectedOutcomes).scheduled();
    }

    /**
     * Server-facing schedule result used by the Alchemy Cauldron to defer discovery until the reaction actually
     * finishes. The selected result ids are captured at schedule time because layered mixtures frequently have no
     * canonical PotionContents holder after the reaction has been applied.
     */
    public static ScheduleResult scheduleDetailed(Level level, AlchemyMixtureState state, ItemStack ingredient) {
        return scheduleInternal(level, state, ingredient, level == null ? null : level.getRandom(), null);
    }

    public static ScheduleResult scheduleOutcomeSetDetailed(Level level, AlchemyMixtureState state, ItemStack ingredient,
                                                             List<MultiOutcomeBrewing.Outcome> selectedOutcomes) {
        return scheduleInternal(level, state, ingredient, null,
                selectedOutcomes == null ? List.of() : List.copyOf(selectedOutcomes));
    }

    private static ScheduleResult scheduleInternal(Level level, AlchemyMixtureState state, ItemStack ingredient,
                                                    RandomSource random, List<MultiOutcomeBrewing.Outcome> selectedOutcomes) {
        if (!canReact(level, state, ingredient)) return ScheduleResult.NOT_SCHEDULED;

        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        String sourcePotion = state.canonicalPotionId();
        String targetPotion = null;
        Map<String, AlchemyMixtureState.EffectDose> source = Map.of();
        Map<String, AlchemyMixtureState.EffectDose> target = Map.of();
        boolean outcomeIngredient = MultiOutcomeBrewing.isOutcomeIngredient(ingredient);
        List<MultiOutcomeBrewing.Outcome> chosenOutcomes = List.of();

        boolean startingBase = BrewingMaterialSettings.isStarter(ingredient.getItem()) && !state.baseActivated();
        if (startingBase) {
            if (state.effects().isEmpty()) targetPotion = "minecraft:awkward";
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
        } else if (outcomeIngredient) {
            if (!state.baseActivated()) {
                state.setStability(0);
                state.addProvenance("unstable:no_starter");
            }
            chosenOutcomes = selectedOutcomes == null
                    ? MultiOutcomeBrewing.chooseOutcomes(ingredient, random == null ? level.getRandom() : random)
                    : selectedOutcomes;
            // An empty independent roll is a valid no-effect reaction: consume/process the material but add nothing.
            target = chosenOutcomes.isEmpty()
                    ? Map.of()
                    : scaleEffects(effectsForOutcomes(chosenOutcomes), state.volumeUnits());
        } else {
            ItemStack input = canonicalInput(state);
            if (input.isEmpty()) return ScheduleResult.NOT_SCHEDULED;
            ItemStack output = level.potionBrewing().mix(ingredient, input);
            AlchemyMixtureState targetState = AlchemyMixtureBottle.fromPotion(output);
            if (targetState.isEmpty()) return ScheduleResult.NOT_SCHEDULED;
            source = state.effects();
            target = scaleEffects(targetState.effects(), state.volumeUnits());
            targetPotion = targetState.canonicalPotionId();
        }

        String reactionPrefix = outcomeIngredient || state.preservesIndependentOutcomes() ? "brewset:" : "brew:";
        String id = reactionPrefix + (sourcePotion == null ? "mixed" : sourcePotion)
                + ">" + ingredientId + ">" + (targetPotion == null ? "mixed" : targetPotion);
        int processingTicks = BrewingMaterialSettings.processingTicks(ingredient.getItem());
        state.addReaction(new AlchemyMixtureState.Reaction(
                id, ingredientId, 0, processingTicks,
                state.volumeUnits(), sourcePotion, targetPotion, source, target));

        List<String> resultPotionIds;
        boolean researchable;
        if (outcomeIngredient) {
            resultPotionIds = chosenOutcomes.stream()
                    .map(MultiOutcomeBrewing.Outcome::potion)
                    .map(holder -> BuiltInRegistries.POTION.getKey(holder.value()).toString())
                    .distinct()
                    .toList();
            // Empty is still researchable: it represents the legitimate no-effect outcome.
            researchable = true;
        } else if (targetPotion != null) {
            resultPotionIds = List.of(targetPotion);
            researchable = true;
        } else {
            resultPotionIds = List.of();
            researchable = false;
        }
        return new ScheduleResult(true, id, ingredientId, processingTicks, researchable, resultPotionIds);
    }

    public static boolean canApplyBrewingStandIngredient(ItemStack input, ItemStack ingredient) {
        if (!AlchemyMixtureBottle.isPotionContainer(input) || ingredient == null || ingredient.isEmpty()) return false;
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(input);
        if (state.isEmpty()) return false;
        if (ingredient.is(Items.REDSTONE) || ingredient.is(Items.GLOWSTONE_DUST)) return !state.effects().isEmpty();
        if (ingredient.is(Items.GUNPOWDER)) return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.DRINKABLE;
        if (ingredient.is(Items.DRAGON_BREATH)) return state.deliveryForm() == AlchemyMixtureState.DeliveryForm.SPLASH;
        if (BrewingMaterialSettings.isStarter(ingredient.getItem()) && !state.baseActivated()) return true;
        return MultiOutcomeBrewing.isOutcomeIngredient(ingredient);
    }

    public static ItemStack applyBrewingStandIngredient(ItemStack ingredient, ItemStack input,
                                                        ItemStack vanillaOutput,
                                                        MultiOutcomeBrewing.Outcome chosenOutcome) {
        return applyBrewingStandOutcomes(ingredient, input, vanillaOutput,
                chosenOutcome == null ? List.of() : List.of(chosenOutcome));
    }

    public static ItemStack applyBrewingStandOutcomes(ItemStack ingredient, ItemStack input,
                                                      ItemStack vanillaOutput,
                                                      List<MultiOutcomeBrewing.Outcome> chosenOutcomes) {
        if (!AlchemyMixtureBottle.isPotionContainer(input) || ingredient == null || ingredient.isEmpty()) return vanillaOutput;
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(input);
        if (state.isEmpty()) return vanillaOutput;

        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        boolean startingBase = BrewingMaterialSettings.isStarter(ingredient.getItem()) && !state.baseActivated();
        if (startingBase) {
            state.setBaseActivated(true);
            if (state.effects().isEmpty()) state.setCanonicalPotionId("minecraft:awkward");
            state.addProvenance("reaction:" + ingredientId);
            return AlchemyMixtureBottle.toPotion(state);
        }

        if (!state.baseActivated() && !ingredient.is(Items.REDSTONE) && !ingredient.is(Items.GLOWSTONE_DUST)
                && !ingredient.is(Items.GUNPOWDER) && !ingredient.is(Items.DRAGON_BREATH)) {
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
            Map<String, AlchemyMixtureState.EffectDose> additions = chosenOutcomes == null
                    ? (AlchemyMixtureBottle.isPotionContainer(vanillaOutput)
                        ? AlchemyMixtureBottle.fromPotion(vanillaOutput).effects() : Map.of())
                    : effectsForOutcomes(chosenOutcomes);
            state.addIndependentOutcomeEffects(scaleEffects(additions, state.volumeUnits()));
            state.setCanonicalPotionId(null);
            state.addProvenance(chosenOutcomes != null && chosenOutcomes.isEmpty()
                    ? "reaction:no_effect:" + ingredientId : "reaction:" + ingredientId);
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
        if (id == null) return ItemStack.EMPTY;
        Holder<Potion> potion = AlchemyMixtureBottle.potionHolder(id);
        return potion == null ? ItemStack.EMPTY : PotionContents.createItemStack(Items.POTION, potion);
    }

    private static Map<String, AlchemyMixtureState.EffectDose> effectsForPotion(Holder<Potion> potion) {
        if (potion == null) return Map.of();
        return AlchemyMixtureBottle.fromPotion(PotionContents.createItemStack(Items.POTION, potion)).effects();
    }

    private static Map<String, AlchemyMixtureState.EffectDose> effectsForOutcomes(List<MultiOutcomeBrewing.Outcome> outcomes) {
        Map<String, AlchemyMixtureState.EffectDose> result = new LinkedHashMap<>();
        for (MultiOutcomeBrewing.Outcome outcome : outcomes) {
            effectsForPotion(outcome.potion()).forEach((id, dose) -> result.merge(id, dose, AlchemyMixtureState.EffectDose::merge));
        }
        return result;
    }

    private static Map<String, AlchemyMixtureState.EffectDose> scaleEffects(Map<String, AlchemyMixtureState.EffectDose> effects,
                                                                            int factor) {
        Map<String, AlchemyMixtureState.EffectDose> result = new LinkedHashMap<>();
        effects.forEach((id, dose) -> result.put(id, dose.scale(Math.max(1, factor))));
        return result;
    }

    public record ScheduleResult(
            boolean scheduled,
            String reactionId,
            String ingredientId,
            int processingTicks,
            boolean researchable,
            List<String> resultPotionIds
    ) {
        private static final ScheduleResult NOT_SCHEDULED =
                new ScheduleResult(false, "", "", 0, false, List.of());

        public ScheduleResult {
            reactionId = reactionId == null ? "" : reactionId;
            ingredientId = ingredientId == null ? "" : ingredientId;
            processingTicks = Math.max(0, processingTicks);
            resultPotionIds = List.copyOf(resultPotionIds == null ? List.of() : resultPotionIds);
        }
    }
}
