package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Candidate server-owned registry for pending signature rewards, independent
 * of cauldron chunk and player inventory saves.
 *
 * <p>Registration is NOT a durable payout authorization. This registry only
 * establishes uniqueness of the original source and transaction while
 * retaining the full S2 escrow ticket for administrative recovery. No API
 * releases, confirms or retries an item. Missing ledger data must never be
 * interpreted as permission to mint an output.</p>
 */
public final class SignatureBrewTransactionRegistry {
    private static final String VERSION = "A1";

    public enum RegisterResult {
        REGISTERED, ALREADY_REGISTERED, CONFLICT_TRANSACTION,
        CONFLICT_SOURCE, UNBOUND_TICKET, UNTRUSTED_REGISTRY
    }

    public enum LookupResult {
        PRESENT, ABSENT_UNVERIFIED, CONFLICT, UNTRUSTED_REGISTRY
    }

    /** Stable source identity even after a cauldron block has been replaced. */
    public record Source(Identifier dimensionId, long packedBlockPos) {
        public Source {
            Objects.requireNonNull(dimensionId, "dimensionId");
        }
    }

    public record Entry(Source source, SignatureBrewDeliveryTicket ticket) {
        public Entry {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(ticket, "ticket");
            if (ticket.isLegacyUnbound()) {
                throw new IllegalArgumentException("Registry entries require owner-bound S2 tickets");
            }
        }

        public UUID transactionId() {
            return ticket.transactionId();
        }

        public String encode() {
            String serializedTicket = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(ticket.encode().getBytes(StandardCharsets.UTF_8));
            return VERSION + "|" + source.dimensionId() + "|" + source.packedBlockPos()
                    + "|" + serializedTicket;
        }

        public static Optional<Entry> decode(String encoded) {
            if (encoded == null || encoded.isBlank()) {
                return Optional.empty();
            }
            try {
                String[] fields = encoded.split("\\|", -1);
                if (fields.length != 4 || !VERSION.equals(fields[0])) {
                    return Optional.empty();
                }
                Identifier dimension = Identifier.parse(fields[1]);
                long position = Long.parseLong(fields[2]);
                String ticketPayload = new String(Base64.getUrlDecoder().decode(fields[3]),
                        StandardCharsets.UTF_8);
                return SignatureBrewDeliveryTicket.decode(ticketPayload)
                        .filter(ticket -> !ticket.isLegacyUnbound())
                        .map(ticket -> new Entry(new Source(dimension, position), ticket));
            } catch (RuntimeException error) {
                // Identifier.parse throws Minecraft's IdentifierException (not
                // necessarily IllegalArgumentException) for malformed IDs.
                // Untrusted persisted records must be quarantined, never crash
                // SavedData loading or disappear during a subsequent save.
                return Optional.empty();
            }
        }
    }

    private final Map<UUID, Entry> byTransaction = new HashMap<>();
    private final Map<Source, UUID> bySource = new HashMap<>();
    private final Set<UUID> conflictedTransactions = new HashSet<>();
    private final Set<Source> conflictedSources = new HashSet<>();
    private final Set<String> rawEntries = new HashSet<>();
    private boolean unknownRecords;

    public SignatureBrewTransactionRegistry() {
    }

    /**
     * Restart constructor: retain all source records (including bad/future
     * entries) and quarantine conflicts rather than silently preferring a
     * newer record or an arbitrary iteration order.
     */
    public SignatureBrewTransactionRegistry(List<String> serialized) {
        if (serialized == null) {
            unknownRecords = true;
            return;
        }
        for (String raw : serialized) {
            if (raw == null || raw.isBlank()) {
                unknownRecords = true;
                continue;
            }
            rawEntries.add(raw);
            var entry = Entry.decode(raw);
            if (entry.isEmpty()) {
                unknownRecords = true;
                continue;
            }
            Entry candidate = entry.get();
            Entry previous = byTransaction.putIfAbsent(candidate.transactionId(), candidate);
            if (previous != null && !previous.encode().equals(candidate.encode())) {
                conflictedTransactions.add(candidate.transactionId());
                conflictedSources.add(previous.source());
                conflictedSources.add(candidate.source());
            }
            UUID priorAtSource = bySource.putIfAbsent(candidate.source(), candidate.transactionId());
            if (priorAtSource != null && !priorAtSource.equals(candidate.transactionId())) {
                conflictedSources.add(candidate.source());
                conflictedTransactions.add(priorAtSource);
                conflictedTransactions.add(candidate.transactionId());
            }
        }
    }

