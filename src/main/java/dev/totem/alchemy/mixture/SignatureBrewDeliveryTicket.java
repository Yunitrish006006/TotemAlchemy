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
 * <p>This ticket is only a recoverable escrow record: it does NOT certify that
 * the player received an item, and replaying it must not mint another item.
 * A separate, crash-safe delivery acknowledgment protocol is required before
 * this can become the live inventory hand-off path.</p>
 */
public record SignatureBrewDeliveryTicket(
        UUID transactionId,
        Identifier signatureId,
        SignatureBrewDefinition.Result result,
        AlchemyMixtureState dose
) {
    private static final String VERSION = "S1";

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

    @Override
    public AlchemyMixtureState dose() {
        return dose.copy();
    }

    /** Stable codec persisted with the cauldron, separate from the remaining mixture. */
    public String encode() {
        return String.join("|",
                VERSION,
                transactionId.toString(),
                token(signatureId.toString()),
                result.type().name(),
                token(result.itemId().toString()),
                Integer.toString(result.count()),
                token(result.containerItemId() == null ? "" : result.containerItemId().toString()),
                token(result.potionId() == null ? "" : result.potionId().toString()),
                token(dose.encode()));
    }

    /**
     * Invalid or future-version receipts are rejected, never partially redeemed.
     * The caller must not silently issue an item when this returns empty.
     */
    public static Optional<SignatureBrewDeliveryTicket> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            String[] parts = encoded.split("\\|", -1);
            if (parts.length != 9 || !VERSION.equals(parts[0])) {
                return Optional.empty();
            }
            UUID id = UUID.fromString(parts[1]);
            Identifier signature = Identifier.parse(untoken(parts[2]));
            var type = SignatureBrewDefinition.Type.valueOf(parts[3]);
            Identifier resultItem = Identifier.parse(untoken(parts[4]));
            int count = Integer.parseInt(parts[5]);
            String container = untoken(parts[6]);
            String potion = untoken(parts[7]);
            var result = new SignatureBrewDefinition.Result(type, resultItem, count,
                    container.isBlank() ? null : Identifier.parse(container),
                    potion.isBlank() ? null : Identifier.parse(potion));
            AlchemyMixtureState dose = AlchemyMixtureState.decode(untoken(parts[8]));
            return Optional.of(new SignatureBrewDeliveryTicket(id, signature, result, dose));
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
