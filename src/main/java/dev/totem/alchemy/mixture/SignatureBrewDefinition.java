package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Immutable signature brew datapack definition.
 *
 * <p>A definition is declarative only. The brewing scheduler must commit its
 * {@link SignatureBrewResolver.ReactionGroup} before any member is completed,
 * and the group settlement layer must suppress the ordinary member outputs.
 * Neither behavior is activated by merely loading a definition.</p>
 */
public record SignatureBrewDefinition(
        SignatureBrewResolver.Signature signature,
        boolean requiresHeat,
        Result result
) {
    public SignatureBrewDefinition {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(result, "result");
    }

    public Identifier id() {
        return signature.id();
    }

    public record Result(
            Type type,
            Identifier itemId,
            int count,
            Identifier containerItemId,
            Identifier potionId
    ) {
        public Result {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(itemId, "itemId");
            if (count < 1 || count > 64) {
                throw new IllegalArgumentException("Signature result count must be within [1, 64]");
            }
            if (type == Type.BOTTLED_ITEM && containerItemId == null) {
                throw new IllegalArgumentException("Bottled signature result needs a container item");
            }
            // A bottled recipe describes exactly one drink per unit of liquid; the
            // batch volume determines the number of available drinks, not item count.
            if (type == Type.BOTTLED_ITEM && count != 1) {
                throw new IllegalArgumentException("Bottled signature results must have count=1");
            }
            if (type == Type.DROP_ITEM && (containerItemId != null || potionId != null)) {
                throw new IllegalArgumentException("Drop-item signature result cannot specify a container or potion");
            }
        }
    }

    public enum Type {
        BOTTLED_ITEM,
        DROP_ITEM
    }
}
