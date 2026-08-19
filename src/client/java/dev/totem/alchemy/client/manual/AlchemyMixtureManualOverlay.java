package dev.totem.alchemy.client.manual;

import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Dedicated visual tutorial for the fifteenth Alchemy manual page. */
public final class AlchemyMixtureManualOverlay {
    private static final String PAGE_KEY = "book.deadrecall.alchemy_manual.page.15";
    private static final int INK = 0xFF4B3826;
    private static final int MUTED = 0xFF765B3D;
    private static final int GOOD = 0xFF287A45;
    private static final int WARN = 0xFFA33A2B;

    private AlchemyMixtureManualOverlay() {
    }

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-alchemy", "manual_mixtures"),
                AlchemyMixtureManualOverlay::render
        );
    }

    private static void render(TotemManualPageRenderContext context) {
        if (!PAGE_KEY.equals(context.pageKey())) {
            return;
        }

        int y = context.pageTop() + 32;
        stack(context, new ItemStack(Items.POTION), 30, y);
        text(context, "+", 51, y + 4, INK);
        stack(context, new ItemStack(Items.POTION), 64, y);
        text(context, "→", 88, y + 4, INK);
        stack(context, new ItemStack(Items.CAULDRON), 111, y);
        centered(context, "book.deadrecall.alchemy_manual.mixture.mix", y + 20, GOOD);

        y += 40;
        stack(context, new ItemStack(Items.NETHER_WART), 30, y);
        text(context, "+", 51, y + 4, INK);
        stack(context, new ItemStack(Items.CAULDRON), 64, y);
        text(context, "⌛", 88, y + 3, MUTED);
        stack(context, new ItemStack(Items.GLASS_BOTTLE), 111, y);
        centered(context, "book.deadrecall.alchemy_manual.mixture.bottle_early", y + 20, WARN);

        y += 40;
        stack(context, new ItemStack(Items.GLASS_BOTTLE), 30, y);
        text(context, "→", 53, y + 4, INK);
        stack(context, new ItemStack(Items.CAULDRON), 76, y);
        text(context, "→", 99, y + 4, INK);
        text(context, "⌛", 122, y + 3, MUTED);
        centered(context, "book.deadrecall.alchemy_manual.mixture.resume", y + 20, GOOD);

        y += 40;
        stack(context, new ItemStack(Items.SUGAR), 30, y);
        text(context, "↔", 54, y + 4, MUTED);
        stack(context, new ItemStack(Items.FERMENTED_SPIDER_EYE), 76, y);
        text(context, "−", 101, y + 4, WARN);
        centered(context, "book.deadrecall.alchemy_manual.mixture.counteract", y + 20, MUTED);
    }

    private static void stack(TotemManualPageRenderContext context, ItemStack stack, int localX, int y) {
        int x = context.pageLeft() + localX;
        context.graphics().item(stack, x, y);
        if (context.mouseX() >= x && context.mouseX() < x + 16
                && context.mouseY() >= y && context.mouseY() < y + 16) {
            context.graphics().setTooltipForNextFrame(context.font(), stack, context.mouseX(), context.mouseY());
        }
    }

    private static void text(TotemManualPageRenderContext context, String text, int localX, int y, int color) {
        context.graphics().text(context.font(), text, context.pageLeft() + localX, y, color, false);
    }

    private static void centered(TotemManualPageRenderContext context, String key, int y, int color) {
        context.graphics().centeredText(
                context.font(),
                Component.translatable(key),
                context.pageLeft() + 93,
                y,
                color
        );
    }
}
