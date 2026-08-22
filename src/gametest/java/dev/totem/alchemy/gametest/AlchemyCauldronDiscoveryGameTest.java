package dev.totem.alchemy.gametest;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.discovery.AlchemyDiscoverySavedData;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AlchemyCauldronDiscoveryGameTest {
    @GameTest(maxTicks = 20)
    public void completedMixtureReactionRecordsServerResearchForStartingPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            BlockPos cauldronPos = player.blockPosition().below();
            helper.getLevel().setBlock(cauldronPos.below(), Blocks.CAMPFIRE.defaultBlockState(), 3);
            BlockState cauldronState = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, 1);
            helper.getLevel().setBlock(cauldronPos, cauldronState, 3);

            BlockEntity blockEntity = helper.getLevel().getBlockEntity(cauldronPos);
            require(helper, blockEntity instanceof AlchemyCauldronBlockEntity,
                    "Alchemy Cauldron BlockEntity was not created");
            AlchemyCauldronBlockEntity cauldron = (AlchemyCauldronBlockEntity) blockEntity;

            ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
            AlchemyMixtureState initial = AlchemyMixtureBottle.fromPotion(awkward);
            require(helper, cauldron.initializeMixture(initial), "Could not initialize the Alchemy Cauldron mixture");

            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            int samplesBefore = data.materialSampleCount(player.getUUID(), "minecraft:sugar");
            require(helper, cauldron.scheduleMixtureReaction(helper.getLevel(), new ItemStack(Items.SUGAR)),
                    "Could not schedule the sugar mixture reaction");

            AlchemyMixtureState scheduled = cauldron.mixtureSnapshot();
            require(helper, scheduled.hasPendingReactions(), "Scheduled cauldron reaction disappeared before ticking");
            int processingTicks = scheduled.reactions().iterator().next().requiredTicks();
            for (int tick = 0; tick < processingTicks; tick++) {
                AlchemyCauldronBlockEntity.serverTick(
                        helper.getLevel(), cauldronPos, helper.getLevel().getBlockState(cauldronPos), cauldron);
            }

            require(helper, !cauldron.mixtureSnapshot().hasPendingReactions(),
                    "Cauldron reaction did not finish after its processing time");
            require(helper,
                    data.materialSampleCount(player.getUUID(), "minecraft:sugar") == samplesBefore + 1,
                    "Completed cauldron reaction did not record a server-side material sample");
            require(helper,
                    data.research(player.getUUID()).keySet().stream().anyMatch(key -> key.startsWith("minecraft:sugar>")),
                    "Completed cauldron reaction did not record its outcome/no-effect research key");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
