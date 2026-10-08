package dev.totem.alchemy.reaction;

import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Predicate;

/**
 * Runtime lookup facade for next-generation Alchemy reactions.
 *
 * <p>Exact item selectors always win over tag selectors. Tag candidates are already ordered
 * deterministically by the immutable reaction index, so overlapping tags resolve by reaction id
 * until explicit merge semantics are introduced later in the migration.</p>
 */
public final class AlchemyReactionResolver {
    private static final double EPSILON = 1.0E-6D;

    private AlchemyReactionResolver() {
    }

    public static Optional<BaseReactionResolution> resolveBaseReaction(
            AlchemyMixtureState state,
            ItemStack starter
    ) {
        if (state == null || state.isEmpty() || starter == null || starter.isEmpty()) {
            return Optional.empty();
        }

        Identifier starterItemId = BuiltInRegistries.ITEM.getKey(starter.getItem());
        return resolveBaseReaction(
                AlchemyReactionDataLoader.index(),
                state,
                starterItemId,
                tagId -> starter.is(TagKey.create(Registries.ITEM, tagId))
        );
    }

    public static Optional<IngredientReaction> resolveIngredientReaction(
            Identifier baseId,
            ItemStack ingredient
    ) {
        if (baseId == null || ingredient == null || ingredient.isEmpty()) {
            return Optional.empty();
        }

        Identifier ingredientItemId = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
        return resolveIngredientReaction(
                AlchemyReactionDataLoader.index(),
                baseId,
                ingredientItemId,
                tagId -> ingredient.is(TagKey.create(Registries.ITEM, tagId))
        );
    }

    public static OptionalDouble successChance(
            Identifier baseId,
            ItemStack ingredient
    ) {
        Identifier ingredientItemId = ingredient == null || ingredient.isEmpty()
                ? null
                : BuiltInRegistries.ITEM.getKey(ingredient.getItem());
        return successChance(
                AlchemyReactionDataLoader.index(),
                baseId,
                ingredientItemId,
                tagId -> ingredient != null
                        && !ingredient.isEmpty()
                        && ingredient.is(TagKey.create(Registries.ITEM, tagId))
        );
    }

    public static List<ReactionOutcome> outcomes(
            Identifier baseId,
            ItemStack ingredient
    ) {
        Optional<IngredientReaction> reaction = resolveIngredientReaction(baseId, ingredient);
        return reaction.map(IngredientReaction::outcomes).orElseGet(List::of);
    }

    public static OptionalDouble outcomeChance(
            Identifier baseId,
            ItemStack ingredient,
            Identifier resultPotionId
    ) {
        if (resultPotionId == null) {
            return OptionalDouble.empty();
        }
        for (ReactionOutcome outcome : outcomes(baseId, ingredient)) {
            if (outcome.resultPotionId().equals(resultPotionId)) {
                return OptionalDouble.of(outcome.chance());
            }
        }
        return OptionalDouble.empty();
    }

    static List<ReactionOutcome> outcomes(
            AlchemyReactionIndex index,
            Identifier baseId,
            Identifier ingredientItemId,
            Predicate<Identifier> matchesTag
    ) {
        return resolveIngredientReaction(index, baseId, ingredientItemId, matchesTag)
                .map(IngredientReaction::outcomes)
                .orElseGet(List::of);
    }

    static OptionalDouble outcomeChance(
            AlchemyReactionIndex index,
            Identifier baseId,
            Identifier ingredientItemId,
            Predicate<Identifier> matchesTag,
            Identifier resultPotionId
    ) {
        if (resultPotionId == null) {
            return OptionalDouble.empty();
        }
        for (ReactionOutcome outcome : outcomes(index, baseId, ingredientItemId, matchesTag)) {
            if (outcome.resultPotionId().equals(resultPotionId)) {
                return OptionalDouble.of(outcome.chance());
            }
        }
        return OptionalDouble.empty();
    }

    static OptionalDouble successChance(
            AlchemyReactionIndex index,
            Identifier baseId,
            Identifier ingredientItemId,
            Predicate<Identifier> matchesTag
    ) {
        Optional<IngredientReaction> reaction =
                resolveIngredientReaction(index, baseId, ingredientItemId, matchesTag);
        return reaction.isPresent()
                ? OptionalDouble.of(reaction.get().successChance())
                : OptionalDouble.empty();
    }

    static Optional<BaseReactionResolution> resolveBaseReaction(
            AlchemyReactionIndex index,
            AlchemyMixtureState state,
            Identifier starterItemId,
            Predicate<Identifier> matchesTag
    ) {
        if (index == null || state == null || state.isEmpty()
                || starterItemId == null || matchesTag == null
                || state.unactivatedUnits() <= EPSILON) {
            return Optional.empty();
        }

        for (BaseReaction candidate : index.exactBaseStarterCandidates(starterItemId)) {
            if (matchesLiquidRequirements(state, candidate)) {
                return Optional.of(baseResolution(state, candidate));
            }
        }

        for (BaseReaction candidate : index.taggedBaseStarterCandidates()) {
            if (matchesTag.test(candidate.starter().id()) && matchesLiquidRequirements(state, candidate)) {
                return Optional.of(baseResolution(state, candidate));
            }
        }
        return Optional.empty();
    }

    private static boolean matchesLiquidRequirements(AlchemyMixtureState state, BaseReaction reaction) {
        for (var requirement : reaction.minimumLiquidFractions().entrySet()) {
            if (state.liquidComposition().amount(requirement.getKey()) + EPSILON < requirement.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static BaseReactionResolution baseResolution(AlchemyMixtureState state, BaseReaction reaction) {
        double unactivatedUnits = state.unactivatedUnits();
        double activationUnits = Math.min(unactivatedUnits, reaction.activationYield());
        return new BaseReactionResolution(reaction, unactivatedUnits, activationUnits);
    }

    static Optional<IngredientReaction> resolveIngredientReaction(
            AlchemyReactionIndex index,
            Identifier baseId,
            Identifier ingredientItemId,
            Predicate<Identifier> matchesTag
    ) {
        if (index == null || baseId == null || ingredientItemId == null || matchesTag == null) {
            return Optional.empty();
        }

        Optional<IngredientReaction> exact = index.exactIngredient(baseId, ingredientItemId);
        if (exact.isPresent()) {
            return exact;
        }

        for (IngredientReaction candidate : index.taggedIngredientCandidates(baseId)) {
            if (matchesTag.test(candidate.ingredient().id())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public record BaseReactionResolution(
            BaseReaction reaction,
            double unactivatedUnits,
            double activationUnits
    ) {
    }
}
