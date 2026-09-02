package dev.totem.alchemy.client.mixture;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureTooltipLines;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

public final class AlchemyMixtureTooltip {
    private AlchemyMixtureTooltip() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, tooltipType, lines) -> {
            // The large flask owns its tooltip so it also works without a client callback.
            if (stack.is(AlchemyItems.LARGE_POTION_FLASK)
                    || !AlchemyMixtureBottle.hasStoredMixture(stack)) {
                return;
            }
            AlchemyMixtureTooltipLines.append(stack, lines::add);
        });
    }
}
