package dev.totem.alchemy.resource;

import dev.totem.alchemy.TotemAlchemy;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class AlchemyBuiltinPacks {
    public static final Identifier TOTEM_ALCHEMY =
            Identifier.fromNamespaceAndPath("totem-alchemy", "totem_alchemy");
    public static final Identifier MINECRAFT_ALCHEMY =
            Identifier.fromNamespaceAndPath("totem-alchemy", "minecraft_alchemy");

    private AlchemyBuiltinPacks() {
    }

    private static final String OFF_OFF_FIXTURE_ENV = "TOTEM_ALCHEMY_GAMETEST_OFF_OFF";
    private static final String PACK_MATRIX_FIXTURE_ENV = "TOTEM_ALCHEMY_GAMETEST_PACKS";

    public static void register() {
        // Development-only fixture. Values: ON_ON, ON_OFF, OFF_ON, OFF_OFF.
        // Legacy OFF/OFF switch remains supported for the M10-T07 CI lane.
        String fixture = System.getenv(PACK_MATRIX_FIXTURE_ENV);
        if (FabricLoader.getInstance().isDevelopmentEnvironment()
                && fixture != null && !fixture.isBlank()) {
            if (!java.util.Set.of("ON_ON", "ON_OFF", "OFF_ON", "OFF_OFF").contains(fixture)) {
                throw new IllegalArgumentException("Invalid " + PACK_MATRIX_FIXTURE_ENV + ": " + fixture);
            }
            TotemAlchemy.LOGGER.info("[M10-T08] GameTest pack matrix fixture: {}", fixture);
            registerSelected(fixture);
            return;
        }
        if (FabricLoader.getInstance().isDevelopmentEnvironment()
                && "true".equalsIgnoreCase(System.getenv(OFF_OFF_FIXTURE_ENV))) {
            TotemAlchemy.LOGGER.info("[M10-T07] OFF/OFF GameTest fixture: optional builtin packs not registered");
            return;
        }
        registerSelected("ON_ON");
    }

    private static void registerSelected(String fixture) {
        ModContainer container = FabricLoader.getInstance()
                .getModContainer("totem-alchemy")
                .orElseThrow(() -> new IllegalStateException("TotemAlchemy mod container is unavailable"));

        if (fixture.startsWith("ON_")) register(
                TOTEM_ALCHEMY,
                container,
                Component.literal("TotemAlchemy — Totem Alchemy")
        );
        if (fixture.endsWith("_ON")) register(
                MINECRAFT_ALCHEMY,
                container,
                Component.literal("TotemAlchemy — Minecraft Alchemy")
        );
    }

    private static void register(Identifier id, ModContainer container, Component displayName) {
        if (!ResourceLoader.registerBuiltinPack(id, container, displayName, PackActivationType.NORMAL)) {
            TotemAlchemy.LOGGER.warn("Failed to register built-in datapack {}", id);
        }
    }
}
