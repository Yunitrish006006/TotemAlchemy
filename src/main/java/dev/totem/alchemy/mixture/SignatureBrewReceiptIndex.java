package dev.totem.alchemy.mixture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player audit index for item-side R1 receipts, independent of item stacks.
 *
 * <p>An observation means a server saw a matching item marker at one point.
 * It is not proof of an inventory write, receipt delivery, or ownership now.
 * Absence is NEVER permission to grant/retry an escrowed reward.</p>
 */
public final class SignatureBrewReceiptIndex {
    public enum RecordResult {
        RECORDED, ALREADY_RECORDED, WRONG_RECIPIENT, CONFLICT, UNTRUSTED_LEDGER
    }

    public enum Lookup {
        OBSERVED, NOT_OBSERVED, WRONG_RECIPIENT, CONFLICT, UNTRUSTED_LEDGER
    }

    private final Map<UUID, SignatureBrewRewardReceipt> observedByTransaction = new HashMap<>();
    private final Set<UUID> conflictedTransactions = new HashSet<>();
    private final Set<String> rawEntries = new HashSet<>();
    private boolean unknownEntries;

    public SignatureBrewReceiptIndex() {
    }

    /**
     * Load without dropping malformed entries. Unknown data quarantines the
     * entire ledger for writes until an explicit recovery procedure exists.
     */
    public SignatureBrewReceiptIndex(List<String> entries) {
        if (entries == null) {
            unknownEntries = true;
            return;
        }
        for (String encoded : entries) {
            if (encoded == null || encoded.isBlank()) {
                unknownEntries = true;
                continue;
            }
            rawEntries.add(encoded);
            var parsed = SignatureBrewRewardReceipt.decode(encoded);
            if (parsed.isEmpty()) {
                unknownEntries = true;
                continue;
            }
            SignatureBrewRewardReceipt receipt = parsed.get();
            SignatureBrewRewardReceipt previous =
                    observedByTransaction.putIfAbsent(receipt.transactionId(), receipt);
            if (previous != null && !previous.equals(receipt)) {
                conflictedTransactions.add(receipt.transactionId());
            }
        }
    }

    /**
     * Record a checked audit observation without granting an item.
     * The caller must validate the actual item marker separately.
     */
    public RecordResult observe(UUID actualRecipient, SignatureBrewRewardReceipt receipt) {
        if (actualRecipient == null || receipt == null || !actualRecipient.equals(receipt.recipientId())) {
            return RecordResult.WRONG_RECIPIENT;
        }
        if (unknownEntries) {
            return RecordResult.UNTRUSTED_LEDGER;
        }
        UUID transaction = receipt.transactionId();
        if (conflictedTransactions.contains(transaction)) {
            return RecordResult.CONFLICT;
        }
        SignatureBrewRewardReceipt previous = observedByTransaction.get(transaction);
        if (previous == null) {
            observedByTransaction.put(transaction, receipt);
            rawEntries.add(receipt.encode());
            return RecordResult.RECORDED;
        }
        if (previous.equals(receipt)) {
            return RecordResult.ALREADY_RECORDED;
        }
        // Preserve BOTH conflicting entries; never silently replace an owner.
        conflictedTransactions.add(transaction);
        rawEntries.add(receipt.encode());
        return RecordResult.CONFLICT;
    }

    public Lookup lookup(UUID recipient, UUID transaction) {
        if (unknownEntries) {
            return Lookup.UNTRUSTED_LEDGER;
        }
        if (transaction == null || recipient == null) {
            return Lookup.NOT_OBSERVED;
        }
        if (conflictedTransactions.contains(transaction)) {
            return Lookup.CONFLICT;
        }
        SignatureBrewRewardReceipt found = observedByTransaction.get(transaction);
        if (found == null) {
            return Lookup.NOT_OBSERVED;
        }
        return recipient.equals(found.recipientId()) ? Lookup.OBSERVED : Lookup.WRONG_RECIPIENT;
    }

    public boolean hasUntrustedData() {
        return unknownEntries || !conflictedTransactions.isEmpty();
    }

    /** Deterministic serialized representation, retaining all conflicting/raw entries. */
    public List<String> encodedEntries() {
        return rawEntries.stream().sorted().toList();
    }
}
