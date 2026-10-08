package dev.totem.alchemy.liquid;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in Alchemy liquid definitions keyed by the same identifiers used by LiquidComposition.
 *
 * <p>This is a chemistry property table rather than a Minecraft object registry. M8-T02 intentionally
 * registers only Water; later M8 tasks add Milk, Honey, weighted resolution, and runtime application.</p>
 */
public final class AlchemyLiquids {
    public static final Identifier WATER_ID = Identifier.fromNamespaceAndPath("minecraft", "water");

    private static final Map<Identifier, AlchemyLiquid> REGISTRY = new LinkedHashMap<>();

    public static final AlchemyLiquid WATER = register(AlchemyLiquid.neutral(WATER_ID));

    private AlchemyLiquids() {
    }

    public static Optional<AlchemyLiquid> get(Identifier id) {
        return id == null ? Optional.empty() : Optional.ofNullable(REGISTRY.get(id));
    }

    public static Map<Identifier, AlchemyLiquid> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    private static AlchemyLiquid register(AlchemyLiquid liquid) {
        AlchemyLiquid previous = REGISTRY.putIfAbsent(liquid.id(), liquid);
        if (previous != null) {
            throw new IllegalStateException("Duplicate Alchemy liquid id: " + liquid.id());
        }
        return liquid;
    }
}
