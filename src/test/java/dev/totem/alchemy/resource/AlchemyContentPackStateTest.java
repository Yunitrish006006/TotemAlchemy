package dev.totem.alchemy.resource;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlchemyContentPackStateTest {
    private static final String TOTEM_MARKER = "alchemy_pack_state/totem_alchemy.json";
    private static final String MINECRAFT_MARKER = "alchemy_pack_state/minecraft_alchemy.json";

    @Test
    void detectsEveryOptionalPackCombination() {
        assertState(Set.of(), false, false, 10L);
        assertState(Set.of(TOTEM_MARKER), true, false, 11L);
        assertState(Set.of(MINECRAFT_MARKER), false, true, 12L);
        assertState(Set.of(TOTEM_MARKER, MINECRAFT_MARKER), true, true, 13L);
    }

    private static void assertState(
            Set<String> availableMarkerPaths,
            boolean expectedTotem,
            boolean expectedMinecraft,
            long revision
    ) {
        AlchemyContentPackState.Snapshot snapshot = AlchemyContentPackState.detect(
                id -> availableMarkerPaths.contains(id.getPath()),
                revision
        );

        assertEquals(expectedTotem, snapshot.totemAlchemyEnabled());
        assertEquals(expectedMinecraft, snapshot.minecraftAlchemyEnabled());
        assertEquals(revision, snapshot.revision());
    }
}