    /**
     * Register an exact prepared reward; no item issuance, cauldron mutation,
     * or persistence synchronization is triggered.
     *
     * <p>Retaining a source lock for the entire registry lifetime is deliberate:
     * source reuse after successful delivery requires a separately proven and
     * persisted terminal/tombstone protocol. A second different ticket at the
     * same block position is not automatically trusted.</p>
     */
    public RegisterResult register(Source source, SignatureBrewDeliveryTicket ticket) {
        if (source == null || ticket == null || ticket.isLegacyUnbound()) {
            return RegisterResult.UNBOUND_TICKET;
        }
        if (unknownRecords) {
            return RegisterResult.UNTRUSTED_REGISTRY;
        }
        if (conflictedTransactions.contains(ticket.transactionId())) {
            return RegisterResult.CONFLICT_TRANSACTION;
        }
        if (conflictedSources.contains(source)) {
            return RegisterResult.CONFLICT_SOURCE;
        }

        Entry incoming = new Entry(source, ticket);
        Entry previous = byTransaction.get(ticket.transactionId());
        if (previous != null) {
            return previous.encode().equals(incoming.encode()) ? RegisterResult.ALREADY_REGISTERED
                    : RegisterResult.CONFLICT_TRANSACTION;
        }
        UUID sourceOwner = bySource.get(source);
        if (sourceOwner != null) {
            return RegisterResult.CONFLICT_SOURCE;
        }

        byTransaction.put(ticket.transactionId(), incoming);
        bySource.put(source, ticket.transactionId());
        rawEntries.add(incoming.encode());
        return RegisterResult.REGISTERED;
    }

    /** Read-only and fail-closed: even PRESENT never authorizes a payout. */
    public LookupResult lookup(UUID transactionId) {
        if (unknownRecords) {
            return LookupResult.UNTRUSTED_REGISTRY;
        }
        if (transactionId == null) {
            return LookupResult.ABSENT_UNVERIFIED;
        }
        Entry entry = byTransaction.get(transactionId);
        if (conflictedTransactions.contains(transactionId)
                || (entry != null && conflictedSources.contains(entry.source()))) {
            return LookupResult.CONFLICT;
        }
        return entry == null ? LookupResult.ABSENT_UNVERIFIED : LookupResult.PRESENT;
    }

    public enum Verification {
        EXACT_SNAPSHOT, ABSENT_UNVERIFIED, SOURCE_MISMATCH,
        ESCROW_PAYLOAD_MISMATCH, CONFLICT, UNTRUSTED_REGISTRY, UNBOUND_TICKET
    }

    /**
     * Compare a cauldron's candidate escrow against the separately saved world
     * registry. EXACT_SNAPSHOT proves matching serialized identity only, never
     * that a player item was saved or a disk write was flushed successfully.
     */
    public Verification verify(Source expectedSource, SignatureBrewDeliveryTicket ticket) {
        if (unknownRecords) {
            return Verification.UNTRUSTED_REGISTRY;
        }
        if (expectedSource == null || ticket == null || ticket.isLegacyUnbound()) {
            return Verification.UNBOUND_TICKET;
        }
        if (conflictedTransactions.contains(ticket.transactionId())
                || conflictedSources.contains(expectedSource)) {
            return Verification.CONFLICT;
        }
        Entry original = byTransaction.get(ticket.transactionId());
        if (original == null) {
            return Verification.ABSENT_UNVERIFIED;
        }
        if (conflictedSources.contains(original.source())) {
            return Verification.CONFLICT;
        }
        if (!original.source().equals(expectedSource)) {
            return Verification.SOURCE_MISMATCH;
        }
        return original.ticket().encode().equals(ticket.encode())
                ? Verification.EXACT_SNAPSHOT : Verification.ESCROW_PAYLOAD_MISMATCH;
    }

    /** Original complete ticket can support human recovery after chunk loss. */
    public Optional<Entry> inspect(UUID transactionId) {
        return lookup(transactionId) == LookupResult.PRESENT
                ? Optional.ofNullable(byTransaction.get(transactionId)) : Optional.empty();
    }

    public boolean needsManualRecovery() {
        return unknownRecords || !conflictedTransactions.isEmpty() || !conflictedSources.isEmpty();
    }

    public List<String> encodedEntries() {
        return rawEntries.stream().sorted().toList();
    }
}
