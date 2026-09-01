package dev.totem.alchemy.client;

import dev.totem.alchemy.client.manual.AlchemyResearchClientCache;
import dev.totem.alchemy.registry.AlchemyGameRules;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Captures the vanilla item-activation presentation used for a newly acquired material. */
@SuppressWarnings("UnstableApiUsage")
public final class AlchemyMaterialDiscoveryVisualGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            singleplayer.getServer().runOnServer(server -> {
                server.getGameRules().set(
                        AlchemyGameRules.AUTO_RECORD_BREWING_MATERIALS,
                        true,
                        server
                );
                if (server.getPlayerList().getPlayers().isEmpty()) {
                    throw new AssertionError("Material discovery visual test had no connected player");
                }
                server.getPlayerList().getPlayers().getFirst().getInventory()
                        .add(new ItemStack(Items.DRAGON_BREATH));
            });
            context.waitFor(client -> AlchemyResearchClientCache.isMaterialKnown(Items.DRAGON_BREATH)
                    && AlchemyResearchClientCache.samples(Items.DRAGON_BREATH) == 0);
            context.waitTicks(3);
            context.takeScreenshot("alchemy-material-discovery-activation");
        }
    }
}
