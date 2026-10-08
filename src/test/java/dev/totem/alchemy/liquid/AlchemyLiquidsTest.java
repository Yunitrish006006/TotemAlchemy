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
    void milkResolvesAsRegisteredNeutralLiquid() {
        AlchemyLiquid milk = AlchemyLiquids.get(AlchemyLiquids.MILK_ID).orElseThrow();

        assertSame(AlchemyLiquids.MILK, milk);
        assertEquals(Identifier.fromNamespaceAndPath("minecraft", "milk"), milk.id());
        assertSame(LiquidProperties.NEUTRAL, milk.properties());
        assertEquals(1.0D, milk.properties().stabilityMultiplier());
        assertEquals(1.0D, milk.properties().reactionSpeedMultiplier());
        assertEquals(1.0D, milk.properties().durationMultiplier());
        assertEquals(1.0D, milk.properties().potencyMultiplier());
    }

    @Test
    void honeyResolvesAsRegisteredNeutralLiquid() {
        AlchemyLiquid honey = AlchemyLiquids.get(AlchemyLiquids.HONEY_ID).orElseThrow();

        assertSame(AlchemyLiquids.HONEY, honey);
        assertEquals(Identifier.fromNamespaceAndPath("minecraft", "honey"), honey.id());
        assertSame(LiquidProperties.NEUTRAL, honey.properties());
        assertEquals(1.0D, honey.properties().stabilityMultiplier());
        assertEquals(1.0D, honey.properties().reactionSpeedMultiplier());
        assertEquals(1.0D, honey.properties().durationMultiplier());
        assertEquals(1.0D, honey.properties().potencyMultiplier());
    }

    @Test
    void registryContainsWaterMilkAndHoneyAtTheM8T04Boundary() {
        assertEquals(3, AlchemyLiquids.all().size());
        assertTrue(AlchemyLiquids.all().containsKey(AlchemyLiquids.WATER_ID));
        assertTrue(AlchemyLiquids.all().containsKey(AlchemyLiquids.MILK_ID));
        assertTrue(AlchemyLiquids.all().containsKey(AlchemyLiquids.HONEY_ID));
    }

    @Test
    void nullOrUnknownLiquidIdsDoNotResolve() {
        assertTrue(AlchemyLiquids.get(null).isEmpty());
        assertTrue(AlchemyLiquids.get(
                Identifier.fromNamespaceAndPath("totem", "alchemy/unknown_liquid")
        ).isEmpty());
    }
}
