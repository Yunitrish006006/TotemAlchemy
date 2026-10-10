package dev.totem.alchemy.mixture;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A transaction marker traveling WITH a prepared reward ItemStack.
 *
 * <p>The marker supports reconciliation and recipient checks, but is NOT a
 * durable player-side ledger or proof of payout: the item may be consumed,
 * moved, deleted or duplicated. Its presence must never directly authorize
 * the minting/retrying of another reward.</p>
 */
public record SignatureBrewRewardReceipt(
        UUID transactionId,
        UUID recipientId,
        Identifier signatureId,
        Identifier outputItemId
) {
    private static final String TAG = "totem_alchemy_signature_receipt";
    private static final String VERSION = "R1";

    public SignatureBrewRewardReceipt {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(signatureId, "signatureId");
        Objects.requireNonNull(outputItemId, "outputItemId");
    }

    public static Optional<SignatureBrewRewardReceipt> fromTicket(
            SignatureBrewDeliveryTicket ticket
    ) {
        if (ticket == null || ticket.isLegacyUnbound()) {
            return Optional.empty();
        }
        return Optional.of(new SignatureBrewRewardReceipt(
                ticket.transactionId(), ticket.recipientId(),
                ticket.signatureId(), ticket.result().itemId()));
    }

    public boolean matches(SignatureBrewDeliveryTicket ticket) {
        return ticket != null && !ticket.isLegacyUnbound()
                && transactionId.equals(ticket.transactionId())
                && recipientId.equals(ticket.recipientId())
                && signatureId.equals(ticket.signatureId())
                && outputItemId.equals(ticket.result().itemId());
    }

    public String encode() {
        return String.join("|", VERSION, transactionId.toString(),
                recipientId.toString(), signatureId.toString(), outputItemId.toString());
    }

    public static Optional<SignatureBrewRewardReceipt> decode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            String[] parts = value.split("\\|", -1);
            if (parts.length != 5 || !VERSION.equals(parts[0])) {
                return Optional.empty();
            }
            return Optional.of(new SignatureBrewRewardReceipt(
                    UUID.fromString(parts[1]), UUID.fromString(parts[2]),
                    Identifier.parse(parts[3]), Identifier.parse(parts[4])));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return Optional.empty();
        }
    }

    /**
     * Stamp a detached one-item reward that already carries the signature dose.
     * A copy is returned; no player inventory or cauldron is mutated here.
     */
    public Optional<ItemStack> stamp(ItemStack output) {
        if (!isMatchingOutput(output)) {
            return Optional.empty();
        }
        ItemStack copy = output.copy();
        CompoundTag tag = copy.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        // Do not overwrite a receipt from another transaction on an item.
        if (tag.contains(TAG)) {
            return Optional.empty();
        }
        tag.putString(TAG, encode());
        copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return Optional.of(copy);
    }

    public static Optional<SignatureBrewRewardReceipt> inspect(ItemStack output) {
        if (output == null || output.isEmpty() || output.getCount() != 1
                || !AlchemyMixtureBottle.isDrinkablePotion(output)) {
            return Optional.empty();
        }
        CompoundTag tag = output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG)) {
            return Optional.empty();
        }
        String payload = tag.getStringOr(TAG, "");
        Optional<SignatureBrewRewardReceipt> receipt = decode(payload);
        if (receipt.isEmpty() || !receipt.get().isMatchingOutput(output)) {
            return Optional.empty();
        }
        return receipt;
    }

    /** Both the item identity and stored dose's signature provenance must agree. */
    private boolean isMatchingOutput(ItemStack output) {
        if (output == null || output.isEmpty() || output.getCount() != 1
                || !AlchemyMixtureBottle.isDrinkablePotion(output)
                || !outputItemId.equals(BuiltInRegistries.ITEM.getKey(output.getItem()))
                || !AlchemyMixtureBottle.hasStoredMixture(output)) {
            return false;
        }
        AlchemyMixtureState dose = AlchemyMixtureBottle.storedMixture(output);
        return dose.volumeUnits() == 1 && !dose.hasCommittedSignatureProcess()
                && dose.signatureGroups().isEmpty() && !dose.hasPendingReactions()
                && dose.hasProvenance("signature:result:" + signatureId);
    }
}
