package dev.totem.alchemy.gametest;

import dev.totem.alchemy.discovery.AlchemyDiscoveryKey;
import dev.totem.alchemy.discovery.AlchemyDiscoverySavedData;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

/** Regression coverage for discoveries after multi-effect mixtures lose a canonical Potion holder. */
public final class LayeredDiscoveryGameTest {
    @GameTest(maxTicks = 20)
    public void selectedMultiOutcomeStillUpdatesManualForCustomMixture(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack ingredient = new ItemStack(Items.MAGMA_CREAM);
            ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);

            AlchemyMixtureState layeredState = AlchemyMixtureBottle.fromPotion(awkward);
            layeredState.putEffect("minecraft:strength", 20.0D * 180.0D, 0);
            layeredState.setCanonicalPotionId(null);
            ItemStack layeredOutput = AlchemyMixtureBottle.toPotion(layeredState);

            if (layeredOutput.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                    .potion().isPresent()) {
                helper.fail("Regression fixture unexpectedly retained a canonical potion holder");
                return;
            }

            AlchemyDiscoveryService.recordSuccessfulBrew(
                    player.level(),
                    player.blockPosition(),
                    ingredient,
                    List.of(awkward),
                    List.of(layeredOutput),
                    400,
                    Potions.STRENGTH
            );

            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            String strengthKey = AlchemyDiscoveryKey.of(Items.MAGMA_CREAM, Potions.STRENGTH);
            if (!data.has(player.getUUID(), strengthKey)) {
                helper.fail("Layered multi-outcome brew was not written to the player's manual");
                return;
            }
            if (data.research(player.getUUID()).getOrDefault(strengthKey, 0) != 1) {
                helper.fail("Layered multi-outcome brew did not add one research observation");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
