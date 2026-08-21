package dev.totem.alchemy.gametest;

import dev.totem.alchemy.discovery.AlchemyDiscoverySavedData;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

/** Guards persisted single-player research data so reconnect resync has a snapshot to send. */
public final class AlchemyDiscoveryConnectionSyncGameTest {
    @GameTest(maxTicks = 20)
    public void persistedResearchRemainsAvailableForReconnectSnapshot(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack sugar = new ItemStack(Items.SUGAR);
            ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
            ItemStack swiftness = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
            AlchemyDiscoveryService.recordSuccessfulBrew(
                    player.level(), player.blockPosition(), sugar,
                    List.of(awkward), List.of(swiftness), 300, Potions.SWIFTNESS, player.getUUID());

            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            if (data.materialSampleCount(player.getUUID(), "minecraft:sugar") <= 0) {
                helper.fail("Reconnect sync would have no persisted sugar research sample to send");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
