package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewClosurePreflightTest {
    private static final UUID TX = UUID.fromString("80d1ae99-e608-4a66-9d58-8c2b1d91f1f6");
    private static final UUID OTHER = UUID.fromString("f72a7597-1316-4275-91d4-6308fb5640d7");
    private static final UUID RECIPIENT = UUID.fromString("ec8e1e37-332b-409a-aa86-2412a857bfe9");
    private static final Identifier SIGNATURE =
            Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");
    private static final SignatureBrewTransactionRegistry.Source SOURCE =
            new SignatureBrewTransactionRegistry.Source(
                    Identifier.fromNamespaceAndPath("minecraft", "overworld"), 781234L);
    private static final SignatureBrewTransactionRegistry.Source WRONG_SOURCE =
            new SignatureBrewTransactionRegistry.Source(
                    Identifier.fromNamespaceAndPath("minecraft", "the_nether"), 781234L);

    private static SignatureBrewDeliveryTicket ticket() {
        AlchemyMixtureState dose = new AlchemyMixtureState(1);
        dose.addProvenance("signature:result:" + SIGNATURE);
        return new SignatureBrewDeliveryTicket(TX, RECIPIENT, SIGNATURE,
                new SignatureBrewDefinition.Result(
                        SignatureBrewDefinition.Type.BOTTLED_ITEM, SIGNATURE, 1,
                        Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null),
                dose);
    }

    private static SignatureBrewTransactionRegistry reviewedRegistry() {
        var ledger = new SignatureBrewTransactionRegistry();
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                ledger.register(SOURCE, ticket()));
        assertEquals(SignatureBrewTransactionRegistry.ClosureResult.REVIEW_REQUESTED,
                ledger.requestClosureReview(SOURCE, ticket()));
        return ledger;
    }

    private static SignatureBrewReceiptIndex observed() {
        var index = new SignatureBrewReceiptIndex();
        index.observe(RECIPIENT, SignatureBrewRewardReceipt.fromTicket(ticket()).orElseThrow());
        return index;
    }

    private static SignatureBrewDeliveryProgress prepared() {
        return SignatureBrewDeliveryProgress.prepared(ticket());
    }

    private static void alwaysDeny(SignatureBrewClosurePreflight.Decision decision) {
        assertTrue(decision.requiresAuthoritativeReconciliation());
        assertFalse(decision.mayReleaseSource());
        assertFalse(decision.mayPayOut());
        assertFalse(decision.mayAcknowledge());
    }

    private static SignatureBrewClosurePreflight.Decision check(
            SignatureBrewTransactionRegistry registry,
            SignatureBrewDeliveryProgress progress,
            SignatureBrewReceiptIndex receipts
    ) {
        return SignatureBrewClosurePreflight.assess(registry, SOURCE, TX, progress, receipts);
    }

    @Test
    void missingOriginalReviewOrWrongSourceCannotUnlockCauldron() {
        var empty = new SignatureBrewTransactionRegistry();
        var absent = check(empty, prepared(), observed());
        assertEquals(SignatureBrewClosurePreflight.Blocker.ORIGINAL_ABSENT, absent.blocker());
        alwaysDeny(absent);

        empty.register(SOURCE, ticket());
        var noReview = check(empty, prepared(), observed());
        assertEquals(SignatureBrewClosurePreflight.Blocker.REVIEW_NOT_REQUESTED, noReview.blocker());
        alwaysDeny(noReview);

        var reviewed = reviewedRegistry();
        var wrong = SignatureBrewClosurePreflight.assess(
                reviewed, WRONG_SOURCE, TX, prepared(), observed());
        assertEquals(SignatureBrewClosurePreflight.Blocker.SOURCE_MISMATCH, wrong.blocker());
        alwaysDeny(wrong);
    }

    @Test
    void preparedEscrowIsNotARewardEvenWithObservedItem() {
        var registry = reviewedRegistry();
        var missingReceipt = check(registry, prepared(), new SignatureBrewReceiptIndex());
        var matchingReceipt = check(registry, prepared(), observed());
        assertEquals(SignatureBrewClosurePreflight.Blocker.PREPARED_WITHOUT_OBSERVATION,
                missingReceipt.blocker());
        assertEquals(SignatureBrewClosurePreflight.Blocker.PREPARED_WITH_OBSERVATION,
                matchingReceipt.blocker());
        alwaysDeny(missingReceipt);
        alwaysDeny(matchingReceipt);
    }

    @Test
    void uncertainAttemptCannotBeClosedWithOrWithoutItemObservation() {
        var progress = prepared().beginIssuance(TX, RECIPIENT).orElseThrow();
        var registry = reviewedRegistry();
        var absent = check(registry, progress, new SignatureBrewReceiptIndex());
        var present = check(registry, progress, observed());
        assertEquals(SignatureBrewClosurePreflight.Blocker.ISSUANCE_UNCERTAIN_WITHOUT_OBSERVATION,
                absent.blocker());
        assertEquals(SignatureBrewClosurePreflight.Blocker.ISSUANCE_UNCERTAIN_WITH_OBSERVATION,
                present.blocker());
        alwaysDeny(absent);
        alwaysDeny(present);
    }

    @Test
    void claimedAcknowledgedStateAloneCannotReleaseSource() {
        var registry = reviewedRegistry();
        var ack = new SignatureBrewDeliveryProgress(
                TX, RECIPIENT, SignatureBrewDeliveryProgress.Phase.ACKNOWLEDGED);
        for (var audit : List.of(new SignatureBrewReceiptIndex(), observed())) {
            var decision = check(registry, ack, audit);
            assertEquals(SignatureBrewClosurePreflight.Blocker.ACKNOWLEDGED_LABEL_NOT_AUTHORITATIVE,
                    decision.blocker());
            alwaysDeny(decision);
        }
    }

    @Test
    void missingOrForeignProgressAndMissingLedgerRequireManualReconciliation() {
        var registry = reviewedRegistry();
        var missingProgress = check(registry, null, observed());
        var wrong = check(registry, new SignatureBrewDeliveryProgress(
                TX, OTHER, SignatureBrewDeliveryProgress.Phase.ISSUANCE_UNCERTAIN), observed());
        var missingLedger = check(registry, prepared(), null);
        assertEquals(SignatureBrewClosurePreflight.Blocker.JOURNAL_MISSING_OR_MISMATCHED,
                missingProgress.blocker());
        assertEquals(SignatureBrewClosurePreflight.Blocker.JOURNAL_MISSING_OR_MISMATCHED,
                wrong.blocker());
        assertEquals(SignatureBrewClosurePreflight.Blocker.LEDGER_UNAVAILABLE,
                missingLedger.blocker());
        alwaysDeny(missingProgress);
        alwaysDeny(wrong);
        alwaysDeny(missingLedger);
    }

    @Test
    void conflictingFutureAndMismatchedReceiptPayloadsCannotResolveReview() {
        var registry = reviewedRegistry();
        var conflict = observed();
        conflict.observe(OTHER, new SignatureBrewRewardReceipt(TX, OTHER, SIGNATURE, SIGNATURE));

        var badOwner = new SignatureBrewReceiptIndex();
        badOwner.observe(OTHER, new SignatureBrewRewardReceipt(TX, OTHER, SIGNATURE, SIGNATURE));

        var badItem = new SignatureBrewReceiptIndex();
        badItem.observe(RECIPIENT, new SignatureBrewRewardReceipt(
                TX, RECIPIENT, SIGNATURE,
                Identifier.fromNamespaceAndPath("totem", "alchemy/cherry_brew")));

        var future = new SignatureBrewReceiptIndex(List.of("R9|not-readable"));

        assertEquals(SignatureBrewClosurePreflight.Blocker.RECEIPT_CONFLICT,
                check(registry, prepared(), conflict).blocker());
        assertEquals(SignatureBrewClosurePreflight.Blocker.RECEIPT_WRONG_RECIPIENT,
                check(registry, prepared(), badOwner).blocker());
        assertEquals(SignatureBrewClosurePreflight.Blocker.RECEIPT_PAYLOAD_MISMATCH,
                check(registry, prepared(), badItem).blocker());
        assertEquals(SignatureBrewClosurePreflight.Blocker.LEDGER_UNTRUSTED,
                check(registry, prepared(), future).blocker());

        for (var audit : List.of(conflict, badOwner, badItem, future)) {
            alwaysDeny(check(registry, prepared(), audit));
        }
    }

    @Test
    void corruptReviewAndUnknownOriginalBlockAllDecisions() {
        var original = new SignatureBrewTransactionRegistry.Entry(SOURCE, ticket());
        var badReview = SignatureBrewClosureIntent.forOriginal(original).encode()
                .replace(RECIPIENT.toString(), OTHER.toString());
        var corrupted = new SignatureBrewTransactionRegistry(
                List.of(original.encode()), List.of(badReview));
        var conflict = check(corrupted, prepared(), observed());
        assertEquals(SignatureBrewClosurePreflight.Blocker.UNTRUSTED_REGISTRY, conflict.blocker());
        alwaysDeny(conflict);

        assertEquals(SignatureBrewClosurePreflight.Blocker.REGISTRY_UNAVAILABLE,
                check(null, prepared(), observed()).blocker());
        assertEquals(SignatureBrewClosurePreflight.Blocker.INVALID_REQUEST,
                SignatureBrewClosurePreflight.assess(reviewedRegistry(), null, TX,
                        prepared(), observed()).blocker());
    }

    @Test
    void savedA1AndC1RoundTripDoesNotGrantPermissionToSettle() {
        var original = reviewedRegistry();
        var restored = new SignatureBrewTransactionRegistry(
                original.encodedEntries(), original.encodedClosureIntents());
        var progress = SignatureBrewDeliveryProgress.decode(prepared().encode()).orElseThrow();
        var audit = new SignatureBrewReceiptIndex(observed().encodedEntries());
        var decision = check(restored, progress, audit);
        assertEquals(SignatureBrewClosurePreflight.Blocker.PREPARED_WITH_OBSERVATION,
                decision.blocker());
        alwaysDeny(decision);
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                restored.register(SOURCE, new SignatureBrewDeliveryTicket(
                        OTHER, RECIPIENT, ticket().signatureId(), ticket().result(), ticket().dose())));
    }
}
