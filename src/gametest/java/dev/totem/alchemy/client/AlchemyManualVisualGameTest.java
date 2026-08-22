package dev.totem.alchemy.client;

import dev.totem.alchemy.alchemy.AlchemyPotions;
import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.client.manual.AlchemyDiscoveryClientCache;
import dev.totem.alchemy.client.manual.AlchemyResearchClientCache;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.AlchemyMixtureTiming;
import dev.totem.core.api.v1.manual.TotemManualAssembler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Opens every Traditional Chinese Alchemy spread so icon layouts are regression-tested. */
@SuppressWarnings("UnstableApiUsage")
public final class AlchemyManualVisualGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        AtomicReference<BlockPos> cauldronTarget = new AtomicReference<>();
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
            if (!I18n.get("book.totem_alchemy.material_slot.red_mushroom").isEmpty()) {
                throw new AssertionError("Undiscovered material page slot leaked its static label");
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
                var player = server.getPlayerList().getPlayers().getFirst();
                ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
                ItemStack awkward = server.overworld().potionBrewing().mix(
                        new ItemStack(Items.NETHER_WART), water);
                if (!awkward.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                        .is(Potions.AWKWARD)) {
                    throw new AssertionError("Nether Wart visual fixture did not produce Awkward Potion");
                }
                AlchemyDiscoveryService.recordSuccessfulBrew(
                        player.level(), player.blockPosition(), new ItemStack(Items.NETHER_WART),
                        List.of(water), List.of(awkward), 400, null, player.getUUID());

                AlchemyDiscoveryService.record(player, new ItemStack(Items.SPIDER_EYE), Potions.POISON);
                AlchemyDiscoveryService.recordSuccessfulBrewOutcomes(
                        player.level(), player.blockPosition(), new ItemStack(Items.SUGAR),
                        List.of(awkward), List.of(awkward), 400,
                        List.of(Potions.SWIFTNESS, Potions.SLOWNESS, AlchemyPotions.SATURATION),
                        player.getUUID());

                BlockPos target = player.blockPosition().above(3);
                for (int x = -2; x <= 2; x++) {
                    for (int z = -5; z <= 2; z++) {
                        server.overworld().setBlock(target.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                        for (int y = 0; y <= 3; y++) {
                            server.overworld().setBlock(target.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
                server.overworld().setBlock(target, AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState(), 3);
                if (!(server.overworld().getBlockEntity(target) instanceof AlchemyCauldronBlockEntity cauldron)) {
                    throw new AssertionError("Visual fixture did not create an Alchemy Cauldron block entity");
                }
                AlchemyMixtureState mixture = new AlchemyMixtureState(1);
                mixture.setBaseActivated(true);
                mixture.addReaction(new AlchemyMixtureState.Reaction(
                        "visual:nether_wart", "minecraft:nether_wart", 650, 1_000, 1,
                        "minecraft:water", "minecraft:awkward", Map.of(), Map.of()));
                if (!cauldron.initializeMixture(mixture)) {
                    throw new AssertionError("Visual fixture could not initialize its mixture");
                }
                cauldronTarget.set(target.immutable());
                player.teleportTo(target.getX() + 0.5D, target.getY(), target.getZ() - 3.5D);
            });
            context.waitFor(client -> AlchemyDiscoveryClientCache.has(Items.NETHER_WART, Potions.AWKWARD)
                    && AlchemyResearchClientCache.isMaterialKnown(Items.NETHER_WART)
                    && AlchemyResearchClientCache.samples(Items.NETHER_WART) > 0
                    && isLowConfidenceTwentySecondEstimate(Items.NETHER_WART)
                    && AlchemyDiscoveryClientCache.has(Items.SPIDER_EYE, Potions.POISON)
                    && AlchemyResearchClientCache.isMaterialKnown(Items.SPIDER_EYE)
                    && AlchemyDiscoveryClientCache.has(Items.SUGAR, Potions.SWIFTNESS)
                    && AlchemyDiscoveryClientCache.has(Items.SUGAR, Potions.SLOWNESS)
                    && AlchemyDiscoveryClientCache.has(Items.SUGAR, AlchemyPotions.SATURATION)
                    && AlchemyResearchClientCache.isMaterialKnown(Items.SUGAR)
                    && !AlchemyResearchClientCache.isMaterialKnown(Items.RED_MUSHROOM)
                    && AlchemyResearchClientCache.samples(Items.SUGAR) == 1
                    && isLowConfidenceTwentySecondEstimate(Items.SUGAR));
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
            context.waitFor(client -> {
                BlockPos target = cauldronTarget.get();
                return target != null
                        && client.level != null
                        && client.level.getBlockEntity(target) instanceof AlchemyCauldronBlockEntity cauldron
                        && AlchemyMixtureTiming.classify(cauldron.mixtureSnapshot())
                        == AlchemyMixtureTiming.State.ALMOST_READY;
            });
            context.runOnClient(client -> {
                BlockPos target = cauldronTarget.get();
                client.player.setPosRaw(target.getX() + 0.5D, target.getY(), target.getZ() - 3.5D);
                client.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
            });
            context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                    && hit.getBlockPos().equals(cauldronTarget.get()));
            context.waitTicks(5);
            context.takeScreenshot("alchemy-cauldron-hud-zh-tw");
        }
    }

    private static boolean isLowConfidenceTwentySecondEstimate(net.minecraft.world.item.Item ingredient) {
        return AlchemyResearchClientCache.timeEstimate(ingredient)
                .filter(estimate -> estimate.samples() == 1
                        && estimate.accuracyPercent() == 20
                        && estimate.lowerTenths() == 100
                        && estimate.upperTenths() == 300
                        && !estimate.exact())
                .isPresent();
    }
}
