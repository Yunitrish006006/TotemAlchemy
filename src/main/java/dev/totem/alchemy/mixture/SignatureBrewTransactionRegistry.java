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
    /** Review-only closure records; never unlock a source or grant a reward. */
    private final Map<UUID, SignatureBrewClosureIntent> closureIntents = new HashMap<>();
    private final Set<String> rawClosureIntents = new HashSet<>();
    private final Set<UUID> conflictedClosureIds = new HashSet<>();
    /** Immutable F1 generation-one anchors saved with the original A1 escrow. */
    private final Map<Source, SignatureBrewSourceFence> sourceFences = new HashMap<>();
    private final Set<Source> conflictedFenceSources = new HashSet<>();
    private final Set<String> rawSourceFences = new HashSet<>();
    private boolean unknownFenceRecords;
    private boolean unknownClosureRecords;
    private boolean unknownRecords;

    public SignatureBrewTransactionRegistry() {
    }

    /**
     * Restart constructor: retain all source records (including bad/future
     * entries) and quarantine conflicts rather than silently preferring a
     * newer record or an arbitrary iteration order.
     */
    public SignatureBrewTransactionRegistry(List<String> serialized) {
        this(serialized, List.of());
    }

    /**
     * Decode canonical reward entries first, then reconcile closure intents
     * against that exact immutable ticket. Old saves had no closure field.
     */
    public SignatureBrewTransactionRegistry(
            List<String> serialized, List<String> serializedClosureIntents
    ) {
        this(serialized, serializedClosureIntents, List.of());
    }

    /**
     * A1 and C1 migration never fabricates missing F1 generations. Old world
     * files remain readable, but cannot pass a future terminal fencing gate.
     */
    public SignatureBrewTransactionRegistry(
            List<String> serialized, List<String> serializedClosureIntents,
            List<String> serializedSourceFences
    ) {
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
        loadClosureIntents(serializedClosureIntents);
        loadSourceFences(serializedSourceFences);
    }

    private void loadSourceFences(List<String> serialized) {
        if (serialized == null) {
            unknownFenceRecords = true;
            return;
        }
        for (String raw : serialized) {
            if (raw == null || raw.isBlank()) {
                unknownFenceRecords = true;
                continue;
            }
            rawSourceFences.add(raw);
            var decoded = SignatureBrewSourceFence.decode(raw);
            if (decoded.isEmpty()) {
                unknownFenceRecords = true;
                continue;
            }
            SignatureBrewSourceFence fence = decoded.get();
            UUID originalId = bySource.get(fence.source());
            Entry original = originalId == null ? null : byTransaction.get(originalId);
            if (original == null || !fence.matchesOriginal(original)
                    || conflictedTransactions.contains(fence.transactionId())
                    || conflictedSources.contains(fence.source())) {
                // A fence from another owner/generation cannot acquire an old
                // A1 source. Keep bytes and quarantine the entire world index.
                unknownFenceRecords = true;
                continue;
            }
            SignatureBrewSourceFence previous =
                    sourceFences.putIfAbsent(fence.source(), fence);
            if (previous != null && !previous.equals(fence)) {
                conflictedFenceSources.add(fence.source());
            }
        }
    }

    private void loadClosureIntents(List<String> serialized) {
        if (serialized == null) {
            unknownClosureRecords = true;
            return;
        }
        for (String raw : serialized) {
            if (raw == null || raw.isBlank()) {
                unknownClosureRecords = true;
                continue;
            }
            rawClosureIntents.add(raw);
            var parsed = SignatureBrewClosureIntent.decode(raw);
            if (parsed.isEmpty()) {
                unknownClosureRecords = true;
                continue;
            }
            SignatureBrewClosureIntent intent = parsed.get();
            Entry original = byTransaction.get(intent.transactionId());
            if (original == null || conflictedTransactions.contains(intent.transactionId())
                    || conflictedSources.contains(original.source())
                    || !intent.matchesOriginal(original)) {
                // Never auto-close a transaction whose original has disappeared
                // or whose review intent was forged, reordered or corrupted.
                unknownClosureRecords = true;
                continue;
            }
            SignatureBrewClosureIntent prior = closureIntents.putIfAbsent(
                    intent.transactionId(), intent);
            if (prior != null && !prior.equals(intent)) {
                conflictedClosureIds.add(intent.transactionId());
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
        if (unknownRecords || unknownClosureRecords || unknownFenceRecords
                || !conflictedClosureIds.isEmpty() || !conflictedFenceSources.isEmpty()) {
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

        // Register A1 and its first immutable source generation together in
        // the same world SavedData mutation. This does NOT persist player.dat
        // nor prove the cauldron's separate debit survived a server crash.
        SignatureBrewSourceFence first = SignatureBrewSourceFence.first(incoming);
        byTransaction.put(ticket.transactionId(), incoming);
        bySource.put(source, ticket.transactionId());
        rawEntries.add(incoming.encode());
        sourceFences.put(source, first);
        rawSourceFences.add(first.encode());
        return RegisterResult.REGISTERED;
    }

    public enum FenceState {
        GENESIS_MATCH, LEGACY_UNFENCED, ORIGINAL_ABSENT,
        WRONG_SOURCE, CONFLICT_OR_UNTRUSTED
    }

    /**
     * Verify the exact initial generation independently of A1/C1. A missing
     * F1 on a legacy A1 save MUST NOT be reconstructed automatically.
     * GENESIS_MATCH confirms identity only: no terminal grant proof exists.
     */
    public FenceState inspectFence(Source source, UUID transactionId) {
        if (source == null || transactionId == null) {
            return FenceState.CONFLICT_OR_UNTRUSTED;
        }
        if (unknownRecords || unknownClosureRecords || unknownFenceRecords
                || !conflictedClosureIds.isEmpty() || !conflictedFenceSources.isEmpty()
                || conflictedTransactions.contains(transactionId)
                || conflictedSources.contains(source)) {
            return FenceState.CONFLICT_OR_UNTRUSTED;
        }
        Entry original = byTransaction.get(transactionId);
        if (original == null) {
            return FenceState.ORIGINAL_ABSENT;
        }
        if (!original.source().equals(source)) {
            return FenceState.WRONG_SOURCE;
        }
        SignatureBrewSourceFence fence = sourceFences.get(source);
        if (fence == null) {
            return FenceState.LEGACY_UNFENCED;
        }
        return fence.matchesOriginal(original)
                ? FenceState.GENESIS_MATCH : FenceState.CONFLICT_OR_UNTRUSTED;
    }

    public List<String> encodedSourceFences() {
        return rawSourceFences.stream().sorted().toList();
    }

    /** Read-only and fail-closed: even PRESENT never authorizes a payout. */
    public LookupResult lookup(UUID transactionId) {
        if (unknownRecords || unknownClosureRecords || unknownFenceRecords
                || !conflictedClosureIds.isEmpty() || !conflictedFenceSources.isEmpty()) {
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
        if (unknownRecords || unknownClosureRecords || unknownFenceRecords
                || !conflictedClosureIds.isEmpty() || !conflictedFenceSources.isEmpty()) {
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

    public enum ClosureResult {
        REVIEW_REQUESTED, ALREADY_REQUESTED, NO_VERIFIED_ORIGINAL,
        INVALID_SOURCE_OR_TICKET, UNTRUSTED_REGISTRY
    }

    public enum ClosureState {
        NONE, REVIEW_REQUESTED, CONFLICT_OR_UNTRUSTED
    }

    /**
     * Record an idempotent human-review intent against a canonical original.
     *
     * <p>IMPORTANT: This is NOT a delivery acknowledgment or a terminal
     * tombstone. The original transaction and source lock remain held. Future
     * code must require independent durable delivery proof and a separate
     * finalization protocol before source reuse is ever allowed.</p>
     */
    public ClosureResult requestClosureReview(Source source, SignatureBrewDeliveryTicket ticket) {
        if (unknownRecords || unknownClosureRecords || unknownFenceRecords
                || !conflictedClosureIds.isEmpty() || !conflictedFenceSources.isEmpty()) {
            return ClosureResult.UNTRUSTED_REGISTRY;
        }
        if (source == null || ticket == null || ticket.isLegacyUnbound()) {
            return ClosureResult.INVALID_SOURCE_OR_TICKET;
        }
        if (verify(source, ticket) != Verification.EXACT_SNAPSHOT) {
            return ClosureResult.NO_VERIFIED_ORIGINAL;
        }
        Entry original = byTransaction.get(ticket.transactionId());
        SignatureBrewClosureIntent candidate = SignatureBrewClosureIntent.forOriginal(original);
        SignatureBrewClosureIntent previous = closureIntents.get(ticket.transactionId());
        if (previous != null) {
            return previous.equals(candidate) ? ClosureResult.ALREADY_REQUESTED
                    : ClosureResult.UNTRUSTED_REGISTRY;
        }
        closureIntents.put(ticket.transactionId(), candidate);
        rawClosureIntents.add(candidate.encode());
        return ClosureResult.REVIEW_REQUESTED;
    }

    public ClosureState closureState(UUID transactionId) {
        if (unknownRecords || unknownClosureRecords || unknownFenceRecords
                || !conflictedClosureIds.isEmpty() || !conflictedFenceSources.isEmpty()) {
            return ClosureState.CONFLICT_OR_UNTRUSTED;
        }
        return transactionId != null && closureIntents.containsKey(transactionId)
                ? ClosureState.REVIEW_REQUESTED : ClosureState.NONE;
    }

    /** Read-only record inspection, not authorization to settle or issue items. */
    public Optional<SignatureBrewClosureIntent> inspectClosure(UUID transactionId) {
        return closureState(transactionId) == ClosureState.REVIEW_REQUESTED
                ? Optional.ofNullable(closureIntents.get(transactionId)) : Optional.empty();
    }

    public List<String> encodedClosureIntents() {
        return rawClosureIntents.stream().sorted().toList();
    }

    /** Original complete ticket can support human recovery after chunk loss. */
    public Optional<Entry> inspect(UUID transactionId) {
        return lookup(transactionId) == LookupResult.PRESENT
                ? Optional.ofNullable(byTransaction.get(transactionId)) : Optional.empty();
    }

    public boolean needsManualRecovery() {
        return unknownRecords || unknownClosureRecords || unknownFenceRecords
                || !conflictedClosureIds.isEmpty() || !conflictedFenceSources.isEmpty()
                || !conflictedTransactions.isEmpty() || !conflictedSources.isEmpty();
    }

    public List<String> encodedEntries() {
        return rawEntries.stream().sorted().toList();
    }
}
