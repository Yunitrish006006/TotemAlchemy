package dev.totem.alchemy.gametest;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.SignatureBrewDefinition;
import dev.totem.alchemy.mixture.SignatureBrewResolver;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/** Server-side contract: committed signatures cannot be drained as ordinary potion bottles. */
public final class SignatureBrewCauldronSafetyGameTest {
    private static final Identifier HOT_COCOA = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");

    @GameTest(maxTicks = 30)
    public void committedSignatureCannotEscapeThroughOrdinaryBottleOrIngredientPath(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        AlchemyMixtureState source = committedMixture();
        require(helper, cauldron.initializeMixture(source), "Could not initialize signature mixture");

        String before = cauldron.mixtureSnapshot().encode();
        require(helper, !cauldron.canExtractMixtureBottle(), "Committed signature became an ordinary potion");
        require(helper, cauldron.extractMixtureBottle().isEmpty(), "Bottle drained a reserved signature batch");
        require(helper, !cauldron.scheduleMixtureReaction(helper.getLevel(), new ItemStack(Items.SUGAR)),
                "Committed signature accepted a competing ordinary reaction");
        require(helper, before.equals(cauldron.mixtureSnapshot().encode()),
                "Rejected bottle/ingredient action mutated the committed group");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void normalMixtureStillAllowsStandardBottleExtraction(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(new AlchemyMixtureState(3)),
                "Could not initialize normal mixture");
        require(helper, cauldron.canExtractMixtureBottle(), "Normal mixture lost ordinary bottling");
        require(helper, cauldron.extractMixtureBottle().volumeUnits() == 1,
                "Normal mixture did not yield one ordinary bottle unit");
        require(helper, cauldron.mixtureSnapshot().volumeUnits() == 2,
                "Normal extraction did not preserve remaining two units");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void completedSignatureDoesNotTurnIntoOrdinaryPotionOrOvercook(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(committedMixture()),
                "Could not initialize committed signature");
        // A plain direct mixture tick exercises the same state machine as serverTick,
        // without relying on the structure's heat source during this contract test.
        AlchemyMixtureState ticking = cauldron.mixtureSnapshot();
        ticking.tickReactions(40);
        require(helper, ticking.signatureProcesses().size() == 1
                        && ticking.signatureProcesses().iterator().next().ready(),
                "Signature was not ready when all members completed");
        require(helper, ticking.effects().isEmpty(), "Ordinary effects leaked from claimed members");
        require(helper, !ticking.canOvercook(), "Unclaimed signature result became overcookable");
        require(helper, ticking.claimSignatureResult(HOT_COCOA).isPresent(),
                "Completed signature could not be claimed");
        helper.succeed();
    }

    private static AlchemyCauldronBlockEntity createCauldron(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        BlockState block = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3);
        helper.setBlock(relative, block);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(relative))
                instanceof AlchemyCauldronBlockEntity cauldron)) {
            throw helper.assertionException("Alchemy cauldron block entity not created");
        }
        return cauldron;
    }

    private static AlchemyMixtureState committedMixture() {
        AlchemyMixtureState state = new AlchemyMixtureState(3);
        state.setBaseActivated(true);
        state.addReaction(new AlchemyMixtureState.Reaction(
                "signature:sugar", "minecraft:sugar", 0, 20, 3, null, null, Map.of(),
                Map.of("minecraft:speed", new AlchemyMixtureState.EffectDose(400, 0))));
        state.addReaction(new AlchemyMixtureState.Reaction(
                "signature:cocoa", "totem:alchemy/cocoa_powder", 0, 40, 3, null, null, Map.of(),
                Map.of("minecraft:strength", new AlchemyMixtureState.EffectDose(400, 0))));
        if (!state.replaceSignatureGroups(List.of(new SignatureBrewResolver.ReactionGroup(
                HOT_COCOA, List.of("signature:sugar", "signature:cocoa"))))) {
            throw new IllegalStateException("Could not reserve test signature reactions");
        }
        if (!state.commitSignatureGroup(HOT_COCOA, new SignatureBrewDefinition.Result(
                SignatureBrewDefinition.Type.BOTTLED_ITEM, HOT_COCOA, 1,
                Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null))) {
            throw new IllegalStateException("Could not commit test signature group");
        }
        return state;
    }

    private static void require(GameTestHelper helper, boolean condition, String reason) {
        if (!condition) {
            throw helper.assertionException(reason);
        }
    }
}
