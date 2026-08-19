package dev.totem.alchemy.mixture;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds delayed cauldron reactions from the live PotionBrewing registry. */
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
        ItemStack input = canonicalInput(state);
        return !input.isEmpty() && level.potionBrewing().hasMix(input, ingredient);
    }

    public static boolean schedule(Level level, AlchemyMixtureState state, ItemStack ingredient) {
        if (!canReact(level, state, ingredient)) {
            return false;
        }

        Map<String, AlchemyMixtureState.EffectDose> source = state.effects();
        Map<String, AlchemyMixtureState.EffectDose> target;
        String sourcePotion = state.canonicalPotionId();
        String targetPotion = null;

        if (ingredient.is(Items.REDSTONE) && state.canonicalPotionId() == null) {
            AlchemyMixtureState targetState = state.copy();
            targetState.applyRedstoneModifier();
            target = targetState.effects();
        } else if (ingredient.is(Items.GLOWSTONE_DUST) && state.canonicalPotionId() == null) {
            AlchemyMixtureState targetState = state.copy();
            targetState.applyGlowstoneModifier();
            target = targetState.effects();
        } else {
            ItemStack input = canonicalInput(state);
            ItemStack output = level.potionBrewing().mix(ingredient, input);
            MultiOutcomeBrewing.Outcome chosen = MultiOutcomeBrewing.chooseOutcome(
                    ingredient,
                    input,
                    level.getRandom().nextFloat()
            );
            if (chosen != null) {
                output = PotionContents.createItemStack(Items.POTION, chosen.potion());
            }
            AlchemyMixtureState targetState = AlchemyMixtureBottle.fromPotion(output);
            targetPotion = targetState.canonicalPotionId();
            target = scaleEffects(targetState.effects(), state.volumeUnits());
        }

        String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        String id = "brew:" + (sourcePotion == null ? "mixed" : sourcePotion)
                + ">" + ingredientId + ">" + (targetPotion == null ? "mixed" : targetPotion);
        state.addReaction(new AlchemyMixtureState.Reaction(
                id,
                ingredientId,
                0,
                AlchemyMixtureState.DEFAULT_REACTION_TICKS,
                state.volumeUnits(),
                sourcePotion,
                targetPotion,
                source,
                target
        ));
        return true;
    }

    public static AlchemyMixtureState waterState(int volumeUnits) {
        AlchemyMixtureState state = new AlchemyMixtureState(volumeUnits);
        state.setCanonicalPotionId("minecraft:water");
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

    private static Map<String, AlchemyMixtureState.EffectDose> scaleEffects(
            Map<String, AlchemyMixtureState.EffectDose> effects,
            int factor
    ) {
        Map<String, AlchemyMixtureState.EffectDose> result = new LinkedHashMap<>();
        effects.forEach((id, dose) -> result.put(id, dose.scale(Math.max(1, factor))));
        return result;
    }
}
