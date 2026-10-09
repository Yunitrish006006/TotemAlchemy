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

    public static void register() {
        // Explicit CI-only fixture: production pack registration remains unchanged.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()
                && "true".equalsIgnoreCase(System.getenv(OFF_OFF_FIXTURE_ENV))) {
            TotemAlchemy.LOGGER.info("[M10-T07] OFF/OFF GameTest fixture: optional builtin packs not registered");
            return;
        }
        ModContainer container = FabricLoader.getInstance()
                .getModContainer("totem-alchemy")
                .orElseThrow(() -> new IllegalStateException("TotemAlchemy mod container is unavailable"));

        register(
                TOTEM_ALCHEMY,
                container,
                Component.literal("TotemAlchemy — Totem Alchemy")
        );
        register(
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
