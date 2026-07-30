package dev.totem.alchemy.gametest;

import dev.totem.alchemy.block.AlchemyBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTestHelper;

/** Verifies player-fed pigs produce delayed, snow-like manure piles. */
public final class PigManureLayerGameTest {
    private static final BlockPos PILE_POS = new BlockPos(2, 2, 2);

    @GameTest(maxTicks = 140)
    public void pigDefecatesOnlyAfterSuccessfulFeeding(GameTestHelper helper) {
        prepareGround(helper);
        Pig pig = createPig(helper);
        Player mockPlayer = helper.makeMockServerPlayer(GameType.SURVIVAL);
        if (!(mockPlayer instanceof ServerPlayer player)) {
            throw helper.assertionException("GameTest did not create a server-side player fixture");
        }

        helper.startSequence()
                .thenExecuteAfter(45, () -> {
                    require(helper, helper.getLevel().getBlockState(helper.absolutePos(PILE_POS)).isAir(),
                            "Pig produced manure before a player fed it");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT));
                    require(helper, pig.mobInteract(player, InteractionHand.MAIN_HAND).consumesAction(),
                            "Feeding the pig did not consume the interaction");
                })
                .thenExecuteAfter(39, () -> require(
                        helper,
                        helper.getLevel().getBlockState(helper.absolutePos(PILE_POS)).isAir(),
                        "Pig produced manure before the post-feeding delay elapsed"
                ))
                .thenExecuteAfter(2, () -> requireLayers(helper, 1))
                .thenSucceed();
    }

    @GameTest(maxTicks = 40)
    public void manureDepositsBuildOneLayerAtATime(GameTestHelper helper) {
        prepareGround(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pilePos = helper.absolutePos(PILE_POS);

        for (int expectedLayers = 1; expectedLayers <= SnowLayerBlock.MAX_HEIGHT; expectedLayers++) {
            require(helper, AlchemyBlocks.addPigManureLayer(level, pilePos),
                    "Could not add manure layer " + expectedLayers);
            requireLayers(helper, expectedLayers);
        }

        require(helper, !AlchemyBlocks.addPigManureLayer(level, pilePos),
                "An eight-layer manure pile accepted a ninth layer");
        helper.succeed();
    }

    private static void prepareGround(GameTestHelper helper) {
        helper.setBlock(PILE_POS.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(PILE_POS, Blocks.AIR);
        // Keep the pig on the asserted deposit position even after the held
        // carrot activates its temptation goal. A one-block cardinal ring was
        // nondeterministic because the pig could step up or escape diagonally.
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
                if (xOffset == 0 && zOffset == 0) {
                    continue;
                }
                BlockPos wallPos = PILE_POS.offset(xOffset, 0, zOffset);
                helper.setBlock(wallPos, Blocks.STONE);
                helper.setBlock(wallPos.above(), Blocks.STONE);
            }
        }
    }

    private static Pig createPig(GameTestHelper helper) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "pig")
        );
        if (entityType == null) {
            throw helper.assertionException("Missing pig entity type");
        }

        Entity entity = entityType.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        if (!(entity instanceof Pig pig)) {
            throw helper.assertionException("Could not create pig fixture");
        }

        BlockPos absolutePos = helper.absolutePos(PILE_POS);
        pig.snapTo(absolutePos.getX() + 0.5D, absolutePos.getY(), absolutePos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!helper.getLevel().addFreshEntity(pig)) {
            throw helper.assertionException("Could not add pig fixture to the GameTest level");
        }
        return pig;
    }

    private static void requireLayers(GameTestHelper helper, int expectedLayers) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(PILE_POS));
        require(helper, state.is(AlchemyBlocks.PIG_MANURE_LAYER), "Pig manure pile block was missing");
        require(helper, state.getValue(SnowLayerBlock.LAYERS) == expectedLayers,
                "Expected " + expectedLayers + " manure layers but found "
                        + state.getValue(SnowLayerBlock.LAYERS));
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
