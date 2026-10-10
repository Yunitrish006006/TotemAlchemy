package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewDeliveryTicketTest {
    private static final Identifier SIGNATURE = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");
    private static final UUID RECEIPT_ID = UUID.fromString("5f82c691-2a23-48f0-881f-fc9978342afb");
    private static final UUID RECIPIENT = UUID.fromString("3c3fd25e-8195-40e9-a50d-13f73c682611");

    private static SignatureBrewDefinition.Result result() {
        return new SignatureBrewDefinition.Result(
                SignatureBrewDefinition.Type.BOTTLED_ITEM, SIGNATURE, 1,
                Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"),
                Identifier.fromNamespaceAndPath("totem", "alchemy/saturation"));
    }

    private static AlchemyMixtureState dose() {
        AlchemyMixtureState dose = new AlchemyMixtureState(1);
        dose.setLiquidComposition(LiquidComposition.single(
                Identifier.fromNamespaceAndPath("minecraft", "milk"), 1.0D));
        dose.addProvenance("signature:result:" + SIGNATURE);
        return dose;
    }

    @Test
    void preparedTicketRoundTripsWithStableIdAndOneDose() {
        var original = new SignatureBrewDeliveryTicket(RECEIPT_ID, RECIPIENT, SIGNATURE, result(), dose());
        var restored = SignatureBrewDeliveryTicket.decode(original.encode()).orElseThrow();

        assertEquals(RECEIPT_ID, restored.transactionId());
        assertEquals(RECIPIENT, restored.recipientId());
        assertTrue(restored.belongsTo(RECIPIENT));
        assertFalse(restored.belongsTo(null));
        assertFalse(restored.belongsTo(UUID.fromString("63a01eb6-6c51-4f8d-80f1-b22ded8716dc")));
        assertTrue(original.encode().startsWith("S2|"));
        assertEquals(SIGNATURE, restored.signatureId());
        assertEquals(result(), restored.result());
        assertEquals(original.encode(), restored.encode());
        assertEquals(1, restored.dose().volumeUnits());
        assertFalse(restored.dose().hasCommittedSignatureProcess());
    }

    @Test
    void ticketOwnsDetachedDoseDespiteCallerMutations() {
        AlchemyMixtureState source = dose();
        var ticket = new SignatureBrewDeliveryTicket(RECEIPT_ID, RECIPIENT, SIGNATURE, result(), source);
        source.extractUnits(1);
        assertEquals(1, ticket.dose().volumeUnits());

        AlchemyMixtureState detached = ticket.dose();
        detached.extractUnits(1);
        assertEquals(1, ticket.dose().volumeUnits());
    }

    @Test
    void rejectsClaimsWithMissingReceiptProvenanceOrIncorrectVolume() {
        var missingMarker = new AlchemyMixtureState(1);
        assertThrows(IllegalArgumentException.class,
                () -> new SignatureBrewDeliveryTicket(RECEIPT_ID, RECIPIENT, SIGNATURE, result(), missingMarker));
        var twoUnits = new AlchemyMixtureState(2);
        twoUnits.addProvenance("signature:result:" + SIGNATURE);
        assertThrows(IllegalArgumentException.class,
                () -> new SignatureBrewDeliveryTicket(RECEIPT_ID, RECIPIENT, SIGNATURE, result(), twoUnits));

        var wrongSignature = Identifier.fromNamespaceAndPath("totem", "alchemy/cherry_brew");
        assertThrows(IllegalArgumentException.class,
                () -> new SignatureBrewDeliveryTicket(RECEIPT_ID, RECIPIENT, wrongSignature, result(), dose()));
    }

    @Test
    void damagedUnknownOrMalformedRecordsNeverYieldRedeemableTicket() {
        var ticket = new SignatureBrewDeliveryTicket(RECEIPT_ID, RECIPIENT, SIGNATURE, result(), dose());
        assertTrue(SignatureBrewDeliveryTicket.decode(null).isEmpty());
        assertTrue(SignatureBrewDeliveryTicket.decode("").isEmpty());
        assertTrue(SignatureBrewDeliveryTicket.decode(ticket.encode().replaceFirst("^S2", "S3")).isEmpty());
        assertTrue(SignatureBrewDeliveryTicket.decode(ticket.encode() + "|extra").isEmpty());
        assertTrue(SignatureBrewDeliveryTicket.decode(ticket.encode().replace(RECEIPT_ID.toString(), "invalid")).isEmpty());
        assertTrue(SignatureBrewDeliveryTicket.decode(ticket.encode().replace(RECIPIENT.toString(), "invalid")).isEmpty());

        String wrongDose = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new AlchemyMixtureState(1).encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String[] parts = ticket.encode().split("\\|", -1);
        parts[9] = wrongDose;
        assertTrue(SignatureBrewDeliveryTicket.decode(String.join("|", parts)).isEmpty());
    }

    @Test
    void legacyS1TicketsKeepOriginalWireFormatButNeverMatchAnyPlayer() {
        var old = new SignatureBrewDeliveryTicket(RECEIPT_ID, SIGNATURE, result(), dose());
        assertTrue(old.encode().startsWith("S1|"));
        var restored = SignatureBrewDeliveryTicket.decode(old.encode()).orElseThrow();
        assertTrue(restored.isLegacyUnbound());
        assertFalse(restored.belongsTo(RECIPIENT));
        assertFalse(restored.belongsTo(null));
        assertEquals(old.encode(), restored.encode());
    }

    @Test
    void malformedOwnerCannotBeInterpretedAsLegacy() {
        var ticket = new SignatureBrewDeliveryTicket(RECEIPT_ID, RECIPIENT, SIGNATURE, result(), dose());
        String[] parts = ticket.encode().split("\\|", -1);
        parts[2] = "";
        assertTrue(SignatureBrewDeliveryTicket.decode(String.join("|", parts)).isEmpty());
        parts[2] = RECIPIENT.toString();
        parts[0] = "S1";
        assertTrue(SignatureBrewDeliveryTicket.decode(String.join("|", parts)).isEmpty());
    }

    @Test
    void sameRecipeWithDifferentTransactionIdentifiersIsNeverConfused() {
        var first = new SignatureBrewDeliveryTicket(RECEIPT_ID, RECIPIENT, SIGNATURE, result(), dose());
        var second = new SignatureBrewDeliveryTicket(
                UUID.fromString("8e25b4ab-8643-48f0-bb10-a195dfe393b1"),
                RECIPIENT, SIGNATURE, result(), dose());
        assertNotEquals(first.transactionId(), second.transactionId());
        assertNotEquals(first.encode(), second.encode());
    }
}
