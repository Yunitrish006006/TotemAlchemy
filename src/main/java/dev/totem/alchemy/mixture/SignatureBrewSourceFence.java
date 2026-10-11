package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Initial source-generation anchor stored in the same world SavedData file as
 * the A1 escrow. It is NOT a terminal tombstone or source-unlock authority.
 *
 * <p>Generation 1 is the sole creatable generation at this checkpoint. A
 * future generation may only be introduced by a separate crash-tested,
 * recipient-confirmed finalization protocol. This record alone can neither
 * confirm a payout nor make chunk and player saves atomic.</p>
 */
public record SignatureBrewSourceFence(
        SignatureBrewTransactionRegistry.Source source,
        long generation,
        UUID transactionId,
        String originalTicketSha256
) {
    private static final String VERSION = "F1";
    public static final long INITIAL_GENERATION = 1L;

    public SignatureBrewSourceFence {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(originalTicketSha256, "originalTicketSha256");
        if (generation != INITIAL_GENERATION || !originalTicketSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Only generation-one canonical source fences are supported");
        }
    }

    public static SignatureBrewSourceFence first(SignatureBrewTransactionRegistry.Entry original) {
        Objects.requireNonNull(original, "original");
        SignatureBrewClosureIntent fingerprint = SignatureBrewClosureIntent.forOriginal(original);
        return new SignatureBrewSourceFence(original.source(), INITIAL_GENERATION,
                original.transactionId(), fingerprint.originalTicketSha256());
    }

    public boolean matchesOriginal(SignatureBrewTransactionRegistry.Entry original) {
        return original != null && !original.ticket().isLegacyUnbound()
                && source.equals(original.source())
                && transactionId.equals(original.transactionId())
                && originalTicketSha256.equals(
                        SignatureBrewClosureIntent.forOriginal(original).originalTicketSha256());
    }

    public String encode() {
        return VERSION + "|" + source.dimensionId() + "|" + source.packedBlockPos()
                + "|" + generation + "|" + transactionId + "|" + originalTicketSha256;
    }

    public static Optional<SignatureBrewSourceFence> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            String[] parts = encoded.split("\\|", -1);
            if (parts.length != 6 || !VERSION.equals(parts[0])) {
                return Optional.empty();
            }
            return Optional.of(new SignatureBrewSourceFence(
                    new SignatureBrewTransactionRegistry.Source(
                            Identifier.parse(parts[1]), Long.parseLong(parts[2])),
                    Long.parseLong(parts[3]),
                    UUID.fromString(parts[4]),
                    parts[5]));
        } catch (RuntimeException malformed) {
            // Unknown versions, invalid identifiers or corrupted source data
            // are quarantined by the registry instead of crashing world load.
            return Optional.empty();
        }
    }
}
