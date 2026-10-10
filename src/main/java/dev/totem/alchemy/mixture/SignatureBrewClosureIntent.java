package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A review request for an existing canonical transaction, NOT a receipt of
 * delivery and NOT permission to unlock its cauldron source.
 *
 * <p>Keeping this intention separate from terminal acknowledgement prevents a
 * reboot or a misleading client-side R1 observation from silently returning a
 * source to service while its original payout remains unresolved.</p>
 */
public record SignatureBrewClosureIntent(
        UUID transactionId,
        UUID recipientId,
        Identifier dimensionId,
        long packedBlockPos,
        String originalTicketSha256
) {
    private static final String VERSION = "C1";

    public SignatureBrewClosureIntent {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(originalTicketSha256, "originalTicketSha256");
        if (!originalTicketSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Closure intent requires a canonical SHA-256 fingerprint");
        }
    }

    public static SignatureBrewClosureIntent forOriginal(SignatureBrewTransactionRegistry.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.ticket().isLegacyUnbound()) {
            throw new IllegalArgumentException("Cannot review an ownerless legacy escrow");
        }
        return new SignatureBrewClosureIntent(
                entry.transactionId(),
                entry.ticket().recipientId(),
                entry.source().dimensionId(),
                entry.source().packedBlockPos(),
                fingerprint(entry.ticket().encode()));
    }

    public boolean matchesOriginal(SignatureBrewTransactionRegistry.Entry entry) {
        return entry != null && transactionId.equals(entry.transactionId())
                && !entry.ticket().isLegacyUnbound()
                && recipientId.equals(entry.ticket().recipientId())
                && dimensionId.equals(entry.source().dimensionId())
                && packedBlockPos == entry.source().packedBlockPos()
                && originalTicketSha256.equals(fingerprint(entry.ticket().encode()));
    }

    public String encode() {
        return VERSION + "|" + transactionId + "|" + recipientId + "|"
                + dimensionId + "|" + packedBlockPos + "|" + originalTicketSha256;
    }

    public static Optional<SignatureBrewClosureIntent> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            String[] fields = encoded.split("\\|", -1);
            if (fields.length != 6 || !VERSION.equals(fields[0])) {
                return Optional.empty();
            }
            return Optional.of(new SignatureBrewClosureIntent(
                    UUID.fromString(fields[1]),
                    UUID.fromString(fields[2]),
                    Identifier.parse(fields[3]),
                    Long.parseLong(fields[4]),
                    fields[5]));
        } catch (RuntimeException malformed) {
            // A future/malformed record must not crash world-load migration.
            return Optional.empty();
        }
    }

    private static String fingerprint(String serializedTicket) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(serializedTicket.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossibleOnJava21) {
            throw new IllegalStateException("Required SHA-256 unavailable", impossibleOnJava21);
        }
    }
}
