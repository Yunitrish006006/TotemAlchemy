package dev.totem.alchemy.gametest;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.liquid.AlchemyLiquids;
import dev.totem.alchemy.mixture.ActivatedBaseComposition;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.LiquidComposition;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class MixedLiquidContainerGameTest {
    private static final double EPSILON = 1.0E-6D;
    private static final BlockPos LOCAL_CAULDRON_POS = new BlockPos(2, 2, 2);

    @GameTest(maxTicks = 40)
    public void mixedMixtureBucketRoundTripPreservesFullCauldronState(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(LOCAL_CAULDRON_POS);
        AlchemyMixtureState source = mixedState(3);
        String expected = source.encode();
        AlchemyCauldronBlockEntity cauldron = placeMixtureCauldron(helper, pos, source);

        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));

        interact(player, pos);

        ItemStack filledBucket = player.getMainHandItem();
        require(helper, filledBucket.is(Items.WATER_BUCKET),
                "Mixed cauldron did not fill the compatibility Water Bucket");
        require(helper, AlchemyMixtureBottle.hasStoredMixture(filledBucket),
                "Mixed compatibility bucket lost its stored mixture marker");
        require(helper, AlchemyMixtureBottle.storedMixture(filledBucket).encode().equals(expected),
                "Mixed compatibility bucket changed the full mixture snapshot");
        require(helper, level.getBlockState(pos).is(Blocks.CAULDRON),
                "Bucket extraction did not empty the source cauldron");

        interact(player, pos);

        require(helper, player.getMainHandItem().is(Items.BUCKET),
                "Repouring the stored mixture bucket did not return an empty Bucket");
        require(helper, level.getBlockEntity(pos) instanceof AlchemyCauldronBlockEntity,
                "Repouring the stored mixture bucket did not recreate the Alchemy Cauldron");
        AlchemyCauldronBlockEntity restored = (AlchemyCauldronBlockEntity) level.getBlockEntity(pos);
        require(helper, restored.mixtureSnapshot().encode().equals(expected),
                "Stored mixture bucket round-trip changed the cauldron mixture");

        player.discard();
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void mixedBottleExtractionConservesCompositionDoseAndPendingReaction(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(LOCAL_CAULDRON_POS);
        AlchemyMixtureState source = mixedState(3);
        double effectBefore = source.effects().get("minecraft:speed").quantity();
        double baseBefore = source.activatedBaseUnits();
        AlchemyCauldronBlockEntity cauldron = placeMixtureCauldron(helper, pos, source);

        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));

        interact(player, pos);

        ItemStack bottle = player.getMainHandItem();
        require(helper, bottle.is(Items.POTION),
                "Mixed cauldron extraction did not produce a potion container");
        require(helper, AlchemyMixtureBottle.hasStoredMixture(bottle),
                "Mixed potion extraction lost its stored mixture snapshot");

        AlchemyMixtureState bottled = AlchemyMixtureBottle.storedMixture(bottle);
        AlchemyMixtureState remaining = cauldron.mixtureSnapshot();
        require(helper, bottled.volumeUnits() == 1 && remaining.volumeUnits() == 2,
                "One-bottle extraction did not split three units into one plus two");
        require(helper, bottled.liquidComposition().equals(source.liquidComposition())
                        && remaining.liquidComposition().equals(source.liquidComposition()),
                "Bottle extraction changed mixed-liquid composition");
        requireNear(helper,
                bottled.effects().get("minecraft:speed").quantity()
                        + remaining.effects().get("minecraft:speed").quantity(),
                effectBefore,
                "Bottle extraction created or destroyed EffectDose quantity");
        requireNear(helper,
                bottled.activatedBaseUnits() + remaining.activatedBaseUnits(),
                baseBefore,
                "Bottle extraction created or destroyed activated-base units");
        require(helper, bottled.reactions().size() == 1 && remaining.reactions().size() == 1,
                "Bottle extraction lost pending reaction state");
        AlchemyMixtureState.Reaction bottledReaction = bottled.reactions().iterator().next();
        AlchemyMixtureState.Reaction remainingReaction = remaining.reactions().iterator().next();
        require(helper,
                bottledReaction.elapsedTicks() == 25
                        && remainingReaction.elapsedTicks() == 25
                        && bottledReaction.requiredTicks() == 100
                        && remainingReaction.requiredTicks() == 100
                        && bottledReaction.dose() == 2
                        && remainingReaction.dose() == 2,
                "Bottle extraction changed pending reaction timing or dose");

        player.discard();
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void mixedLargeFlaskRoundTripPreservesFullCauldronState(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(LOCAL_CAULDRON_POS);
        AlchemyMixtureState source = mixedState(3);
        String expected = source.encode();
        placeMixtureCauldron(helper, pos, source);

        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AlchemyItems.LARGE_POTION_FLASK));

        interact(player, pos);

        ItemStack flask = player.getMainHandItem();
        require(helper, flask.is(AlchemyItems.LARGE_POTION_FLASK),
                "Mixed cauldron fill replaced the Large Flask item");
        require(helper, AlchemyMixtureBottle.storedMixture(flask).encode().equals(expected),
                "Large Flask fill changed the full mixed mixture snapshot");
        require(helper, level.getBlockState(pos).is(Blocks.CAULDRON),
                "Large Flask fill duplicated source cauldron liquid");

        interact(player, pos);

        require(helper, level.getBlockEntity(pos) instanceof AlchemyCauldronBlockEntity,
                "Large Flask pour did not recreate the Alchemy Cauldron");
        AlchemyCauldronBlockEntity restored = (AlchemyCauldronBlockEntity) level.getBlockEntity(pos);
        require(helper, restored.mixtureSnapshot().encode().equals(expected),
                "Large Flask round-trip changed the full mixed mixture snapshot");
        require(helper, AlchemyMixtureBottle.storedMixture(player.getMainHandItem()).isEmpty(),
                "Large Flask retained duplicated liquid after full pour");

        player.discard();
        helper.succeed();
    }

    private static AlchemyCauldronBlockEntity placeMixtureCauldron(
            GameTestHelper helper,
            BlockPos pos,
            AlchemyMixtureState source
    ) {
        var level = helper.getLevel();
        level.setBlockAndUpdate(
                pos,
                AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                        .setValue(LayeredCauldronBlock.LEVEL, source.volumeUnits())
        );
        require(helper, level.getBlockEntity(pos) instanceof AlchemyCauldronBlockEntity,
                "Could not create Alchemy Cauldron block entity");
        AlchemyCauldronBlockEntity cauldron = (AlchemyCauldronBlockEntity) level.getBlockEntity(pos);
        require(helper, cauldron.initializeMixture(source),
                "Could not initialize mixed-liquid cauldron fixture");
        return cauldron;
    }

    private static AlchemyMixtureState mixedState(int volumeUnits) {
        AlchemyMixtureState state = new AlchemyMixtureState(volumeUnits);
        state.setLiquidComposition(LiquidComposition.of(Map.of(
                AlchemyLiquids.WATER_ID, 0.50D,
                AlchemyLiquids.MILK_ID, 0.25D,
                AlchemyLiquids.HONEY_ID, 0.25D
        )));
        state.setActivatedBaseComposition(ActivatedBaseComposition.single(
                Identifier.fromNamespaceAndPath("totem", "alchemy/test_base"),
                1.5D
        ));
        state.putEffect("minecraft:speed", 900.0D, 1);
        state.setStability(84);
        state.addProvenance("test:mixed-container-regression");
        state.addReaction(new AlchemyMixtureState.Reaction(
                "test:mixed-pending",
                "minecraft:sugar",
                25,
                100,
                volumeUnits,
                2,
                null,
                null,
                Map.of("minecraft:speed", new AlchemyMixtureState.EffectDose(900.0D, 1)),
                Map.of("minecraft:speed", new AlchemyMixtureState.EffectDose(1200.0D, 1))
        ));
        return state;
    }

    private static void interact(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        UseBlockCallback.EVENT.invoker().interact(
                player,
                player.level(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
    }

    private static void requireNear(
            GameTestHelper helper,
            double actual,
            double expected,
            String message
    ) {
        require(helper, Math.abs(actual - expected) <= EPSILON,
                message + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
