package dev.totem.alchemy.discovery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-persistent, per-player successful brewing discoveries. */
public final class AlchemyDiscoverySavedData extends SavedData {
    private static final Codec<PlayerDiscoveries> PLAYER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerDiscoveries::player),
            Codec.STRING.listOf().optionalFieldOf("discoveries", List.of())
                    .forGetter(PlayerDiscoveries::discoveries)
    ).apply(instance, PlayerDiscoveries::new));

    public static final Codec<AlchemyDiscoverySavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PLAYER_CODEC.listOf().optionalFieldOf("players", List.of())
                    .forGetter(AlchemyDiscoverySavedData::playerList)
    ).apply(instance, AlchemyDiscoverySavedData::new));

    public static final SavedDataType<AlchemyDiscoverySavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("totem-alchemy", "brew_discoveries"),
            AlchemyDiscoverySavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, Set<String>> discoveriesByPlayer = new HashMap<>();

    public AlchemyDiscoverySavedData() {
    }

    private AlchemyDiscoverySavedData(List<PlayerDiscoveries> players) {
        for (PlayerDiscoveries player : players) {
            discoveriesByPlayer.put(player.player(), new HashSet<>(player.discoveries()));
        }
    }

    public static AlchemyDiscoverySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean record(UUID playerId, String discovery) {
        boolean changed = discoveriesByPlayer
                .computeIfAbsent(playerId, ignored -> new HashSet<>())
                .add(discovery);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean has(UUID playerId, String discovery) {
        return discoveriesByPlayer.getOrDefault(playerId, Set.of()).contains(discovery);
    }

    public Set<String> discoveries(UUID playerId) {
        return Set.copyOf(discoveriesByPlayer.getOrDefault(playerId, Set.of()));
    }

    private List<PlayerDiscoveries> playerList() {
        List<PlayerDiscoveries> players = new ArrayList<>(discoveriesByPlayer.size());
        discoveriesByPlayer.forEach((player, discoveries) ->
                players.add(new PlayerDiscoveries(player, List.copyOf(discoveries))));
        return players;
    }

    private record PlayerDiscoveries(UUID player, List<String> discoveries) {
        private PlayerDiscoveries {
            discoveries = List.copyOf(discoveries == null ? List.of() : discoveries);
        }
    }
}
