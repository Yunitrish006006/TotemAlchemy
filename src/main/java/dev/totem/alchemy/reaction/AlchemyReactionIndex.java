package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable lookup index built once per server-data reload.
 *
 * <p>Exact item selectors receive direct maps. Tag selectors stay as small deterministic candidate
 * lists because actual tag membership belongs to the resolver layer and can depend on registry state.</p>
 */
public final class AlchemyReactionIndex {
    private static final Comparator<IngredientReaction> INGREDIENT_ORDER =
            Comparator.comparing(reaction -> reaction.id().toString());
    private static final Comparator<BaseReaction> BASE_ORDER =
            Comparator.comparingInt(BaseReaction::priority).reversed()
                    .thenComparing(reaction -> reaction.id().toString());

    private final Map<Identifier, Map<Identifier, IngredientReaction>> exactIngredientsByBase;
    private final Map<Identifier, List<IngredientReaction>> taggedIngredientsByBase;
    private final Map<Identifier, List<BaseReaction>> exactBaseStarters;
    private final List<BaseReaction> taggedBaseStarters;

    private AlchemyReactionIndex(
            Map<Identifier, Map<Identifier, IngredientReaction>> exactIngredientsByBase,
            Map<Identifier, List<IngredientReaction>> taggedIngredientsByBase,
            Map<Identifier, List<BaseReaction>> exactBaseStarters,
            List<BaseReaction> taggedBaseStarters
    ) {
        this.exactIngredientsByBase = exactIngredientsByBase;
        this.taggedIngredientsByBase = taggedIngredientsByBase;
        this.exactBaseStarters = exactBaseStarters;
        this.taggedBaseStarters = taggedBaseStarters;
    }

    public static AlchemyReactionIndex empty() {
        return new AlchemyReactionIndex(Map.of(), Map.of(), Map.of(), List.of());
    }

    public static AlchemyReactionIndex build(
            List<BaseReaction> baseReactions,
            List<IngredientReaction> ingredientReactions
    ) {
        Objects.requireNonNull(baseReactions, "baseReactions");
        Objects.requireNonNull(ingredientReactions, "ingredientReactions");

        Map<Identifier, Map<Identifier, IngredientReaction>> exactIngredients = new LinkedHashMap<>();
        Map<Identifier, List<IngredientReaction>> taggedIngredients = new LinkedHashMap<>();

        ingredientReactions.stream()
                .sorted(INGREDIENT_ORDER)
                .forEach(reaction -> {
                    if (reaction.ingredient().kind() == ReactionIngredient.Kind.ITEM) {
                        Map<Identifier, IngredientReaction> byIngredient =
                                exactIngredients.computeIfAbsent(reaction.baseId(), ignored -> new LinkedHashMap<>());
                        IngredientReaction previous = byIngredient.putIfAbsent(reaction.ingredient().id(), reaction);
                        if (previous != null) {
                            throw new IllegalArgumentException(
                                    "Duplicate exact ingredient reaction for base " + reaction.baseId()
                                            + " and item " + reaction.ingredient().id()
                                            + ": " + previous.id() + " / " + reaction.id()
                            );
                        }
                    } else {
                        taggedIngredients.computeIfAbsent(reaction.baseId(), ignored -> new ArrayList<>()).add(reaction);
                    }
                });

        Map<Identifier, List<BaseReaction>> exactStarters = new LinkedHashMap<>();
        List<BaseReaction> taggedStarters = new ArrayList<>();
        baseReactions.stream()
                .sorted(BASE_ORDER)
                .forEach(reaction -> {
                    if (reaction.starter().kind() == ReactionIngredient.Kind.ITEM) {
                        exactStarters.computeIfAbsent(reaction.starter().id(), ignored -> new ArrayList<>()).add(reaction);
                    } else {
                        taggedStarters.add(reaction);
                    }
                });

        return new AlchemyReactionIndex(
                freezeNestedMap(exactIngredients),
                freezeListsByKey(taggedIngredients, INGREDIENT_ORDER),
                freezeListsByKey(exactStarters, BASE_ORDER),
                List.copyOf(taggedStarters)
        );
    }

    public Optional<IngredientReaction> exactIngredient(Identifier baseId, Identifier ingredientItemId) {
        Map<Identifier, IngredientReaction> byIngredient = exactIngredientsByBase.get(baseId);
        return byIngredient == null ? Optional.empty() : Optional.ofNullable(byIngredient.get(ingredientItemId));
    }

    public List<IngredientReaction> taggedIngredientCandidates(Identifier baseId) {
        return taggedIngredientsByBase.getOrDefault(baseId, List.of());
    }

    public List<BaseReaction> exactBaseStarterCandidates(Identifier starterItemId) {
        return exactBaseStarters.getOrDefault(starterItemId, List.of());
    }

    public List<BaseReaction> taggedBaseStarterCandidates() {
        return taggedBaseStarters;
    }

    private static Map<Identifier, Map<Identifier, IngredientReaction>> freezeNestedMap(
            Map<Identifier, Map<Identifier, IngredientReaction>> source
    ) {
        Map<Identifier, Map<Identifier, IngredientReaction>> frozen = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> frozen.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue()))));
        return Collections.unmodifiableMap(frozen);
    }

    private static <T> Map<Identifier, List<T>> freezeListsByKey(
            Map<Identifier, List<T>> source,
            Comparator<T> order
    ) {
        Map<Identifier, List<T>> frozen = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    List<T> values = new ArrayList<>(entry.getValue());
                    values.sort(order);
                    frozen.put(entry.getKey(), List.copyOf(values));
                });
        return Collections.unmodifiableMap(frozen);
    }
}
