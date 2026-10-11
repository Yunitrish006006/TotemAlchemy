package dev.totem.alchemy.mixture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.UUID;

/**
 * World-saved per-player audit observations of R1-marked reward items.
 *
 * <p>This separate SavedData file survives destruction/consumption of the
 * observed ItemStack, but does NOT prove the player's inventory file was
 * durably persisted. It does not authorize retries, payouts or cauldron escrow
 * acknowledgment; the future transaction coordinator must resolve independent
 * save ordering and crash windows first.</p>
 */
public final class SignatureBrewPlayerReceiptSavedData extends SavedData {
    public static final Codec<SignatureBrewPlayerReceiptSavedData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.listOf().optionalFieldOf("observations", List.of())
                            .forGetter(SignatureBrewPlayerReceiptSavedData::encodedObservations)
            ).apply(instance, SignatureBrewPlayerReceiptSavedData::new));

    public static final SavedDataType<SignatureBrewPlayerReceiptSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("totem", "alchemy/signature_receipt_observations"),
            SignatureBrewPlayerReceiptSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final SignatureBrewReceiptIndex index;

    public SignatureBrewPlayerReceiptSavedData() {
        index = new SignatureBrewReceiptIndex();
    }

    private SignatureBrewPlayerReceiptSavedData(List<String> savedEntries) {
        index = new SignatureBrewReceiptIndex(savedEntries);
    }

    /** Persisted in the overworld's data storage; keyed by player and ticket UUID. */
    public static SignatureBrewPlayerReceiptSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Append a receipt only after inspecting an actual signed one-dose item.
     * This method NEVER issues any item, modifies player inventory, or changes
     * the cauldron's J1 delivery state.
     */
    public SignatureBrewReceiptIndex.RecordResult observe(
            UUID playerId, SignatureBrewDeliveryTicket ticket, ItemStack observedItem
    ) {
        var receipt = SignatureBrewRewardReceipt.inspect(observedItem);
        if (playerId == null || ticket == null || receipt.isEmpty()
                || !ticket.belongsTo(playerId) || !receipt.get().matches(ticket)) {
            return SignatureBrewReceiptIndex.RecordResult.WRONG_RECIPIENT;
        }
        var status = index.observe(playerId, receipt.get());
        if (status == SignatureBrewReceiptIndex.RecordResult.RECORDED
                || status == SignatureBrewReceiptIndex.RecordResult.CONFLICT) {
            setDirty();
        }
        return status;
    }

    public SignatureBrewReceiptIndex.Lookup lookup(UUID playerId, UUID transactionId) {
        return index.lookup(playerId, transactionId);
    }

    /** Compare the full saved observation, not merely the UUID lookup key. */
    public SignatureBrewReceiptIndex.Evidence compareTicket(SignatureBrewDeliveryTicket ticket) {
        return index.compareTicket(ticket);
    }

    public boolean needsManualRecovery() {
        return index.hasUntrustedData();
    }

    /** Codec input/output for independent persistence and restart tests. */
    private List<String> encodedObservations() {
        return index.encodedEntries();
    }
}
