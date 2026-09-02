package dev.totem.alchemy.mixture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlchemyMixtureUiSourceTest {
    @Test
    void bottleAndBlockHintsDoNotExposeNumericTiming() throws IOException {
        String tooltip = source("src/client/java/dev/totem/alchemy/client/mixture/AlchemyMixtureTooltip.java");
        String hud = source("src/client/java/dev/totem/alchemy/client/mixture/AlchemyCauldronHud.java");
        String en = source("src/main/resources/assets/totem/lang/en_us.json");
        String zh = source("src/main/resources/assets/totem/lang/zh_tw.json");

        assertTrue(tooltip.contains("AlchemyMixtureTiming.classify"));
        assertFalse(tooltip.contains(".progress()"));
        assertFalse(tooltip.contains("remainingTicks"));
        assertFalse(tooltip.contains("requiredTicks"));
        assertTrue(hud.contains("AlchemyMixtureTiming.classify"));
        assertTrue(hud.contains("for (AlchemyMixtureState.Reaction reaction : mixture.reactions())"));
        assertTrue(hud.contains("for (AlchemyMixtureState.CompletedStage stage : mixture.completedStages())"));
        assertFalse(hud.contains("overcookTicks()"));
        assertFalse(hud.contains("stability()"));
        assertFalse(en.contains("%s%% reacted"));
        assertFalse(zh.contains("已反應 %s%%"));
    }

    @Test
    void cauldronHudIsNativeAndHasNoOptionalOverlayGate() throws IOException {
        String hud = source("src/client/java/dev/totem/alchemy/client/mixture/AlchemyCauldronHud.java");
        String client = source("src/client/java/dev/totem/alchemy/client/TotemAlchemyClient.java");
        String cauldron = source("src/main/java/dev/totem/alchemy/block/entity/AlchemyCauldronBlockEntity.java");
        String lower = hud.toLowerCase();

        assertTrue(hud.contains("HudElementRegistry.attachElementAfter"));
        assertTrue(hud.contains("client.hitResult instanceof BlockHitResult"));
        assertTrue(hud.contains("client.level.getBlockEntity(hit.getBlockPos())"));
        assertTrue(client.contains("AlchemyCauldronHud.register()"));
        assertTrue(cauldron.contains("return saveCustomOnly(registries)"));
        assertTrue(cauldron.contains("timingSignature != lastSyncedTimingSignature"));
        assertTrue(cauldron.contains("AlchemyMixtureTiming.visualSignature(mixture)"));
        assertTrue(cauldron.contains("volumeChanged || timingChanged ||"));
        assertFalse(lower.contains("jade"));
        assertFalse(lower.contains("wthit"));
        assertFalse(lower.contains("ismodloaded"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
