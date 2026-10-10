package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static dev.totem.alchemy.mixture.SignatureBrewRecoveryAssessment.Finding;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewRecoveryAssessmentTest {
    private static final UUID TX = UUID.fromString("44e1d6d5-1ea7-4346-acd2-8a39e478f971");
    private static final UUID OWNER = UUID.fromString("4d5855d5-cfbd-4659-a30b-39987ff57d4c");
    private static final UUID OTHER = UUID.fromString("ada55692-bec4-4e66-8f22-31dc47f78efc");
    private static final Identifier RECIPE = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");
    private static final Identifier ALT = Identifier.fromNamespaceAndPath("totem", "alchemy/cherry_brew");

    private static SignatureBrewDeliveryTicket ticket() {
        AlchemyMixtureState dose = new AlchemyMixtureState(1);
        dose.addProvenance("signature:result:" + RECIPE);
        return new SignatureBrewDeliveryTicket(
                TX, OWNER, RECIPE,
                new SignatureBrewDefinition.Result(
                        SignatureBrewDefinition.Type.BOTTLED_ITEM, RECIPE, 1,
                        Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null),
                dose);
    }

    private static SignatureBrewReceiptIndex observedLedger(SignatureBrewDeliveryTicket ticket) {
        var index = new SignatureBrewReceiptIndex();
        index.observe(OWNER, SignatureBrewRewardReceipt.fromTicket(ticket).orElseThrow());
        return index;
    }

    private static SignatureBrewRecoveryAssessment.Decision check(
            SignatureBrewDeliveryTicket ticket,
            SignatureBrewDeliveryProgress progress,
            SignatureBrewReceiptIndex ledger
    ) {
        return SignatureBrewRecoveryAssessment.assess(TX, OWNER, ticket, progress, ledger);
    }

    private static void mustNotAutoResolve(SignatureBrewRecoveryAssessment.Decision decision) {
        assertTrue(decision.requiresIndependentRecovery());
        assertFalse(decision.allowsAutomaticPayout());
        assertFalse(decision.allowsAutomaticAcknowledgment());
        assertFalse(decision.allowsAutomaticEscrowDeletion());
    }

    @Test
    void crashBeforeEscrowOrDestroyedLastDoseCauldronIsNeverAnAutomaticPayout() {
        var t = ticket();
        for (var ledger : List.of(new SignatureBrewReceiptIndex(), observedLedger(t))) {
            var d = check(null, null, ledger);
            assertEquals(Finding.ESCROW_MISSING, d.finding());
            mustNotAutoResolve(d);
        }
        assertEquals(Finding.INVALID_REQUEST,
                SignatureBrewRecoveryAssessment.assess(null, OWNER, t,
                        SignatureBrewDeliveryProgress.prepared(t), observedLedger(t)).finding());
    }

    @Test
    void crashAfterPrepareBeforeIssueHasNoProofEvenIfObservationExists() {
        var t = ticket();
        var prepared = SignatureBrewDeliveryProgress.prepared(t);
        var without = check(t, prepared, new SignatureBrewReceiptIndex());
        var with = check(t, prepared, observedLedger(t));
        assertEquals(Finding.PREPARED_WITH_NO_OBSERVATION, without.finding());
        assertEquals(Finding.PREPARED_WITH_OBSERVATION, with.finding());
        mustNotAutoResolve(without);
        mustNotAutoResolve(with);
    }

    @Test
    void crashAfterMarkingAttemptBothObservedAndUnobservedRemainAmbiguous() {
        var t = ticket();
        var attempted = SignatureBrewDeliveryProgress.prepared(t)
                .beginIssuance(TX, OWNER).orElseThrow();
        var absent = check(t, attempted, new SignatureBrewReceiptIndex());
        var observed = check(t, attempted, observedLedger(t));
        assertEquals(Finding.ISSUANCE_UNCERTAIN_WITH_NO_OBSERVATION, absent.finding());
        assertEquals(Finding.ISSUANCE_UNCERTAIN_WITH_OBSERVATION, observed.finding());
        mustNotAutoResolve(absent);
        mustNotAutoResolve(observed);
    }

    @Test
    void missingOrReorderedJournalOrLedgerBlocksAllAutomatedRecovery() {
        var t = ticket();
        var good = observedLedger(t);
        assertEquals(Finding.JOURNAL_MISSING_OR_MISMATCHED, check(t, null, good).finding());
        assertEquals(Finding.JOURNAL_MISSING_OR_MISMATCHED,
                check(t, new SignatureBrewDeliveryProgress(TX, OTHER,
                        SignatureBrewDeliveryProgress.Phase.PREPARED), good).finding());
        assertEquals(Finding.RECEIPT_LEDGER_UNAVAILABLE,
                check(t, SignatureBrewDeliveryProgress.prepared(t), null).finding());
        assertEquals(Finding.ESCROW_IDENTITY_MISMATCH,
                SignatureBrewRecoveryAssessment.assess(TX, OTHER, t,
                        SignatureBrewDeliveryProgress.prepared(t), good).finding());
        assertEquals(Finding.LEGACY_UNBOUND_ESCROW,
                check(new SignatureBrewDeliveryTicket(TX, RECIPE, t.result(), t.dose()),
                        null, good).finding());
    }

    @Test
    void transactionCollisionWrongOwnerAndWrongPayloadRequireInvestigation() {
        var t = ticket();
        var progress = SignatureBrewDeliveryProgress.prepared(t);

        var conflict = observedLedger(t);
        conflict.observe(OTHER, new SignatureBrewRewardReceipt(TX, OTHER, RECIPE, RECIPE));
        assertEquals(Finding.RECEIPT_TRANSACTION_CONFLICT,
                check(t, progress, conflict).finding());

        var foreign = new SignatureBrewReceiptIndex();
        foreign.observe(OTHER, new SignatureBrewRewardReceipt(TX, OTHER, RECIPE, RECIPE));
        assertEquals(Finding.RECEIPT_WRONG_RECIPIENT, check(t, progress, foreign).finding());

        var payload = new SignatureBrewReceiptIndex();
        payload.observe(OWNER, new SignatureBrewRewardReceipt(TX, OWNER, RECIPE, ALT));
        assertEquals(Finding.RECEIPT_PAYLOAD_MISMATCH, check(t, progress, payload).finding());

        var damaged = new SignatureBrewReceiptIndex(List.of("R2|unrecognized"));
        assertEquals(Finding.RECEIPT_LEDGER_UNTRUSTED, check(t, progress, damaged).finding());

        for (var ledger : List.of(conflict, foreign, payload, damaged)) {
            mustNotAutoResolve(check(t, progress, ledger));
        }
    }

    @Test
    void oldAcknowledgedLabelIsNotTrustedWithoutDurablePlayerProof() {
        var t = ticket();
        var nominalAck = new SignatureBrewDeliveryProgress(
                TX, OWNER, SignatureBrewDeliveryProgress.Phase.ACKNOWLEDGED);
        for (var ledger : List.of(new SignatureBrewReceiptIndex(), observedLedger(t))) {
            var decision = check(t, nominalAck, ledger);
            assertEquals(Finding.ACKNOWLEDGED_BUT_NOT_DURABLY_VERIFIED, decision.finding());
            mustNotAutoResolve(decision);
        }
    }

    @Test
    void fullReceiptPayloadComparisonDetectsMatchingIdButDifferentRecipe() {
        var t = ticket();
        var ledger = new SignatureBrewReceiptIndex();
        ledger.observe(OWNER, new SignatureBrewRewardReceipt(TX, OWNER, ALT, RECIPE));
        assertEquals(SignatureBrewReceiptIndex.Evidence.PAYLOAD_MISMATCH, ledger.compareTicket(t));
        assertEquals(SignatureBrewReceiptIndex.Evidence.NOT_OBSERVED,
                new SignatureBrewReceiptIndex().compareTicket(t));
        assertEquals(SignatureBrewReceiptIndex.Evidence.MATCHING_OBSERVATION,
                observedLedger(t).compareTicket(t));
        assertEquals(SignatureBrewReceiptIndex.Evidence.UNBOUND_TICKET,
                ledger.compareTicket(new SignatureBrewDeliveryTicket(
                        TX, RECIPE, t.result(), t.dose())));
        assertEquals(Finding.RECEIPT_PAYLOAD_MISMATCH,
                check(t, SignatureBrewDeliveryProgress.prepared(t), ledger).finding());
    }

    @Test
    void codecRoundTripsDoNotTurnUncertainAttemptIntoNewPreparedReward() {
        var t = ticket();
        var uncertain = SignatureBrewDeliveryProgress.prepared(t)
                .beginIssuance(TX, OWNER).orElseThrow();
        var reloaded = SignatureBrewDeliveryProgress.decode(uncertain.encode()).orElseThrow();
        var index = observedLedger(t);
        var reloadedIndex = new SignatureBrewReceiptIndex(index.encodedEntries());
        assertEquals(Finding.ISSUANCE_UNCERTAIN_WITH_OBSERVATION,
                check(SignatureBrewDeliveryTicket.decode(t.encode()).orElseThrow(),
                        reloaded, reloadedIndex).finding());
        assertTrue(reloaded.beginIssuance(TX, OWNER).isEmpty());
    }
}
