package dev.totem.alchemy.discovery;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void legacyAlchemyIdentifiersRewriteWhenPersistentDiscoveriesDecode() {
        UUID playerId = UUID.fromString("c3f17f0a-2ca4-48de-979f-9f81823bf6fe");
        String legacyMaterial = "deadrecall:cocoa_powder";
        String legacyDiscovery = legacyMaterial + ">deadrecall:cherry_swiftness";
        AlchemyDiscoverySavedData legacy = new AlchemyDiscoverySavedData();
        legacy.record(playerId, legacyDiscovery);
        legacy.recordKnownMaterial(playerId, legacyMaterial);
        legacy.recordResearch(playerId, legacyDiscovery);
        legacy.recordMaterialSample(playerId, legacyMaterial);
        legacy.recordProcessingTime(playerId, legacyMaterial, 200);

        var encoded = AlchemyDiscoverySavedData.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow();
        AlchemyDiscoverySavedData restored =
                AlchemyDiscoverySavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        String canonicalMaterial = "totem:alchemy/cocoa_powder";
        String canonicalDiscovery = canonicalMaterial + ">totem:alchemy/cherry_swiftness";
        assertTrue(restored.has(playerId, canonicalDiscovery));
        assertTrue(restored.hasKnownMaterial(playerId, canonicalMaterial));
        assertEquals(1, restored.research(playerId).getOrDefault(canonicalDiscovery, 0));
        assertEquals(1, restored.materialSampleCount(playerId, canonicalMaterial));
        assertEquals(200, restored.processingTime(playerId, canonicalMaterial).totalTicks());
    }
}
