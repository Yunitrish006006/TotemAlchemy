package dev.totem.alchemy.liquid;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in Alchemy liquid definitions keyed by the same identifiers used by LiquidComposition.
 *
 * <p>This is a chemistry property table rather than a Minecraft object registry. M8-T02 through M8-T04
 * register the built-in Water, Milk, and Honey identities; later M8 tasks add weighted resolution and runtime application.</p>
 */
public final class AlchemyLiquids {
    public static final Identifier WATER_ID = Identifier.fromNamespaceAndPath("minecraft", "water");
    public static final Identifier MILK_ID = Identifier.fromNamespaceAndPath("minecraft", "milk");
    public static final Identifier HONEY_ID = Identifier.fromNamespaceAndPath("minecraft", "honey");

    private static final Map<Identifier, AlchemyLiquid> REGISTRY = new LinkedHashMap<>();

    public static final AlchemyLiquid WATER = register(AlchemyLiquid.neutral(WATER_ID));
    public static final AlchemyLiquid MILK = register(AlchemyLiquid.neutral(MILK_ID));
    public static final AlchemyLiquid HONEY = register(AlchemyLiquid.neutral(HONEY_ID));

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
