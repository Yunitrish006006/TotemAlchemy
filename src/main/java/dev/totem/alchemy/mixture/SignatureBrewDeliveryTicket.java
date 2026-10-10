package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A single prepared (but not delivered) signature bottle reward.
 *
 * <p>V2 binds the ticket to exactly one recipient UUID. Old V1 tickets remain
 * readable only for quarantined recovery, with a null recipient: they can
 * never authorize player delivery. Neither version proves that a player has
 * received an item. A crash-safe acknowledgment protocol remains necessary.</p>
 */
public record SignatureBrewDeliveryTicket(
        UUID transactionId,
        UUID recipientId,
        Identifier signatureId,
        SignatureBrewDefinition.Result result,
        AlchemyMixtureState dose
) {
    private static final String LEGACY_VERSION = "S1";
    private static final String RECIPIENT_VERSION = "S2";

    public SignatureBrewDeliveryTicket {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(signatureId, "signatureId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(dose, "dose");
        if (result.type() != SignatureBrewDefinition.Type.BOTTLED_ITEM
                || dose.volumeUnits() != 1
                || dose.hasCommittedSignatureProcess()
                || !dose.signatureGroups().isEmpty()
                || dose.hasPendingReactions()
                || !dose.hasProvenance("signature:result:" + signatureId)) {
            throw new IllegalArgumentException("Escrow requires one redeemed, non-replayable signature dose");
        }
        // A caller cannot mutate the saved ticket's dose after preparation.
        dose = dose.copy();
    }

    /**
     * Legacy unbound S1 construction is supported for migration/tests only.
     * A ticket without a recipient must never be used as delivery authority.
     */
    @Deprecated
    public SignatureBrewDeliveryTicket(
            UUID transactionId,
            Identifier signatureId,
            SignatureBrewDefinition.Result result,
            AlchemyMixtureState dose
    ) {
        this(transactionId, null, signatureId, result, dose);
    }

    /** An S1 ticket has no owner and must be held for manual recovery. */
    public boolean isLegacyUnbound() {
        return recipientId == null;
    }

    /** A UUID match alone is NOT a receipt or authorization to mint an item. */
    public boolean belongsTo(UUID candidate) {
        return recipientId != null && recipientId.equals(candidate);
    }

    @Override
    public AlchemyMixtureState dose() {
        return dose.copy();
    }

    /** Stable codec persisted with the cauldron, separate from the remaining mixture. */
    public String encode() {
        String payload = String.join("|",
                token(signatureId.toString()),
                result.type().name(),
                token(result.itemId().toString()),
                Integer.toString(result.count()),
                token(result.containerItemId() == null ? "" : result.containerItemId().toString()),
                token(result.potionId() == null ? "" : result.potionId().toString()),
                token(dose.encode()));
        if (recipientId == null) {
            // Preserve S1 exactly; never invent a recipient for old world saves.
            return LEGACY_VERSION + "|" + transactionId + "|" + payload;
        }
        return RECIPIENT_VERSION + "|" + transactionId + "|" + recipientId + "|" + payload;
    }

    /**
     * Malformed and unknown-version receipts are rejected, never partially redeemed.
     * A parsed legacy S1 ticket is unbound and cannot authorize item issuance.
     */
    public static Optional<SignatureBrewDeliveryTicket> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            String[] parts = encoded.split("\\|", -1);
            boolean legacy = parts.length == 9 && LEGACY_VERSION.equals(parts[0]);
            boolean bound = parts.length == 10 && RECIPIENT_VERSION.equals(parts[0]);
            if (!legacy && !bound) {
                return Optional.empty();
            }
            int offset = bound ? 1 : 0;
            UUID id = UUID.fromString(parts[1]);
            UUID recipient = bound ? UUID.fromString(parts[2]) : null;
            Identifier signature = Identifier.parse(untoken(parts[2 + offset]));
            SignatureBrewDefinition.Type type =
                    SignatureBrewDefinition.Type.valueOf(parts[3 + offset]);
            Identifier resultItem = Identifier.parse(untoken(parts[4 + offset]));
            int count = Integer.parseInt(parts[5 + offset]);
            String container = untoken(parts[6 + offset]);
            String potion = untoken(parts[7 + offset]);
            var result = new SignatureBrewDefinition.Result(type, resultItem, count,
                    container.isBlank() ? null : Identifier.parse(container),
                    potion.isBlank() ? null : Identifier.parse(potion));
            AlchemyMixtureState dose = AlchemyMixtureState.decode(untoken(parts[8 + offset]));
            return Optional.of(new SignatureBrewDeliveryTicket(id, recipient, signature, result, dose));
        } catch (IllegalArgumentException | NullPointerException error) {
            return Optional.empty();
        }
    }

    private static String token(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String untoken(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
