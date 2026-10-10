package dev.totem.alchemy.mixture;

import java.util.Objects;
import java.util.UUID;

/**
 * Read-only comparison of independently persisted cauldron escrow, delivery
 * journal, and observed player reward receipts after an interrupted handoff.
 *
 * <p>This is NOT a transaction coordinator. Its assessments always require
 * further recovery/audit, never authorize automatic payout, escrow clearing,
 * or ACKNOWLEDGED. Neither an observed item nor an absent item is authoritative
 * evidence that player.dat and chunk data were saved in a consistent order.</p>
 */
public final class SignatureBrewRecoveryAssessment {
    private SignatureBrewRecoveryAssessment() {
    }

    public enum Finding {
        INVALID_REQUEST,
        ESCROW_MISSING,
        LEGACY_UNBOUND_ESCROW,
        ESCROW_IDENTITY_MISMATCH,
        JOURNAL_MISSING_OR_MISMATCHED,
        RECEIPT_LEDGER_UNAVAILABLE,
        RECEIPT_LEDGER_UNTRUSTED,
        RECEIPT_TRANSACTION_CONFLICT,
        RECEIPT_WRONG_RECIPIENT,
        RECEIPT_PAYLOAD_MISMATCH,
        PREPARED_WITH_NO_OBSERVATION,
        PREPARED_WITH_OBSERVATION,
        ISSUANCE_UNCERTAIN_WITH_NO_OBSERVATION,
        ISSUANCE_UNCERTAIN_WITH_OBSERVATION,
        ACKNOWLEDGED_BUT_NOT_DURABLY_VERIFIED
    }

    /**
     * The only permissible outcomes at this checkpoint are read-only review.
     * Keep these flags explicit so no caller treats a matching R1 item as
     * permission to mint another item or to delete a spent escrow ticket.
     */
    public record Decision(Finding finding) {
        public Decision {
            Objects.requireNonNull(finding, "finding");
        }

        public boolean allowsAutomaticPayout() {
            return false;
        }

        public boolean allowsAutomaticAcknowledgment() {
            return false;
        }

        public boolean allowsAutomaticEscrowDeletion() {
            return false;
        }

        public boolean requiresIndependentRecovery() {
            return true;
        }
    }

    /**
     * Inspect a possible transaction, including a destroyed/missing cauldron.
     *
     * @param transactionId expected transaction identifier from a recovery log
     * @param recipientId expected recipient, never inferred from an item
     * @param ticket independently loaded S2 cauldron escrow, or null if missing
     * @param progress separately serialized J1 state, or null if unavailable
     * @param ledger independently loaded R1 observation index, or null if absent
     */
    public static Decision assess(
            UUID transactionId,
            UUID recipientId,
            SignatureBrewDeliveryTicket ticket,
            SignatureBrewDeliveryProgress progress,
            SignatureBrewReceiptIndex ledger
    ) {
        return assessEvidence(transactionId, recipientId, ticket, progress,
                ledger == null ? null : ledger.compareTicket(ticket));
    }

    /**
     * Evaluate a fully decoded SavedData observation without requiring callers
     * to reconstruct its underlying audit index. Null represents a missing or
     * unreadable ledger, never a negative receipt.
     */
    public static Decision assessEvidence(
            UUID transactionId,
            UUID recipientId,
            SignatureBrewDeliveryTicket ticket,
            SignatureBrewDeliveryProgress progress,
            SignatureBrewReceiptIndex.Evidence evidence
    ) {
        if (transactionId == null || recipientId == null) {
            return new Decision(Finding.INVALID_REQUEST);
        }
        if (ticket == null) {
            // The last dose may have been in a deleted/replaced block; an
            // observed R1 receipt cannot reconstruct a safe debit or payout.
            return new Decision(Finding.ESCROW_MISSING);
        }
        if (ticket.isLegacyUnbound()) {
            return new Decision(Finding.LEGACY_UNBOUND_ESCROW);
        }
        if (!ticket.transactionId().equals(transactionId) || !ticket.belongsTo(recipientId)) {
            return new Decision(Finding.ESCROW_IDENTITY_MISMATCH);
        }
        if (progress == null || !progress.matches(ticket)) {
            // Never assume missing J1 means PREPARED: an item may already
            // have been issued by an older server version before it crashed.
            return new Decision(Finding.JOURNAL_MISSING_OR_MISMATCHED);
        }
        if (evidence == null) {
            return new Decision(Finding.RECEIPT_LEDGER_UNAVAILABLE);
        }
        switch (evidence) {
            case UNTRUSTED_LEDGER:
                return new Decision(Finding.RECEIPT_LEDGER_UNTRUSTED);
            case CONFLICT:
                return new Decision(Finding.RECEIPT_TRANSACTION_CONFLICT);
            case WRONG_RECIPIENT:
                return new Decision(Finding.RECEIPT_WRONG_RECIPIENT);
            case PAYLOAD_MISMATCH:
                return new Decision(Finding.RECEIPT_PAYLOAD_MISMATCH);
            case UNBOUND_TICKET:
                return new Decision(Finding.LEGACY_UNBOUND_ESCROW);
            case MATCHING_OBSERVATION:
            case NOT_OBSERVED:
                // Both are ambiguous across independent chunk/player/SavedData
                // saves. An item could have been consumed, copied, or rolled
                // back after a ledger observation was persisted.
                break;
        }

        if (progress.phase() == SignatureBrewDeliveryProgress.Phase.ACKNOWLEDGED) {
            // J1's ACKNOWLEDGED enum is reserved but no authoritative player
            // inventory receipt or crash-tested acknowledgment exists yet.
            return new Decision(Finding.ACKNOWLEDGED_BUT_NOT_DURABLY_VERIFIED);
        }
        boolean observed = evidence == SignatureBrewReceiptIndex.Evidence.MATCHING_OBSERVATION;
        if (progress.phase() == SignatureBrewDeliveryProgress.Phase.PREPARED) {
            return new Decision(observed ? Finding.PREPARED_WITH_OBSERVATION
                    : Finding.PREPARED_WITH_NO_OBSERVATION);
        }
        return new Decision(observed ? Finding.ISSUANCE_UNCERTAIN_WITH_OBSERVATION
                : Finding.ISSUANCE_UNCERTAIN_WITH_NO_OBSERVATION);
    }
}
