package dev.totem.alchemy.gametest;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.SignatureBrewDefinition;
import dev.totem.alchemy.mixture.SignatureBrewDeliveryTicket;
import dev.totem.alchemy.mixture.SignatureBrewResolver;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tests escrow preparation only. No simulated player reward is delivered here. */
public final class SignatureBrewEscrowGameTest {
    private static final Identifier SIGNATURE =
            Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");
    private static final UUID RECIPIENT =
            UUID.fromString("3c3fd25e-8195-40e9-a50d-13f73c682611");
    private static final UUID INTRUDER =
            UUID.fromString("63a01eb6-6c51-4f8d-80f1-b22ded8716dc");

    @GameTest(maxTicks = 30)
    public void preparedTicketPersistsWithDebitedLiquidAndBlocksDoublePreparation(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Cannot initialize ready batch");

        var prepared = cauldron.prepareSignatureBottleDelivery(new ItemStack(Items.GLASS_BOTTLE), RECIPIENT);
        require(helper, prepared.isPresent(), "Could not stage a valid drink ticket");
        require(helper, cauldron.mixtureSnapshot().volumeUnits() == 2,
                "Preparing one ticket must debit exactly one liquid unit");
        require(helper, prepared.get().dose().volumeUnits() == 1,
                "Staged ticket must hold exactly one detached unit");
        require(helper, cauldron.hasPendingSignatureDelivery(), "Staged ticket disappeared");
        require(helper, prepared.get().belongsTo(RECIPIENT)
                        && !prepared.get().belongsTo(INTRUDER),
                "Staged ticket has incorrect recipient authorization");
        require(helper, cauldron.pendingSignatureDeliveryFor(RECIPIENT).isPresent()
                        && cauldron.pendingSignatureDeliveryFor(INTRUDER).isEmpty(),
                "Escrow lookup leaked recipient-bound metadata to another player");
        require(helper, cauldron.prepareSignatureBottleDelivery(new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).isEmpty(),
                "One pending ticket allowed a second liquid debit");
        require(helper, cauldron.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE)).isEmpty(),
                "Live synchronous bottle route bypassed a pending escrow");
        require(helper, cauldron.extractMixtureUnits(1).isEmpty(),
                "Generic mixture extraction bypassed a pending escrow");

        AlchemyCauldronBlockEntity restored = restore(helper, cauldron);
        require(helper, restored.mixtureSnapshot().volumeUnits() == 2,
                "Reload did not preserve the depleted source");
        require(helper, restored.pendingSignatureDelivery().isPresent()
                        && prepared.get().transactionId().equals(
                                restored.pendingSignatureDelivery().orElseThrow().transactionId()),
                "Reload did not preserve original transaction ID");
        require(helper, prepared.get().encode().equals(
                        restored.pendingSignatureDelivery().orElseThrow().encode()),
                "Reload changed the pending reward");
        require(helper, restored.pendingSignatureDeliveryFor(RECIPIENT).isPresent()
                        && restored.pendingSignatureDeliveryFor(INTRUDER).isEmpty(),
                "Reload changed recipient identity or allowed an intruder to inspect the escrow");
        require(helper, restored.prepareSignatureBottleDelivery(new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).isEmpty(),
                "Reload allowed duplicate preparation of an unresolved ticket");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void finalDoseEscrowSurvivesEvenWhenSourceMixtureIsEmpty(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(1)), "Cannot initialize last dose");
        var ticket = cauldron.prepareSignatureBottleDelivery(new ItemStack(Items.GLASS_BOTTLE), RECIPIENT);
        require(helper, ticket.isPresent(), "Last dose was not staged");
        require(helper, !cauldron.hasMixture() && cauldron.hasPendingSignatureDelivery(),
                "Last dose must live only in the escrow record");
        AlchemyCauldronBlockEntity restored = restore(helper, cauldron);
        require(helper, !restored.hasMixture()
                        && restored.pendingSignatureDelivery().isPresent()
                        && restored.pendingSignatureDelivery().orElseThrow()
                                .transactionId().equals(ticket.orElseThrow().transactionId()),
                "Last dose disappeared when no mixture_state field was saved");
        require(helper, !restored.initializeMixture(new AlchemyMixtureState(3))
                        && restored.extractSignatureBottle(new ItemStack(Items.GLASS_BOTTLE)).isEmpty(),
                "Escrow was overwritten or re-claimed after reload");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void corruptTicketIsPreservedAndLocksTheCauldron(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Cannot initialize batch");
        CompoundTag stored = cauldron.saveWithFullMetadata(helper.getLevel().registryAccess());
        stored.putString("signature_delivery_ticket", "S1|corrupted");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState state = helper.getLevel().getBlockState(pos);
        BlockEntity loaded = BlockEntity.loadStatic(pos, state, stored, helper.getLevel().registryAccess());
        require(helper, loaded instanceof AlchemyCauldronBlockEntity, "Could not read saved cauldron");
        AlchemyCauldronBlockEntity locked = (AlchemyCauldronBlockEntity) loaded;
        require(helper, locked.hasPendingSignatureDelivery() && locked.pendingSignatureDelivery().isEmpty(),
                "Invalid escrow was silently discarded");
        require(helper, locked.prepareSignatureBottleDelivery(new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).isEmpty()
                        && locked.extractMixtureUnits(1).isEmpty(),
                "Corrupt escrow permitted further output or extraction");
        CompoundTag roundTrip = locked.saveWithFullMetadata(helper.getLevel().registryAccess());
        require(helper, "S1|corrupted".equals(roundTrip.getStringOr("signature_delivery_ticket", "")),
                "Invalid ticket bytes were lost across subsequent saves");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void failedEscrowPreparationLeavesSourceUntouched(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Cannot initialize batch");
        String original = cauldron.mixtureSnapshot().encode();
        require(helper, cauldron.prepareSignatureBottleDelivery(new ItemStack(Items.BUCKET), RECIPIENT).isEmpty(),
                "Incorrect bottle container created a ticket");
        require(helper, !cauldron.hasPendingSignatureDelivery()
                        && original.equals(cauldron.mixtureSnapshot().encode()),
                "Failed preparation consumed liquid or created a ticket");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void nullRecipientCannotCreateOrDebitEscrow(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Cannot initialize ready batch");
        String before = cauldron.mixtureSnapshot().encode();
        require(helper, cauldron.prepareSignatureBottleDelivery(
                        new ItemStack(Items.GLASS_BOTTLE), null).isEmpty(),
                "Unbound ticket unexpectedly created");
        require(helper, before.equals(cauldron.mixtureSnapshot().encode())
                        && !cauldron.hasPendingSignatureDelivery(),
                "Null recipient consumed dose or left an orphaned ticket");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void oldS1EscrowSurvivesReloadWithoutBecomingPlayerClaimable(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Cannot initialize ready batch");
        var prepared = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT);
        require(helper, prepared.isPresent(), "Could not stage receiver-bound ticket");

        SignatureBrewDeliveryTicket ticket = prepared.orElseThrow();
        SignatureBrewDeliveryTicket oldFormat = new SignatureBrewDeliveryTicket(
                ticket.transactionId(), ticket.signatureId(), ticket.result(), ticket.dose());
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState block = helper.getLevel().getBlockState(pos);
        CompoundTag save = cauldron.saveWithFullMetadata(helper.getLevel().registryAccess());
        save.putString("signature_delivery_ticket", oldFormat.encode());
        BlockEntity loaded = BlockEntity.loadStatic(pos, block, save, helper.getLevel().registryAccess());
        require(helper, loaded instanceof AlchemyCauldronBlockEntity, "Could not load legacy ticket");
        AlchemyCauldronBlockEntity quarantined = (AlchemyCauldronBlockEntity) loaded;
        require(helper, quarantined.hasPendingSignatureDelivery()
                        && quarantined.pendingSignatureDelivery().orElseThrow().isLegacyUnbound(),
                "Old S1 ticket was dropped or silently promoted to S2");
        require(helper, quarantined.pendingSignatureDeliveryFor(RECIPIENT).isEmpty()
                        && quarantined.pendingSignatureDeliveryFor(INTRUDER).isEmpty(),
                "Unbound legacy ticket was attributed to a player without proof");
        require(helper, quarantined.prepareSignatureBottleDelivery(
                        new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).isEmpty(),
                "Unresolved old ticket allowed a second preparation");
        helper.succeed();
    }

    private static AlchemyCauldronBlockEntity createCauldron(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        BlockState block = AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3);
        helper.setBlock(relative, block);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(relative))
                instanceof AlchemyCauldronBlockEntity cauldron)) {
            throw helper.assertionException("Could not create alchemy cauldron");
        }
        return cauldron;
    }

    private static AlchemyCauldronBlockEntity restore(
            GameTestHelper helper, AlchemyCauldronBlockEntity source
    ) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState block = helper.getLevel().getBlockState(pos);
        CompoundTag saved = source.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(pos, block, saved, helper.getLevel().registryAccess());
        if (!(loaded instanceof AlchemyCauldronBlockEntity cauldron)) {
            throw helper.assertionException("Escrow cauldron failed to deserialize");
        }
        return cauldron;
    }

    private static AlchemyMixtureState readyMixture(int units) {
        AlchemyMixtureState state = new AlchemyMixtureState(units);
        state.setBaseActivated(true);
        state.addReaction(new AlchemyMixtureState.Reaction(
                "signature:sugar", "minecraft:sugar", 0, 20, units, null, null,
                Map.of(), Map.of()));
        state.addReaction(new AlchemyMixtureState.Reaction(
                "signature:cocoa", "totem:alchemy/cocoa_powder", 0, 40, units, null, null,
                Map.of(), Map.of()));
        if (!state.replaceSignatureGroups(List.of(new SignatureBrewResolver.ReactionGroup(
                SIGNATURE, List.of("signature:sugar", "signature:cocoa"))))) {
            throw new IllegalStateException("Failed to reserve escrow test group");
        }
        var result = new SignatureBrewDefinition.Result(
                SignatureBrewDefinition.Type.BOTTLED_ITEM, SIGNATURE, 1,
                Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null);
        if (!state.commitSignatureGroup(SIGNATURE, result)) {
            throw new IllegalStateException("Failed to commit escrow test group");
        }
        state.tickReactions(40);
        return state;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
