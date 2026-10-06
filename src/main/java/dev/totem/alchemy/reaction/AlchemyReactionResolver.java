package dev.totem.alchemy.reaction;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

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
    private AlchemyReactionResolver() {
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
}
