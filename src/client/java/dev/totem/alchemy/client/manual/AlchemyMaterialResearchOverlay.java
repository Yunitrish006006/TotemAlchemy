package dev.totem.alchemy.client.manual;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One brewing material per manual page. Hidden true numeric probabilities are deliberately never rendered. */
public final class AlchemyMaterialResearchOverlay {
    private static final int INK = 0xFF4B3826;
    private static final int MUTED = 0xFF765B3D;
    private static final int WARN = 0xFFA33A2B;
    private static final Map<String, MaterialPage> MATERIAL_PAGES = createMaterialPages();
    private static final Map<Item, String> MATERIAL_NOTES = Map.ofEntries(
            Map.entry(Items.NETHER_WART, "book.totem_alchemy.research.note.base"),
            Map.entry(Items.REDSTONE, "book.totem_alchemy.research.note.extend"),
            Map.entry(Items.GLOWSTONE_DUST, "book.totem_alchemy.research.note.strengthen"),
            Map.entry(Items.GUNPOWDER, "book.totem_alchemy.research.note.splash"),
            Map.entry(Items.DRAGON_BREATH, "book.totem_alchemy.research.note.lingering"),
            Map.entry(Items.CHERRY_LEAVES, "book.totem_alchemy.research.note.cherry_variant"),
            Map.entry(Items.FIREFLY_BUSH, "book.totem_alchemy.research.note.firefly_variant")
    );

