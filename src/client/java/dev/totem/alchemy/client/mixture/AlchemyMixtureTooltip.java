package dev.totem.alchemy.client.mixture;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AlchemyMixtureTooltip {
    private AlchemyMixtureTooltip() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, tooltipType, lines) -> {
            if (!AlchemyMixtureBottle.hasStoredMixture(stack)) {
                return;
            }
            AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(stack);
            lines.add(Component.translatable(
                    state.hasPendingReactions()
                            ? "tooltip.deadrecall.alchemy.mixture.incomplete"
                            : "tooltip.deadrecall.alchemy.mixture.complete"
            ).withStyle(state.hasPendingReactions() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            lines.add(Component.translatable(
                    "tooltip.deadrecall.alchemy.mixture.stability",
                    state.stability()
            ).withStyle(ChatFormatting.DARK_GRAY));

            for (AlchemyMixtureState.Reaction reaction : state.reactions()) {
                int seconds = (int) Math.ceil(reaction.remainingTicks() / 20.0D);
                int percent = (int) Math.round(reaction.progress() * 100.0D);
                lines.add(Component.translatable(
                        "tooltip.deadrecall.alchemy.mixture.reaction",
                        ingredientName(reaction.ingredientId()),
                        percent,
                        seconds
                ).withStyle(ChatFormatting.YELLOW));
            }

            if (state.hasPendingReactions()) {
                lines.add(Component.translatable("tooltip.deadrecall.alchemy.mixture.drink_warning")
                        .withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("tooltip.deadrecall.alchemy.mixture.repour_hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }

    private static Component ingredientName(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return Component.literal(id);
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        return item == null ? Component.literal(id) : new ItemStack(item).getHoverName();
    }
}
