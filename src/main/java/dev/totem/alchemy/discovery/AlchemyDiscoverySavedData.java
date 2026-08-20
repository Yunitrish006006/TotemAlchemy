package dev.totem.alchemy.discovery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-persistent, per-player brewing discoveries plus observed outcome counts. */
public final class AlchemyDiscoverySavedData extends SavedData {
    private static final Codec<PlayerDiscoveries> PLAYER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerDiscoveries::player),
            Codec.STRING.listOf().optionalFieldOf("discoveries", List.of()).forGetter(PlayerDiscoveries::discoveries),
            Codec.STRING.listOf().optionalFieldOf("research", List.of()).forGetter(PlayerDiscoveries::research)
    ).apply(instance, PlayerDiscoveries::new));

    public static final Codec<AlchemyDiscoverySavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PLAYER_CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(AlchemyDiscoverySavedData::playerList)
    ).apply(instance, AlchemyDiscoverySavedData::new));

    public static final SavedDataType<AlchemyDiscoverySavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("totem-alchemy", "brew_discoveries"),
            AlchemyDiscoverySavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, Set<String>> discoveriesByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> researchByPlayer = new HashMap<>();

    public AlchemyDiscoverySavedData() {
    }

    private AlchemyDiscoverySavedData(List<PlayerDiscoveries> players) {
        for (PlayerDiscoveries player : players) {
            discoveriesByPlayer.put(player.player(), new HashSet<>(player.discoveries()));
            Map<String, Integer> counts = new HashMap<>();
            for (String encoded : player.research()) {
                int split = encoded.lastIndexOf('=');
                if (split <= 0 || split >= encoded.length() - 1) {
                    continue;
                }
                try {
                    int count = Integer.parseInt(encoded.substring(split + 1));
                    if (count > 0) {
                        counts.put(encoded.substring(0, split), count);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (!counts.isEmpty()) {
                researchByPlayer.put(player.player(), counts);
            }
        }
    }

    public static AlchemyDiscoverySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean record(UUID playerId, String discovery) {
        boolean changed = discoveriesByPlayer.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(discovery);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public void recordResearch(UUID playerId, String outcomeKey) {
        researchByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>()).merge(outcomeKey, 1, Integer::sum);
        setDirty();
    }

    public boolean has(UUID playerId, String discovery) {
        return discoveriesByPlayer.getOrDefault(playerId, Set.of()).contains(discovery);
    }

    public Set<String> discoveries(UUID playerId) {
        return Set.copyOf(discoveriesByPlayer.getOrDefault(playerId, Set.of()));
    }

    public Map<String, Integer> research(UUID playerId) {
        return Map.copyOf(researchByPlayer.getOrDefault(playerId, Map.of()));
    }

    public int researchTotal(UUID playerId, String ingredientId) {
        String prefix = ingredientId + ">";
        return researchByPlayer.getOrDefault(playerId, Map.of()).entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    private List<PlayerDiscoveries> playerList() {
        Set<UUID> players = new HashSet<>(discoveriesByPlayer.keySet());
        players.addAll(researchByPlayer.keySet());
        return players.stream().sorted().map(playerId -> {
            List<String> discoveries = discoveriesByPlayer.getOrDefault(playerId, Set.of()).stream().sorted().toList();
            List<String> research = researchByPlayer.getOrDefault(playerId, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toList();
            return new PlayerDiscoveries(playerId, discoveries, research);
        }).toList();
    }

    private record PlayerDiscoveries(UUID player, List<String> discoveries, List<String> research) {
        private PlayerDiscoveries {
            discoveries = List.copyOf(discoveries == null ? List.of() : discoveries);
            research = List.copyOf(research == null ? List.of() : research);
        }
    }
}
