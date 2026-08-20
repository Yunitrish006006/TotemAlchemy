package dev.totem.alchemy.client.mixture;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.AlchemyMixtureTiming;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;

/** Always-on native crosshair tooltip for a synchronized Alchemy Cauldron mixture. */
public final class AlchemyCauldronHud {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("totem", "alchemy_cauldron_tooltip");
    private static final int PANEL_TOP = 12;
    private static final int PANEL_PADDING = 5;
    private static final int PANEL_HEIGHT = 38;

    private AlchemyCauldronHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, HUD_ID,
                AlchemyCauldronHud::extractRenderState);
    }

    private static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.gui.screen() != null
                || !(client.hitResult instanceof BlockHitResult hit)
                || !AlchemyBlocks.isAlchemyCauldron(client.level.getBlockState(hit.getBlockPos()))
                || !(client.level.getBlockEntity(hit.getBlockPos()) instanceof AlchemyCauldronBlockEntity cauldron)
                || !cauldron.hasMixture()) {
            return;
        }

        AlchemyMixtureState mixture = cauldron.mixtureSnapshot();
        AlchemyMixtureTiming.State timing = AlchemyMixtureTiming.classify(mixture);
        Component title = Component.translatable("block.totem.alchemy_cauldron")
                .withStyle(ChatFormatting.BOLD);
        Component timingText = Component.translatable(timing.translationKey())
                .withStyle(AlchemyMixtureTooltip.timingColor(timing));
        Component stateLine = Component.translatable(
                "hud.deadrecall.alchemy.cauldron.timing", timingText);
        Component details = Component.translatable(
                "hud.deadrecall.alchemy.cauldron.details",
                mixture.volumeUnits(), AlchemyMixtureState.MAX_VOLUME_UNITS);

        Font font = client.font;
        int contentWidth = Math.max(font.width(title), Math.max(font.width(stateLine), font.width(details)));
        int panelWidth = Math.min(graphics.guiWidth() - 8, contentWidth + PANEL_PADDING * 2);
        int panelLeft = (graphics.guiWidth() - panelWidth) / 2;
        int centerX = graphics.guiWidth() / 2;

        graphics.fill(panelLeft, PANEL_TOP, panelLeft + panelWidth, PANEL_TOP + PANEL_HEIGHT, 0xC0101010);
        graphics.outline(panelLeft, PANEL_TOP, panelWidth, PANEL_HEIGHT, 0xA0807060);
        graphics.centeredText(font, title, centerX, PANEL_TOP + 4, 0xFFFFFFFF);
        graphics.centeredText(font, stateLine, centerX, PANEL_TOP + 15, 0xFFE8E8E8);
        graphics.centeredText(font, details, centerX, PANEL_TOP + 26, 0xFFB0B0B0);
    }
}
