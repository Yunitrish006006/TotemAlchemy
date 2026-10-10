package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewSourceFenceTest {
    private static final UUID TX = UUID.fromString("c83d2550-5020-4052-9cf8-6d7c168da5cd");
    private static final UUID OTHER_TX = UUID.fromString("7c9b4b71-86d5-4b35-9c1c-31bff71c2a60");
    private static final UUID RECIPIENT = UUID.fromString("a93ad420-3ad6-4cca-b658-983c1995fc0d");
    private static final UUID OTHER_RECIPIENT = UUID.fromString("e4a6c6f3-cc56-4423-a3fa-01b7aaea3cc7");
    private static final Identifier RECIPE =
            Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");
    private static final SignatureBrewTransactionRegistry.Source SOURCE =
            new SignatureBrewTransactionRegistry.Source(
                    Identifier.fromNamespaceAndPath("minecraft", "overworld"), 293847L);
    private static final SignatureBrewTransactionRegistry.Source NETHER_SOURCE =
            new SignatureBrewTransactionRegistry.Source(
                    Identifier.fromNamespaceAndPath("minecraft", "the_nether"), 293847L);

    private static SignatureBrewDeliveryTicket ticket(UUID id, UUID recipient) {
        AlchemyMixtureState dose = new AlchemyMixtureState(1);
        dose.addProvenance("signature:result:" + RECIPE);
        return new SignatureBrewDeliveryTicket(
                id, recipient, RECIPE,
                new SignatureBrewDefinition.Result(
                        SignatureBrewDefinition.Type.BOTTLED_ITEM, RECIPE, 1,
                        Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null),
                dose);
    }

    private static SignatureBrewTransactionRegistry.Entry original() {
        return new SignatureBrewTransactionRegistry.Entry(SOURCE, ticket(TX, RECIPIENT));
    }

    @Test
    void canonicalFenceRoundTripsAndBindsFullOriginalTicketFingerprint() {
        var entry = original();
        var fence = SignatureBrewSourceFence.first(entry);
        assertEquals(SignatureBrewSourceFence.INITIAL_GENERATION, fence.generation());
        assertEquals(TX, fence.transactionId());
        assertEquals(SOURCE, fence.source());
        assertEquals(64, fence.originalTicketSha256().length());
        assertTrue(fence.matchesOriginal(entry));
        assertEquals(fence, SignatureBrewSourceFence.decode(fence.encode()).orElseThrow());

        assertFalse(fence.matchesOriginal(new SignatureBrewTransactionRegistry.Entry(
                SOURCE, ticket(TX, OTHER_RECIPIENT))));
        assertFalse(fence.matchesOriginal(new SignatureBrewTransactionRegistry.Entry(
                NETHER_SOURCE, ticket(TX, RECIPIENT))));
    }

    @Test
    void registeringOriginalAlsoRegistersExactlyOneInitialFence() {
        var registry = new SignatureBrewTransactionRegistry();
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                registry.register(SOURCE, ticket(TX, RECIPIENT)));
        assertEquals(SignatureBrewTransactionRegistry.FenceState.GENESIS_MATCH,
                registry.inspectFence(SOURCE, TX));
        assertEquals(1, registry.encodedSourceFences().size());
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.ALREADY_REGISTERED,
                registry.register(SOURCE, ticket(TX, RECIPIENT)));
        assertEquals(1, registry.encodedSourceFences().size());

        var restored = new SignatureBrewTransactionRegistry(
                registry.encodedEntries(), registry.encodedClosureIntents(),
                registry.encodedSourceFences());
        assertEquals(SignatureBrewTransactionRegistry.FenceState.GENESIS_MATCH,
                restored.inspectFence(SOURCE, TX));
        assertFalse(restored.needsManualRecovery());
        assertEquals(registry.encodedSourceFences(), restored.encodedSourceFences());
    }

    @Test
    void legacyA1WithoutF1NeverSilentlyManufacturesGeneration() {
        var registry = new SignatureBrewTransactionRegistry(List.of(original().encode()));
        assertEquals(SignatureBrewTransactionRegistry.FenceState.LEGACY_UNFENCED,
                registry.inspectFence(SOURCE, TX));
        assertTrue(registry.encodedSourceFences().isEmpty());
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.ALREADY_REGISTERED,
                registry.register(SOURCE, ticket(TX, RECIPIENT)));
        assertTrue(registry.encodedSourceFences().isEmpty());
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                registry.register(SOURCE, ticket(OTHER_TX, RECIPIENT)));
        assertEquals(SignatureBrewTransactionRegistry.FenceState.LEGACY_UNFENCED,
                new SignatureBrewTransactionRegistry(
                        registry.encodedEntries(), List.of(), registry.encodedSourceFences())
                        .inspectFence(SOURCE, TX));
    }

    @Test
    void differentSourceAndUnknownTransactionCannotBorrowOriginalFence() {
        var registry = new SignatureBrewTransactionRegistry();
        registry.register(SOURCE, ticket(TX, RECIPIENT));
        assertEquals(SignatureBrewTransactionRegistry.FenceState.WRONG_SOURCE,
                registry.inspectFence(NETHER_SOURCE, TX));
        assertEquals(SignatureBrewTransactionRegistry.FenceState.ORIGINAL_ABSENT,
                registry.inspectFence(SOURCE, OTHER_TX));
        assertEquals(SignatureBrewTransactionRegistry.FenceState.CONFLICT_OR_UNTRUSTED,
                registry.inspectFence(null, TX));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                registry.register(SOURCE, ticket(OTHER_TX, RECIPIENT)));
    }

    @Test
    void forgedDifferentRecipientDigestOrTransactionIsKeptAndQuarantined() {
        var original = original();
        var valid = SignatureBrewSourceFence.first(original);
        var wrongTransaction = new SignatureBrewSourceFence(
                SOURCE, 1, OTHER_TX, valid.originalTicketSha256()).encode();
        var wrongDigest = new SignatureBrewSourceFence(
                SOURCE, 1, TX, "0".repeat(64)).encode();

        for (String tampered : List.of(wrongTransaction, wrongDigest)) {
            var loaded = new SignatureBrewTransactionRegistry(
                    List.of(original.encode()), List.of(), List.of(tampered));
            assertTrue(loaded.needsManualRecovery());
            assertEquals(SignatureBrewTransactionRegistry.FenceState.CONFLICT_OR_UNTRUSTED,
                    loaded.inspectFence(SOURCE, TX));
            assertEquals(SignatureBrewTransactionRegistry.LookupResult.UNTRUSTED_REGISTRY,
                    loaded.lookup(TX));
            assertEquals(SignatureBrewTransactionRegistry.RegisterResult.UNTRUSTED_REGISTRY,
                    loaded.register(SOURCE, original.ticket()));
            assertTrue(loaded.encodedSourceFences().contains(tampered));
        }
    }

    @Test
    void malformedUnknownAndFutureEpochRecordsNeverCreateValidFence() {
        String valid = SignatureBrewSourceFence.first(original()).encode();
        for (String invalid : List.of(
                valid.replaceFirst("^F1", "F2"),
                valid.replace("|minecraft:overworld|", "|invalid dimension|"),
                valid.replace("|1|", "|2|"),
                valid.replace(TX.toString(), "not-a-uuid"),
                valid.replaceFirst("[0-9a-f]{64}$", "wrong-fingerprint"),
                valid + "|extra")) {
            assertTrue(SignatureBrewSourceFence.decode(invalid).isEmpty());
            var loaded = new SignatureBrewTransactionRegistry(
                    List.of(original().encode()), List.of(), List.of(invalid));
            assertEquals(SignatureBrewTransactionRegistry.FenceState.CONFLICT_OR_UNTRUSTED,
                    loaded.inspectFence(SOURCE, TX));
            assertTrue(loaded.encodedSourceFences().contains(invalid));
        }
    }

    @Test
    void independentDimensionsReceiveSeparateGenesisWithoutAnyUnlockApi() {
        var registry = new SignatureBrewTransactionRegistry();
        registry.register(SOURCE, ticket(TX, RECIPIENT));
        registry.register(NETHER_SOURCE, ticket(OTHER_TX, RECIPIENT));
        assertEquals(2, registry.encodedSourceFences().size());
        assertEquals(SignatureBrewTransactionRegistry.FenceState.GENESIS_MATCH,
                registry.inspectFence(SOURCE, TX));
        assertEquals(SignatureBrewTransactionRegistry.FenceState.GENESIS_MATCH,
                registry.inspectFence(NETHER_SOURCE, OTHER_TX));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                registry.register(SOURCE, ticket(UUID.randomUUID(), RECIPIENT)));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                registry.register(NETHER_SOURCE, ticket(UUID.randomUUID(), RECIPIENT)));
    }
}
