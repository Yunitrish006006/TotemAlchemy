package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.AlchemyCauldronRecipe;
import dev.totem.alchemy.alchemy.AlchemyCauldronRecipes;
import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyCompoundBrewing;
import dev.totem.alchemy.mixture.SignatureBrewDefinition;
import dev.totem.alchemy.mixture.SignatureBrewResolver;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;

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
        require(helper, ticking.claimSignatureResult(HOT_COCOA).isEmpty(),
                "Bottled signature bypassed per-dose extraction");
        for (int remaining = 2; remaining >= 0; remaining--) {
            var claim = ticking.claimSignatureBottle(HOT_COCOA);
            require(helper, claim.isPresent() && claim.get().mixture().volumeUnits() == 1,
                    "Completed signature could not produce a detached one-unit bottle");
            require(helper, ticking.volumeUnits() == remaining,
                    "Signature result did not consume one liquid unit");
        }
        require(helper, ticking.claimSignatureBottle(HOT_COCOA).isEmpty(),
                "Completed signature generated more bottles than its liquid volume");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void legacyHotCocoaCannotSettleInParallelWithCommittedSignature(GameTestHelper helper) {
        AlchemyCauldronRecipe recipe = AlchemyCauldronRecipes.get(HOT_COCOA);
        require(helper, recipe != null, "Hot cocoa legacy recipe is unavailable");
        AlchemyMixtureState mixture = AlchemyCompoundBrewing.initialState(recipe);
        for (AlchemyCauldronRecipe.IngredientStep ingredient : recipe.ingredients()) {
            AlchemyCompoundBrewing.restoreCompletedInput(mixture, recipe, ingredient);
        }
        mixture.addReaction(new AlchemyMixtureState.Reaction(
                "signature:sugar", "minecraft:sugar", 0, 20, mixture.volumeUnits(),
                null, null, Map.of(), Map.of()));
        mixture.addReaction(new AlchemyMixtureState.Reaction(
                "signature:cocoa", "totem:alchemy/cocoa_powder", 0, 40, mixture.volumeUnits(),
                null, null, Map.of(), Map.of()));
        require(helper, mixture.replaceSignatureGroups(List.of(
                new SignatureBrewResolver.ReactionGroup(
                        HOT_COCOA, List.of("signature:sugar", "signature:cocoa")))),
                "Could not reserve hot cocoa group");
        require(helper, mixture.commitSignatureGroup(HOT_COCOA, new SignatureBrewDefinition.Result(
                SignatureBrewDefinition.Type.BOTTLED_ITEM, HOT_COCOA, 1,
                Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null)),
                "Could not commit hot cocoa group");
        require(helper, AlchemyCompoundBrewing.completeIfReady(mixture) == null,
                "Legacy hot cocoa settled while a signature owns its reaction inputs");
        mixture.tickReactions(40);
        require(helper, AlchemyCompoundBrewing.completeIfReady(mixture) == null,
                "Legacy hot cocoa settled after signature members completed");
        require(helper, AlchemyCompoundBrewing.bottledResult(mixture).isEmpty(),
                "Legacy hot cocoa emitted a second result from committed signature");
        require(helper, !AlchemyCompoundBrewing.isReady(mixture),
                "Legacy hot cocoa gained a ready marker during signature settlement");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void uncommittedSignatureReservationStillAllowsOrdinaryCompletion(GameTestHelper helper) {
        AlchemyMixtureState mixture = new AlchemyMixtureState(3);
        // Use a fully activated liquid so ordinary ingredient scaling does not
        // depend on which optional content packs are enabled in this test run.
        mixture.setBaseActivated(true);
        mixture.addReaction(new AlchemyMixtureState.Reaction(
                "signature:sugar", "minecraft:sugar", 0, 20, 3, null, null,
                Map.of(), Map.of("minecraft:speed",
                        new AlchemyMixtureState.EffectDose(400.0D, 0))));
        require(helper, mixture.replaceSignatureGroups(List.of(
                new SignatureBrewResolver.ReactionGroup(
                        HOT_COCOA, List.of("signature:sugar")))),
                "Could not create uncommitted reservation");
        mixture.tickReactions(20);
        require(helper, mixture.signatureGroups().isEmpty() && mixture.signatureProcesses().isEmpty(),
                "Inactive reservation improperly committed after a material finished");
        require(helper, mixture.effects().containsKey("minecraft:speed"),
                "Inactive reservation wrongly suppressed ordinary ingredient result");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void threeSignatureBottlesCarryOneDoseStateAndCannotBeClaimedFourTimes(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        AlchemyMixtureState ready = committedMixture();
        ready.tickReactions(40);
        require(helper, cauldron.initializeMixture(ready), "Could not initialize completed signature batch");

        for (int remaining = 2; remaining >= 0; remaining--) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            require(helper, cauldron.canExtractSignatureBottle(bottle), "Ready result did not accept glass bottle");
            ItemStack output = cauldron.extractSignatureBottle(bottle);
            require(helper, output.is(AlchemyItems.HOT_COCOA) && output.getCount() == 1,
                    "Signature did not issue one hot cocoa item");
            require(helper, AlchemyMixtureBottle.hasStoredMixture(output),
                    "Signature output lost its stored chemistry");
            AlchemyMixtureState dose = AlchemyMixtureBottle.fromPotion(output);
            require(helper, dose.volumeUnits() == 1 && !dose.hasCommittedSignatureProcess(),
                    "Output still contained an outstanding signature claim");
            require(helper, dose.hasProvenance("signature:result:" + HOT_COCOA),
                    "Signature output lost its recipe identity");
            require(helper, cauldron.mixtureSnapshot().volumeUnits() == remaining,
                    "Signature output did not consume exactly one volume unit");
        }
        require(helper, !cauldron.canExtractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE))
                        && cauldron.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE)).isEmpty(),
                "Empty batch still issued another signature item");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void signatureBottleClaimSurvivesCauldronSaveAndLoad(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        AlchemyMixtureState ready = committedMixture();
        ready.tickReactions(40);
        require(helper, cauldron.initializeMixture(ready), "Could not initialize signature batch");
        require(helper, cauldron.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE))
                        .is(AlchemyItems.HOT_COCOA), "Could not claim the first signature bottle");

        BlockPos relative = new BlockPos(2, 2, 2);
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(pos);
        CompoundTag persisted = cauldron.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(pos, state, persisted, helper.getLevel().registryAccess());
        require(helper, loaded instanceof AlchemyCauldronBlockEntity,
                "Saved signature cauldron did not deserialize");
        AlchemyCauldronBlockEntity restored = (AlchemyCauldronBlockEntity) loaded;
        require(helper, restored.mixtureSnapshot().volumeUnits() == 2,
                "Save/load restored already-redeemed liquid");
        require(helper, restored.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE))
                        .is(AlchemyItems.HOT_COCOA), "Second bottle was lost after reload");
        require(helper, restored.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE))
                        .is(AlchemyItems.HOT_COCOA), "Final bottle was lost after reload");
        require(helper, restored.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE)).isEmpty(),
                "Save/load allowed signature bottle replay");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void unfinishedSignatureCannotBeBottled(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(committedMixture()),
                "Could not initialize pending signature");
        require(helper, !cauldron.canExtractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE)),
                "Unfinished signature was bottled");
        require(helper, cauldron.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE)).isEmpty(),
                "Unfinished signature issued an item");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void signatureBottleRejectsWrongContainersWithoutLoss(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        AlchemyMixtureState ready = committedMixture();
        ready.tickReactions(40);
        require(helper, cauldron.initializeMixture(ready), "Could not initialize ready signature");
        String before = cauldron.mixtureSnapshot().encode();
        require(helper, !cauldron.canExtractSignatureBottle(new ItemStack(Items.BUCKET)),
                "Wrong input container was accepted");
        require(helper, cauldron.extractSignatureBottle(new ItemStack(Items.BUCKET)).isEmpty(),
                "Wrong input container issued an item");
        require(helper, before.equals(cauldron.mixtureSnapshot().encode()),
                "Invalid signature bottle attempt consumed liquid");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void signatureResultPotionEffectsAreWrittenToStoredDrink(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        AlchemyMixtureState state = committedMixtureWithResult(new SignatureBrewDefinition.Result(
                SignatureBrewDefinition.Type.BOTTLED_ITEM, HOT_COCOA, 1,
                Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"),
                Identifier.fromNamespaceAndPath("totem", "alchemy/saturation")));
        state.tickReactions(40);
        require(helper, cauldron.initializeMixture(state), "Could not initialize potion-result signature");
        ItemStack output = cauldron.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE));
        require(helper, output.is(AlchemyItems.HOT_COCOA), "Potion signature did not yield hot cocoa");
        AlchemyMixtureState oneDose = AlchemyMixtureBottle.storedMixture(output);
        require(helper, oneDose.effects().containsKey("minecraft:saturation"),
                "Configured signature potion effects were omitted from the drink");
        require(helper, oneDose.volumeUnits() == 1 && !oneDose.hasCommittedSignatureProcess(),
                "Signature potion output retained an outstanding claim");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void playerRightClickExchangesExactlyThreeSignatureBottles(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        AlchemyMixtureState ready = committedMixture();
        ready.tickReactions(40);
        require(helper, cauldron.initializeMixture(ready), "Could not initialize ready signature batch");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.getAbilities().instabuild = false;
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            for (int remaining = 2; remaining >= 0; remaining--) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
            InteractionResult response = UseBlockCallback.EVENT.invoker().interact(
                    player, helper.getLevel(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            require(helper, response == InteractionResult.SUCCESS,
                    "Real server interaction did not accept signature bottle extraction");
            ItemStack output = player.getItemInHand(InteractionHand.MAIN_HAND);
            require(helper, output.is(AlchemyItems.HOT_COCOA) && output.getCount() == 1,
                    "Right-click failed to exchange the glass bottle for exactly one drink");
            require(helper, AlchemyMixtureBottle.fromPotion(output).volumeUnits() == 1,
                    "Delivered drink did not retain one-volume-unit chemistry");
            if (remaining > 0) {
                require(helper, cauldron.mixtureSnapshot().volumeUnits() == remaining,
                        "Cauldron display state lost the expected remaining volume");
            }
        }
        require(helper, helper.getLevel().getBlockState(pos).is(Blocks.CAULDRON),
                "Last signature bottle did not restore an empty cauldron");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        ItemStack extra = cauldron.extractSignatureBottle(player.getItemInHand(InteractionHand.MAIN_HAND));
            require(helper, extra.isEmpty(), "A fourth bottle was issued from an already exhausted batch");
            helper.succeed();
        } finally {
            // All GameTests run in the same server/world: leaving a mock player
            // behind can steal the nearest-player discovery from unrelated tests.
            player.discard();
        }
    }

    @GameTest(maxTicks = 30)
    public void unsupportedSignatureDrinkResultNeverConsumesCauldronVolume(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        AlchemyMixtureState ready = committedMixtureWithResult(
                new SignatureBrewDefinition.Result(
                        SignatureBrewDefinition.Type.BOTTLED_ITEM,
                        Identifier.fromNamespaceAndPath("minecraft", "dirt"),
                        1, Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null));
        ready.tickReactions(40);
        require(helper, cauldron.initializeMixture(ready), "Could not initialize unsupported result batch");
        String before = cauldron.mixtureSnapshot().encode();
        require(helper, !cauldron.canExtractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE))
                        && cauldron.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE)).isEmpty(),
                "Non-drink item incorrectly accepted as a signature drink output");
        require(helper, before.equals(cauldron.mixtureSnapshot().encode()),
                "Unsupported output caused permanent loss of a signature liquid unit");
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
        return committedMixtureWithResult(new SignatureBrewDefinition.Result(
                SignatureBrewDefinition.Type.BOTTLED_ITEM, HOT_COCOA, 1,
                Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null));
    }

    private static AlchemyMixtureState committedMixtureWithResult(SignatureBrewDefinition.Result result) {
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
        if (!state.commitSignatureGroup(HOT_COCOA, result)) {
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
