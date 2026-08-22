package dev.totem.alchemy.client.manual;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.manual.AlchemyMaterialCatalog;
import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact discovery journal: several materials share each manual page. */
public final class AlchemyMaterialResearchOverlay {
    private static final int INK = 0xFF4B3826;
    private static final int MUTED = 0xFF765B3D;
    private static final int WARN = 0xFFA33A2B;
    private static final int ROW_HEIGHT = 22;
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
        List<AlchemyMaterialCatalog.Entry> materials = AlchemyMaterialCatalog.entriesForPage(context.pageKey());
        if (materials.isEmpty()) return;

        int left = context.pageLeft() + 18;
        int y = context.pageTop() + 18;
        context.graphics().text(context.font(), Component.translatable("book.totem_alchemy.research.compact_title"),
                left, y, INK, false);
        y += 16;

        for (AlchemyMaterialCatalog.Entry entry : materials) {
            renderMaterialRow(context, entry, y);
            y += ROW_HEIGHT;
        }
    }

    private static void renderMaterialRow(
            TotemManualPageRenderContext context,
            AlchemyMaterialCatalog.Entry entry,
            int y
    ) {
        Item ingredient = entry.item();
        int localIconX = 18;
        int textX = context.pageLeft() + 40;

        if (!AlchemyResearchClientCache.isMaterialKnown(ingredient)) {
            context.graphics().text(context.font(), "?", context.pageLeft() + localIconX + 5, y + 4, WARN, false);
            context.graphics().text(context.font(), Component.translatable("book.totem_alchemy.research.compact_unknown"),
                    textX, y + 4, WARN, false);
            return;
        }

        ItemStack ingredientStack = new ItemStack(ingredient);
        stack(context, ingredientStack, localIconX, y);

        String samples = "×" + AlchemyResearchClientCache.samples(ingredient);
        int samplesX = context.pageLeft() + 169 - context.font().width(samples);
        int nameWidth = Math.max(30, samplesX - textX - 4);
        context.graphics().text(context.font(), clipped(context, ingredientStack.getHoverName(), nameWidth),
                textX, y, INK, false);
        context.graphics().text(context.font(), samples, samplesX, y, MUTED, false);

        Component detail = compactDetail(ingredient);
        context.graphics().text(context.font(), clipped(context, detail, 129),
                textX, y + 10, MUTED, false);
    }

    private static Component compactDetail(Item ingredient) {
        String noteKey = MATERIAL_NOTES.get(ingredient);
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        List<MultiOutcomeBrewing.Outcome> outcomes = MultiOutcomeBrewing.outcomesFor(new ItemStack(ingredient), awkward);

        Component time = compactTime(ingredient);
        if (outcomes.isEmpty()) {
            if (noteKey == null) return time;
            return Component.empty().append(time)
                    .append(Component.literal(" · "))
                    .append(Component.translatable(noteKey));
        }

        List<String> effects = new ArrayList<>(outcomes.size() + 1);
        for (MultiOutcomeBrewing.Outcome outcome : outcomes) {
            effects.add(AlchemyDiscoveryClientCache.has(ingredient, outcome.potion())
                    ? Component.translatable(outcome.messageKey()).getString()
                    : "?");
        }
        if (AlchemyResearchClientCache.hasNoEffectObservation(ingredient)) {
            effects.add(Component.translatable("book.totem_alchemy.research.no_effect").getString());
        }

        return Component.empty().append(time)
                .append(Component.literal(" · "))
                .append(Component.literal(String.join(" / ", effects)));
    }

    private static Component compactTime(Item ingredient) {
        var estimate = AlchemyResearchClientCache.timeEstimate(ingredient);
        if (estimate.isEmpty()) {
            return Component.translatable("book.totem_alchemy.research.compact_time_unknown");
        }
        var value = estimate.orElseThrow();
        if (value.exact()) {
            return Component.translatable("book.totem_alchemy.research.compact_time_exact",
                    formatTenths(value.lowerTenths()));
        }
        return Component.translatable("book.totem_alchemy.research.compact_time_range",
                formatTenths(value.lowerTenths()), formatTenths(value.upperTenths()));
    }

    private static String clipped(TotemManualPageRenderContext context, Component component, int maxWidth) {
        String text = component.getString();
        if (context.font().width(text) <= maxWidth) return text;
        int ellipsis = context.font().width("…");
        return context.font().plainSubstrByWidth(text, Math.max(1, maxWidth - ellipsis)) + "…";
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
