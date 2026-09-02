package dev.totem.alchemy.client.mixture;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.AlchemyMixtureTiming;
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
            AlchemyMixtureState state = AlchemyMixtureBottle.storedMixture(stack);
            if (state.isEmpty()) {
                return;
            }
            lines.add(Component.translatable(
                    state.hasPendingReactions()
                            ? "tooltip.deadrecall.alchemy.mixture.incomplete"
                            : "tooltip.deadrecall.alchemy.mixture.complete"
            ).withStyle(state.hasPendingReactions() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            lines.add(Component.translatable(
                    "tooltip.deadrecall.alchemy.mixture.volume",
                    state.volumeUnits(),
                    AlchemyMixtureState.MAX_VOLUME_UNITS
            ).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable(
                    "tooltip.deadrecall.alchemy.mixture.stability",
                    state.stability()
            ).withStyle(ChatFormatting.DARK_GRAY));

            AlchemyMixtureTiming.State timing = AlchemyMixtureTiming.classify(state);
            lines.add(Component.translatable(
                    "tooltip.deadrecall.alchemy.mixture.timing_state",
                    Component.translatable(timing.translationKey())
            ).withStyle(timingColor(timing)));

            for (AlchemyMixtureState.Reaction reaction : state.reactions()) {
                lines.add(Component.translatable(
                        "tooltip.deadrecall.alchemy.mixture.reacting_ingredient",
                        ingredientName(reaction.ingredientId())
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

    static Component ingredientName(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return Component.literal(id);
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        return item == null ? Component.literal(id) : new ItemStack(item).getHoverName();
    }

    static ChatFormatting timingColor(AlchemyMixtureTiming.State timing) {
        return switch (timing) {
            case JUST_STARTED, WORKING -> ChatFormatting.GRAY;
            case ALMOST_READY, NEARLY_READY -> ChatFormatting.GOLD;
            case JUST_RIGHT, PERFECT -> ChatFormatting.GREEN;
            case SLIGHTLY_OVERDONE -> ChatFormatting.YELLOW;
            case OVERDONE, BADLY_OVERDONE -> ChatFormatting.RED;
            case EMPTY -> ChatFormatting.DARK_GRAY;
        };
    }
}
