package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBrewRewardReceiptTest {
    private static final UUID TX = UUID.fromString("9d72cb79-d7b1-4e0f-8ef3-6106f5fb344c");
    private static final UUID OWNER = UUID.fromString("6025f694-dbc8-4a41-9790-4d97358e7dbc");
    private static final Identifier HOT_COCOA = Identifier.fromNamespaceAndPath("totem", "alchemy/hot_cocoa");

    private static SignatureBrewDeliveryTicket ticket(UUID id, UUID owner) {
        AlchemyMixtureState dose = new AlchemyMixtureState(1);
        dose.addProvenance("signature:result:" + HOT_COCOA);
        return new SignatureBrewDeliveryTicket(id, owner, HOT_COCOA,
                new SignatureBrewDefinition.Result(SignatureBrewDefinition.Type.BOTTLED_ITEM,
                        HOT_COCOA, 1,
                        Identifier.fromNamespaceAndPath("minecraft", "glass_bottle"), null),
                dose);
    }

    @Test
    void receiptRoundTripsAndMatchesOnlyItsExactTransactionAndRecipient() {
        var original = ticket(TX, OWNER);
        var receipt = SignatureBrewRewardReceipt.fromTicket(original).orElseThrow();
        var decoded = SignatureBrewRewardReceipt.decode(receipt.encode()).orElseThrow();
        assertEquals(receipt, decoded);
        assertTrue(decoded.matches(original));
        assertFalse(decoded.matches(ticket(UUID.fromString("8f7ac2e0-2aa3-41b0-b9fc-0b7167d650ae"), OWNER)));
        assertFalse(decoded.matches(ticket(TX, UUID.fromString("b730c678-5488-4b49-8fc2-6a3358a48daa"))));
        assertFalse(decoded.matches(null));
    }

    @Test
    void twoPreparedRewardsCannotShareTheSameReceiptByAccident() {
        var a = SignatureBrewRewardReceipt.fromTicket(ticket(TX, OWNER)).orElseThrow();
        var b = SignatureBrewRewardReceipt.fromTicket(ticket(
                UUID.fromString("2a9c5f73-b32c-4687-a0c1-6bd86b70217b"), OWNER)).orElseThrow();
        assertNotEquals(a, b);
        assertNotEquals(a.encode(), b.encode());
    }

    @Test
    void ownerlessLegacyTicketCannotCreateARewardReceipt() {
        var bound = ticket(TX, OWNER);
        var legacy = new SignatureBrewDeliveryTicket(
                bound.transactionId(), bound.signatureId(), bound.result(), bound.dose());
        assertTrue(SignatureBrewRewardReceipt.fromTicket(legacy).isEmpty());
        assertTrue(SignatureBrewRewardReceipt.fromTicket(null).isEmpty());
    }

    @Test
    void malformedReceiptsCannotBeReinterpretedAsFreshValidClaims() {
        var valid = SignatureBrewRewardReceipt.fromTicket(ticket(TX, OWNER)).orElseThrow().encode();
        assertTrue(SignatureBrewRewardReceipt.decode(null).isEmpty());
        assertTrue(SignatureBrewRewardReceipt.decode("").isEmpty());
        assertTrue(SignatureBrewRewardReceipt.decode(valid.replaceFirst("^R1", "R2")).isEmpty());
        assertTrue(SignatureBrewRewardReceipt.decode(valid + "|extra").isEmpty());
        assertTrue(SignatureBrewRewardReceipt.decode(valid.replace(TX.toString(), "invalid")).isEmpty());
        assertTrue(SignatureBrewRewardReceipt.decode(valid.replace(OWNER.toString(), "")).isEmpty());
    }
}
