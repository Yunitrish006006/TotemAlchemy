package dev.totem.alchemy.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests the vanilla item-activation animation for one newly recorded brewing material. */
public record AlchemyMaterialDiscoveredPayload(Identifier material) implements CustomPacketPayload {
    public static final Type<AlchemyMaterialDiscoveredPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totem-alchemy", "material_discovered"));
    public static final StreamCodec<FriendlyByteBuf, AlchemyMaterialDiscoveredPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeIdentifier(payload.material()),
            buffer -> new AlchemyMaterialDiscoveredPayload(buffer.readIdentifier())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
