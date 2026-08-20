package dev.totem.alchemy.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Player-visible research snapshot. It intentionally contains no true brewing probabilities. */
public record AlchemyResearchPayload(List<String> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 1024;
    public static final int MAX_ENTRY_LENGTH = 256;
    public static final Type<AlchemyResearchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totem-alchemy", "brew_research"));
    public static final StreamCodec<FriendlyByteBuf, AlchemyResearchPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> write(buffer, payload.entries()),
            AlchemyResearchPayload::read
    );

    public AlchemyResearchPayload {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many Alchemy research entries: " + entries.size());
        }
    }

    private static void write(FriendlyByteBuf buffer, List<String> entries) {
        buffer.writeVarInt(entries.size());
        entries.forEach(entry -> buffer.writeUtf(entry, MAX_ENTRY_LENGTH));
    }

    private static AlchemyResearchPayload read(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new DecoderException("Invalid Alchemy research entry count: " + size);
        }
        List<String> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(buffer.readUtf(MAX_ENTRY_LENGTH));
        }
        return new AlchemyResearchPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
