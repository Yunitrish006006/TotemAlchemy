package dev.totem.alchemy.mixture;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable per-ticket delivery state kept beside the prepared reward.
 *
 * <p>Mark the attempt BEFORE mutating any player inventory. After a crash,
 * ISSUANCE_UNCERTAIN MUST NOT be retried automatically: the player might already
 * have received the item. ACKNOWLEDGED is a reserved persisted state only; no
 * production acknowledgment transition is exposed until inventory receipt
 * verification has been implemented.</p>
 */
public record SignatureBrewDeliveryProgress(
        UUID transactionId,
        UUID recipientId,
        Phase phase
) {
    private static final String VERSION = "J1";

    public enum Phase {
        PREPARED,
        ISSUANCE_UNCERTAIN,
        ACKNOWLEDGED
    }

    public SignatureBrewDeliveryProgress {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(phase, "phase");
    }

    public static SignatureBrewDeliveryProgress prepared(SignatureBrewDeliveryTicket ticket) {
        requireBound(ticket);
        return new SignatureBrewDeliveryProgress(
                ticket.transactionId(), ticket.recipientId(), Phase.PREPARED);
    }

    /**
     * Fail closed when loading an older S2 ticket whose missing progress record
     * cannot prove whether an item was already issued.
     */
    public static SignatureBrewDeliveryProgress unresolved(SignatureBrewDeliveryTicket ticket) {
        requireBound(ticket);
        return new SignatureBrewDeliveryProgress(
                ticket.transactionId(), ticket.recipientId(), Phase.ISSUANCE_UNCERTAIN);
    }

    public boolean matches(SignatureBrewDeliveryTicket ticket) {
        return ticket != null && !ticket.isLegacyUnbound()
                && transactionId.equals(ticket.transactionId())
                && recipientId.equals(ticket.recipientId());
    }

    /**
     * Single allowed state transition at this checkpoint.
     *
     * <p>A second call is rejected, not treated as permission to issue again.
     * Caller must persist this state before attempting an inventory mutation.
     * Its persistence is still separate from the inventory's save and cannot
     * alone provide exactly-once delivery.</p>
     */
    public Optional<SignatureBrewDeliveryProgress> beginIssuance(
            UUID requestedTransactionId, UUID requestedRecipientId
    ) {
        if (phase != Phase.PREPARED
                || !transactionId.equals(requestedTransactionId)
                || !recipientId.equals(requestedRecipientId)) {
            return Optional.empty();
        }
        return Optional.of(new SignatureBrewDeliveryProgress(
                transactionId, recipientId, Phase.ISSUANCE_UNCERTAIN));
    }

    public boolean needsReconciliation() {
        return phase == Phase.ISSUANCE_UNCERTAIN;
    }

    public String encode() {
        return VERSION + "|" + transactionId + "|" + recipientId + "|" + phase.name();
    }

    public static Optional<SignatureBrewDeliveryProgress> decode(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return Optional.empty();
        }
        try {
            String[] parts = serialized.split("\\|", -1);
            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                return Optional.empty();
            }
            return Optional.of(new SignatureBrewDeliveryProgress(
                    UUID.fromString(parts[1]),
                    UUID.fromString(parts[2]),
                    Phase.valueOf(parts[3])));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return Optional.empty();
        }
    }

    private static void requireBound(SignatureBrewDeliveryTicket ticket) {
        if (ticket == null || ticket.isLegacyUnbound()) {
            throw new IllegalArgumentException("Delivery progress requires a recipient-bound S2 ticket");
        }
    }
}
