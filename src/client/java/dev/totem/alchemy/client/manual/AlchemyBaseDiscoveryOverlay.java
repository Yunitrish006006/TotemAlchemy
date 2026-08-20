package dev.totem.alchemy.client.manual;

import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

/** Hides base-potion results until the player has actually discovered them. */
public final class AlchemyBaseDiscoveryOverlay {
    private static final String PAGE_KEY = "book.deadrecall.alchemy_manual.page.2";
    private static final int WARN = 0xFFA33A2B;

    private AlchemyBaseDiscoveryOverlay() {
    }

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-alchemy", "manual_base_discoveries"),
                AlchemyBaseDiscoveryOverlay::render
        );
    }

    private static void render(TotemManualPageRenderContext context) {
        if (!PAGE_KEY.equals(context.pageKey())) {
            return;
        }
        int y = context.pageTop() + 36;
        hideUndiscoveredBase(context, Items.NETHER_WART, y);
        hideUndiscoveredBase(context, Items.RED_MUSHROOM, y + 28);
    }

    private static void hideUndiscoveredBase(TotemManualPageRenderContext context, Item ingredient, int y) {
        if (AlchemyDiscoveryClientCache.has(ingredient, Potions.AWKWARD)) {
            return;
        }
        int x = context.pageLeft() + 116;
        ItemStack unknown = new ItemStack(Items.GLASS_BOTTLE);
        context.graphics().item(unknown, x, y);
        context.graphics().text(context.font(), "?", x + 6, y + 4, WARN, false);
        if (context.mouseX() >= x && context.mouseX() < x + 16
                && context.mouseY() >= y && context.mouseY() < y + 16) {
            context.graphics().setTooltipForNextFrame(
                    context.font(), unknown, context.mouseX(), context.mouseY());
        }
    }
}
