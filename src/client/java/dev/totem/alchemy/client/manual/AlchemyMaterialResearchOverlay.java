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
        context.graphics().text(context.font(), remaining,
                context.pageLeft() + 18, context.pageTop() + 12, MUTED, false);

        int top = context.pageTop() + 30;
        ItemStack ingredientStack = new ItemStack(ingredient);
        stack(context, ingredientStack, 18, top);
        context.graphics().text(context.font(), ingredientStack.getHoverName(),
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
}
