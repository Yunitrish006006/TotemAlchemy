package dev.totem.alchemy.manual;

import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single ordered source of truth for materials represented in Alchemy research.
 * Each material owns one stable manual page key. Client-side discovery filters decide
 * whether that page is currently visible, so unknown materials never occupy blank pages.
 */
public final class AlchemyMaterialCatalog {
    /** Kept for source compatibility; the canonical layout is one material per page. */
    public static final int MATERIALS_PER_PAGE = 1;

    private static final List<Entry> ENTRIES = List.of(
            entry("nether_wart", Items.NETHER_WART),
            entry("red_mushroom", Items.RED_MUSHROOM),
            entry("brown_mushroom", Items.BROWN_MUSHROOM),
            entry("spider_eye", Items.SPIDER_EYE),
            entry("fermented_spider_eye", Items.FERMENTED_SPIDER_EYE),
            entry("sugar", Items.SUGAR),
            entry("rabbit_foot", Items.RABBIT_FOOT),
            entry("magma_cream", Items.MAGMA_CREAM),
            entry("glistering_melon_slice", Items.GLISTERING_MELON_SLICE),
            entry("golden_carrot", Items.GOLDEN_CARROT),
            entry("blaze_powder", Items.BLAZE_POWDER),
            entry("blaze_rod", Items.BLAZE_ROD),
            entry("ghast_tear", Items.GHAST_TEAR),
            entry("pufferfish", Items.PUFFERFISH),
            entry("turtle_helmet", Items.TURTLE_HELMET),
            entry("phantom_membrane", Items.PHANTOM_MEMBRANE),
            entry("breeze_rod", Items.BREEZE_ROD),
            entry("slime_block", Items.SLIME_BLOCK),
            entry("slime_ball", Items.SLIME_BALL),
            entry("stone", Items.STONE),
            entry("cobweb", Items.COBWEB),
            entry("string", Items.STRING),
            entry("feather", Items.FEATHER),
            entry("leather", Items.LEATHER),
            entry("rabbit_hide", Items.RABBIT_HIDE),
            entry("shulker_shell", Items.SHULKER_SHELL),
            entry("ink_sac", Items.INK_SAC),
            entry("glow_ink_sac", Items.GLOW_INK_SAC),
            entry("nautilus_shell", Items.NAUTILUS_SHELL),
            entry("ender_pearl", Items.ENDER_PEARL),
            entry("chorus_fruit", Items.CHORUS_FRUIT),
            entry("rotten_flesh", Items.ROTTEN_FLESH),
            entry("egg", Items.EGG),
            entry("bone_meal", Items.BONE_MEAL),
            entry("charcoal", Items.CHARCOAL),
            entry("resin_clump", Items.RESIN_CLUMP),
            entry("honey_block", Items.HONEY_BLOCK),
            entry("honeycomb", Items.HONEYCOMB),
            entry("melon_slice", Items.MELON_SLICE),
            entry("apple", Items.APPLE),
            entry("sweet_berries", Items.SWEET_BERRIES),
            entry("glow_berries", Items.GLOW_BERRIES),
            entry("honey_bottle", Items.HONEY_BOTTLE),
            entry("golden_apple", Items.GOLDEN_APPLE),
            entry("enchanted_golden_apple", Items.ENCHANTED_GOLDEN_APPLE),
            entry("wheat", Items.WHEAT),
            entry("kelp", Items.KELP),
            entry("sugar_cane", Items.SUGAR_CANE),
            entry("cactus", Items.CACTUS),
            entry("bamboo", Items.BAMBOO),
            entry("cocoa_beans", Items.COCOA_BEANS),
            entry("cherry_leaves", Items.CHERRY_LEAVES),
            entry("firefly_bush", Items.FIREFLY_BUSH),
            entry("pig_manure", AlchemyItems.PIG_MANURE),
            entry("wood_ash", AlchemyItems.WOOD_ASH),
            entry("cocoa_powder", AlchemyItems.COCOA_POWDER),
            entry("redstone", Items.REDSTONE),
            entry("glowstone_dust", Items.GLOWSTONE_DUST),
            entry("gunpowder", Items.GUNPOWDER),
            entry("dragon_breath", Items.DRAGON_BREATH)
    );
    private static final Map<String, Entry> BY_PAGE_KEY = buildByPageKey();
    private static final List<String> PAGE_KEYS = ENTRIES.stream().map(Entry::pageKey).toList();

    private AlchemyMaterialCatalog() {}

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** Returns all canonical one-material page keys in deterministic catalog order. */
    public static List<String> pageKeys() {
        return PAGE_KEYS;
    }

    public static int pageCount() {
        return PAGE_KEYS.size();
    }

    /** Compatibility helper: a canonical material page always represents exactly one entry. */
    public static List<Entry> entriesForPage(String pageKey) {
        Entry entry = byPageKey(pageKey);
        return entry == null ? List.of() : List.of(entry);
    }

    public static Entry byPageKey(String pageKey) {
        return pageKey == null ? null : BY_PAGE_KEY.get(pageKey);
    }

    public static boolean contains(Item item) {
        return ENTRIES.stream().anyMatch(entry -> entry.item() == item);
    }

    private static Entry entry(String id, Item item) {
        return new Entry(id, item, "book.totem_alchemy.material_slot." + id);
    }

    private static Map<String, Entry> buildByPageKey() {
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            Entry previous = result.put(entry.pageKey(), entry);
            if (previous != null) {
                throw new IllegalStateException("Duplicate Alchemy material page key: " + entry.pageKey());
            }
        }
        return Map.copyOf(result);
    }

    public record Entry(String id, Item item, String pageKey) {}
}
