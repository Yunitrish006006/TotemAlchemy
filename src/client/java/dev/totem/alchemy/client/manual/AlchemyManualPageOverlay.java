package dev.totem.alchemy.client.manual;

import dev.totem.alchemy.registry.AlchemyItems;
import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Visual overlay retained only for the concrete Alchemy Cauldron recipe reference page. */
public final class AlchemyManualPageOverlay {
    private static final String CAULDRON_RECIPE_PAGE = "book.deadrecall.alchemy_manual.page.8";
    private static final int MUTED = 0xFF765B3D;

    private AlchemyManualPageOverlay() {
    }

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-alchemy", "manual_ingredients"),
                AlchemyManualPageOverlay::render
        );
    }

    private static void render(TotemManualPageRenderContext context) {
        if (CAULDRON_RECIPE_PAGE.equals(context.pageKey())) {
            renderCauldronRecipes(context);
        }
    }

    private static void renderCauldronRecipes(TotemManualPageRenderContext context) {
        int y = context.pageTop() + 40;
        recipe(context,
                List.of(new ItemStack(AlchemyItems.WOOD_ASH), new ItemStack(Items.RED_MUSHROOM),
                        new ItemStack(AlchemyItems.PIG_MANURE)),
                new ItemStack(AlchemyItems.SALTPETER), y);
        centeredClipped(context, Component.translatable(
                "book.deadrecall.alchemy_manual.diagram.saltpeter"), y + 21, MUTED, 140);

        y += 40;
        recipe(context,
                List.of(new ItemStack(Items.MILK_BUCKET), new ItemStack(AlchemyItems.COCOA_POWDER),
                        new ItemStack(Items.SUGAR)),
                new ItemStack(AlchemyItems.HOT_COCOA), y);
        centeredClipped(context, Component.translatable(
                "book.deadrecall.alchemy_manual.diagram.hot_cocoa"), y + 21, MUTED, 150);

        y += 40;
        recipe(context,
                List.of(new ItemStack(Items.SUGAR), new ItemStack(Items.CHERRY_LEAVES),
                        new ItemStack(Items.GLOW_BERRIES), new ItemStack(Items.SWEET_BERRIES)),
                new ItemStack(AlchemyItems.CHERRY_BREW), y);
        centeredClipped(context, Component.translatable(
                "book.deadrecall.alchemy_manual.diagram.cherry_brew"), y + 21, MUTED, 150);
    }

    private static void recipe(TotemManualPageRenderContext context, List<ItemStack> ingredients,
                               ItemStack result, int y) {
        int startX = 27;
        for (int index = 0; index < ingredients.size(); index++) {
            stack(context, ingredients.get(index), startX + index * 23, y);
            if (index < ingredients.size() - 1) {
                plus(context, startX + 17 + index * 23, y + 8);
            }
        }
        int arrowX = startX + ingredients.size() * 23 + 1;
        arrow(context, arrowX, y + 8, 9);
        stack(context, result, arrowX + 15, y);
    }

    private static void stack(TotemManualPageRenderContext context, ItemStack stack, int localX, int y) {
        int x = context.pageLeft() + localX;
        context.graphics().item(stack, x, y);
        if (inside(context, x, y, 16, 16)) {
            context.graphics().setTooltipForNextFrame(
                    context.font(), stack, context.mouseX(), context.mouseY());
        }
    }

    private static void arrow(TotemManualPageRenderContext context, int localX, int y, int length) {
        int x = context.pageLeft() + localX;
        context.graphics().fill(x, y, x + length + 1, y + 1, MUTED);
        context.graphics().fill(x + length - 2, y - 2, x + length + 1, y + 3, MUTED);
    }

    private static void plus(TotemManualPageRenderContext context, int localX, int y) {
        context.graphics().text(context.font(), "+", context.pageLeft() + localX, y - 4, MUTED, false);
    }

    private static void centeredClipped(TotemManualPageRenderContext context, Component component,
                                        int y, int color, int maxWidth) {
        String text = component.getString();
        if (context.font().width(text) > maxWidth) {
            text = context.font().plainSubstrByWidth(text, maxWidth - context.font().width("…")) + "…";
        }
        context.graphics().centeredText(context.font(), text, context.pageLeft() + 93, y, color);
    }

    private static boolean inside(TotemManualPageRenderContext context,
                                  int x, int y, int width, int height) {
        return context.mouseX() >= x && context.mouseX() < x + width
                && context.mouseY() >= y && context.mouseY() < y + height;
    }
}
