package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewDeliveryProgressTest {
    private static final UUID TX = UUID.fromString("3f9fbc6c-9734-4b14-819a-af6c59a2cb31");
    private static final UUID OWNER = UUID.fromString("ee4262a3-59ad-4d6f-843c-abd2de6a1b71");
    private static final UUID STRANGER = UUID.fromString("abf8b3a9-4f6f-40ae-aab1-16a7035f9683");
    private static final Identifier SIGNATURE = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");

    private static SignatureBrewDeliveryTicket ticket() {
        AlchemyMixtureState dose = new AlchemyMixtureState(1);
        dose.addProvenance("signature:result:" + SIGNATURE);
        return new SignatureBrewDeliveryTicket(
                TX, OWNER, SIGNATURE,
                new SignatureBrewDefinition.Result(
                        SignatureBrewDefinition.Type.BOTTLED_ITEM, SIGNATURE, 1,
                        Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null),
                dose);
    }

    @Test
    void preparedStateIsIdempotentAndNeverAuthorizesRepeatedAttempt() {
        var prepared = SignatureBrewDeliveryProgress.prepared(ticket());
        assertEquals(SignatureBrewDeliveryProgress.Phase.PREPARED, prepared.phase());
        assertTrue(prepared.matches(ticket()));
        assertFalse(prepared.needsReconciliation());

        var started = prepared.beginIssuance(TX, OWNER).orElseThrow();
        assertEquals(SignatureBrewDeliveryProgress.Phase.ISSUANCE_UNCERTAIN, started.phase());
        assertTrue(started.needsReconciliation());
        assertTrue(started.beginIssuance(TX, OWNER).isEmpty(),
                "A repeated request is never permission to mint a second item");
        assertEquals(started, SignatureBrewDeliveryProgress.decode(started.encode()).orElseThrow());
    }

    @Test
    void wrongTransactionOrRecipientCannotStartIssuance() {
        var prepared = SignatureBrewDeliveryProgress.prepared(ticket());
        assertTrue(prepared.beginIssuance(TX, STRANGER).isEmpty());
        assertTrue(prepared.beginIssuance(STRANGER, OWNER).isEmpty());
        assertTrue(prepared.beginIssuance(TX, null).isEmpty());
        assertTrue(prepared.beginIssuance(null, OWNER).isEmpty());
        assertEquals(SignatureBrewDeliveryProgress.Phase.PREPARED, prepared.phase());
    }

    @Test
    void olderUnjournaledTicketIsUncertainNotAutomaticallyRetryable() {
        var uncertain = SignatureBrewDeliveryProgress.unresolved(ticket());
        assertTrue(uncertain.matches(ticket()));
        assertTrue(uncertain.needsReconciliation());
        assertTrue(uncertain.beginIssuance(TX, OWNER).isEmpty());
    }

    @Test
    void cannotMakeProgressForUnboundLegacyTicket() {
        var bound = ticket();
        var legacy = new SignatureBrewDeliveryTicket(
                bound.transactionId(), bound.signatureId(), bound.result(), bound.dose());
        assertThrows(IllegalArgumentException.class,
                () -> SignatureBrewDeliveryProgress.prepared(legacy));
        assertThrows(IllegalArgumentException.class,
                () -> SignatureBrewDeliveryProgress.unresolved(legacy));
    }

    @Test
    void decoderRejectsDamagedAndFutureStateWithoutInventingReceipt() {
        var progress = SignatureBrewDeliveryProgress.prepared(ticket());
        assertTrue(SignatureBrewDeliveryProgress.decode(null).isEmpty());
        assertTrue(SignatureBrewDeliveryProgress.decode("").isEmpty());
        assertTrue(SignatureBrewDeliveryProgress.decode(
                progress.encode().replaceFirst("^J1", "J2")).isEmpty());
        assertTrue(SignatureBrewDeliveryProgress.decode(
                progress.encode().replace("PREPARED", "RETRY_ALLOWED")).isEmpty());
        assertTrue(SignatureBrewDeliveryProgress.decode(progress.encode() + "|extra").isEmpty());
        assertTrue(SignatureBrewDeliveryProgress.decode(
                progress.encode().replace(TX.toString(), "not-a-uuid")).isEmpty());
        assertTrue(SignatureBrewDeliveryProgress.decode(
                progress.encode().replace(OWNER.toString(), "")).isEmpty());
        assertTrue(SignatureBrewDeliveryProgress.decode(progress.encode()).isPresent());
    }

    @Test
    void matchingBothIdentifiersIsRequiredWhenReloadingJournal() {
        var progress = SignatureBrewDeliveryProgress.prepared(ticket());
        var sameRecipientOtherTransaction = new SignatureBrewDeliveryTicket(
                STRANGER, OWNER, SIGNATURE, ticket().result(), ticket().dose());
        var sameTransactionOtherRecipient = new SignatureBrewDeliveryTicket(
                TX, STRANGER, SIGNATURE, ticket().result(), ticket().dose());
        assertFalse(progress.matches(sameRecipientOtherTransaction));
        assertFalse(progress.matches(sameTransactionOtherRecipient));
    }
}
