package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewReceiptIndexTest {
    private static final UUID TX = UUID.fromString("4e351d0c-e09e-4e86-81ed-325e82161483");
    private static final UUID OWNER = UUID.fromString("cc22463e-a894-465c-8eb5-5c3371ce402f");
    private static final UUID OTHER = UUID.fromString("247c5c55-f686-4c1c-a460-4a0ff801e988");
    private static final Identifier RECIPE = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");
    private static final Identifier ITEM = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");

    private static SignatureBrewRewardReceipt receipt(UUID transaction, UUID recipient) {
        return new SignatureBrewRewardReceipt(transaction, recipient, RECIPE, ITEM);
    }

    @Test
    void firstObservationIsRecordedAndRepeatedObservationIsIdempotent() {
        var index = new SignatureBrewReceiptIndex();
        var r = receipt(TX, OWNER);
        assertEquals(SignatureBrewReceiptIndex.Lookup.NOT_OBSERVED, index.lookup(OWNER, TX));
        assertEquals(SignatureBrewReceiptIndex.RecordResult.RECORDED, index.observe(OWNER, r));
        assertEquals(SignatureBrewReceiptIndex.RecordResult.ALREADY_RECORDED, index.observe(OWNER, r));
        assertEquals(SignatureBrewReceiptIndex.Lookup.OBSERVED, index.lookup(OWNER, TX));
        assertEquals(List.of(r.encode()), index.encodedEntries());
        assertFalse(index.hasUntrustedData());
    }

    @Test
    void lookupIsBoundToActualPlayerEvenWhenTransactionUuidMatches() {
        var index = new SignatureBrewReceiptIndex();
        var r = receipt(TX, OWNER);
        assertEquals(SignatureBrewReceiptIndex.RecordResult.WRONG_RECIPIENT, index.observe(OTHER, r));
        assertTrue(index.encodedEntries().isEmpty());
        index.observe(OWNER, r);
        assertEquals(SignatureBrewReceiptIndex.Lookup.WRONG_RECIPIENT, index.lookup(OTHER, TX));
        assertEquals(SignatureBrewReceiptIndex.Lookup.NOT_OBSERVED, index.lookup(OWNER, OTHER));
    }

    @Test
    void separatelyLoadedObservationSurvivesOriginalItemDisappearance() {
        var first = new SignatureBrewReceiptIndex();
        var receipt = receipt(TX, OWNER);
        assertEquals(SignatureBrewReceiptIndex.RecordResult.RECORDED, first.observe(OWNER, receipt));
        var restored = new SignatureBrewReceiptIndex(first.encodedEntries());
        assertEquals(SignatureBrewReceiptIndex.Lookup.OBSERVED, restored.lookup(OWNER, TX));
        assertEquals(SignatureBrewReceiptIndex.RecordResult.ALREADY_RECORDED, restored.observe(OWNER, receipt));
        assertEquals(first.encodedEntries(), restored.encodedEntries());
    }

    @Test
    void transactionCollisionQuarantinesBothRecordsAcrossReload() {
        var first = new SignatureBrewReceiptIndex();
        assertEquals(SignatureBrewReceiptIndex.RecordResult.RECORDED, first.observe(OWNER, receipt(TX, OWNER)));
        assertEquals(SignatureBrewReceiptIndex.RecordResult.CONFLICT, first.observe(OTHER, receipt(TX, OTHER)));
        assertEquals(SignatureBrewReceiptIndex.Lookup.CONFLICT, first.lookup(OWNER, TX));
        assertEquals(SignatureBrewReceiptIndex.Lookup.CONFLICT, first.lookup(OTHER, TX));
        assertTrue(first.hasUntrustedData());
        assertEquals(2, first.encodedEntries().size());

        var restored = new SignatureBrewReceiptIndex(first.encodedEntries());
        assertEquals(SignatureBrewReceiptIndex.Lookup.CONFLICT, restored.lookup(OWNER, TX));
        assertEquals(SignatureBrewReceiptIndex.RecordResult.CONFLICT, restored.observe(OWNER, receipt(TX, OWNER)));
        assertEquals(first.encodedEntries(), restored.encodedEntries());
    }

    @Test
    void unparseableSavedRecordsAreNeverDiscardedOrTreatedAsAbsence() {
        String invalid = "R2|future-format|not-readable";
        var index = new SignatureBrewReceiptIndex(List.of(invalid, receipt(TX, OWNER).encode()));
        assertEquals(SignatureBrewReceiptIndex.Lookup.UNTRUSTED_LEDGER, index.lookup(OWNER, TX));
        assertEquals(SignatureBrewReceiptIndex.RecordResult.UNTRUSTED_LEDGER,
                index.observe(OTHER, receipt(OTHER, OTHER)));
        assertTrue(index.hasUntrustedData());
        assertTrue(index.encodedEntries().contains(invalid));
        assertEquals(index.encodedEntries(), new SignatureBrewReceiptIndex(index.encodedEntries()).encodedEntries());
    }

    @Test
    void independentTransactionsRemainIndependentRegardlessOfRecordingOrder() {
        var index = new SignatureBrewReceiptIndex();
        var second = receipt(OTHER, OTHER);
        var first = receipt(TX, OWNER);
        index.observe(OTHER, second);
        index.observe(OWNER, first);
        assertEquals(SignatureBrewReceiptIndex.Lookup.OBSERVED, index.lookup(OWNER, TX));
        assertEquals(SignatureBrewReceiptIndex.Lookup.OBSERVED, index.lookup(OTHER, OTHER));
        var restored = new SignatureBrewReceiptIndex(index.encodedEntries());
        assertEquals(index.encodedEntries(), restored.encodedEntries());
    }
}
