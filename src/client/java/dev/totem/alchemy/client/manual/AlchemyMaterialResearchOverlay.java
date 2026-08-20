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
import java.util.Map;

/** One material per manual page. True and observed numeric probabilities are deliberately never rendered. */
public final class AlchemyMaterialResearchOverlay {
    private static final int INK = 0xFF4B3826;
    private static final int MUTED = 0xFF765B3D;
    private static final int WARN = 0xFFA33A2B;
    private static final Map<String, Item> MATERIAL_PAGES = createMaterialPages();

    private AlchemyMaterialResearchOverlay() {
    }

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-alchemy", "material_research_pages"),
                AlchemyMaterialResearchOverlay::render
        );
    }

    private static void render(TotemManualPageRenderContext context) {
        Item ingredient = MATERIAL_PAGES.get(context.pageKey());
        if (ingredient == null) {
            return;
        }
        int top = context.pageTop() + 24;
        ItemStack ingredientStack = new ItemStack(ingredient);
        stack(context, ingredientStack, 18, top);
        context.graphics().text(context.font(), ingredientStack.getHoverName(),
                context.pageLeft() + 40, top + 4, INK, false);

        int samples = AlchemyResearchClientCache.samples(ingredient);
        context.graphics().text(context.font(), Component.translatable(
                        "book.totem_alchemy.research.samples", samples),
                context.pageLeft() + 18, top + 24, MUTED, false);

        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        List<MultiOutcomeBrewing.Outcome> outcomes = MultiOutcomeBrewing.outcomesFor(ingredientStack, awkward);
        int y = top + 48;
        for (MultiOutcomeBrewing.Outcome outcome : outcomes) {
            boolean discovered = AlchemyDiscoveryClientCache.has(ingredient, outcome.potion());
            if (discovered) {
                ItemStack potion = PotionContents.createItemStack(Items.POTION, outcome.potion());
                stack(context, potion, 20, y);
                context.graphics().text(context.font(), Component.translatable(outcome.messageKey()),
                        context.pageLeft() + 43, y, INK, false);
                Component detail = Component.translatable(
                                AlchemyResearchClientCache.frequencyKey(ingredient, outcome.potion()))
                        .append(Component.literal(" · "))
                        .append(Component.translatable(
                                AlchemyResearchClientCache.tierKey(ingredient, outcome.potion())));
                context.graphics().text(context.font(), detail,
                        context.pageLeft() + 43, y + 11, MUTED, false);
            } else {
                stack(context, new ItemStack(Items.GLASS_BOTTLE), 20, y);
                context.graphics().text(context.font(), "?", context.pageLeft() + 26, y + 4, WARN, false);
                context.graphics().text(context.font(), Component.translatable(
                                "book.totem_alchemy.research.unknown_effect"),
                        context.pageLeft() + 43, y + 5, WARN, false);
            }
            y += 38;
        }
    }

    private static void stack(TotemManualPageRenderContext context, ItemStack stack, int localX, int y) {
        int x = context.pageLeft() + localX;
        context.graphics().item(stack, x, y);
        if (context.mouseX() >= x && context.mouseX() < x + 16
                && context.mouseY() >= y && context.mouseY() < y + 16) {
            context.graphics().setTooltipForNextFrame(context.font(), stack, context.mouseX(), context.mouseY());
        }
    }

    private static Map<String, Item> createMaterialPages() {
        Map<String, Item> pages = new LinkedHashMap<>();
        pages.put("book.totem_alchemy.material.spider_eye", Items.SPIDER_EYE);
        pages.put("book.totem_alchemy.material.red_mushroom", Items.RED_MUSHROOM);
        pages.put("book.totem_alchemy.material.glistering_melon_slice", Items.GLISTERING_MELON_SLICE);
        pages.put("book.totem_alchemy.material.sugar", Items.SUGAR);
        pages.put("book.totem_alchemy.material.rabbit_foot", Items.RABBIT_FOOT);
        pages.put("book.totem_alchemy.material.magma_cream", Items.MAGMA_CREAM);
        pages.put("book.totem_alchemy.material.golden_carrot", Items.GOLDEN_CARROT);
        pages.put("book.totem_alchemy.material.blaze_powder", Items.BLAZE_POWDER);
        pages.put("book.totem_alchemy.material.ghast_tear", Items.GHAST_TEAR);
        pages.put("book.totem_alchemy.material.pufferfish", Items.PUFFERFISH);
        pages.put("book.totem_alchemy.material.turtle_helmet", Items.TURTLE_HELMET);
        pages.put("book.totem_alchemy.material.phantom_membrane", Items.PHANTOM_MEMBRANE);
        pages.put("book.totem_alchemy.material.breeze_rod", Items.BREEZE_ROD);
        pages.put("book.totem_alchemy.material.slime_block", Items.SLIME_BLOCK);
        pages.put("book.totem_alchemy.material.stone", Items.STONE);
        pages.put("book.totem_alchemy.material.cobweb", Items.COBWEB);
        pages.put("book.totem_alchemy.material.fermented_spider_eye", Items.FERMENTED_SPIDER_EYE);
        return Map.copyOf(pages);
    }
}
