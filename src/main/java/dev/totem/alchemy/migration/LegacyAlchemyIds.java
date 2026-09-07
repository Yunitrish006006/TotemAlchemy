package dev.totem.alchemy.migration;

import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * One-way identifier rewrite for Alchemy data written before the module split.
 *
 * <p>This class is deliberately a decoder helper, not a registry alias. New
 * gameplay registers and writes only {@code totem:alchemy/*}; callers use this
 * when reading persisted Alchemy-owned strings so the next save is canonical.
 * World-level registry data is migrated by the compatibility loader before it
 * reaches this module.</p>
 */
public final class LegacyAlchemyIds {
    public static final String LEGACY_NAMESPACE = "deadrecall";
    public static final String CANONICAL_NAMESPACE = "totem";
    public static final String CANONICAL_PREFIX = "alchemy/";

    private static final Set<String> OWNED_PATHS = Set.of(
            "alchemy_cauldron",
            "pig_manure_dirt",
            "pig_manure_grass_block",
            "pig_manure_coarse_dirt",
            "pig_manure_rooted_dirt",
            "pig_manure_podzol",
            "pig_manure_mycelium",
            "pig_manure_mud",
            "pig_manure_layer",
            "saltpeter",
            "pig_manure",
            "wood_ash",
            "cocoa_powder",
            "hot_cocoa",
            "cherry_brew",
            "stone_bowl",
            "sulfur_bowl",
            "large_potion_flask",
            "stinky",
            "cherry_bloom",
            "firefly_strength",
            "saturation",
            "strong_saturation",
            "resistance",
            "long_resistance",
            "strong_resistance",
            "cherry_swiftness",
            "long_cherry_swiftness",
            "strong_cherry_swiftness",
            "long_firefly_strength",
            "strong_firefly_strength",
            "flint_from_bowl",
            "gunpowder_from_alchemy",
            "wood_ash_from_hay_block_smelting",
            "pig_manure_hit_entity",
            "pig_manure_got_hit",
            "brew_discoveries",
            "brew_research",
            "material_discovered",
            "alchemy_auto_record_brewing_materials"
    );

    private LegacyAlchemyIds() {
    }

    /** Returns the canonical Alchemy ID only for an owned legacy ID. */
    public static Identifier canonicalize(Identifier id) {
        if (id == null || !LEGACY_NAMESPACE.equals(id.getNamespace()) || !OWNED_PATHS.contains(id.getPath())) {
            return id;
        }
        return Identifier.fromNamespaceAndPath(CANONICAL_NAMESPACE, CANONICAL_PREFIX + id.getPath());
    }

    /** Rewrites a standalone serialized identifier if it is one of Alchemy's legacy IDs. */
    public static String canonicalize(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return rawId;
        }
        Identifier parsed = Identifier.tryParse(rawId);
        Identifier canonical = canonicalize(parsed);
        return canonical == null ? rawId : canonical.toString();
    }

    /**
     * Rewrites only owned identifier occurrences in structured string fields
     * such as mixture provenance and reaction IDs. It intentionally leaves
     * third-party {@code deadrecall:*} data untouched.
     */
    public static String canonicalizeEmbedded(String value) {
        if (value == null || value.isBlank() || !value.contains(LEGACY_NAMESPACE + ":")) {
            return value;
        }
        String rewritten = value;
        for (String path : OWNED_PATHS) {
            rewritten = rewritten.replace(LEGACY_NAMESPACE + ":" + path,
                    CANONICAL_NAMESPACE + ":" + CANONICAL_PREFIX + path);
        }
        return rewritten;
    }
}
