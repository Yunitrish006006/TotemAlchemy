package dev.totem.alchemy.client.manual;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.manual.AlchemyMaterialCatalog;
import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import dev.totem.core.api.v1.manual.TotemManualPageFilterRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One researched brewing material per visible manual page. Hidden probabilities are never rendered. */
public final class AlchemyMaterialResearchOverlay {
    private static final int INK = 0xFF4B3826;
    private static final int MUTED = 0xFF765B3D;
    private static final int WARN = 0xFFA33A2B;

    // TotemCore renders normal book text at pageLeft + 36 with a 114 px text column.
    // Keep every Alchemy overlay element inside that same safe rectangle.
    private static final int CONTENT_LEFT = 36;
    private static final int CONTENT_WIDTH = 114;
    private static final int ITEM_TEXT_LEFT = 59;
    private static final int ITEM_TEXT_WIDTH = CONTENT_LEFT + CONTENT_WIDTH - ITEM_TEXT_LEFT;
    private static final int OUTCOME_ROW_HEIGHT = 17;
    private static final int CONTENT_BOTTOM = 176;

    private static final Identifier PAGE_FILTER_ID =
            Identifier.fromNamespaceAndPath("totem-alchemy", "researched_material_pages");
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
        TotemManualPageFilterRegistry.register(PAGE_FILTER_ID, AlchemyMaterialResearchOverlay::isPageVisible);
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-alchemy", "material_research_pages"),
                AlchemyMaterialResearchOverlay::render);
    }

    private static boolean isPageVisible(String pageKey) {
        AlchemyMaterialCatalog.Entry entry = AlchemyMaterialCatalog.byPageKey(pageKey);
        return entry == null || AlchemyResearchClientCache.isMaterialKnown(entry.item());
    }

    private static void render(TotemManualPageRenderContext context) {
        AlchemyMaterialCatalog.Entry materialPage = AlchemyMaterialCatalog.byPageKey(context.pageKey());
        if (materialPage == null) return;

        Item ingredient = materialPage.item();
        if (!AlchemyResearchClientCache.isMaterialKnown(ingredient)) return;

        int totalMaterials = AlchemyMaterialCatalog.entries().size();
        int knownMaterials = (int) AlchemyMaterialCatalog.entries().stream()
                .filter(entry -> AlchemyResearchClientCache.isMaterialKnown(entry.item()))
                .count();
        int remainingMaterials = Math.max(0, totalMaterials - knownMaterials);
        Component remaining = Component.translatable("book.totem_alchemy.research.compact_unknown")
                .append(Component.literal(": " + remainingMaterials + " / " + totalMaterials));
        text(context, remaining, CONTENT_LEFT, context.pageTop() + 31, CONTENT_WIDTH, MUTED);

        int ingredientY = context.pageTop() + 43;
        ItemStack ingredientStack = new ItemStack(ingredient);
        stack(context, ingredientStack, CONTENT_LEFT, ingredientY);
        text(context, ingredientStack.getHoverName(), ITEM_TEXT_LEFT, ingredientY + 4, ITEM_TEXT_WIDTH, INK);

        int samples = AlchemyResearchClientCache.samples(ingredient);
        text(context, Component.translatable("book.totem_alchemy.research.samples", samples),
                CONTENT_LEFT, context.pageTop() + 62, CONTENT_WIDTH, MUTED);

        var timeEstimate = AlchemyResearchClientCache.timeEstimate(ingredient);
        if (timeEstimate.isPresent()) {
            var estimate = timeEstimate.orElseThrow();
            Component processingTime = estimate.exact()
                    ? Component.translatable("book.totem_alchemy.research.processing_time_exact",
                            formatTenths(estimate.lowerTenths()))
                    : Component.translatable("book.totem_alchemy.research.processing_time_range",
                            formatTenths(estimate.lowerTenths()), formatTenths(estimate.upperTenths()));
            text(context, processingTime,
                    CONTENT_LEFT, context.pageTop() + 73, CONTENT_WIDTH, MUTED);
            text(context, Component.translatable(
                            "book.totem_alchemy.research.processing_time_accuracy", estimate.accuracyPercent()),
                    CONTENT_LEFT, context.pageTop() + 84, CONTENT_WIDTH, MUTED);
        } else {
            text(context, Component.translatable("book.totem_alchemy.research.processing_time_unknown"),
                    CONTENT_LEFT, context.pageTop() + 73, CONTENT_WIDTH, MUTED);
        }

        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        List<MultiOutcomeBrewing.Outcome> outcomes = MultiOutcomeBrewing.outcomesFor(new ItemStack(ingredient), awkward);
        int y = context.pageTop() + 97;
        if (outcomes.isEmpty()) {
            String noteKey = MATERIAL_NOTES.get(ingredient);
            if (noteKey != null) {
                text(context, Component.translatable(noteKey), CONTENT_LEFT, y + 4, CONTENT_WIDTH, INK);
            }
            return;
        }

        for (MultiOutcomeBrewing.Outcome outcome : outcomes) {
            renderOutcome(context, ingredient, outcome, y);
            y += OUTCOME_ROW_HEIGHT;
        }

        if (AlchemyResearchClientCache.hasNoEffectObservation(ingredient)) {
            renderNoEffect(context, ingredient, y);
        }
    }

    private static void renderOutcome(
            TotemManualPageRenderContext context,
            Item ingredient,
            MultiOutcomeBrewing.Outcome outcome,
            int y
    ) {
        boolean discovered = AlchemyDiscoveryClientCache.has(ingredient, outcome.potion());
        if (discovered) {
            stack(context, PotionContents.createItemStack(Items.POTION, outcome.potion()), CONTENT_LEFT, y);
            text(context, Component.translatable(outcome.messageKey()), ITEM_TEXT_LEFT, y,
                    ITEM_TEXT_WIDTH, INK);
            Component detail = Component.translatable(AlchemyResearchClientCache.frequencyKey(ingredient, outcome.potion()))
                    .append(Component.literal(" · "))
                    .append(Component.translatable(AlchemyResearchClientCache.tierKey(ingredient, outcome.potion())));
            text(context, detail, ITEM_TEXT_LEFT, y + 8, ITEM_TEXT_WIDTH, MUTED);
            return;
        }

        stack(context, new ItemStack(Items.GLASS_BOTTLE), CONTENT_LEFT, y);
        context.graphics().text(context.font(), "?",
                context.pageLeft() + CONTENT_LEFT + 6, y + 4, WARN, false);
        text(context, Component.translatable("book.totem_alchemy.research.unknown_effect"),
                ITEM_TEXT_LEFT, y + 4, ITEM_TEXT_WIDTH, WARN);
    }

    private static void renderNoEffect(TotemManualPageRenderContext context, Item ingredient, int y) {
        stack(context, new ItemStack(Items.GLASS_BOTTLE), CONTENT_LEFT, y);
        text(context, Component.translatable("book.totem_alchemy.research.no_effect"),
                ITEM_TEXT_LEFT, y, ITEM_TEXT_WIDTH, INK);

        // Four-effect materials can already consume the full lower half of the page. Keep the
        // no-effect observation visible, but only add its secondary detail when it still fits.
        if (y + 17 > context.pageTop() + CONTENT_BOTTOM) {
            return;
        }
        Component detail = Component.translatable(AlchemyResearchClientCache.noEffectFrequencyKey(ingredient))
                .append(Component.literal(" · "))
                .append(Component.translatable(AlchemyResearchClientCache.noEffectTierKey(ingredient)));
        text(context, detail, ITEM_TEXT_LEFT, y + 8, ITEM_TEXT_WIDTH, MUTED);
    }

    private static void text(
            TotemManualPageRenderContext context,
            Component component,
            int localX,
            int y,
            int maxWidth,
            int color
    ) {
        context.graphics().text(context.font(), clipped(context, component, maxWidth),
                context.pageLeft() + localX, y, color, false);
    }

    private static String clipped(TotemManualPageRenderContext context, Component component, int maxWidth) {
        String value = component.getString();
        if (context.font().width(value) <= maxWidth) return value;
        int ellipsisWidth = context.font().width("…");
        return context.font().plainSubstrByWidth(value, Math.max(1, maxWidth - ellipsisWidth)) + "…";
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
}
