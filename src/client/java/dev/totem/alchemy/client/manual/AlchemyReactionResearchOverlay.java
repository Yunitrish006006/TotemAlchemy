package dev.totem.alchemy.client.manual;

import dev.totem.alchemy.discovery.AlchemyConflictCatalog;
import dev.totem.core.api.v1.client.manual.TotemManualPageOverlayRegistry;
import dev.totem.core.api.v1.client.manual.TotemManualPageRenderContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Discovery-driven manual page for chemically opposing potion effects. */
public final class AlchemyReactionResearchOverlay {
    public static final String PAGE_KEY = "book.totem_alchemy.reaction_research.slot";

    private static final int INK = 0xFF4B3826;
    private static final int MUTED = 0xFF765B3D;
    private static final int WARN = 0xFFA33A2B;

    private AlchemyReactionResearchOverlay() {
    }

    public static void register() {
        TotemManualPageOverlayRegistry.register(
                Identifier.fromNamespaceAndPath("totem-alchemy", "reaction_research"),
                AlchemyReactionResearchOverlay::render
        );
    }

    private static void render(TotemManualPageRenderContext context) {
        if (!PAGE_KEY.equals(context.pageKey())) {
            return;
        }

        boolean anyKnown = AlchemyConflictCatalog.entries().stream()
                .anyMatch(entry -> AlchemyDiscoveryClientCache.hasKey(AlchemyConflictCatalog.relationKey(entry)));
        if (!anyKnown) {
            context.graphics().centeredText(
                    context.font(), "?", context.pageLeft() + 93, context.pageTop() + 54, WARN);
            return;
        }

        int x = context.pageLeft() + 18;
        int y = context.pageTop() + 19;
        context.graphics().text(context.font(), Component.translatable("book.totem_alchemy.reaction.title"), x, y, INK, false);
        y += 15;
        context.graphics().text(context.font(), Component.translatable("book.totem_alchemy.reaction.intro.1"), x, y, MUTED, false);
        y += 11;
        context.graphics().text(context.font(), Component.translatable("book.totem_alchemy.reaction.intro.2"), x, y, MUTED, false);
        y += 18;

        for (AlchemyConflictCatalog.Entry entry : AlchemyConflictCatalog.entries()) {
            boolean known = AlchemyDiscoveryClientCache.hasKey(AlchemyConflictCatalog.relationKey(entry));
            if (!known) {
                context.graphics().text(context.font(), "?  ↔  ?", x, y, WARN, false);
                y += 28;
                continue;
            }

            Component pair = Component.translatable(entry.positiveNameKey())
                    .append(Component.literal("  ↔  "))
                    .append(Component.translatable(entry.negativeNameKey()));
            context.graphics().text(context.font(), pair, x, y, INK, false);
            y += 11;

            Component observation = observedResolutions(entry);
            context.graphics().text(context.font(), observation, x + 4, y, MUTED, false);
            y += 17;
        }
    }

    private static Component observedResolutions(AlchemyConflictCatalog.Entry entry) {
        List<Component> observed = new ArrayList<>();
        for (AlchemyConflictCatalog.Resolution resolution : AlchemyConflictCatalog.Resolution.values()) {
            if (AlchemyDiscoveryClientCache.hasKey(AlchemyConflictCatalog.resolutionKey(entry, resolution))) {
                observed.add(Component.translatable("book.totem_alchemy.reaction.result." + resolution.id()));
            }
        }
        if (observed.isEmpty()) {
            return Component.translatable("book.totem_alchemy.reaction.result.unknown");
        }
        MutableComponent out = Component.translatable("book.totem_alchemy.reaction.observed")
                .append(Component.literal(" "));
        for (int index = 0; index < observed.size(); index++) {
            if (index > 0) {
                out.append(Component.literal(" / "));
            }
            out.append(observed.get(index));
        }
        return out;
    }
}
