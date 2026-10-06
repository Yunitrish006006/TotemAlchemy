package dev.totem.alchemy.reaction;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Resolves the reaction base represented by a Brewing Stand input before looking up ingredient chemistry.
 *
 * <p>The current mixture schema stores only whether a base is activated, not the activated base identity.
 * Until activated-base composition lands, every activated legacy potion is therefore mapped through this
 * compatibility boundary to the historical awkward base. Keeping that mapping here prevents individual
 * station implementations from hard-coding a reaction base and gives the future base-composition model one
 * replacement point.</p>
 */
public record BrewingReactionContext(Identifier baseId, IngredientReaction reaction) {
    static final Identifier LEGACY_ACTIVATED_BASE_ID =
            Identifier.fromNamespaceAndPath("totem", "alchemy/awkward");

    public BrewingReactionContext {
        Objects.requireNonNull(baseId, "baseId");
        Objects.requireNonNull(reaction, "reaction");
    }

    public static Optional<Identifier> baseId(ItemStack input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        return baseId(AlchemyMixtureBottle.fromPotion(input));
    }

    static Optional<Identifier> baseId(AlchemyMixtureState state) {
        if (state == null || state.isEmpty() || !state.baseActivated()) {
            return Optional.empty();
        }
        return Optional.of(LEGACY_ACTIVATED_BASE_ID);
    }

    public static Optional<BrewingReactionContext> resolve(ItemStack input, ItemStack ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return Optional.empty();
        }
        return baseId(input).flatMap(baseId ->
                AlchemyReactionResolver.resolveIngredientReaction(baseId, ingredient)
                        .map(reaction -> new BrewingReactionContext(baseId, reaction))
        );
    }

    public static Optional<BrewingReactionContext> resolveFirst(
            Iterable<ItemStack> inputs,
            ItemStack ingredient
    ) {
        if (inputs == null || ingredient == null || ingredient.isEmpty()) {
            return Optional.empty();
        }
        for (ItemStack input : inputs) {
            Optional<BrewingReactionContext> resolved = resolve(input, ingredient);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    /**
     * Compatibility lookup for legacy callers that do not yet carry an input mixture.
     * New station runtime code should prefer {@link #resolve(ItemStack, ItemStack)}.
     */
    public static Optional<IngredientReaction> resolveLegacyActivated(ItemStack ingredient) {
        return AlchemyReactionResolver.resolveIngredientReaction(LEGACY_ACTIVATED_BASE_ID, ingredient);
    }

    static Optional<BrewingReactionContext> resolve(
            AlchemyReactionIndex index,
            AlchemyMixtureState state,
            Identifier ingredientItemId,
            Predicate<Identifier> matchesTag
    ) {
        if (index == null || ingredientItemId == null || matchesTag == null) {
            return Optional.empty();
        }
        return baseId(state).flatMap(baseId ->
                AlchemyReactionResolver.resolveIngredientReaction(
                                index,
                                baseId,
                                ingredientItemId,
                                matchesTag
                        )
                        .map(reaction -> new BrewingReactionContext(baseId, reaction))
        );
    }
}
