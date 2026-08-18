package dev.totem.alchemy.client;

import dev.totem.alchemy.client.manual.AlchemyDiscoveryClientCache;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.core.api.v1.manual.TotemManualAssembler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Opens every Traditional Chinese Alchemy spread so icon layouts are regression-tested. */
@SuppressWarnings("UnstableApiUsage")
public final class AlchemyManualVisualGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> {
            client.options.languageCode = "zh_tw";
            client.getLanguageManager().setSelected("zh_tw");
            reload.set(client.reloadResourcePacks());
        });
        context.waitFor(client -> reload.get() != null && reload.get().isDone());
        context.runOnClient(client -> {
            if (!I18n.get("book.deadrecall.alchemy_manual.title").equals("Alchemy 煉金手冊")) {
                throw new AssertionError("Traditional Chinese Alchemy manual resources were not loaded");
            }
            client.options.guiScale().set(3);
        });
        context.getInput().resizeWindow(1280, 720);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            singleplayer.getServer().runOnServer(server -> {
                if (server.getPlayerList().getPlayers().isEmpty()) {
                    throw new AssertionError("Visual test had no connected server player");
                }
                AlchemyDiscoveryService.record(
                        server.getPlayerList().getPlayers().getFirst(),
                        new ItemStack(Items.SPIDER_EYE),
                        Potions.POISON
                );
            });
            context.waitFor(client -> AlchemyDiscoveryClientCache.has(Items.SPIDER_EYE, Potions.POISON));
            ItemStack manual = TotemManualAssembler.create();
            WrittenBookContent content = manual.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content == null) {
                throw new AssertionError("Alchemy Totem manual had no pages");
            }
            context.runOnClient(client -> client.setScreenAndShow(new BookViewScreen(
                    BookViewScreen.BookAccess.fromItem(manual)
            )));
            context.waitForScreen(BookViewScreen.class);
            context.waitTicks(10);

            for (int page = 0; page < content.pages().size(); page += 2) {
                int capturedPage = page;
                context.runOnClient(client -> {
                    BookViewScreen screen = (BookViewScreen) client.gui.screen();
                    if (screen == null) {
                        throw new AssertionError("Alchemy manual closed before page " + (capturedPage + 1));
                    }
                    screen.setPage(capturedPage);
                });
                context.waitTicks(2);
                context.takeScreenshot("alchemy-manual-spread-%02d-%02d".formatted(
                        page + 1, Math.min(page + 2, content.pages().size())));
            }
            context.runOnClient(client -> client.setScreenAndShow(null));
        }
    }
}
