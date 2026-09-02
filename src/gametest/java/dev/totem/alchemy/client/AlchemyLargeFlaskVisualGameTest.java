package dev.totem.alchemy.client;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Native-scale proof for the large flask amount bar, dose text and potion-effect tooltip. */
@SuppressWarnings("UnstableApiUsage")
public final class AlchemyLargeFlaskVisualGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> {
            client.options.languageCode = "zh_tw";
            client.getLanguageManager().setSelected("zh_tw");
            client.options.guiScale().set(3);
            reload.set(client.reloadResourcePacks());
        });
        context.waitFor(client -> reload.get() != null && reload.get().isDone());
        context.getInput().resizeWindow(1280, 720);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            singleplayer.getServer().runOnServer(server -> {
                var players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    throw new IllegalStateException("Large-flask visual test had no connected player");
                }
                AlchemyMixtureState mixture = new AlchemyMixtureState(2);
                mixture.setBaseActivated(true);
                mixture.putEffect("minecraft:speed", 20.0D * 180.0D * 2.0D, 0);
                mixture.putEffect("minecraft:regeneration", 20.0D * 45.0D * 2.0D, 1);
                ItemStack flask = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
                AlchemyMixtureBottle.writeState(flask, mixture);
                players.getFirst().getInventory().setItem(9, flask);
                players.getFirst().inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> {
                if (client.player == null) {
                    return false;
                }
                ItemStack flask = client.player.getInventory().getItem(9);
                return flask.is(AlchemyItems.LARGE_POTION_FLASK)
                        && AlchemyMixtureBottle.storedMixture(flask).volumeUnits() == 2
                        && flask.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).hasEffects();
            });
            context.runOnClient(client -> client.setScreenAndShow(new InventoryScreen(client.player)));
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(5);
            context.runOnClient(client -> {
                ItemStack flask = client.player.getInventory().getItem(9);
                var lines = flask.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
                boolean hasDose = lines.stream().map(line -> line.getString())
                        .anyMatch(line -> line.contains("容量：2 / 3 份"));
                String speed = MobEffects.SPEED.value().getDisplayName().getString();
                String regeneration = MobEffects.REGENERATION.value().getDisplayName().getString();
                boolean hasSpeed = lines.stream().map(line -> line.getString())
                        .anyMatch(line -> line.contains(speed));
                boolean hasRegeneration = lines.stream().map(line -> line.getString())
                        .anyMatch(line -> line.contains(regeneration));
                if (!hasDose || !hasSpeed || !hasRegeneration) {
                    throw new AssertionError("Large-flask tooltip omitted dose/effect text: " + lines);
                }
                if (!flask.isBarVisible() || flask.getBarWidth() != 9) {
                    throw new AssertionError("Partly drained large flask omitted its native amount bar");
                }
            });

            int[] slot = slotCenter(context);
            context.getInput().setCursorPos(slot[0], slot[1]);
            context.waitTicks(3);
            context.takeScreenshot("alchemy-large-flask-tooltip-zh-tw");
            context.runOnClient(client -> client.setScreenAndShow(null));
        }
    }

    private static int[] slotCenter(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.gui.screen();
            var slot = screen.getMenu().getSlot(InventoryMenu.INV_SLOT_START);
            int left = (screen.width - 176) / 2;
            int top = (screen.height - 166) / 2;
            double xScale = (double) client.getWindow().getScreenWidth()
                    / client.getWindow().getGuiScaledWidth();
            double yScale = (double) client.getWindow().getScreenHeight()
                    / client.getWindow().getGuiScaledHeight();
            return new int[]{
                    (int) Math.round((left + slot.x + 9) * xScale),
                    (int) Math.round((top + slot.y + 9) * yScale)
            };
        });
    }
}
