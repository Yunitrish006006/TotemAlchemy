package dev.totem.alchemy.discovery;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class AlchemyDiscoverySavedDataTest {
    @Test
    void legacyDiscoveryOnlySaveRestoresOneResearchSampleWithoutInventingTiming() {
        UUID playerId = UUID.fromString("83aa9ee8-ef91-4a40-a46b-26912199ce32");
        String key = "minecraft:nether_wart>minecraft:awkward";
        AlchemyDiscoverySavedData legacy = new AlchemyDiscoverySavedData();
        legacy.record(playerId, key);

        var encoded = AlchemyDiscoverySavedData.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow();
        AlchemyDiscoverySavedData restored =
                AlchemyDiscoverySavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(1, restored.research(playerId).getOrDefault(key, 0));
        assertEquals(1, restored.researchTotal(playerId, "minecraft:nether_wart"));
        assertEquals(1, restored.materialSampleCount(playerId, "minecraft:nether_wart"));
        assertEquals(AlchemyDiscoverySavedData.ProcessingTimeStats.EMPTY,
                restored.processingTime(playerId, "minecraft:nether_wart"));
    }

    @Test
    void ensuringLegacySampleDoesNotIncrementExistingResearch() {
        UUID playerId = UUID.fromString("ad5f1b96-e933-4147-aafe-bf4bcd7c506c");
        String key = "minecraft:nether_wart>minecraft:awkward";
        AlchemyDiscoverySavedData data = new AlchemyDiscoverySavedData();
        data.recordResearch(playerId, key);

        assertFalse(data.ensureResearchSample(playerId, key));
        assertEquals(1, data.research(playerId).getOrDefault(key, 0));
    }

    @Test
    void legacyOutcomeCountsSeedMissingMaterialSamplesFromTheirSum() {
        UUID playerId = UUID.fromString("076e8772-2ca8-4de8-b77c-2918d22469f8");
        AlchemyDiscoverySavedData legacy = new AlchemyDiscoverySavedData();
        legacy.recordResearch(playerId, "minecraft:sugar>minecraft:swiftness");
        legacy.recordResearch(playerId, "minecraft:sugar>minecraft:swiftness");
        legacy.recordResearch(playerId, "minecraft:sugar>minecraft:slowness");

        var encoded = AlchemyDiscoverySavedData.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow();
        AlchemyDiscoverySavedData restored =
                AlchemyDiscoverySavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(3, restored.materialSampleCount(playerId, "minecraft:sugar"));
    }

    @Test
    void explicitMaterialSamplesRemainOneWhenOneBatchHasSeveralOutcomes() {
        UUID playerId = UUID.fromString("88991d70-2976-4d66-a2ca-0cfd412f1892");
        AlchemyDiscoverySavedData data = new AlchemyDiscoverySavedData();
        data.recordMaterialSample(playerId, "minecraft:sugar");
        data.recordResearch(playerId, "minecraft:sugar>minecraft:swiftness");
        data.recordResearch(playerId, "minecraft:sugar>minecraft:slowness");

        var encoded = AlchemyDiscoverySavedData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        AlchemyDiscoverySavedData restored =
                AlchemyDiscoverySavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(1, restored.materialSampleCount(playerId, "minecraft:sugar"));
        assertEquals(1, restored.research(playerId)
                .getOrDefault("minecraft:sugar>minecraft:swiftness", 0));
        assertEquals(1, restored.research(playerId)
                .getOrDefault("minecraft:sugar>minecraft:slowness", 0));
    }
}
