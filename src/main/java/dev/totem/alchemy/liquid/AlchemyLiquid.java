package dev.totem.alchemy.liquid;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Immutable identity + property definition for one Alchemy liquid.
 *
 * <p>Registration, datapack loading, container adapters, and mixed-liquid property resolution are
 * intentionally outside M8-T01.</p>
 */
public record AlchemyLiquid(
        Identifier id,
        LiquidProperties properties
) {
    public AlchemyLiquid {
        id = Objects.requireNonNull(id, "id");
        properties = Objects.requireNonNull(properties, "properties");
    }

    public static AlchemyLiquid neutral(Identifier id) {
        return new AlchemyLiquid(id, LiquidProperties.neutral());
    }
}
