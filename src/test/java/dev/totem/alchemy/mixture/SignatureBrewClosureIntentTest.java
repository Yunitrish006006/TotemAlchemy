package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewClosureIntentTest {
    private static final UUID TX = UUID.fromString("963be2ac-08bd-423e-bca2-c031d2dc4b3e");
    private static final UUID OTHER_TX = UUID.fromString("c4847899-651b-4f8b-adb9-4b7e04a66298");
    private static final UUID PLAYER = UUID.fromString("985ea3bf-63c6-4d27-a022-c01b67a7d1fb");
    private static final UUID OTHER_PLAYER = UUID.fromString("bc45d9fb-0710-498b-b275-53bd2632630d");
    private static final Identifier SIGNATURE =
            Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");
    private static final SignatureBrewTransactionRegistry.Source SOURCE =
            new SignatureBrewTransactionRegistry.Source(
                    Identifier.fromNamespaceAndPath("minecraft", "overworld"), 82921L);

    private static SignatureBrewDeliveryTicket ticket(UUID id, UUID recipient) {
        var dose = new AlchemyMixtureState(1);
        dose.addProvenance("signature:result:" + SIGNATURE);
        return new SignatureBrewDeliveryTicket(id, recipient, SIGNATURE,
                new SignatureBrewDefinition.Result(
                        SignatureBrewDefinition.Type.BOTTLED_ITEM, SIGNATURE, 1,
                        Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null), dose);
    }

    private static SignatureBrewTransactionRegistry.Entry entry() {
        return new SignatureBrewTransactionRegistry.Entry(SOURCE, ticket(TX, PLAYER));
    }

    @Test
    void canonicalIntentRoundTripsItsExactOriginalFingerprintedTicket() {
        var original = entry();
        var intent = SignatureBrewClosureIntent.forOriginal(original);
        assertTrue(intent.matchesOriginal(original));
        assertEquals(64, intent.originalTicketSha256().length());
        assertEquals(intent, SignatureBrewClosureIntent.decode(intent.encode()).orElseThrow());
        assertEquals(original.transactionId(), intent.transactionId());
        assertEquals(original.source().dimensionId(), intent.dimensionId());
        assertEquals(original.source().packedBlockPos(), intent.packedBlockPos());
    }

    @Test
    void mismatchedRecipientSourceOrRewardCannotBorrowClosureReview() {
        var intent = SignatureBrewClosureIntent.forOriginal(entry());
        assertFalse(intent.matchesOriginal(new SignatureBrewTransactionRegistry.Entry(
                SOURCE, ticket(TX, OTHER_PLAYER))));
        assertFalse(intent.matchesOriginal(new SignatureBrewTransactionRegistry.Entry(
                SOURCE, ticket(OTHER_TX, PLAYER))));
        assertFalse(intent.matchesOriginal(new SignatureBrewTransactionRegistry.Entry(
                new SignatureBrewTransactionRegistry.Source(
                        Identifier.fromNamespaceAndPath("minecraft", "the_nether"), 82921L),
                ticket(TX, PLAYER))));
        assertFalse(intent.matchesOriginal(new SignatureBrewTransactionRegistry.Entry(
                new SignatureBrewTransactionRegistry.Source(SOURCE.dimensionId(), 82922L),
                ticket(TX, PLAYER))));
    }

    @Test
    void reviewRequestPersistsWithoutUnlockingOriginalSourceOrDeletingTicket() {
        var registry = new SignatureBrewTransactionRegistry();
        var original = entry();
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                registry.register(SOURCE, original.ticket()));
        assertEquals(SignatureBrewTransactionRegistry.ClosureResult.REVIEW_REQUESTED,
                registry.requestClosureReview(SOURCE, original.ticket()));
        assertEquals(SignatureBrewTransactionRegistry.ClosureResult.ALREADY_REQUESTED,
                registry.requestClosureReview(SOURCE, ticket(TX, PLAYER)));
        assertEquals(SignatureBrewTransactionRegistry.ClosureState.REVIEW_REQUESTED,
                registry.closureState(TX));
        assertEquals(SignatureBrewTransactionRegistry.LookupResult.PRESENT,
                registry.lookup(TX));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                registry.register(SOURCE, ticket(OTHER_TX, PLAYER)));
        assertEquals(original.ticket().encode(), registry.inspect(TX).orElseThrow().ticket().encode());
        assertEquals(1, registry.encodedClosureIntents().size());
    }

    @Test
    void originalAndReviewSurviveIndependentSaveLoadWithoutSourceReuse() {
        var registry = new SignatureBrewTransactionRegistry();
        registry.register(SOURCE, ticket(TX, PLAYER));
        registry.requestClosureReview(SOURCE, ticket(TX, PLAYER));

        var reloaded = new SignatureBrewTransactionRegistry(
                registry.encodedEntries(), registry.encodedClosureIntents());
        assertFalse(reloaded.needsManualRecovery());
        assertEquals(SignatureBrewTransactionRegistry.ClosureState.REVIEW_REQUESTED,
                reloaded.closureState(TX));
        assertTrue(reloaded.inspectClosure(TX).orElseThrow()
                .matchesOriginal(reloaded.inspect(TX).orElseThrow()));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                reloaded.register(SOURCE, ticket(OTHER_TX, PLAYER)));
        assertEquals(SignatureBrewTransactionRegistry.ClosureResult.ALREADY_REQUESTED,
                reloaded.requestClosureReview(SOURCE, ticket(TX, PLAYER)));
    }

    @Test
    void unknownOrForgedReviewRecordsAreRetainedAndQuarantined() {
        var original = entry();
        var invalid = SignatureBrewClosureIntent.forOriginal(original).encode()
                .replace(original.ticket().recipientId().toString(), OTHER_PLAYER.toString());
        var loaded = new SignatureBrewTransactionRegistry(
                List.of(original.encode()), List.of(invalid, "C2|future-data"));
        assertTrue(loaded.needsManualRecovery());
        assertEquals(SignatureBrewTransactionRegistry.LookupResult.UNTRUSTED_REGISTRY,
                loaded.lookup(TX));
        assertEquals(SignatureBrewTransactionRegistry.ClosureState.CONFLICT_OR_UNTRUSTED,
                loaded.closureState(TX));
        assertEquals(SignatureBrewTransactionRegistry.ClosureResult.UNTRUSTED_REGISTRY,
                loaded.requestClosureReview(SOURCE, original.ticket()));
        assertTrue(loaded.encodedClosureIntents().contains(invalid));
        assertTrue(loaded.encodedClosureIntents().contains("C2|future-data"));
        assertEquals(loaded.encodedClosureIntents(), new SignatureBrewTransactionRegistry(
                loaded.encodedEntries(), loaded.encodedClosureIntents()).encodedClosureIntents());
    }

    @Test
    void closureRequestRequiresExactRegisteredOriginalNotObservationOrStaleClaim() {
        var registry = new SignatureBrewTransactionRegistry();
        assertEquals(SignatureBrewTransactionRegistry.ClosureResult.NO_VERIFIED_ORIGINAL,
                registry.requestClosureReview(SOURCE, ticket(TX, PLAYER)));
        registry.register(SOURCE, ticket(TX, PLAYER));
        assertEquals(SignatureBrewTransactionRegistry.ClosureResult.NO_VERIFIED_ORIGINAL,
                registry.requestClosureReview(SOURCE, ticket(TX, OTHER_PLAYER)));
        assertEquals(SignatureBrewTransactionRegistry.ClosureResult.NO_VERIFIED_ORIGINAL,
                registry.requestClosureReview(
                        new SignatureBrewTransactionRegistry.Source(
                                Identifier.fromNamespaceAndPath("minecraft", "overworld"), 1000L),
                        ticket(TX, PLAYER)));
        assertTrue(registry.encodedClosureIntents().isEmpty());
    }

    @Test
    void brokenOrFutureClosureDoesNotDecodeIntoTrustedTombstone() {
        var raw = SignatureBrewClosureIntent.forOriginal(entry()).encode();
        assertTrue(SignatureBrewClosureIntent.decode(raw.replaceFirst("^C1", "C2")).isEmpty());
        assertTrue(SignatureBrewClosureIntent.decode(raw.replace("|minecraft:overworld|", "|invalid dimension|")).isEmpty());
        assertTrue(SignatureBrewClosureIntent.decode(raw.replace(TX.toString(), "invalid")).isEmpty());
        assertTrue(SignatureBrewClosureIntent.decode(raw + "|extra").isEmpty());
        assertTrue(SignatureBrewClosureIntent.decode(raw.replaceFirst("[0-9a-f]{64}$", "0")).isEmpty());
        assertTrue(SignatureBrewClosureIntent.decode(raw).isPresent());
    }
}
