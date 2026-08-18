package dev.totem.alchemy.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Complete discovery snapshot sent to the player's client. */
public record AlchemyDiscoveriesPayload(List<String> discoveries) implements CustomPacketPayload {
    public static final int MAX_DISCOVERIES = 512;
    public static final int MAX_KEY_LENGTH = 192;
    public static final Type<AlchemyDiscoveriesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totem-alchemy", "brew_discoveries"));
    public static final StreamCodec<FriendlyByteBuf, AlchemyDiscoveriesPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> write(buffer, payload.discoveries()),
            AlchemyDiscoveriesPayload::read
    );

    public AlchemyDiscoveriesPayload {
        discoveries = List.copyOf(discoveries);
        if (discoveries.size() > MAX_DISCOVERIES) {
            throw new IllegalArgumentException("Too many Alchemy discoveries: " + discoveries.size());
        }
    }

    private static void write(FriendlyByteBuf buffer, List<String> discoveries) {
        buffer.writeVarInt(discoveries.size());
        discoveries.forEach(discovery -> buffer.writeUtf(discovery, MAX_KEY_LENGTH));
    }

    private static AlchemyDiscoveriesPayload read(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_DISCOVERIES) {
            throw new DecoderException("Invalid Alchemy discovery count: " + size);
        }
        List<String> discoveries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            discoveries.add(buffer.readUtf(MAX_KEY_LENGTH));
        }
        return new AlchemyDiscoveriesPayload(discoveries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
