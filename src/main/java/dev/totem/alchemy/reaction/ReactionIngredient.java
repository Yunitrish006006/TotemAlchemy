package dev.totem.alchemy.reaction;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** A data-only item or item-tag selector used by alchemy reactions. */
public record ReactionIngredient(Kind kind, Identifier id) {
    public ReactionIngredient {
        kind = Objects.requireNonNull(kind, "kind");
        id = Objects.requireNonNull(id, "id");
    }

    public static ReactionIngredient item(Identifier id) {
        return new ReactionIngredient(Kind.ITEM, id);
    }

    public static ReactionIngredient tag(Identifier id) {
        return new ReactionIngredient(Kind.TAG, id);
    }

    public enum Kind {
        ITEM,
        TAG
    }
}
