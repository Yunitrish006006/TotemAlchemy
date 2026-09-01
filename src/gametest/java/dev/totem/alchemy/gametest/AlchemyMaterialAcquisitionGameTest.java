package dev.totem.alchemy.gametest;

import dev.totem.alchemy.discovery.AlchemyDiscoverySavedData;
import dev.totem.alchemy.discovery.AlchemyMaterialAcquisitionDiscovery;
import dev.totem.alchemy.registry.AlchemyGameRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Covers rule-controlled, count-free material acquisition records. */
public final class AlchemyMaterialAcquisitionGameTest {
    @GameTest(maxTicks = 20)
    public void firstPossessionRecordsMaterialOnceWithoutAddingResearchSamples(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        boolean originalRule = helper.getLevel().getGameRules()
                .get(AlchemyGameRules.AUTO_RECORD_BREWING_MATERIALS);
        try {
            player.getInventory().add(new ItemStack(Items.DRAGON_BREATH));
            player.getInventory().add(new ItemStack(Items.DIAMOND));
            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(helper.getLevel().getServer());
            String ingredientId = "minecraft:dragon_breath";

            helper.getLevel().getGameRules().set(
                    AlchemyGameRules.AUTO_RECORD_BREWING_MATERIALS,
                    false,
                    helper.getLevel().getServer()
            );
            require(helper,
                    AlchemyMaterialAcquisitionDiscovery.discoverInventoryMaterials(player).isEmpty(),
                    "Disabled auto-recording still discovered an inventory material");
            require(helper, !data.hasKnownMaterial(player.getUUID(), ingredientId),
                    "Disabled auto-recording wrote Dragon's Breath to the manual");

            helper.getLevel().getGameRules().set(
                    AlchemyGameRules.AUTO_RECORD_BREWING_MATERIALS,
                    true,
                    helper.getLevel().getServer()
            );
            List<ItemStack> firstScan =
                    AlchemyMaterialAcquisitionDiscovery.discoverInventoryMaterials(player);
            require(helper, firstScan.size() == 1 && firstScan.getFirst().is(Items.DRAGON_BREATH),
                    "Enabled auto-recording did not discover only the brewable inventory material");
            require(helper, data.hasKnownMaterial(player.getUUID(), ingredientId),
                    "Dragon's Breath was not persisted as a known material");
            require(helper, data.materialSampleCount(player.getUUID(), ingredientId) == 0,
                    "Inventory acquisition incorrectly counted as a research sample");
            require(helper, data.research(player.getUUID()).keySet().stream()
                            .noneMatch(key -> key.startsWith(ingredientId + ">")),
                    "Inventory acquisition incorrectly recorded a brewing outcome");

            require(helper,
                    AlchemyMaterialAcquisitionDiscovery.discoverInventoryMaterials(player).isEmpty(),
                    "The same material was recorded more than once");
            require(helper, data.materialSampleCount(player.getUUID(), ingredientId) == 0,
                    "Repeated inventory scans changed the research sample count");
            helper.succeed();
        } finally {
            helper.getLevel().getGameRules().set(
                    AlchemyGameRules.AUTO_RECORD_BREWING_MATERIALS,
                    originalRule,
                    helper.getLevel().getServer()
            );
            player.discard();
        }
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
