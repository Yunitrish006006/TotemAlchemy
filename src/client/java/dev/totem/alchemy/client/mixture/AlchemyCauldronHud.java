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

import java.util.ArrayList;
import java.util.List;

/** Always-on native crosshair tooltip for a synchronized Alchemy Cauldron mixture. */
public final class AlchemyCauldronHud {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("totem", "alchemy_cauldron_tooltip");
    private static final int PANEL_TOP = 12;
    private static final int PANEL_PADDING = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int MAX_VISIBLE_STAGES = 5;

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
        Component title = Component.translatable("block.totem.alchemy_cauldron")
                .withStyle(ChatFormatting.BOLD);
        List<Component> stageLines = stageLines(mixture);
        Component details = Component.translatable(
                "hud.deadrecall.alchemy.cauldron.details",
                mixture.volumeUnits(), AlchemyMixtureState.MAX_VOLUME_UNITS);

        Font font = client.font;
        int contentWidth = Math.max(font.width(title), font.width(details));
        for (Component stageLine : stageLines) {
            contentWidth = Math.max(contentWidth, font.width(stageLine));
        }
        int panelWidth = Math.min(graphics.guiWidth() - 8, contentWidth + PANEL_PADDING * 2);
        int panelHeight = 27 + LINE_HEIGHT * stageLines.size();
        int panelLeft = (graphics.guiWidth() - panelWidth) / 2;
        int centerX = graphics.guiWidth() / 2;

        graphics.fill(panelLeft, PANEL_TOP, panelLeft + panelWidth, PANEL_TOP + panelHeight, 0xC0101010);
        graphics.outline(panelLeft, PANEL_TOP, panelWidth, panelHeight, 0xA0807060);
        graphics.centeredText(font, title, centerX, PANEL_TOP + 4, 0xFFFFFFFF);
        int lineY = PANEL_TOP + 15;
        for (Component stageLine : stageLines) {
            graphics.centeredText(font, stageLine, centerX, lineY, 0xFFE8E8E8);
            lineY += LINE_HEIGHT;
        }
        graphics.centeredText(font, details, centerX, lineY, 0xFFB0B0B0);
    }

    private static List<Component> stageLines(AlchemyMixtureState mixture) {
        List<Component> all = new ArrayList<>();
        for (AlchemyMixtureState.Reaction reaction : mixture.reactions()) {
            all.add(stageLine(reaction.ingredientId(), AlchemyMixtureTiming.classify(reaction)));
        }
        for (AlchemyMixtureState.CompletedStage stage : mixture.completedStages()) {
            all.add(stageLine(stage.ingredientId(), AlchemyMixtureTiming.classify(stage)));
        }
        if (all.isEmpty()) {
            AlchemyMixtureTiming.State timing = AlchemyMixtureTiming.classify(mixture);
            Component timingText = Component.translatable(timing.translationKey())
                    .withStyle(AlchemyMixtureTooltip.timingColor(timing));
            return List.of(Component.translatable("hud.deadrecall.alchemy.cauldron.timing", timingText));
        }
        if (all.size() <= MAX_VISIBLE_STAGES) {
            return List.copyOf(all);
        }

        List<Component> visible = new ArrayList<>(all.subList(0, MAX_VISIBLE_STAGES));
        visible.add(Component.translatable(
                "hud.deadrecall.alchemy.cauldron.more_stages",
                all.size() - MAX_VISIBLE_STAGES
        ).withStyle(ChatFormatting.DARK_GRAY));
        return List.copyOf(visible);
    }

    private static Component stageLine(String ingredientId, AlchemyMixtureTiming.State timing) {
        Component timingText = Component.translatable(timing.translationKey())
                .withStyle(AlchemyMixtureTooltip.timingColor(timing));
        return Component.translatable(
                "hud.deadrecall.alchemy.cauldron.stage",
                AlchemyMixtureTooltip.ingredientName(ingredientId),
                timingText
        );
    }
}
