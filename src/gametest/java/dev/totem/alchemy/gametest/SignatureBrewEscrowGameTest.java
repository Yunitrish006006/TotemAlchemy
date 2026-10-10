package dev.totem.alchemy.gametest;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.SignatureBrewDefinition;
import dev.totem.alchemy.mixture.SignatureBrewDeliveryTicket;
import dev.totem.alchemy.mixture.SignatureBrewDeliveryProgress;
import dev.totem.alchemy.mixture.SignatureBrewBottleOutput;
import dev.totem.alchemy.mixture.SignatureBrewRewardReceipt;
import dev.totem.alchemy.mixture.SignatureBrewReceiptIndex;
import dev.totem.alchemy.mixture.SignatureBrewPlayerReceiptSavedData;
import dev.totem.alchemy.mixture.SignatureBrewRecoveryAssessment;
import dev.totem.alchemy.mixture.SignatureBrewTransactionRegistry;
import dev.totem.alchemy.mixture.SignatureBrewTransactionSavedData;
import dev.totem.alchemy.mixture.SignatureBrewClosurePreflight;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.registry.AlchemyItems;
import dev.totem.alchemy.mixture.SignatureBrewResolver;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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

    @GameTest(maxTicks = 30)
    public void deliveryAttemptIsPersistedAndNeverAutomaticallyRepeated(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize ready batch");
        var staged = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        require(helper, cauldron.signatureDeliveryProgress().orElseThrow().phase()
                        == SignatureBrewDeliveryProgress.Phase.PREPARED,
                "New ticket has no prepared journal state");
        require(helper, !cauldron.markSignatureDeliveryAttempt(staged.transactionId(), INTRUDER),
                "Wrong recipient advanced the delivery journal");
        require(helper, !cauldron.markSignatureDeliveryAttempt(INTRUDER, RECIPIENT),
                "Wrong transaction advanced the delivery journal");
        require(helper, cauldron.markSignatureDeliveryAttempt(staged.transactionId(), RECIPIENT),
                "Expected first delivery attempt to mark the escrow uncertain");
        require(helper, !cauldron.markSignatureDeliveryAttempt(staged.transactionId(), RECIPIENT),
                "Repeated attempt was incorrectly authorized");

        AlchemyCauldronBlockEntity restored = restore(helper, cauldron);
        require(helper, restored.signatureDeliveryProgress().orElseThrow().needsReconciliation(),
                "Reload forgot that an item may already have been delivered");
        require(helper, !restored.markSignatureDeliveryAttempt(staged.transactionId(), RECIPIENT)
                        && restored.prepareSignatureBottleDelivery(
                                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).isEmpty(),
                "Restart made uncertain item issuance retryable");
        require(helper, restored.pendingSignatureDelivery().orElseThrow()
                        .transactionId().equals(staged.transactionId()),
                "Uncertain journal lost original ticket identity");
        require(helper, restored.mixtureSnapshot().volumeUnits() == 2,
                "Reloaded uncertain journal changed spent liquid volume");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void unjournaledS2TicketIsQuarantinedAsUncertainOnReload(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize ready batch");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        CompoundTag saved = cauldron.saveWithFullMetadata(helper.getLevel().registryAccess());
        saved.remove("signature_delivery_progress");
        AlchemyCauldronBlockEntity restored = load(helper, saved);
        require(helper, restored.signatureDeliveryProgress().orElseThrow().needsReconciliation(),
                "Missing journal granted an unsafe fresh delivery attempt");
        require(helper, !restored.markSignatureDeliveryAttempt(ticket.transactionId(), RECIPIENT),
                "Unjournaled old ticket was made automatically retryable");
        require(helper, restored.pendingSignatureDeliveryFor(RECIPIENT).isPresent(),
                "The recoverable original ticket itself was discarded");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void foreignOrCorruptDeliveryJournalIsRetainedAndFailsClosed(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize ready batch");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        CompoundTag saved = cauldron.saveWithFullMetadata(helper.getLevel().registryAccess());
        String foreign = "J1|" + INTRUDER + "|" + RECIPIENT + "|PREPARED";
        saved.putString("signature_delivery_progress", foreign);
        AlchemyCauldronBlockEntity quarantined = load(helper, saved);
        require(helper, quarantined.signatureDeliveryProgress().isEmpty(),
                "Contradictory journal was trusted");
        require(helper, !quarantined.markSignatureDeliveryAttempt(ticket.transactionId(), RECIPIENT)
                        && quarantined.hasPendingSignatureDelivery()
                        && quarantined.extractMixtureUnits(1).isEmpty(),
                "Contradictory journal unlocked an escrow");
        CompoundTag reserialized = quarantined.saveWithFullMetadata(helper.getLevel().registryAccess());
        require(helper, foreign.equals(reserialized.getStringOr("signature_delivery_progress", "")),
                "Contradictory journal was deleted or rewritten during reload");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void signedPreparedRewardCarriesTransactionAndChemistryWithoutPlayerPayout(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize signature mix");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        ItemStack output = SignatureBrewBottleOutput.createWithReceipt(ticket);
        require(helper, output.is(AlchemyItems.HOT_COCOA) && output.getCount() == 1,
                "Prepared reward was not the expected drink");
        require(helper, AlchemyMixtureBottle.hasStoredMixture(output)
                        && AlchemyMixtureBottle.storedMixture(output).volumeUnits() == 1,
                "Preparing a signed reward discarded its one-unit chemistry");
        var parsed = SignatureBrewRewardReceipt.inspect(output).orElseThrow();
        require(helper, parsed.matches(ticket),
                "Prepared item's transaction ID, recipient or result did not match escrow");
        require(helper, SignatureBrewRewardReceipt.inspect(output.copy()).orElseThrow().matches(ticket),
                "Receipt marker did not survive ItemStack copy");
        require(helper, cauldron.signatureDeliveryProgress().orElseThrow().phase()
                        == SignatureBrewDeliveryProgress.Phase.PREPARED,
                "Constructing a detached item illegally marked player issuance as attempted");
        require(helper, cauldron.mixtureSnapshot().volumeUnits() == 2,
                "Constructing a detached item consumed a second volume unit");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void stampedRewardRejectsWrongItemAndReusedReceipt(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize signature mix");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        var receipt = SignatureBrewRewardReceipt.fromTicket(ticket).orElseThrow();
        var output = SignatureBrewBottleOutput.createWithReceipt(ticket);
        require(helper, !output.isEmpty(), "Could not create initial receipt-tagged output");

        require(helper, receipt.stamp(output).isEmpty(),
                "Same physical stack was allowed to acquire a second receipt");
        ItemStack forgedItem = new ItemStack(Items.DIRT);
        forgedItem.set(DataComponents.CUSTOM_DATA,
                output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
        require(helper, SignatureBrewRewardReceipt.inspect(forgedItem).isEmpty(),
                "Receipt on wrong item type was accepted");
        ItemStack wrongDose = new ItemStack(AlchemyItems.HOT_COCOA);
        wrongDose.set(DataComponents.CUSTOM_DATA,
                output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
        AlchemyMixtureBottle.writeState(wrongDose, new AlchemyMixtureState(1));
        require(helper, SignatureBrewRewardReceipt.inspect(wrongDose).isEmpty(),
                "Item with wrong signature chemistry was accepted");
        ItemStack stacked = output.copy();
        stacked.setCount(2);
        require(helper, SignatureBrewRewardReceipt.inspect(stacked).isEmpty(),
                "Multiple physical bottles claimed a single transaction receipt");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void unboundLegacyTicketCannotBeTurnedIntoSignedReward(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize signature mix");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        var legacy = new SignatureBrewDeliveryTicket(
                ticket.transactionId(), ticket.signatureId(), ticket.result(), ticket.dose());
        require(helper, SignatureBrewBottleOutput.createWithReceipt(legacy).isEmpty(),
                "Old S1 unbound ticket unexpectedly generated a payable reward");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void playerReceiptLedgerPersistsObservationIndependentOfConsumedItem(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize signature mix");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        ItemStack marked = SignatureBrewBottleOutput.createWithReceipt(ticket);
        require(helper, !marked.isEmpty(), "Could not create receipt-tagged output");

        var ledger = new SignatureBrewPlayerReceiptSavedData();
        require(helper, ledger.observe(RECIPIENT, ticket, marked)
                        == SignatureBrewReceiptIndex.RecordResult.RECORDED,
                "Initial observation was not recorded");
        require(helper, ledger.observe(RECIPIENT, ticket, marked)
                        == SignatureBrewReceiptIndex.RecordResult.ALREADY_RECORDED,
                "Repeated observation was recorded twice");
        require(helper, ledger.lookup(INTRUDER, ticket.transactionId())
                        == SignatureBrewReceiptIndex.Lookup.WRONG_RECIPIENT,
                "Different recipient was allowed to read another player's transaction");

        // Serialize with the actual SavedData Codec, then simulate destruction
        // of the one-dose item before deserializing the independent ledger.
        var serialized = SignatureBrewPlayerReceiptSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, ledger).getOrThrow();
        marked = ItemStack.EMPTY;
        var restored = SignatureBrewPlayerReceiptSavedData.CODEC
                .parse(JsonOps.INSTANCE, serialized).getOrThrow();
        require(helper, restored.lookup(RECIPIENT, ticket.transactionId())
                        == SignatureBrewReceiptIndex.Lookup.OBSERVED,
                "Item removal caused the persisted observation to disappear");
        require(helper, restored.lookup(RECIPIENT, INTRUDER)
                        == SignatureBrewReceiptIndex.Lookup.NOT_OBSERVED,
                "Unknown transaction was misinterpreted as already observed");
        // Neither OBSERVED nor NOT_OBSERVED authorizes a payout.
        require(helper, cauldron.signatureDeliveryProgress().orElseThrow().phase()
                        == SignatureBrewDeliveryProgress.Phase.PREPARED,
                "Observing a receipt unexpectedly advanced the cauldron journal");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void playerReceiptLedgerRejectsWrongOwnerAndDifferentTicketWithoutChange(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize signature mix");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        ItemStack marked = SignatureBrewBottleOutput.createWithReceipt(ticket);
        var ledger = new SignatureBrewPlayerReceiptSavedData();
        require(helper, ledger.observe(INTRUDER, ticket, marked)
                        == SignatureBrewReceiptIndex.RecordResult.WRONG_RECIPIENT,
                "Wrong receiver was allowed to record a reward observation");
        var unrelated = new SignatureBrewDeliveryTicket(
                INTRUDER, RECIPIENT, ticket.signatureId(), ticket.result(), ticket.dose());
        require(helper, ledger.observe(RECIPIENT, unrelated, marked)
                        == SignatureBrewReceiptIndex.RecordResult.WRONG_RECIPIENT,
                "A different transaction used this item as its receipt");
        require(helper, ledger.lookup(RECIPIENT, ticket.transactionId())
                        == SignatureBrewReceiptIndex.Lookup.NOT_OBSERVED,
                "Rejected observation silently entered the ledger");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void corruptedOrConflictedPlayerReceiptLedgerRetainsEvidenceAndFailsClosed(GameTestHelper helper) {
        var first = new SignatureBrewRewardReceipt(
                RECIPIENT, RECIPIENT, SIGNATURE, SIGNATURE);
        var otherOwner = new SignatureBrewRewardReceipt(
                RECIPIENT, INTRUDER, SIGNATURE, SIGNATURE);
        JsonObject serialized = new JsonObject();
        JsonArray receipts = new JsonArray();
        receipts.add(first.encode());
        receipts.add(otherOwner.encode());
        receipts.add("R2|unknown-format");
        serialized.add("observations", receipts);

        var ledger = SignatureBrewPlayerReceiptSavedData.CODEC
                .parse(JsonOps.INSTANCE, serialized).getOrThrow();
        require(helper, ledger.needsManualRecovery(), "Corrupted/contradictory records were trusted");
        require(helper, ledger.lookup(RECIPIENT, RECIPIENT)
                        == SignatureBrewReceiptIndex.Lookup.UNTRUSTED_LEDGER,
                "Untrusted ledger lookup was treated as verified receipt");
        var roundTrip = SignatureBrewPlayerReceiptSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, ledger).getOrThrow();
        require(helper, roundTrip.toString().contains("R2|unknown-format")
                        && roundTrip.toString().contains(first.encode())
                        && roundTrip.toString().contains(otherOwner.encode()),
                "Untrusted receipt evidence was deleted during serialization");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void independentChunkAndAuditSavesCannotAuthorizeAutomaticPayout(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize signature mix");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        // Snapshot one independent persistence boundary before the attempt.
        AlchemyCauldronBlockEntity preparedReload = restore(helper, cauldron);
        require(helper, cauldron.markSignatureDeliveryAttempt(ticket.transactionId(), RECIPIENT),
                "Could not record delivery attempt");
        AlchemyCauldronBlockEntity attemptedReload = restore(helper, cauldron);

        var audit = new SignatureBrewPlayerReceiptSavedData();
        var emptyAuditReload = SignatureBrewPlayerReceiptSavedData.CODEC
                .parse(JsonOps.INSTANCE, SignatureBrewPlayerReceiptSavedData.CODEC
                        .encodeStart(JsonOps.INSTANCE, audit).getOrThrow()).getOrThrow();
        ItemStack signedDrink = SignatureBrewBottleOutput.createWithReceipt(ticket);
        require(helper, audit.observe(RECIPIENT, ticket, signedDrink)
                        == SignatureBrewReceiptIndex.RecordResult.RECORDED,
                "Could not observe signed drink");
        var observedAuditReload = SignatureBrewPlayerReceiptSavedData.CODEC
                .parse(JsonOps.INSTANCE, SignatureBrewPlayerReceiptSavedData.CODEC
                        .encodeStart(JsonOps.INSTANCE, audit).getOrThrow()).getOrThrow();

        // Four possible independently saved states, *not* atomic ordering.
        var preparedEmpty = SignatureBrewRecoveryAssessment.assessEvidence(
                ticket.transactionId(), RECIPIENT, preparedReload.pendingSignatureDelivery().orElseThrow(),
                preparedReload.signatureDeliveryProgress().orElseThrow(),
                emptyAuditReload.compareTicket(ticket));
        var preparedObserved = SignatureBrewRecoveryAssessment.assessEvidence(
                ticket.transactionId(), RECIPIENT, preparedReload.pendingSignatureDelivery().orElseThrow(),
                preparedReload.signatureDeliveryProgress().orElseThrow(),
                observedAuditReload.compareTicket(ticket));
        var attemptedEmpty = SignatureBrewRecoveryAssessment.assessEvidence(
                ticket.transactionId(), RECIPIENT, attemptedReload.pendingSignatureDelivery().orElseThrow(),
                attemptedReload.signatureDeliveryProgress().orElseThrow(),
                emptyAuditReload.compareTicket(ticket));
        var attemptedObserved = SignatureBrewRecoveryAssessment.assessEvidence(
                ticket.transactionId(), RECIPIENT, attemptedReload.pendingSignatureDelivery().orElseThrow(),
                attemptedReload.signatureDeliveryProgress().orElseThrow(),
                observedAuditReload.compareTicket(ticket));

        require(helper, preparedEmpty.finding()
                        == SignatureBrewRecoveryAssessment.Finding.PREPARED_WITH_NO_OBSERVATION,
                "Prepared + no receipt should remain unresolved");
        require(helper, preparedObserved.finding()
                        == SignatureBrewRecoveryAssessment.Finding.PREPARED_WITH_OBSERVATION,
                "Prepared + observed item incorrectly implied delivery");
        require(helper, attemptedEmpty.finding()
                        == SignatureBrewRecoveryAssessment.Finding.ISSUANCE_UNCERTAIN_WITH_NO_OBSERVATION,
                "Attempt + no receipt incorrectly implied missed payout");
        require(helper, attemptedObserved.finding()
                        == SignatureBrewRecoveryAssessment.Finding.ISSUANCE_UNCERTAIN_WITH_OBSERVATION,
                "Attempt + observed item incorrectly implied confirmed payout");
        for (var decision : List.of(preparedEmpty, preparedObserved, attemptedEmpty, attemptedObserved)) {
            require(helper, decision.requiresIndependentRecovery()
                            && !decision.allowsAutomaticPayout()
                            && !decision.allowsAutomaticAcknowledgment()
                            && !decision.allowsAutomaticEscrowDeletion(),
                    "A split-save state authorized unsafe automatic settlement");
        }
        require(helper, attemptedReload.mixtureSnapshot().volumeUnits() == 2,
                "Split save changed the debited liquid quota");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void missingLastDoseCauldronCannotBeRestoredFromObservedReceiptAlone(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(1)), "Could not initialize last dose");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        var ledger = new SignatureBrewReceiptIndex();
        ledger.observe(RECIPIENT, SignatureBrewRewardReceipt.fromTicket(ticket).orElseThrow());

        // A final-dose block may be replaced or lost before a consistent save.
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState(), 3);
        var assessment = SignatureBrewRecoveryAssessment.assess(
                ticket.transactionId(), RECIPIENT, null, null, ledger);
        require(helper, assessment.finding() == SignatureBrewRecoveryAssessment.Finding.ESCROW_MISSING,
                "Missing last-dose cauldron was inferred from a player receipt");
        require(helper, !assessment.allowsAutomaticPayout()
                        && !assessment.allowsAutomaticEscrowDeletion(),
                "Destroyed cauldron triggered unauthorized reward replay");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void corruptedIndependentReceiptDoesNotResolvePreparedCauldron(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize ready batch");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        var restored = restore(helper, cauldron);
        JsonObject serialized = new JsonObject();
        JsonArray receipts = new JsonArray();
        receipts.add("R9|future-ledger");
        serialized.add("observations", receipts);
        var invalidLedger = SignatureBrewPlayerReceiptSavedData.CODEC
                .parse(JsonOps.INSTANCE, serialized).getOrThrow();
        var decision = SignatureBrewRecoveryAssessment.assessEvidence(
                ticket.transactionId(), RECIPIENT, restored.pendingSignatureDelivery().orElseThrow(),
                restored.signatureDeliveryProgress().orElseThrow(),
                invalidLedger.compareTicket(ticket));
        require(helper, decision.finding()
                        == SignatureBrewRecoveryAssessment.Finding.RECEIPT_LEDGER_UNTRUSTED,
                "Future ledger schema accidentally became a negative delivery receipt");
        require(helper, decision.requiresIndependentRecovery()
                        && !decision.allowsAutomaticPayout(),
                "Corrupt ledger authorized duplicate reward");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void globalTransactionIndexRetainsLastDoseEscrowAfterCauldronRemoval(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(1)), "Could not initialize last signature dose");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");

        var registry = new SignatureBrewTransactionSavedData();
        require(helper, registry.register(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                "Could not store immutable original transaction in world registry");
        require(helper, registry.verify(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.Verification.EXACT_SNAPSHOT,
                "World registry changed original escrow content");
        var saved = SignatureBrewTransactionSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, registry).getOrThrow();

        helper.getLevel().setBlock(position, net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState(), 3);
        var recovered = SignatureBrewTransactionSavedData.CODEC
                .parse(JsonOps.INSTANCE, saved).getOrThrow();
        require(helper, recovered.lookup(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.LookupResult.PRESENT,
                "Removing the last-dose block deleted the separate transaction record");
        var original = recovered.inspect(ticket.transactionId()).orElseThrow();
        require(helper, original.ticket().encode().equals(ticket.encode())
                        && original.source().packedBlockPos() == position.asLong(),
                "World registry lost canonical source and one-dose reward after reload");
        require(helper, recovered.register(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.ALREADY_REGISTERED,
                "Repeated registration did not deduplicate saved transaction");
        // A saved original reward is NOT evidence of inventory payout.
        var decision = SignatureBrewRecoveryAssessment.assess(
                ticket.transactionId(), RECIPIENT, null, null, new SignatureBrewReceiptIndex());
        require(helper, decision.finding() == SignatureBrewRecoveryAssessment.Finding.ESCROW_MISSING
                        && !decision.allowsAutomaticPayout(),
                "Retained world reward unexpectedly authorized payout after block destruction");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void sourceAndTransactionConflictsDoNotOverwriteCanonicalReward(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize ready batch");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        var registry = new SignatureBrewTransactionSavedData();
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        require(helper, registry.register(dimension, pos, ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                "Could not register first escrow");
        var newIdSameSource = new SignatureBrewDeliveryTicket(
                INTRUDER, RECIPIENT, ticket.signatureId(), ticket.result(), ticket.dose());
        require(helper, registry.register(dimension, pos, newIdSameSource)
                        == SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                "Same cauldron registered a second unresolved transaction");
        require(helper, registry.register(dimension, pos.offset(1, 0, 0), ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_TRANSACTION,
                "Same transaction ID registered a different cauldron");
        require(helper, registry.verify(dimension, pos, newIdSameSource)
                        == SignatureBrewTransactionRegistry.Verification.ABSENT_UNVERIFIED,
                "Rejected transaction was accidentally recorded");
        require(helper, registry.lookup(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.LookupResult.PRESENT,
                "Rejected collision erased canonical transaction");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void worldTransactionIndexPreservesUnknownAndConflictingEntries(GameTestHelper helper) {
        JsonObject saved = new JsonObject();
        JsonArray entries = new JsonArray();
        var ticket = new SignatureBrewDeliveryTicket(
                RECIPIENT, RECIPIENT, SIGNATURE,
                new SignatureBrewDefinition.Result(
                        SignatureBrewDefinition.Type.BOTTLED_ITEM, SIGNATURE, 1,
                        Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null),
                readyMixture(1).claimSignatureBottle(SIGNATURE).orElseThrow().mixture());
        var dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        var origin = new SignatureBrewTransactionRegistry.Source(dimension, 123L);
        entries.add(new SignatureBrewTransactionRegistry.Entry(origin, ticket).encode());
        entries.add("A2|unsupported-version");
        saved.add("transactions", entries);
        var restored = SignatureBrewTransactionSavedData.CODEC
                .parse(JsonOps.INSTANCE, saved).getOrThrow();
        require(helper, restored.needsManualRecovery(),
                "Unknown world transaction format was silently discarded");
        require(helper, restored.lookup(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.LookupResult.UNTRUSTED_REGISTRY,
                "Unknown entry allowed canonical payout lookup");
        require(helper, restored.register(dimension, new BlockPos(1, 1, 1), ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.UNTRUSTED_REGISTRY,
                "Unknown transaction data allowed a ledger write");
        String reserialized = SignatureBrewTransactionSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, restored).getOrThrow().toString();
        require(helper, reserialized.contains("A2|unsupported-version"),
                "Unknown entry was lost on SavedData round-trip");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void closureReviewPersistsButCannotUnlockSameCauldronAfterRestart(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Could not initialize escrow source");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        var ledger = new SignatureBrewTransactionSavedData();
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        require(helper, ledger.register(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                "Original transaction could not be registered");
        require(helper, ledger.requestClosureReview(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.ClosureResult.REVIEW_REQUESTED,
                "Could not store the closure review request");
        require(helper, ledger.requestClosureReview(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.ClosureResult.ALREADY_REQUESTED,
                "Repeated review request was not idempotent");

        var stored = SignatureBrewTransactionSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, ledger).getOrThrow();
        var restored = SignatureBrewTransactionSavedData.CODEC
                .parse(JsonOps.INSTANCE, stored).getOrThrow();
        require(helper, restored.closureState(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.ClosureState.REVIEW_REQUESTED,
                "Restart lost pending closure review request");
        require(helper, restored.lookup(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.LookupResult.PRESENT,
                "Review improperly erased the original escrow");
        var newTicket = new SignatureBrewDeliveryTicket(
                INTRUDER, RECIPIENT, ticket.signatureId(), ticket.result(), ticket.dose());
        require(helper, restored.register(dimension, position, newTicket)
                        == SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                "Review request improperly unlocked source before durable acknowledgment");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void lastDoseClosureReviewNeverGrantsPayoutAfterBlockReplacement(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(1)), "Could not initialize one-dose brew");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        var ledger = new SignatureBrewTransactionSavedData();
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        require(helper, ledger.register(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                "Original transaction could not be registered");
        require(helper, ledger.requestClosureReview(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.ClosureResult.REVIEW_REQUESTED,
                "Could not store review request");

        helper.getLevel().setBlock(position,
                net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState(), 3);
        var restored = SignatureBrewTransactionSavedData.CODEC.parse(JsonOps.INSTANCE,
                SignatureBrewTransactionSavedData.CODEC
                        .encodeStart(JsonOps.INSTANCE, ledger).getOrThrow()).getOrThrow();
        require(helper, restored.lookup(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.LookupResult.PRESENT
                        && restored.closureState(ticket.transactionId())
                            == SignatureBrewTransactionRegistry.ClosureState.REVIEW_REQUESTED,
                "Review request lost original escrow after block replacement");
        require(helper, restored.register(dimension, position, new SignatureBrewDeliveryTicket(
                            INTRUDER, RECIPIENT, ticket.signatureId(), ticket.result(), ticket.dose()))
                        == SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                "Missing source block made reviewed escrow re-issuable");
        var result = SignatureBrewRecoveryAssessment.assess(
                ticket.transactionId(), RECIPIENT, null, null, new SignatureBrewReceiptIndex());
        require(helper, !result.allowsAutomaticPayout() && !result.allowsAutomaticEscrowDeletion(),
                "Closure review accidentally authorized recovery payout");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void closurePreflightAcrossSavedWorldAndPlayerDataNeverUnlocksSource(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Cannot initialize signature group");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        var world = new SignatureBrewTransactionSavedData();
        require(helper, world.register(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                "Original world transaction was not registered");
        require(helper, world.requestClosureReview(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.ClosureResult.REVIEW_REQUESTED,
                "C1 review intent could not be persisted");
        var worldReload = SignatureBrewTransactionSavedData.CODEC.parse(JsonOps.INSTANCE,
                SignatureBrewTransactionSavedData.CODEC
                        .encodeStart(JsonOps.INSTANCE, world).getOrThrow()).getOrThrow();

        var observed = new SignatureBrewPlayerReceiptSavedData();
        ItemStack item = SignatureBrewBottleOutput.createWithReceipt(ticket);
        require(helper, observed.observe(RECIPIENT, ticket, item)
                        == SignatureBrewReceiptIndex.RecordResult.RECORDED,
                "Could not record the signed reward as R1 observation");
        var observedReload = SignatureBrewPlayerReceiptSavedData.CODEC.parse(JsonOps.INSTANCE,
                SignatureBrewPlayerReceiptSavedData.CODEC
                        .encodeStart(JsonOps.INSTANCE, observed).getOrThrow()).getOrThrow();

        var prepared = worldReload.assessClosure(dimension, position, ticket.transactionId(),
                restore(helper, cauldron).signatureDeliveryProgress().orElseThrow(), observedReload);
        require(helper, prepared.blocker()
                        == SignatureBrewClosurePreflight.Blocker.PREPARED_WITH_OBSERVATION,
                "Prepared J1 with observed item was misidentified as a confirmed payout");
        require(helper, !prepared.mayReleaseSource() && !prepared.mayPayOut()
                        && !prepared.mayAcknowledge(),
                "Observed item unlocked a source without durable inventory proof");

        require(helper, cauldron.markSignatureDeliveryAttempt(ticket.transactionId(), RECIPIENT),
                "Could not mark first delivery attempt");
        var uncertain = worldReload.assessClosure(dimension, position, ticket.transactionId(),
                restore(helper, cauldron).signatureDeliveryProgress().orElseThrow(), observedReload);
        require(helper, uncertain.blocker()
                        == SignatureBrewClosurePreflight.Blocker.ISSUANCE_UNCERTAIN_WITH_OBSERVATION,
                "Attempted delivery with observed R1 was treated as terminal");
        require(helper, uncertain.requiresAuthoritativeReconciliation()
                        && !uncertain.mayReleaseSource(),
                "Unverified payout improperly released the original cauldron");
        require(helper, worldReload.register(dimension, position, new SignatureBrewDeliveryTicket(
                            INTRUDER, RECIPIENT, ticket.signatureId(), ticket.result(), ticket.dose()))
                        == SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                "Same source accepted a new transaction without terminal fencing");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void closurePreflightRejectsMissingRecipientJournalAndWrongOrigin(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(1)), "Cannot initialize one-dose batch");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        var world = new SignatureBrewTransactionSavedData();
        world.register(dimension, position, ticket);
        world.requestClosureReview(dimension, position, ticket);
        var journal = cauldron.signatureDeliveryProgress().orElseThrow();

        var wrongOrigin = world.assessClosure(dimension, position.offset(1, 0, 0),
                ticket.transactionId(), journal, new SignatureBrewPlayerReceiptSavedData());
        require(helper, wrongOrigin.blocker() == SignatureBrewClosurePreflight.Blocker.SOURCE_MISMATCH,
                "Wrong origin incorrectly matched reviewed escrow");

        var absentReceipt = world.assessClosure(dimension, position,
                ticket.transactionId(), journal, null);
        require(helper, absentReceipt.blocker() == SignatureBrewClosurePreflight.Blocker.LEDGER_UNAVAILABLE,
                "Absent observation ledger was treated as missing reward authorization");

        var absentJournal = world.assessClosure(dimension, position,
                ticket.transactionId(), null, new SignatureBrewPlayerReceiptSavedData());
        require(helper, absentJournal.blocker()
                        == SignatureBrewClosurePreflight.Blocker.JOURNAL_MISSING_OR_MISMATCHED,
                "Missing delivery progress was treated as a new grant opportunity");

        helper.getLevel().setBlock(position,
                net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState(), 3);
        require(helper, world.lookup(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.LookupResult.PRESENT
                        && !absentJournal.mayReleaseSource()
                        && !absentReceipt.mayReleaseSource()
                        && !wrongOrigin.mayReleaseSource(),
                "Destroying final-dose block bypassed review-only origin fencing");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void genesisFencePersistsWithOneDoseGlobalEscrowAfterBlockReplacement(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(1)), "Cannot initialize one-dose brew");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");

        var ledger = new SignatureBrewTransactionSavedData();
        require(helper, ledger.register(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                "Could not register original S2 escrow");
        require(helper, ledger.inspectFence(dimension, position, ticket.transactionId())
                        == SignatureBrewTransactionRegistry.FenceState.GENESIS_MATCH,
                "New A1 escrow did not create an F1 source fence");

        var saved = SignatureBrewTransactionSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, ledger).getOrThrow();
        require(helper, saved.toString().contains("source_fences")
                        && saved.toString().contains("F1|"),
                "Initial source fence was not serialized in world SavedData");

        helper.getLevel().setBlock(position,
                net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState(), 3);
        var restored = SignatureBrewTransactionSavedData.CODEC
                .parse(JsonOps.INSTANCE, saved).getOrThrow();
        require(helper, restored.inspectFence(dimension, position, ticket.transactionId())
                        == SignatureBrewTransactionRegistry.FenceState.GENESIS_MATCH,
                "Source fence was lost after SavedData round trip and last-dose block removal");
        require(helper, restored.lookup(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.LookupResult.PRESENT,
                "F1 source fence lost its original pending A1 transaction");
        require(helper, restored.register(dimension, position, new SignatureBrewDeliveryTicket(
                        INTRUDER, RECIPIENT, ticket.signatureId(), ticket.result(), ticket.dose()))
                        == SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                "F1 genesis mistakenly allowed a second reward from the same origin");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void legacyA1WorldSaveWithoutF1NeverAutoCreatesSourceGeneration(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Cannot initialize pending brew");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        var ledger = new SignatureBrewTransactionSavedData();
        ledger.register(dimension, position, ticket);

        JsonObject legacy = ((JsonObject) SignatureBrewTransactionSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, ledger).getOrThrow()).deepCopy();
        legacy.remove("source_fences");
        var restored = SignatureBrewTransactionSavedData.CODEC
                .parse(JsonOps.INSTANCE, legacy).getOrThrow();
        require(helper, restored.lookup(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.LookupResult.PRESENT,
                "Missing new F1 field made existing A1 snapshot unreadable");
        require(helper, restored.inspectFence(dimension, position, ticket.transactionId())
                        == SignatureBrewTransactionRegistry.FenceState.LEGACY_UNFENCED,
                "Old world save silently fabricated a new source generation");
        require(helper, restored.register(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.ALREADY_REGISTERED,
                "Legacy A1 cannot be read idempotently");
        require(helper, restored.inspectFence(dimension, position, ticket.transactionId())
                        == SignatureBrewTransactionRegistry.FenceState.LEGACY_UNFENCED,
                "Idempotent legacy registration illegally created a new F1 fence");
        require(helper, restored.register(dimension, position, new SignatureBrewDeliveryTicket(
                        INTRUDER, RECIPIENT, ticket.signatureId(), ticket.result(), ticket.dose()))
                        == SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                "Old A1 was permitted to reissue from a missing F1");
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void damagedF1WorldGenerationPreservesBytesAndBlocksRegistryAccess(GameTestHelper helper) {
        AlchemyCauldronBlockEntity cauldron = createCauldron(helper);
        require(helper, cauldron.initializeMixture(readyMixture(3)), "Cannot initialize ready brew");
        var ticket = cauldron.prepareSignatureBottleDelivery(
                new ItemStack(Items.GLASS_BOTTLE), RECIPIENT).orElseThrow();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        Identifier dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        var ledger = new SignatureBrewTransactionSavedData();
        ledger.register(dimension, position, ticket);

        JsonObject damaged = ((JsonObject) SignatureBrewTransactionSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, ledger).getOrThrow()).deepCopy();
        JsonArray corruptFences = new JsonArray();
        corruptFences.add("F2|unsupported-generation");
        damaged.add("source_fences", corruptFences);
        var restored = SignatureBrewTransactionSavedData.CODEC
                .parse(JsonOps.INSTANCE, damaged).getOrThrow();

        require(helper, restored.needsManualRecovery(),
                "Corrupt future F1 data was silently treated as a valid source generation");
        require(helper, restored.inspectFence(dimension, position, ticket.transactionId())
                        == SignatureBrewTransactionRegistry.FenceState.CONFLICT_OR_UNTRUSTED,
                "Unknown generation made original reward claimable");
        require(helper, restored.lookup(ticket.transactionId())
                        == SignatureBrewTransactionRegistry.LookupResult.UNTRUSTED_REGISTRY,
                "Corrupt fence did not quarantine global registry");
        require(helper, restored.register(dimension, position, ticket)
                        == SignatureBrewTransactionRegistry.RegisterResult.UNTRUSTED_REGISTRY,
                "Corrupt fence allowed another registration");
        String roundTrip = SignatureBrewTransactionSavedData.CODEC
                .encodeStart(JsonOps.INSTANCE, restored).getOrThrow().toString();
        require(helper, roundTrip.contains("F2|unsupported-generation"),
                "Invalid fence bytes were deleted when world SaveData was serialized");
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
        CompoundTag saved = source.saveWithFullMetadata(helper.getLevel().registryAccess());
        return load(helper, saved);
    }

    private static AlchemyCauldronBlockEntity load(GameTestHelper helper, CompoundTag saved) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState block = helper.getLevel().getBlockState(pos);
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
