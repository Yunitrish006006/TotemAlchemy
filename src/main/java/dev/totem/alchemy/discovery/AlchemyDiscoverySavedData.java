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

/** World-persistent, per-player brewing discoveries, observed outcomes, and processing times. */
public final class AlchemyDiscoverySavedData extends SavedData {
    private static final Codec<PlayerDiscoveries> PLAYER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerDiscoveries::player),
            Codec.STRING.listOf().optionalFieldOf("discoveries", List.of()).forGetter(PlayerDiscoveries::discoveries),
            Codec.STRING.listOf().optionalFieldOf("known_materials", List.of()).forGetter(PlayerDiscoveries::knownMaterials),
            Codec.STRING.listOf().optionalFieldOf("research", List.of()).forGetter(PlayerDiscoveries::research),
            Codec.STRING.listOf().optionalFieldOf("material_samples", List.of()).forGetter(PlayerDiscoveries::materialSamples),
            Codec.STRING.listOf().optionalFieldOf("processing_times", List.of()).forGetter(PlayerDiscoveries::processingTimes)
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
    private final Map<UUID, Set<String>> knownMaterialsByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> researchByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> materialSamplesByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, ProcessingTimeStats>> processingTimesByPlayer = new HashMap<>();

    public AlchemyDiscoverySavedData() {
    }

    private AlchemyDiscoverySavedData(List<PlayerDiscoveries> players) {
        boolean normalizedLegacyData = false;
        for (PlayerDiscoveries player : players) {
            Set<String> discoveries = new HashSet<>(player.discoveries());
            discoveriesByPlayer.put(player.player(), discoveries);

            Set<String> knownMaterials = new HashSet<>(player.knownMaterials());
            knownMaterials.removeIf(material -> material == null || material.isBlank());
            if (!knownMaterials.isEmpty()) {
                knownMaterialsByPlayer.put(player.player(), knownMaterials);
            }

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
            for (String discovery : discoveries) {
                int split = discovery.indexOf('>');
                if (split > 0 && split < discovery.length() - 1 && counts.putIfAbsent(discovery, 1) == null) {
                    normalizedLegacyData = true;
                }
            }
            if (!counts.isEmpty()) {
                researchByPlayer.put(player.player(), counts);
            }

            Map<String, Integer> materialSamples = decodePositiveCounts(player.materialSamples());
            Map<String, Integer> legacySamples = new HashMap<>();
            counts.forEach((key, count) -> {
                int split = key.indexOf('>');
                if (split > 0) {
                    legacySamples.merge(key.substring(0, split), count, Integer::sum);
                }
            });
            for (Map.Entry<String, Integer> entry : legacySamples.entrySet()) {
                if (materialSamples.putIfAbsent(entry.getKey(), entry.getValue()) == null) {
                    normalizedLegacyData = true;
                }
            }
            if (!materialSamples.isEmpty()) {
                materialSamplesByPlayer.put(player.player(), materialSamples);
            }

            Map<String, ProcessingTimeStats> timings = new HashMap<>();
            for (String encoded : player.processingTimes()) {
                int split = encoded.lastIndexOf('=');
                if (split <= 0 || split >= encoded.length() - 1) {
                    continue;
                }
                String[] values = encoded.substring(split + 1).split(",", 2);
                if (values.length != 2) {
                    continue;
                }
                try {
                    long totalTicks = Long.parseLong(values[0]);
                    int samples = Integer.parseInt(values[1]);
                    if (totalTicks > 0L && samples > 0) {
                        timings.put(encoded.substring(0, split), new ProcessingTimeStats(totalTicks, samples));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (!timings.isEmpty()) {
                processingTimesByPlayer.put(player.player(), timings);
            }
        }
        if (normalizedLegacyData) {
            setDirty();
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

    /** Records that the player has encountered a brewable material without adding a research sample. */
    public boolean recordKnownMaterial(UUID playerId, String ingredientId) {
        if (ingredientId == null || ingredientId.isBlank()) {
            return false;
        }
        boolean changed = knownMaterialsByPlayer
                .computeIfAbsent(playerId, ignored -> new HashSet<>())
                .add(ingredientId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public void recordResearch(UUID playerId, String outcomeKey) {
        researchByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>()).merge(outcomeKey, 1, Integer::sum);
        setDirty();
    }

    /** Counts one completed successful material batch, independent of how many effects it produced. */
    public void recordMaterialSample(UUID playerId, String ingredientId) {
        if (ingredientId == null || ingredientId.isBlank()) {
            return;
        }
        materialSamplesByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .merge(ingredientId, 1, Integer::sum);
        setDirty();
    }

    /** Makes legacy discovery-only records count as their first successful observation without adding repeats. */
    public boolean ensureResearchSample(UUID playerId, String outcomeKey) {
        Map<String, Integer> research = researchByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        if (research.containsKey(outcomeKey)) {
            return false;
        }
        research.put(outcomeKey, 1);
        setDirty();
        return true;
    }

    /** Makes a discovery-only entry count as at least one successful material batch without adding repeats. */
    public boolean ensureMaterialSample(UUID playerId, String ingredientId) {
        if (ingredientId == null || ingredientId.isBlank()) {
            return false;
        }
        Map<String, Integer> samples = materialSamplesByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        if (samples.containsKey(ingredientId)) {
            return false;
        }
        samples.put(ingredientId, 1);
        setDirty();
        return true;
    }

    public void recordProcessingTime(UUID playerId, String ingredientId, int processingTicks) {
        if (processingTicks <= 0) {
            return;
        }
        Map<String, ProcessingTimeStats> timings = processingTimesByPlayer
                .computeIfAbsent(playerId, ignored -> new HashMap<>());
        timings.put(ingredientId, timings.getOrDefault(ingredientId, ProcessingTimeStats.EMPTY).add(processingTicks));
        setDirty();
    }

    public boolean has(UUID playerId, String discovery) {
        return discoveriesByPlayer.getOrDefault(playerId, Set.of()).contains(discovery);
    }

    public Set<String> discoveries(UUID playerId) {
        return Set.copyOf(discoveriesByPlayer.getOrDefault(playerId, Set.of()));
    }

    public boolean hasKnownMaterial(UUID playerId, String ingredientId) {
        return knownMaterialsByPlayer.getOrDefault(playerId, Set.of()).contains(ingredientId);
    }

    public Set<String> knownMaterials(UUID playerId) {
        return Set.copyOf(knownMaterialsByPlayer.getOrDefault(playerId, Set.of()));
    }

    public Map<String, Integer> research(UUID playerId) {
        return Map.copyOf(researchByPlayer.getOrDefault(playerId, Map.of()));
    }

    public Map<String, Integer> materialSamples(UUID playerId) {
        return Map.copyOf(materialSamplesByPlayer.getOrDefault(playerId, Map.of()));
    }

    public int materialSampleCount(UUID playerId, String ingredientId) {
        return materialSamplesByPlayer.getOrDefault(playerId, Map.of()).getOrDefault(ingredientId, 0);
    }

    public Map<String, ProcessingTimeStats> processingTimes(UUID playerId) {
        return Map.copyOf(processingTimesByPlayer.getOrDefault(playerId, Map.of()));
    }

    public ProcessingTimeStats processingTime(UUID playerId, String ingredientId) {
        return processingTimesByPlayer.getOrDefault(playerId, Map.of())
                .getOrDefault(ingredientId, ProcessingTimeStats.EMPTY);
    }

    public int researchTotal(UUID playerId, String ingredientId) {
        return materialSampleCount(playerId, ingredientId);
    }

    private List<PlayerDiscoveries> playerList() {
        Set<UUID> players = new HashSet<>(discoveriesByPlayer.keySet());
        players.addAll(knownMaterialsByPlayer.keySet());
        players.addAll(researchByPlayer.keySet());
        players.addAll(materialSamplesByPlayer.keySet());
        players.addAll(processingTimesByPlayer.keySet());
        return players.stream().sorted().map(playerId -> {
            List<String> discoveries = discoveriesByPlayer.getOrDefault(playerId, Set.of()).stream().sorted().toList();
            List<String> knownMaterials = knownMaterialsByPlayer.getOrDefault(playerId, Set.of())
                    .stream().sorted().toList();
            List<String> research = researchByPlayer.getOrDefault(playerId, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toList();
            List<String> materialSamples = materialSamplesByPlayer.getOrDefault(playerId, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toList();
            List<String> processingTimes = processingTimesByPlayer.getOrDefault(playerId, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue().totalTicks() + "," + entry.getValue().samples())
                    .toList();
            return new PlayerDiscoveries(
                    playerId, discoveries, knownMaterials, research, materialSamples, processingTimes);
        }).toList();
    }

    private static Map<String, Integer> decodePositiveCounts(List<String> encodedCounts) {
        Map<String, Integer> result = new HashMap<>();
        for (String encoded : encodedCounts) {
            int split = encoded.lastIndexOf('=');
            if (split <= 0 || split >= encoded.length() - 1) {
                continue;
            }
            try {
                int count = Integer.parseInt(encoded.substring(split + 1));
                if (count > 0) {
                    result.put(encoded.substring(0, split), count);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public record ProcessingTimeStats(long totalTicks, int samples) {
        public static final ProcessingTimeStats EMPTY = new ProcessingTimeStats(0L, 0);

        public ProcessingTimeStats add(int ticks) {
            return ticks <= 0 ? this : new ProcessingTimeStats(totalTicks + ticks, samples + 1);
        }

        public int averageTicks() {
            return samples <= 0 ? 0 : (int) Math.round(totalTicks / (double) samples);
        }
    }

    private record PlayerDiscoveries(
            UUID player,
            List<String> discoveries,
            List<String> knownMaterials,
            List<String> research,
            List<String> materialSamples,
            List<String> processingTimes
    ) {
        private PlayerDiscoveries {
            discoveries = List.copyOf(discoveries == null ? List.of() : discoveries);
            knownMaterials = List.copyOf(knownMaterials == null ? List.of() : knownMaterials);
            research = List.copyOf(research == null ? List.of() : research);
            materialSamples = List.copyOf(materialSamples == null ? List.of() : materialSamples);
            processingTimes = List.copyOf(processingTimes == null ? List.of() : processingTimes);
        }
    }
}
