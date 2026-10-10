package dev.totem.alchemy.mixture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Experimental single-world escrow index, persisted independently of cauldron
 * chunks and player.dat; it is not connected to live bottle interactions.
 *
 * <p>Until a durable write/rollback protocol exists, the entry is only an
 * immutable candidate authority for transaction identity and origin, NOT
 * permission to issue, retry, or acknowledge a drink.</p>
 */
public final class SignatureBrewTransactionSavedData extends SavedData {
    public static final Codec<SignatureBrewTransactionSavedData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.listOf().optionalFieldOf("transactions", List.of())
                            .forGetter(SignatureBrewTransactionSavedData::encodedTransactions)
            ).apply(instance, SignatureBrewTransactionSavedData::new));

    public static final SavedDataType<SignatureBrewTransactionSavedData> TYPE =
            new SavedDataType<>(
                    Identifier.fromNamespaceAndPath("totem", "alchemy/signature_transactions"),
                    SignatureBrewTransactionSavedData::new,
                    CODEC,
                    DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final SignatureBrewTransactionRegistry registry;

    public SignatureBrewTransactionSavedData() {
        registry = new SignatureBrewTransactionRegistry();
    }

    private SignatureBrewTransactionSavedData(List<String> raw) {
        registry = new SignatureBrewTransactionRegistry(raw);
    }

    public static SignatureBrewTransactionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Explicit registration only; never invoked automatically from the live
     * cauldron code at this checkpoint. This call does NOT force a disk flush.
     */
    public SignatureBrewTransactionRegistry.RegisterResult register(
            Identifier dimensionId, BlockPos pos, SignatureBrewDeliveryTicket ticket
    ) {
        if (dimensionId == null || pos == null) {
            return SignatureBrewTransactionRegistry.RegisterResult.UNTRUSTED_REGISTRY;
        }
        var result = registry.register(
                new SignatureBrewTransactionRegistry.Source(dimensionId, pos.asLong()), ticket);
        if (result == SignatureBrewTransactionRegistry.RegisterResult.REGISTERED) {
            setDirty();
        }
        return result;
    }

    public SignatureBrewTransactionRegistry.LookupResult lookup(UUID transactionId) {
        return registry.lookup(transactionId);
    }

    public Optional<SignatureBrewTransactionRegistry.Entry> inspect(UUID transactionId) {
        return registry.inspect(transactionId);
    }

    /** Audit only; never authorizes an inventory reward. */
    public SignatureBrewTransactionRegistry.Verification verify(
            Identifier dimensionId, BlockPos sourcePos, SignatureBrewDeliveryTicket ticket
    ) {
        return registry.verify(
                dimensionId == null || sourcePos == null ? null
                        : new SignatureBrewTransactionRegistry.Source(dimensionId, sourcePos.asLong()),
                ticket);
    }

    public boolean needsManualRecovery() {
        return registry.needsManualRecovery();
    }

    private List<String> encodedTransactions() {
        return registry.encodedEntries();
    }
}