    private AlchemyMaterialResearchOverlay() {}

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-alchemy", "material_research_pages"),
                AlchemyMaterialResearchOverlay::render);
    }

    private static void render(TotemManualPageRenderContext context) {
        MaterialPage materialPage = MATERIAL_PAGES.get(context.pageKey());
        if (materialPage == null) return;
        Item ingredient = materialPage.ingredient();
        int top = context.pageTop() + 24;

        if (!AlchemyResearchClientCache.isMaterialKnown(ingredient)) {
            context.graphics().text(context.font(), "?",
                    context.pageLeft() + 20, top + 4, WARN, false);
            return;
        }

        stack(context, new ItemStack(ingredient), 18, top);
        context.graphics().text(context.font(), Component.translatable(materialPage.nameKey()),
                context.pageLeft() + 43, top + 4, INK, false);

        int samples = AlchemyResearchClientCache.samples(ingredient);
        context.graphics().text(context.font(), Component.translatable(
                        "book.totem_alchemy.research.samples", samples),
                context.pageLeft() + 18, top + 24, MUTED, false);

        var timeEstimate = AlchemyResearchClientCache.timeEstimate(ingredient);
        if (timeEstimate.isPresent()) {
            var estimate = timeEstimate.orElseThrow();
            Component processingTime = estimate.exact()
                    ? Component.translatable("book.totem_alchemy.research.processing_time_exact",
                            formatTenths(estimate.lowerTenths()))
                    : Component.translatable("book.totem_alchemy.research.processing_time_range",
                            formatTenths(estimate.lowerTenths()), formatTenths(estimate.upperTenths()));
            context.graphics().text(context.font(), processingTime,
                    context.pageLeft() + 18, top + 36, MUTED, false);
            context.graphics().text(context.font(), Component.translatable(
                            "book.totem_alchemy.research.processing_time_accuracy", estimate.accuracyPercent()),
                    context.pageLeft() + 18, top + 48, MUTED, false);
        } else {
            context.graphics().text(context.font(), Component.translatable(
                            "book.totem_alchemy.research.processing_time_unknown"),
                    context.pageLeft() + 18, top + 36, MUTED, false);
        }

        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        List<MultiOutcomeBrewing.Outcome> outcomes = MultiOutcomeBrewing.outcomesFor(new ItemStack(ingredient), awkward);
        int y = top + 62;
        if (outcomes.isEmpty()) {
            String noteKey = MATERIAL_NOTES.get(ingredient);
            if (noteKey != null) {
                context.graphics().text(context.font(), Component.translatable(noteKey),
                        context.pageLeft() + 18, y + 4, INK, false);
            }
            return;
        }

        for (MultiOutcomeBrewing.Outcome outcome : outcomes) {
            boolean discovered = AlchemyDiscoveryClientCache.has(ingredient, outcome.potion());
            if (discovered) {
                stack(context, PotionContents.createItemStack(Items.POTION, outcome.potion()), 20, y);
                context.graphics().text(context.font(), Component.translatable(outcome.messageKey()),
                        context.pageLeft() + 43, y, INK, false);
                Component detail = Component.translatable(AlchemyResearchClientCache.frequencyKey(ingredient, outcome.potion()))
                        .append(Component.literal(" · "))
                        .append(Component.translatable(AlchemyResearchClientCache.tierKey(ingredient, outcome.potion())));
                context.graphics().text(context.font(), detail,
                        context.pageLeft() + 43, y + 11, MUTED, false);
            } else {
                stack(context, new ItemStack(Items.GLASS_BOTTLE), 20, y);
                context.graphics().text(context.font(), "?", context.pageLeft() + 26, y + 4, WARN, false);
                context.graphics().text(context.font(), Component.translatable("book.totem_alchemy.research.unknown_effect"),
                        context.pageLeft() + 43, y + 5, WARN, false);
            }
            y += 25;
        }

        if (AlchemyResearchClientCache.hasNoEffectObservation(ingredient)) {
            stack(context, new ItemStack(Items.GLASS_BOTTLE), 20, y);
            context.graphics().text(context.font(), Component.translatable("book.totem_alchemy.research.no_effect"),
                    context.pageLeft() + 43, y, INK, false);
            Component detail = Component.translatable(AlchemyResearchClientCache.noEffectFrequencyKey(ingredient))
                    .append(Component.literal(" · "))
                    .append(Component.translatable(AlchemyResearchClientCache.noEffectTierKey(ingredient)));
            context.graphics().text(context.font(), detail,
                    context.pageLeft() + 43, y + 11, MUTED, false);
        }
    }

    private static void stack(TotemManualPageRenderContext context, ItemStack stack, int localX, int y) {
        int x = context.pageLeft() + localX;
        context.graphics().item(stack, x, y);
        if (context.mouseX() >= x && context.mouseX() < x + 16 && context.mouseY() >= y && context.mouseY() < y + 16) {
            context.graphics().setTooltipForNextFrame(context.font(), stack, context.mouseX(), context.mouseY());
        }
    }

    private static String formatTenths(int tenths) {
        return tenths % 10 == 0 ? Integer.toString(tenths / 10) : String.format(Locale.ROOT, "%.1f", tenths / 10.0D);
    }

    private static Map<String, MaterialPage> createMaterialPages() {
        Map<String, MaterialPage> pages = new LinkedHashMap<>();
        addMaterialPage(pages, "nether_wart", Items.NETHER_WART);
        addMaterialPage(pages, "red_mushroom", Items.RED_MUSHROOM);
        addMaterialPage(pages, "spider_eye", Items.SPIDER_EYE);
        addMaterialPage(pages, "fermented_spider_eye", Items.FERMENTED_SPIDER_EYE);
        addMaterialPage(pages, "sugar", Items.SUGAR);
        addMaterialPage(pages, "rabbit_foot", Items.RABBIT_FOOT);
        addMaterialPage(pages, "magma_cream", Items.MAGMA_CREAM);
        addMaterialPage(pages, "glistering_melon_slice", Items.GLISTERING_MELON_SLICE);
        addMaterialPage(pages, "golden_carrot", Items.GOLDEN_CARROT);
        addMaterialPage(pages, "blaze_powder", Items.BLAZE_POWDER);
        addMaterialPage(pages, "ghast_tear", Items.GHAST_TEAR);
        addMaterialPage(pages, "pufferfish", Items.PUFFERFISH);
        addMaterialPage(pages, "turtle_helmet", Items.TURTLE_HELMET);
        addMaterialPage(pages, "phantom_membrane", Items.PHANTOM_MEMBRANE);
        addMaterialPage(pages, "breeze_rod", Items.BREEZE_ROD);
        addMaterialPage(pages, "slime_block", Items.SLIME_BLOCK);
        addMaterialPage(pages, "stone", Items.STONE);
        addMaterialPage(pages, "cobweb", Items.COBWEB);
        addMaterialPage(pages, "melon_slice", Items.MELON_SLICE);
        addMaterialPage(pages, "apple", Items.APPLE);
        addMaterialPage(pages, "sweet_berries", Items.SWEET_BERRIES);
        addMaterialPage(pages, "glow_berries", Items.GLOW_BERRIES);
        addMaterialPage(pages, "honey_bottle", Items.HONEY_BOTTLE);
        addMaterialPage(pages, "golden_apple", Items.GOLDEN_APPLE);
        addMaterialPage(pages, "enchanted_golden_apple", Items.ENCHANTED_GOLDEN_APPLE);
        addMaterialPage(pages, "cherry_leaves", Items.CHERRY_LEAVES);
        addMaterialPage(pages, "firefly_bush", Items.FIREFLY_BUSH);
        addMaterialPage(pages, "redstone", Items.REDSTONE);
        addMaterialPage(pages, "glowstone_dust", Items.GLOWSTONE_DUST);
        addMaterialPage(pages, "gunpowder", Items.GUNPOWDER);
        addMaterialPage(pages, "dragon_breath", Items.DRAGON_BREATH);
        return Map.copyOf(pages);
    }

    private static void addMaterialPage(Map<String, MaterialPage> pages, String id, Item ingredient) {
        pages.put("book.totem_alchemy.material_slot." + id,
                new MaterialPage(ingredient, "book.totem_alchemy.material." + id));
    }

    private record MaterialPage(Item ingredient, String nameKey) {}
}
