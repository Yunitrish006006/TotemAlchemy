package dev.totem.alchemy.liquid;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlchemyLiquidsTest {
    @Test
    void waterResolvesAsRegisteredNeutralBaseline() {
        AlchemyLiquid water = AlchemyLiquids.get(AlchemyLiquids.WATER_ID).orElseThrow();

        assertSame(AlchemyLiquids.WATER, water);
        assertEquals(Identifier.fromNamespaceAndPath("minecraft", "water"), water.id());
        assertSame(LiquidProperties.NEUTRAL, water.properties());
        assertEquals(1.0D, water.properties().stabilityMultiplier());
        assertEquals(1.0D, water.properties().reactionSpeedMultiplier());
        assertEquals(1.0D, water.properties().durationMultiplier());
        assertEquals(1.0D, water.properties().potencyMultiplier());
    }

    @Test
    void registryContainsOnlyWaterAtTheM8T02Boundary() {
        assertEquals(1, AlchemyLiquids.all().size());
        assertTrue(AlchemyLiquids.all().containsKey(AlchemyLiquids.WATER_ID));
        assertFalse(AlchemyLiquids.get(Identifier.fromNamespaceAndPath("minecraft", "milk")).isPresent());
        assertFalse(AlchemyLiquids.get(Identifier.fromNamespaceAndPath("minecraft", "honey")).isPresent());
    }

    @Test
    void nullOrUnknownLiquidIdsDoNotResolve() {
        assertTrue(AlchemyLiquids.get(null).isEmpty());
        assertTrue(AlchemyLiquids.get(
                Identifier.fromNamespaceAndPath("totem", "alchemy/unknown_liquid")
        ).isEmpty());
    }
}
