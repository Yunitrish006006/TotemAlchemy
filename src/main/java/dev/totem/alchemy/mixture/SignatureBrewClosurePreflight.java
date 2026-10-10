package dev.totem.alchemy.mixture;

import java.util.Objects;
import java.util.UUID;

/**
 * Finalization safety gate for an original A1 escrow and its C1 closure review.
 *
 * <p>This is a read-only preflight, NOT a terminal-state transition. It verifies
 * origin fencing, original payload, pending review, J1 phase, and R1 evidence.
 * None of those records is an authoritative durable recipient-inventory grant
 * receipt; even a matching set must NOT unlock the source or issue items.</p>
 */
public final class SignatureBrewClosurePreflight {
    private SignatureBrewClosurePreflight() {
    }

    public enum Blocker {
        INVALID_REQUEST,
        REGISTRY_UNAVAILABLE,
        UNTRUSTED_REGISTRY,
        ORIGINAL_ABSENT,
        ORIGINAL_CONFLICT,
        SOURCE_MISMATCH,
        REVIEW_NOT_REQUESTED,
        REVIEW_UNTRUSTED,
        JOURNAL_MISSING_OR_MISMATCHED,
        LEDGER_UNAVAILABLE,
        LEDGER_UNTRUSTED,
        RECEIPT_CONFLICT,
        RECEIPT_WRONG_RECIPIENT,
        RECEIPT_PAYLOAD_MISMATCH,
        PREPARED_WITHOUT_OBSERVATION,
        PREPARED_WITH_OBSERVATION,
        ISSUANCE_UNCERTAIN_WITHOUT_OBSERVATION,
        ISSUANCE_UNCERTAIN_WITH_OBSERVATION,
        ACKNOWLEDGED_LABEL_NOT_AUTHORITATIVE
    }

    public record Decision(Blocker blocker) {
        public Decision {
            Objects.requireNonNull(blocker, "blocker");
        }

        /** No caller may treat the preflight as a persisted terminal tombstone. */
        public boolean mayReleaseSource() {
            return false;
        }

        /** No caller may retry the original item or pay a new one. */
        public boolean mayPayOut() {
            return false;
        }

        /** J1 cannot become ACKNOWLEDGED from R1 audit observations alone. */
        public boolean mayAcknowledge() {
            return false;
        }

        public boolean requiresAuthoritativeReconciliation() {
            return true;
        }
    }

    public static Decision assess(
            SignatureBrewTransactionRegistry registry,
            SignatureBrewTransactionRegistry.Source expectedSource,
            UUID transactionId,
            SignatureBrewDeliveryProgress journal,
            SignatureBrewReceiptIndex receipts
    ) {
        SignatureBrewTransactionRegistry.Entry original = registry == null
                ? null : registry.inspect(transactionId).orElse(null);
        return assessEvidence(registry, expectedSource, transactionId, journal,
                receipts == null ? null
                        : receipts.compareTicket(original == null ? null : original.ticket()));
    }

    /**
     * Work with evidence decoded directly from independent world SavedData
     * without making its mutable index visible to another storage owner.
     * Null evidence means missing ledger, never proof of non-delivery.
     */
    public static Decision assessEvidence(
            SignatureBrewTransactionRegistry registry,
            SignatureBrewTransactionRegistry.Source expectedSource,
            UUID transactionId,
            SignatureBrewDeliveryProgress journal,
            SignatureBrewReceiptIndex.Evidence evidence
    ) {
        if (expectedSource == null || transactionId == null) {
            return new Decision(Blocker.INVALID_REQUEST);
        }
        if (registry == null) {
            return new Decision(Blocker.REGISTRY_UNAVAILABLE);
        }

        switch (registry.lookup(transactionId)) {
            case UNTRUSTED_REGISTRY:
                return new Decision(Blocker.UNTRUSTED_REGISTRY);
            case CONFLICT:
                return new Decision(Blocker.ORIGINAL_CONFLICT);
            case ABSENT_UNVERIFIED:
                return new Decision(Blocker.ORIGINAL_ABSENT);
            case PRESENT:
                break;
        }
        SignatureBrewTransactionRegistry.Entry original =
                registry.inspect(transactionId).orElse(null);
        if (original == null) {
            return new Decision(Blocker.ORIGINAL_ABSENT);
        }
        if (!expectedSource.equals(original.source())) {
            return new Decision(Blocker.SOURCE_MISMATCH);
        }

        switch (registry.closureState(transactionId)) {
            case CONFLICT_OR_UNTRUSTED:
                return new Decision(Blocker.REVIEW_UNTRUSTED);
            case NONE:
                return new Decision(Blocker.REVIEW_NOT_REQUESTED);
            case REVIEW_REQUESTED:
                break;
        }
        SignatureBrewClosureIntent intent = registry.inspectClosure(transactionId).orElse(null);
        if (intent == null || !intent.matchesOriginal(original)) {
            return new Decision(Blocker.REVIEW_UNTRUSTED);
        }

        SignatureBrewDeliveryTicket ticket = original.ticket();
        if (journal == null || !journal.matches(ticket)) {
            return new Decision(Blocker.JOURNAL_MISSING_OR_MISMATCHED);
        }
        if (evidence == null) {
            return new Decision(Blocker.LEDGER_UNAVAILABLE);
        }
        switch (evidence) {
            case UNTRUSTED_LEDGER:
                return new Decision(Blocker.LEDGER_UNTRUSTED);
            case CONFLICT:
                return new Decision(Blocker.RECEIPT_CONFLICT);
            case WRONG_RECIPIENT:
                return new Decision(Blocker.RECEIPT_WRONG_RECIPIENT);
            case PAYLOAD_MISMATCH:
            case UNBOUND_TICKET:
                return new Decision(Blocker.RECEIPT_PAYLOAD_MISMATCH);
            case MATCHING_OBSERVATION:
            case NOT_OBSERVED:
                break;
        }

        boolean observed = evidence == SignatureBrewReceiptIndex.Evidence.MATCHING_OBSERVATION;
        if (journal.phase() == SignatureBrewDeliveryProgress.Phase.PREPARED) {
            return new Decision(observed ? Blocker.PREPARED_WITH_OBSERVATION
                    : Blocker.PREPARED_WITHOUT_OBSERVATION);
        }
        if (journal.phase() == SignatureBrewDeliveryProgress.Phase.ISSUANCE_UNCERTAIN) {
            return new Decision(observed ? Blocker.ISSUANCE_UNCERTAIN_WITH_OBSERVATION
                    : Blocker.ISSUANCE_UNCERTAIN_WITHOUT_OBSERVATION);
        }
        // An ACKNOWLEDGED enum from an older/forged journal is not an
        // inventory-committed proof of delivery.
        return new Decision(Blocker.ACKNOWLEDGED_LABEL_NOT_AUTHORITATIVE);
    }
}
