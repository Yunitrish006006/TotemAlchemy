package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewTransactionRegistryTest {
    private static final UUID TX = UUID.fromString("b4c20569-31a2-484b-b55a-181214d2625d");
    private static final UUID OWNER = UUID.fromString("6c892e85-6039-4ae6-aa0c-5db9433096a4");
    private static final UUID OTHER = UUID.fromString("0c92b644-f361-476e-8491-068e1bb3aa0b");
    private static final Identifier RECIPE = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");
    private static final SignatureBrewTransactionRegistry.Source SOURCE =
            new SignatureBrewTransactionRegistry.Source(
                    Identifier.fromNamespaceAndPath("minecraft", "overworld"), 12345L);
    private static final SignatureBrewTransactionRegistry.Source OTHER_SOURCE =
            new SignatureBrewTransactionRegistry.Source(
                    Identifier.fromNamespaceAndPath("minecraft", "the_nether"), 12345L);

    private static SignatureBrewDeliveryTicket ticket(UUID id, UUID recipient) {
        var dose = new AlchemyMixtureState(1);
        dose.addProvenance("signature:result:" + RECIPE);
        return new SignatureBrewDeliveryTicket(id, recipient, RECIPE,
                new SignatureBrewDefinition.Result(
                        SignatureBrewDefinition.Type.BOTTLED_ITEM, RECIPE, 1,
                        Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null),
                dose);
    }

    @Test
    void registerAndRoundTripPreserveFullRewardAfterSourceChunkDisappears() {
        var registry = new SignatureBrewTransactionRegistry();
        var first = ticket(TX, OWNER);
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                registry.register(SOURCE, first));
        assertEquals(SignatureBrewTransactionRegistry.LookupResult.PRESENT, registry.lookup(TX));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.ALREADY_REGISTERED,
                registry.register(SOURCE, ticket(TX, OWNER)));

        var recovered = new SignatureBrewTransactionRegistry(registry.encodedEntries());
        var entry = recovered.inspect(TX).orElseThrow();
        assertEquals(SOURCE, entry.source());
        assertEquals(first.encode(), entry.ticket().encode());
        assertEquals(1, entry.ticket().dose().volumeUnits());
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.ALREADY_REGISTERED,
                recovered.register(SOURCE, ticket(TX, OWNER)));
        assertFalse(recovered.needsManualRecovery());
    }

    @Test
    void sameOriginCannotProduceNewTransactionUntilTerminalProtocolIsImplemented() {
        var registry = new SignatureBrewTransactionRegistry();
        registry.register(SOURCE, ticket(TX, OWNER));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_SOURCE,
                registry.register(SOURCE, ticket(OTHER, OWNER)));
        assertEquals(SignatureBrewTransactionRegistry.LookupResult.ABSENT_UNVERIFIED,
                registry.lookup(OTHER));
        assertEquals(1, registry.encodedEntries().size());
    }

    @Test
    void sameTransactionCannotBeReclaimedFromDifferentOriginOrRecipient() {
        var registry = new SignatureBrewTransactionRegistry();
        registry.register(SOURCE, ticket(TX, OWNER));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_TRANSACTION,
                registry.register(OTHER_SOURCE, ticket(TX, OWNER)));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.CONFLICT_TRANSACTION,
                registry.register(SOURCE, ticket(TX, OTHER)));
        assertEquals(OWNER, registry.inspect(TX).orElseThrow().ticket().recipientId());
        assertEquals(SOURCE, registry.inspect(TX).orElseThrow().source());
    }

    @Test
    void twoDifferentDimensionsDoNotShareTheSameOriginLock() {
        var registry = new SignatureBrewTransactionRegistry();
        registry.register(SOURCE, ticket(TX, OWNER));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.REGISTERED,
                registry.register(OTHER_SOURCE, ticket(OTHER, OTHER)));
        assertEquals(2, registry.encodedEntries().size());
    }

    @Test
    void oldUnboundTicketsCannotBecomeCanonicalPayoutAuthority() {
        var registry = new SignatureBrewTransactionRegistry();
        var bound = ticket(TX, OWNER);
        var legacy = new SignatureBrewDeliveryTicket(TX, RECIPE, bound.result(), bound.dose());
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.UNBOUND_TICKET,
                registry.register(SOURCE, legacy));
        assertTrue(registry.encodedEntries().isEmpty());
    }

    @Test
    void sourceCollisionInStoredEntriesQuarantinesBothTransactions() {
        var a = new SignatureBrewTransactionRegistry.Entry(SOURCE, ticket(TX, OWNER)).encode();
        var b = new SignatureBrewTransactionRegistry.Entry(SOURCE, ticket(OTHER, OTHER)).encode();
        var recovered = new SignatureBrewTransactionRegistry(List.of(a, b));
        assertTrue(recovered.needsManualRecovery());
        assertEquals(SignatureBrewTransactionRegistry.LookupResult.CONFLICT, recovered.lookup(TX));
        assertEquals(SignatureBrewTransactionRegistry.LookupResult.CONFLICT, recovered.lookup(OTHER));
        assertTrue(recovered.inspect(TX).isEmpty());
        assertTrue(recovered.inspect(OTHER).isEmpty());
        assertEquals(2, recovered.encodedEntries().size());
    }

    @Test
    void transactionCollisionAndUnknownFutureDataNeverDisappearOnSave() {
        var a = new SignatureBrewTransactionRegistry.Entry(SOURCE, ticket(TX, OWNER)).encode();
        var b = new SignatureBrewTransactionRegistry.Entry(OTHER_SOURCE, ticket(TX, OTHER)).encode();
        var recovered = new SignatureBrewTransactionRegistry(List.of(a, b));
        assertEquals(SignatureBrewTransactionRegistry.LookupResult.CONFLICT, recovered.lookup(TX));
        assertTrue(recovered.inspect(TX).isEmpty());

        List<String> invalid = new ArrayList<>(recovered.encodedEntries());
        invalid.add("A2|future-version");
        var quarantined = new SignatureBrewTransactionRegistry(invalid);
        assertTrue(quarantined.needsManualRecovery());
        assertEquals(SignatureBrewTransactionRegistry.LookupResult.UNTRUSTED_REGISTRY,
                quarantined.lookup(TX));
        assertEquals(SignatureBrewTransactionRegistry.RegisterResult.UNTRUSTED_REGISTRY,
                quarantined.register(OTHER_SOURCE, ticket(OTHER, OTHER)));
        assertTrue(quarantined.encodedEntries().contains("A2|future-version"));
        assertEquals(quarantined.encodedEntries(),
                new SignatureBrewTransactionRegistry(quarantined.encodedEntries()).encodedEntries());
    }

    @Test
    void malformedEncodedTicketCannotBeDecodedIntoExistingTransaction() {
        var entry = new SignatureBrewTransactionRegistry.Entry(SOURCE, ticket(TX, OWNER));
        assertTrue(SignatureBrewTransactionRegistry.Entry.decode(entry.encode()).isPresent());
        assertTrue(SignatureBrewTransactionRegistry.Entry.decode(
                entry.encode().replaceFirst("^A1", "A2")).isEmpty());
        assertTrue(SignatureBrewTransactionRegistry.Entry.decode(entry.encode() + "|extra").isEmpty());
        assertTrue(SignatureBrewTransactionRegistry.Entry.decode("A1|minecraft:overworld|a|x").isEmpty());
        assertTrue(SignatureBrewTransactionRegistry.Entry.decode("A1|invalid dimension|123|x").isEmpty());
    }
}
